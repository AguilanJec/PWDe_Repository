package com.pwde.app.sensors.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.prefs.SettingsRepository
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

enum class MicAvailability(val label: String) {
    AVAILABLE("Microphone ready"),
    NO_PERMISSION("Microphone permission is off"),
    NO_RECOGNIZER("No speech recognition service on this phone"),
    SERVICE_ERROR("Speech recognition keeps failing"),
}

enum class VoiceInputSource { MIC, TEXT }

/** One transcript (partial or final). [command] is set only when it fired. */
data class VoiceResult(
    val transcript: String,
    val isFinal: Boolean,
    val command: VoiceCommand?,
    val source: VoiceInputSource,
)

data class VoiceState(
    /** The user's on/off setting. */
    val enabled: Boolean = false,
    val availability: MicAvailability = MicAvailability.AVAILABLE,
    val listening: Boolean = false,
    /** Mic input level, 0–1, for the waveform. */
    val level: Float = 0f,
    val lastTranscript: String? = null,
    val lastCommand: VoiceCommand? = null,
    val matchMode: VoiceMatchMode = VoiceMatchMode.WORD_ANYWHERE,
    val activationMode: VoiceActivationMode = VoiceActivationMode.IMMEDIATE,
    /** Everything that can be said right now: screen commands first, then global ones. */
    val commands: List<VoiceCommand> = emptyList(),
) {
    /** Typed commands take over when the mic can't be used. */
    val usesTextFallback: Boolean get() = availability != MicAvailability.AVAILABLE
}

/**
 * App-scoped voice commands (no system-wide listening). Listens only while voice is on and
 * someone observes [state] — i.e. while PWDe is on screen.
 */
interface VoiceCommandManager {
    val state: StateFlow<VoiceState>
    val results: SharedFlow<VoiceResult>
    val hasMicPermission: Boolean

    fun refreshPermissions()

    /** Typed fallback: same matching, same command catalog. */
    fun submitText(text: String)

    /** Commands for whatever [owner] (a screen) is showing. Replaces that owner's previous list. */
    fun setScreenCommands(owner: Any, commands: List<VoiceCommand>)

    fun clearScreenCommands(owner: Any)
}

class AndroidVoiceCommandManager(
    context: Context,
    private val controlsRepository: ControlsRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : VoiceCommandManager {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VoiceState())
    private val _results = MutableSharedFlow<VoiceResult>(extraBufferCapacity = 16)
    private val permissionTick = MutableStateFlow(0)
    private val screenCommands = MutableStateFlow<Map<Any, List<VoiceCommand>>>(emptyMap())
    private val gate = VoiceActivationGate()

    private var config = ControlConfig()
    private var recognizer: SpeechRecognizer? = null
    private var wantListening = false
    private var restartJob: Job? = null
    private var consecutiveErrors = 0

    override val state: StateFlow<VoiceState> = _state.asStateFlow()
    override val results: SharedFlow<VoiceResult> = _results.asSharedFlow()

    override val hasMicPermission: Boolean
        get() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    init {
        scope.launch {
            _state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { observed ->
                if (!observed) {
                    stopListening()
                    return@collectLatest
                }
                try {
                    combine(
                        controlsRepository.config,
                        permissionTick,
                        screenCommands,
                        settingsRepository.settings.map { it.pwdeEnabled }.distinctUntilChanged(),
                    ) { latest, _, screens, pwdeEnabled -> Triple(latest, screens, pwdeEnabled) }
                        .collect { (latest, screens, pwdeEnabled) ->
                            config = latest
                            val availability = availability()
                            _state.update {
                                it.copy(
                                    enabled = latest.voiceEnabled,
                                    availability = availability,
                                    matchMode = latest.voiceMatchMode,
                                    activationMode = latest.voiceActivationMode,
                                    commands = screens.values.flatten() + globalCommands(latest),
                                )
                            }
                            // The home screen's master switch wins: PWDe off means nothing listens.
                            val canListen = pwdeEnabled && latest.voiceEnabled && availability == MicAvailability.AVAILABLE
                            if (canListen) startListening() else stopListening()
                        }
                } finally {
                    stopListening()
                }
            }
        }
    }

    override fun refreshPermissions() {
        consecutiveErrors = 0
        permissionTick.value++
    }

    override fun submitText(text: String) {
        if (text.isBlank()) return
        scope.launch {
            val command = CommandMatcher.match(text, allCommands(), config.voiceMatchMode)
            publish(text.trim(), isFinal = true, command = command, source = VoiceInputSource.TEXT)
        }
    }

    override fun setScreenCommands(owner: Any, commands: List<VoiceCommand>) {
        screenCommands.update { it + (owner to commands) }
    }

    override fun clearScreenCommands(owner: Any) {
        screenCommands.update { it - owner }
    }

    private fun globalCommands(config: ControlConfig) = StandardCommands.all + StandardCommands.shortcuts(config.voiceShortcuts)

    private fun allCommands() = screenCommands.value.values.flatten() + globalCommands(config)

    private fun availability(): MicAvailability = when {
        !hasMicPermission -> MicAvailability.NO_PERMISSION
        !SpeechRecognizer.isRecognitionAvailable(appContext) -> MicAvailability.NO_RECOGNIZER
        consecutiveErrors >= MAX_CONSECUTIVE_ERRORS -> MicAvailability.SERVICE_ERROR
        else -> MicAvailability.AVAILABLE
    }

    private fun startListening() {
        if (wantListening) return
        wantListening = true
        listenOnce()
    }

    private fun stopListening() {
        wantListening = false
        restartJob?.cancel()
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
        gate.reset()
        _state.update { it.copy(listening = false, level = 0f) }
    }

    private fun listenOnce() {
        if (!wantListening) return
        val r = recognizer ?: runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
            ?.also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
        if (r == null) {
            _state.update { it.copy(availability = MicAvailability.NO_RECOGNIZER) }
            stopListening()
            return
        }
        gate.reset()
        runCatching { r.startListening(recognizerIntent()) }.onFailure {
            Log.w(TAG, "startListening failed", it)
            onRecognizerError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private fun restartAfter(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            listenOnce()
        }
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
    }

    private fun publish(transcript: String, isFinal: Boolean, command: VoiceCommand?, source: VoiceInputSource) {
        _state.update { it.copy(lastTranscript = transcript, lastCommand = command ?: it.lastCommand) }
        _results.tryEmit(VoiceResult(transcript, isFinal, command, source))
    }

    private fun onHeard(bundle: Bundle?, isFinal: Boolean) {
        val hypotheses = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }.orEmpty()
        if (hypotheses.isEmpty()) {
            if (isFinal) gate.reset()
            return
        }
        val match = CommandMatcher.match(hypotheses, allCommands(), config.voiceMatchMode)
        val fired = gate.offer(match, isFinal, config.voiceActivationMode)
        publish(hypotheses.first(), isFinal, fired, VoiceInputSource.MIC)
    }

    private fun onRecognizerError(error: Int) {
        _state.update { it.copy(listening = false, level = 0f) }
        gate.reset()
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                consecutiveErrors = 0
                restartAfter(RESTART_DELAY_MS)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                _state.update { it.copy(availability = MicAvailability.NO_PERMISSION) }
                stopListening()
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                recognizer?.destroy()
                recognizer = null
                countFailureAndRetry()
            }
            else -> countFailureAndRetry()
        }
    }

    private fun countFailureAndRetry() {
        consecutiveErrors++
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            _state.update { it.copy(availability = MicAvailability.SERVICE_ERROR) }
            stopListening()
        } else {
            restartAfter(min(MAX_BACKOFF_MS, RESTART_DELAY_MS shl consecutiveErrors))
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.update { it.copy(listening = true) }
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // Typical rmsdB runs from about -2 (silence) to 10 (loud speech).
            _state.update { it.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.update { it.copy(level = 0f) }
        }

        override fun onError(error: Int) = onRecognizerError(error)

        override fun onResults(results: Bundle?) {
            consecutiveErrors = 0
            onHeard(results, isFinal = true)
            _state.update { it.copy(listening = false, level = 0f) }
            restartAfter(RESTART_DELAY_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) = onHeard(partialResults, isFinal = false)

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {
        private const val TAG = "VoiceCommands"
        private const val RESTART_DELAY_MS = 250L
        private const val MAX_BACKOFF_MS = 4_000L
        private const val MAX_CONSECUTIVE_ERRORS = 6
    }
}
