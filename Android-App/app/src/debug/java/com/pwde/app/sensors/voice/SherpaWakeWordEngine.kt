package com.pwde.app.sensors.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Phrases the Testing Station starts with: the words PWDe wants to hear most reliably. Word choice
 * matters more than tuning (see the README on what an open-vocabulary spotter does well), but
 * these are the ones the team asked to be easy to hit.
 */
val DEFAULT_WAKE_WORDS: List<String> = listOf("first", "second", "third", "joystick", "cursor")

/** Those phrases start at the most eager preset, so a quiet or rushed attempt still lands. */
val DEFAULT_WAKE_TUNING: Map<String, WakeWordTuning> =
    DEFAULT_WAKE_WORDS.associateWith { WakeWordSensitivity.MAX.tuning }

private const val MODEL_DIR = "models/sherpa-kws-zipformer-gigaspeech-3.3M-2024-01-01"
private const val ENCODER = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
private const val DECODER = "decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
private const val JOINER = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
private const val TOKENS = "tokens.txt"
private const val BPE_MODEL = "bpe.model"
private const val MODEL_TYPE = "zipformer2"

/** The keyword list is read from a file at construction, so it is generated next to the model. */
private const val KEYWORDS_FILE = "keywords.txt"

private const val SAMPLE_RATE = 16000
private const val FEATURE_DIM = 80
private const val NUM_THREADS = 2

/** Samples handed to the spotter per read: 512 is 32 ms at 16 kHz. */
private const val FEED_SAMPLES = 512

/** AudioRecord buffer, in [FEED_SAMPLES] frames, to ride out CPU spikes without dropping audio. */
private const val BUFFER_FRAMES = 8

/** How long to let the app-wide recognizer release the mic before recording starts. */
private const val MIC_SETTLE_MS = 150L

/** How often the panel's level/load readouts are refreshed. */
private const val LEVEL_PUBLISH_MS = 250L

private const val TAG = "SherpaWakeWord"

/**
 * Debug builds get the real engine. The release source set has a no-op twin of this function, which
 * keeps the sherpa-onnx model and its ~32 MB of native libraries out of a release APK.
 */
fun createWakeWordEngine(context: Context, micArbiter: MicArbiter): WakeWordEngine =
    SherpaWakeWordEngine(context, micArbiter)

/**
 * sherpa-onnx keyword spotting: **any** phrase can be listened for, as long as the model can spell
 * it — English, fully on-device, no account and no per-phrase training. The GigaSpeech 3.3M model
 * decides in ~320 ms chunks.
 *
 * Phrases are tokenized on device ([KeywordList] with [SentencePieceUnigramTokenizer]), replacing
 * upstream's Python `text2token` step, so the user can type a new wake word at runtime.
 *
 * The spotter records on its own behalf, so [start] takes the microphone through [MicArbiter] and
 * [stop] gives it back.
 */
class SherpaWakeWordEngine(
    context: Context,
    private val micArbiter: MicArbiter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : WakeWordEngine {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(WakeWordState(availability = availability()))
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private val _detections = MutableSharedFlow<WakeWordDetection>(extraBufferCapacity = 8)
    override val detections: SharedFlow<WakeWordDetection> = _detections.asSharedFlow()

    /**
     * Bumped whenever the phrases change or playback should stop. The capture loop reads it each
     * pass and exits, because a blocking `AudioRecord.read` can't be interrupted from outside.
     */
    private val session = AtomicInteger(0)

    private var listenJob: Job? = null

    override fun start(
        phrases: List<String>,
        tuning: Map<String, WakeWordTuning>,
        spotter: WakeWordSpotterTuning,
    ) {
        // Re-read the microphone permission every time, so granting it and pressing start again is
        // enough to get going.
        val availability = availability()
        if (availability != WakeWordAvailability.READY) {
            _state.update { it.copy(availability = availability, error = null) }
            return
        }
        val wanted = phrases.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) {
            _state.update { it.copy(availability = availability, error = "Type a phrase to listen for") }
            return
        }

        // Starting again is a restart, not a second listener: the old loop is told to exit and the
        // new one waits for it to let go of the microphone first.
        val previous = listenJob
        val mySession = session.incrementAndGet()
        _state.update {
            it.copy(
                availability = availability,
                running = true,
                detections = 0,
                phrases = wanted,
                unsupported = emptyList(),
                lastPhrase = null,
                error = null,
            )
        }
        listenJob = scope.launch {
            previous?.join()
            var prepared: Prepared? = null
            try {
                prepared = prepare(wanted, tuning, spotter)
                _state.update { it.copy(unsupported = prepared.unsupported) }
                capture(mySession, prepared)
            } catch (t: Throwable) {
                // A native library missing for this ABI, a bad model, or an unusable microphone.
                Log.w(TAG, "Wake word engine stopped", t)
                _state.update {
                    it.copy(
                        error = t.message ?: t.toString(),
                        availability = if (prepared == null) WakeWordAvailability.FAILED else it.availability,
                    )
                }
            } finally {
                prepared?.spotter?.release()
                micArbiter.releaseFromWakeWord()
                if (mySession == session.get()) _state.update { it.copy(running = false) }
            }
        }
    }

    override fun stop() {
        session.incrementAndGet()
        micArbiter.releaseFromWakeWord()
        _state.update { it.copy(running = false) }
    }

    /** The live spotter plus what the panel needs to explain a hit or a rejection. */
    private class Prepared(
        val spotter: KeywordSpotter,
        val phrasesByLabel: Map<String, String>,
        val unsupported: List<String>,
    )

    /** Heavy, so it never runs on the main thread: ~6 MB of weights and the tokenizer. */
    private fun prepare(
        phrases: List<String>,
        tuning: Map<String, WakeWordTuning>,
        spotterTuning: WakeWordSpotterTuning,
    ): Prepared {
        val dir = copyModelToFiles()
        val validTokens =
            appContext.assets.open("$MODEL_DIR/$TOKENS").bufferedReader().use {
                KeywordList.readTokenSymbols(it)
            }
        val tokenizer =
            SentencePieceUnigramTokenizer.fromModelProto(
                appContext.assets.open("$MODEL_DIR/$BPE_MODEL").use { it.readBytes() },
            )
        val keywords = KeywordList.build(phrases, tokenizer, validTokens, tuning)
        if (keywords.lines.isEmpty()) {
            throw IllegalStateException(
                "This model can't spot " + keywords.unsupported.joinToString(", ") { "\"$it\"" },
            )
        }
        File(dir, KEYWORDS_FILE).writeText(keywords.fileContent())
        // Hand-typed numbers land here, so they are pulled into the range the native spotter takes.
        val spotter = spotterTuning.clamped()
        val keywordSpotter =
            KeywordSpotter(
                null,
                KeywordSpotterConfig(
                    // Spotters want un-dithered audio: dither is a recognition training augmentation.
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM, dither = 0f),
                    modelConfig =
                        OnlineModelConfig(
                            transducer =
                                OnlineTransducerModelConfig(
                                    encoder = File(dir, ENCODER).absolutePath,
                                    decoder = File(dir, DECODER).absolutePath,
                                    joiner = File(dir, JOINER).absolutePath,
                                ),
                            tokens = File(dir, TOKENS).absolutePath,
                            numThreads = NUM_THREADS,
                            provider = "cpu",
                            modelType = MODEL_TYPE,
                        ),
                    maxActivePaths = spotter.activePaths,
                    keywordsFile = File(dir, KEYWORDS_FILE).absolutePath,
                    keywordsScore = spotter.score,
                    keywordsThreshold = spotter.threshold,
                    numTrailingBlanks = spotter.trailingBlanks,
                ),
            )
        return Prepared(keywordSpotter, keywords.phrasesByLabel, keywords.unsupported)
    }

    /** Copies the runtime files out of assets once; the spotter needs real paths, not asset ids. */
    private fun copyModelToFiles(): File {
        val dir = File(appContext.filesDir, MODEL_DIR)
        dir.mkdirs()
        for (name in listOf(ENCODER, DECODER, JOINER, TOKENS)) {
            val target = File(dir, name)
            val assetPath = "$MODEL_DIR/$name"
            // Uncompressed assets report a length, so an identical copy can be reused as-is.
            val assetLength =
                try {
                    appContext.assets.openFd(assetPath).use { it.length }
                } catch (compressed: Exception) {
                    -1L
                }
            if (assetLength >= 0 && target.length() == assetLength) {
                continue
            }
            appContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dir
    }

    /** Reads the microphone and feeds the spotter until [stop] bumps the session. */
    private suspend fun capture(mySession: Int, prepared: Prepared) {
        val minBufferBytes =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferBytes <= 0) {
            throw IllegalStateException("This device has no 16 kHz microphone the spotter can use")
        }
        // Take the mic before opening it, and give the app-wide recognizer a moment to let go:
        // it releases asynchronously, and audio captured before that is silence.
        micArbiter.takeForWakeWord()
        delay(MIC_SETTLE_MS)
        val record =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                // A slack buffer: face tracking and the spotter share the CPU, and an overrun drops
                // audio rather than delaying it, which shows up as a missed phrase.
                maxOf(minBufferBytes, FEED_SAMPLES * 2 * BUFFER_FRAMES),
            )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("The microphone could not be opened")
        }
        val stream = prepared.spotter.createStream()
        val pcm = ShortArray(FEED_SAMPLES)
        var smoothedRtf = 0f
        var lastPublishMs = 0L
        try {
            // Capture is real-time work; without this the OS may deprioritise it under load.
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            record.startRecording()
            while (mySession == session.get()) {
                val read = record.read(pcm, 0, pcm.size)
                if (read <= 0) {
                    continue
                }
                val samples = FloatArray(read) { i -> pcm[i] / 32768f }
                var sumSquares = 0f
                for (sample in samples) {
                    sumSquares += sample * sample
                }
                val level = sqrt(sumSquares / samples.size)

                val decodeStartedNs = SystemClock.elapsedRealtimeNanos()
                stream.acceptWaveform(samples, SAMPLE_RATE)
                while (prepared.spotter.isReady(stream)) {
                    prepared.spotter.decode(stream)
                }
                val decodeMs = (SystemClock.elapsedRealtimeNanos() - decodeStartedNs) / 1_000_000f
                val frameRtf = decodeMs / (read * 1000f / SAMPLE_RATE)
                smoothedRtf = if (smoothedRtf == 0f) frameRtf else smoothedRtf * 0.9f + frameRtf * 0.1f

                // The result is the keyword's label, e.g. "k0", not the phrase the user typed.
                val label = prepared.spotter.getResult(stream).keyword
                if (label.isNotEmpty()) {
                    // Reset so an immediate repeat of the same phrase is detected on its own.
                    prepared.spotter.reset(stream)
                    onDetection(prepared.phrasesByLabel[label] ?: label)
                }

                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastPublishMs >= LEVEL_PUBLISH_MS) {
                    lastPublishMs = nowMs
                    _state.update { it.copy(level = level, realTimeFactor = smoothedRtf) }
                }
            }
        } finally {
            try {
                record.stop()
            } catch (ignored: IllegalStateException) {
                // Never started.
            }
            record.release()
            stream.release()
            _state.update { it.copy(level = 0f, realTimeFactor = 0f) }
        }
    }

    private fun onDetection(phrase: String) {
        _state.update { it.copy(detections = it.detections + 1, lastPhrase = phrase) }
        _detections.tryEmit(WakeWordDetection(phrase, System.currentTimeMillis()))
    }

    private fun availability(): WakeWordAvailability =
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            WakeWordAvailability.READY
        } else {
            WakeWordAvailability.NO_PERMISSION
        }
}
