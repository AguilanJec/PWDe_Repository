package com.pwde.app.ui.testingstation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.sensors.face.FaceState
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.TrackingStatus
import com.pwde.app.sensors.voice.DEFAULT_WAKE_WORDS
import com.pwde.app.sensors.voice.SherpaInGameVoiceEngine
import com.pwde.app.sensors.voice.VoiceCommandManager
import com.pwde.app.sensors.voice.VoiceResult
import com.pwde.app.sensors.voice.VoiceState
import com.pwde.app.sensors.voice.WakeWordDetection
import com.pwde.app.sensors.voice.WakeWordEngine
import com.pwde.app.sensors.voice.WakeWordSensitivity
import com.pwde.app.sensors.voice.WakeWordSpotterTuning
import com.pwde.app.sensors.voice.WakeWordState
import com.pwde.app.sensors.voice.WakeWordTuning
import com.pwde.app.sensors.voice.WakeWordTuningStore
import com.pwde.app.sensors.voice.stepTuningValue
import com.pwde.app.ui.common.FaceTrackingViewModel
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.CameraFeed
import com.pwde.app.ui.components.SegmentedToggle
import com.pwde.app.ui.controls.GestureCatalog
import com.pwde.app.ui.components.CursorPad
import com.pwde.app.ui.components.DemoModeBanner
import com.pwde.app.ui.components.GestureMeter
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.IconBadge
import com.pwde.app.ui.components.JoystickView
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.PwdeTextField
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.fmt
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Debug-only: live readouts from every input pipeline, for development validation. */
class TestingStationViewModel(
    faceTracking: FaceTrackingManager,
    voiceCommandManager: VoiceCommandManager,
    private val wakeWordEngine: WakeWordEngine,
    private val tuningStore: WakeWordTuningStore,
) : FaceTrackingViewModel(faceTracking) {
    val voice: StateFlow<VoiceState> = voiceCommandManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), voiceCommandManager.state.value)

    private val _lastResult = MutableStateFlow<VoiceResult?>(null)
    val lastResult: StateFlow<VoiceResult?> = _lastResult.asStateFlow()

    val wakeWord: StateFlow<WakeWordState> = wakeWordEngine.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), wakeWordEngine.state.value)

    /** Recent hits, newest first, so one can't be missed between glances at the screen. */
    private val _wakeWordLog = MutableStateFlow<List<WakeWordDetection>>(emptyList())
    val wakeWordLog: StateFlow<List<WakeWordDetection>> = _wakeWordLog.asStateFlow()

    /** Phrases the spotter listens for, as the user typed them. */
    private val _wakeWords = MutableStateFlow(DEFAULT_WAKE_WORDS)
    val wakeWords: StateFlow<List<String>> = _wakeWords.asStateFlow()

    /**
     * Per-phrase boost/threshold, shared with gameplay's button voice through [tuningStore]. A phrase
     * missing from this map starts on [WakeWordTuningStore.DEFAULT_PHRASE_TUNING]; one mapped to
     * [WakeWordTuning.INHERIT] uses [spotterTuning] instead. Editable by hand; see [setTuning].
     */
    val wakeTuning: StateFlow<Map<String, WakeWordTuning>> = tuningStore.phraseTuning

    /**
     * The spotter-wide numbers, i.e. what an untuned phrase uses. Shared with gameplay, which picks
     * edits up on its next restart; this panel's own spotter only takes them on
     * [applyWakeWordTuning], because applying one reloads ~6 MB of weights and nobody wants that per tap.
     */
    val spotterTuning: StateFlow<WakeWordSpotterTuning> = tuningStore.spotter

    /** What the user is currently typing into the phrase field. */
    private val _newWakeWord = MutableStateFlow("")
    val newWakeWord: StateFlow<String> = _newWakeWord.asStateFlow()

    init {
        viewModelScope.launch { voiceCommandManager.results.collect { _lastResult.value = it } }
        viewModelScope.launch {
            wakeWordEngine.detections.collect { detection ->
                _wakeWordLog.update { (listOf(detection) + it).take(MAX_WAKE_WORD_LOG) }
            }
        }
    }

    fun toggleWakeWordListening() {
        if (wakeWord.value.running) {
            wakeWordEngine.stop()
        } else {
            startWakeWord()
        }
    }

    fun onNewWakeWordChange(text: String) {
        _newWakeWord.value = text
    }

    /** Adds a typed phrase and restarts, because the spotter re-tokenizes the whole set. */
    fun addWakeWord() {
        val phrase = _newWakeWord.value.trim()
        if (phrase.isEmpty()) return
        _newWakeWord.value = ""
        if (phrase !in _wakeWords.value) {
            // A freshly added phrase starts on the store's default (the most eager preset).
            _wakeWords.update { it + phrase }
            restartIfListening()
        }
    }

    fun removeWakeWord(phrase: String) {
        // Its tuning stays in the store: gameplay may use the same phrase for a button.
        _wakeWords.update { it - phrase }
        restartIfListening()
    }

    /** A preset is a one-tap action, so it takes effect immediately. */
    fun setSensitivity(phrase: String, sensitivity: WakeWordSensitivity) {
        tuningStore.setPhraseTuning(phrase, sensitivity.tuning)
        restartIfListening()
    }

    /** Hand-edited per-phrase numbers. Applied by [applyWakeWordTuning]. */
    fun setTuning(phrase: String, tuning: WakeWordTuning) {
        tuningStore.setPhraseTuning(phrase, tuning)
    }

    /** Hand-edited spotter numbers. Applied by [applyWakeWordTuning]. */
    fun setSpotterTuning(tuning: WakeWordSpotterTuning) {
        tuningStore.setSpotter(tuning)
    }

    /** Pushes every hand-edited number into the engine by rebuilding the spotter. */
    fun applyWakeWordTuning() {
        restartIfListening()
    }

    /**
     * Every phrase back to the eager preset the app ships with, and the spotter to its defaults —
     * for gameplay's button voice too.
     */
    fun resetWakeWordTuning() {
        tuningStore.reset()
        restartIfListening()
    }

    private fun startWakeWord() {
        wakeWordEngine.start(_wakeWords.value, tuningStore.tuningFor(_wakeWords.value), tuningStore.spotter.value)
    }

    private fun restartIfListening() {
        if (wakeWord.value.running) startWakeWord()
    }

    /** Leaving the screen gives the mic back, so the spotter can't hold it into a game. */
    override fun onCleared() {
        wakeWordEngine.stop()
        super.onCleared()
    }

    private companion object {
        const val MAX_WAKE_WORD_LOG = 20
    }
}

/** F · Testing Station (debug builds only). Every panel is live; nothing here is simulated data. */
@Composable
fun TestingStationScreen(viewModel: TestingStationViewModel, onBack: () -> Unit) {
    val face by viewModel.faceState.collectAsStateWithLifecycle()
    val surface by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastResult.collectAsStateWithLifecycle()
    val wakeWord by viewModel.wakeWord.collectAsStateWithLifecycle()
    val wakeWordLog by viewModel.wakeWordLog.collectAsStateWithLifecycle()
    val selectedWakeWords by viewModel.wakeWords.collectAsStateWithLifecycle()
    val wakeTuning by viewModel.wakeTuning.collectAsStateWithLifecycle()
    val spotterTuning by viewModel.spotterTuning.collectAsStateWithLifecycle()
    val newWakeWord by viewModel.newWakeWord.collectAsStateWithLifecycle()
    val hitTime = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val colors = PwdeTheme.colors
    PwdeScreen(
        title = "Testing Station",
        subtitle = "See exactly what PWDe picks up from you.",
        onBack = onBack,
        voiceHint = "Say \"swap gesture\", or anything — it shows up in the Voice panel",
    ) {
        DemoModeBanner(face)
        CameraFeed(
            faceState = face,
            surfaceRequest = surface,
            canRequestCamera = viewModel.canRequestCamera,
            onCameraPermissionResult = viewModel::onCameraPermissionResult,
            showLandmarks = true,
        )
        GesturesPanel(face)
        FacePanel(face)
        Panel("Voice", Icons.Outlined.Mic) {
            Reading("State", when {
                voice.usesTextFallback -> voice.availability.label
                !voice.enabled -> "Off"
                voice.listening -> "Listening (level ${fmt(voice.level)})"
                else -> "Starting"
            })
            Reading("Match / activation", "${voice.matchMode.label} · ${voice.activationMode.label}")
            Reading("Transcript", lastResult?.let { "\"${it.transcript}\" (${if (it.isFinal) "final" else "partial"}, ${it.source.name.lowercase()})" } ?: "—")
            Reading("Matched command", lastResult?.command?.let { "${it.label} [${it.scope.name.lowercase()}]" } ?: "—")
        }
        Panel("Wake word", Icons.Outlined.Hearing) {
            Reading("Engine", SherpaInGameVoiceEngine.MODEL_LABEL)
            Reading(
                "Status",
                when {
                    wakeWord.running -> "Listening for ${wakeWord.phrases.size} phrase(s)"
                    wakeWord.canListen -> "Stopped"
                    else -> wakeWord.availability.label
                },
            )
            Reading("Detections", wakeWord.detections.toString())
            Reading("Mic level", if (wakeWord.running) fmt(wakeWord.level) else "—")
            Reading(
                "Model load",
                if (wakeWord.running) {
                    val percent = (wakeWord.realTimeFactor * 100).toInt()
                    "$percent% of real time" + if (percent >= 100) " — dropping audio" else ""
                } else {
                    "—"
                },
            )
            Reading("Last hit", wakeWordLog.firstOrNull()?.let { "${hitTime.format(Date(it.atMs))} · \"${it.phrase}\"" } ?: "—")
            wakeWord.error?.let { Reading("Error", it) }
            if (wakeWord.unsupported.isNotEmpty()) {
                Reading("Can't spot", wakeWord.unsupported.joinToString(", ") { "\"$it\"" })
            }
            PwdeTextField(
                "Wake phrase",
                newWakeWord,
                viewModel::onNewWakeWordChange,
                helper = "Any English phrase, e.g. hey pwde",
            )
            PwdeButton(
                "Add phrase",
                viewModel::addWakeWord,
                enabled = newWakeWord.isNotBlank(),
                icon = Icons.Outlined.Add,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Listening for", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            selectedWakeWords.forEach { phrase ->
                // A phrase starts on a preset; opening Tune exposes the by-hand numbers behind it.
                val tuning = wakeTuning[WakeWordTuningStore.key(phrase)] ?: WakeWordTuningStore.DEFAULT_PHRASE_TUNING
                var tuningOpen by rememberSaveable(phrase) { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { tuningOpen = !tuningOpen },
                ) {
                    Text(
                        phrase,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (tuningOpen) "Hide" else "Tune",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                    IconButton(onClick = { viewModel.removeWakeWord(phrase) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Stop listening for $phrase")
                    }
                }
                if (tuningOpen) {
                    SegmentedToggle(
                        WakeWordSensitivity.entries,
                        WakeWordSensitivity.presetFor(tuning),
                        { it.label },
                        { viewModel.setSensitivity(phrase, it) },
                    )
                    TuningSteppers(
                        boost = tuning.boost,
                        threshold = tuning.threshold,
                        inherited = spotterTuning,
                        onTuning = { viewModel.setTuning(phrase, it) },
                    )
                }
            }
            Text("Spotter defaults", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            TuningSteppers(
                boost = spotterTuning.score,
                threshold = spotterTuning.threshold,
                inherited = spotterTuning,
                onTuning = { edited ->
                    viewModel.setSpotterTuning(
                        spotterTuning.copy(
                            score = edited.boost ?: spotterTuning.score,
                            threshold = edited.threshold ?: spotterTuning.threshold,
                        ),
                    )
                },
            )
            ValueStepper(
                "Trailing blanks",
                spotterTuning.trailingBlanks.toString(),
                { direction ->
                    viewModel.setSpotterTuning(
                        spotterTuning.copy(trailingBlanks = spotterTuning.trailingBlanks + direction),
                    )
                },
                canDecrease = spotterTuning.trailingBlanks > WakeWordSpotterTuning.TRAILING_BLANKS_RANGE.start,
                canIncrease = spotterTuning.trailingBlanks < WakeWordSpotterTuning.TRAILING_BLANKS_RANGE.endInclusive,
            )
            ValueStepper(
                "Active paths",
                spotterTuning.activePaths.toString(),
                { direction ->
                    viewModel.setSpotterTuning(
                        spotterTuning.copy(activePaths = spotterTuning.activePaths + direction),
                    )
                },
                canDecrease = spotterTuning.activePaths > WakeWordSpotterTuning.ACTIVE_PATHS_RANGE.start,
                canIncrease = spotterTuning.activePaths < WakeWordSpotterTuning.ACTIVE_PATHS_RANGE.endInclusive,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PwdeButton("Apply & restart", viewModel::applyWakeWordTuning, modifier = Modifier.weight(1f))
                PwdeButton(
                    "Reset",
                    viewModel::resetWakeWordTuning,
                    style = ButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
            }
            Reading("Easier to hit", "Higher boost keeps a keyword alive through beam search; lower threshold fires on weaker evidence. Both raise the false-alarm rate, so they are set per phrase — Normal / High / Max in one tap, or open Tune to step the numbers by hand.")
            Reading("Tuning", "A phrase's Tune numbers are written into its own keyword line; Spotter defaults are what an untuned phrase uses (score, threshold, trailing blanks and active paths are exactly sherpa-onnx's keywordsScore, keywordsThreshold, numTrailingBlanks and maxActivePaths). Values are clamped to what the model accepts, and both halves are pushed to the engine by Apply & restart.")
            Reading("Gameplay", "Mapped buttons are pressed by voice with this same spotter, and it uses the tuning set here. Tune a button's exact voice trigger by adding it as a phrase. App navigation still uses Google speech.")
            Reading("Heads up", "While this listens, PWDe's app-wide voice commands stand down.")
            if (wakeWord.running) {
                PwdeButton(
                    "Stop listening",
                    viewModel::toggleWakeWordListening,
                    style = ButtonStyle.SECONDARY,
                    icon = Icons.Outlined.Hearing,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PwdeButton(
                    "Start listening",
                    viewModel::toggleWakeWordListening,
                    enabled = selectedWakeWords.isNotEmpty(),
                    icon = Icons.Outlined.Hearing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Panel("Cursor", Icons.Outlined.Mouse) {
            Reading("Position", "x ${fmt(face.cursor.x)}, y ${fmt(face.cursor.y)}")
            Reading("Output mode", face.outputMode.label)
            CursorPad(face.cursor, active = face.hasFace)
        }
        Panel("Joystick", Icons.Outlined.Gamepad) {
            Reading("X / Y", "${fmt(face.joystick.x)} / ${fmt(face.joystick.y)}")
            Reading("Direction", face.joystick.direction.label)
            JoystickView(face.joystick, Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally), active = face.hasFace)
        }
    }
}

private val GESTURE_SWAP_COMMANDS = listOf(
    voiceCommand("next_gesture", "next gesture", "swap gesture", "swap"),
    voiceCommand("previous_gesture", "previous gesture"),
    voiceCommand("tab:GESTURES", "gestures", "curated"),
    voiceCommand("tab:MEDIAPIPE", "mediapipe", "media pipe", "blendshapes"),
)

/**
 * One gesture at a time, with a swap button to step through every gesture PWDe knows. The
 * "Detected" line still covers all of them, so nothing firing off-screen is missed.
 */
@Composable
private fun GesturesPanel(face: FaceState) {
    val colors = PwdeTheme.colors
    var catalog by rememberSaveable { mutableStateOf(GestureCatalog.GESTURES) }
    // Separate position per list, so switching lists doesn't lose your place.
    var curatedIndex by rememberSaveable { mutableIntStateOf(0) }
    var rawIndex by rememberSaveable { mutableIntStateOf(0) }
    val gestures = catalog.gestures
    val index = if (catalog == GestureCatalog.GESTURES) curatedIndex else rawIndex
    fun swap(delta: Int) {
        val next = (index + delta).mod(gestures.size)
        if (catalog == GestureCatalog.GESTURES) curatedIndex = next else rawIndex = next
    }
    VoiceCommandsEffect(GESTURE_SWAP_COMMANDS) { id ->
        when {
            id.startsWith("tab:") -> catalog = GestureCatalog.valueOf(id.removePrefix("tab:"))
            else -> swap(if (id == "next_gesture") 1 else -1)
        }
    }
    val gesture = gestures[index]
    val measure = face.gesture.measures[gesture]
    val detected = gesture in face.gesture.active

    Panel("Gestures", Icons.Outlined.TouchApp) {
        SegmentedToggle(GestureCatalog.entries, catalog, { it.label }, { catalog = it })
        Text(
            "Detected: " + face.gesture.active.filter { it in gestures }.joinToString { it.label }.ifEmpty { "none" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (face.gesture.active.isNotEmpty()) colors.primary else colors.textMuted,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PwdeShapes.button)
                .background(if (detected) colors.primary.copy(alpha = 0.18f) else colors.surfaceMuted)
                .border(if (detected) 2.dp else 1.dp, if (detected) colors.primary else colors.secondary.copy(alpha = 0.5f), PwdeShapes.button)
                .padding(PwdeTheme.spacing.internal),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (catalog == GestureCatalog.MEDIAPIPE) "Blendshape ${index + 1} of ${gestures.size} · MediaPipe"
                else "Gesture ${index + 1} of ${gestures.size}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            Text(gesture.label, style = MaterialTheme.typography.headlineSmall, color = if (detected) colors.primary else colors.text)
            Text(gesture.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            GestureMeter(gesture, measure, active = detected)
            Text(
                when {
                    detected -> "DETECTED"
                    measure == null && face.isSimulated -> "Demo mode can only simulate head moves (tilt, nod, shake)."
                    measure == null -> "Waiting for a face…"
                    else -> "Score ${fmt(measure.score)} · fires at ${fmt(measure.threshold)} (your sensitivity)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (detected) colors.primary else colors.text,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PwdeButton(
                "Previous",
                { swap(-1) },
                style = ButtonStyle.SECONDARY,
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                modifier = Modifier.weight(1f),
            )
            PwdeButton(
                "Swap gesture",
                { swap(1) },
                icon = Icons.Outlined.SwapHoriz,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FacePanel(face: FaceState) {
    Panel("Face Tracking", Icons.Outlined.Face) {
        Reading("Source", if (face.isSimulated) "Simulated (motion sensors)" else "Camera + MediaPipe")
        Reading("Status", when (val s = face.status) {
            TrackingStatus.Idle -> "Idle"
            TrackingStatus.Starting -> "Starting"
            TrackingStatus.Live -> "Live · ${face.fps.toInt()} fps"
            TrackingStatus.NoFace -> "No face in view"
            is TrackingStatus.Unavailable -> "Unavailable: ${s.reason}"
        })
        val pose = face.pose
        Reading("Yaw / pitch / roll", if (pose == null) "—" else "${fmt(pose.yaw)}° / ${fmt(pose.pitch)}° / ${fmt(pose.roll)}°")
        Reading("Landmarks", face.landmarks?.let { "${it.size / 2} points" } ?: "—")
        Reading(
            "Confidence",
            when {
                face.confidence != null -> fmt(face.confidence)
                face.hasFace && !face.isSimulated -> "≥ 0.50 (model gate; per-point score not reported)"
                else -> "—"
            },
        )
    }
}

@Composable
private fun Panel(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    GradientCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon)
            Text(title, style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Reading(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PwdeTheme.colors.textMuted, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = PwdeTheme.colors.text, modifier = Modifier.weight(0.6f))
    }
}

/** One tap of the boost stepper. */
private const val BOOST_STEP = 0.5f

/** One tap of the threshold stepper: 0.00 to 1.00 in twenty taps. */
private const val THRESHOLD_STEP = 0.05f

/**
 * Boost and threshold for one tuning, as steppers. Steppers are this screen's design language (see
 * `LevelStepper`), and they are also what makes these numbers safe to edit by hand: every tap is
 * already a valid value, so nothing has to be parsed or re-seeded while it is half-typed.
 *
 * A null field is the "inherit" state — the phrase then writes no numbers into its keyword line and
 * uses [inherited], which the label says out loud. The first tap turns the pair into explicit
 * numbers seeded from [inherited] and the other field's current value.
 */
@Composable
private fun TuningSteppers(
    boost: Float?,
    threshold: Float?,
    inherited: WakeWordSpotterTuning,
    onTuning: (WakeWordTuning) -> Unit,
) {
    val boostValue = boost ?: inherited.score
    val thresholdValue = threshold ?: inherited.threshold
    ValueStepper(
        label = "Boost",
        valueText = if (boost == null) "${fmt(boostValue)} · spotter" else fmt(boostValue),
        onStep = { direction ->
            onTuning(
                WakeWordTuning(
                    stepTuningValue(boostValue, BOOST_STEP, direction, WakeWordSpotterTuning.SCORE_RANGE),
                    thresholdValue,
                ),
            )
        },
        canDecrease = boostValue > WakeWordSpotterTuning.SCORE_RANGE.start,
        canIncrease = boostValue < WakeWordSpotterTuning.SCORE_RANGE.endInclusive,
    )
    ValueStepper(
        label = "Threshold",
        valueText = if (threshold == null) "${fmt(thresholdValue)} · spotter" else fmt(thresholdValue),
        onStep = { direction ->
            onTuning(
                WakeWordTuning(
                    boostValue,
                    stepTuningValue(thresholdValue, THRESHOLD_STEP, direction, WakeWordSpotterTuning.THRESHOLD_RANGE),
                ),
            )
        },
        canDecrease = thresholdValue > WakeWordSpotterTuning.THRESHOLD_RANGE.start,
        canIncrease = thresholdValue < WakeWordSpotterTuning.THRESHOLD_RANGE.endInclusive,
    )
}

/** −  value  +  row for one hand-editable number. */
@Composable
private fun ValueStepper(
    label: String,
    valueText: String,
    onStep: (Int) -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
) {
    val colors = PwdeTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.weight(1f))
        IconButton(onClick = { onStep(-1) }, enabled = canDecrease, modifier = Modifier.size(MinTouchTarget)) {
            Icon(Icons.Filled.Remove, contentDescription = "Lower $label", tint = colors.primary)
        }
        Text(
            valueText,
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        IconButton(onClick = { onStep(1) }, enabled = canIncrease, modifier = Modifier.size(MinTouchTarget)) {
            Icon(Icons.Filled.Add, contentDescription = "Raise $label", tint = colors.primary)
        }
    }
}
