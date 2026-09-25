package com.pwde.app.ui.testingstation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.sensors.face.FaceState
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.TrackingStatus
import com.pwde.app.sensors.voice.VoiceCommandManager
import com.pwde.app.sensors.voice.VoiceResult
import com.pwde.app.sensors.voice.VoiceState
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
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.fmt
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Debug-only: live readouts from both input pipelines, for development validation. */
class TestingStationViewModel(
    faceTracking: FaceTrackingManager,
    voiceCommandManager: VoiceCommandManager,
) : FaceTrackingViewModel(faceTracking) {
    val voice: StateFlow<VoiceState> = voiceCommandManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), voiceCommandManager.state.value)

    private val _lastResult = MutableStateFlow<VoiceResult?>(null)
    val lastResult: StateFlow<VoiceResult?> = _lastResult.asStateFlow()

    init {
        viewModelScope.launch { voiceCommandManager.results.collect { _lastResult.value = it } }
    }
}

/** F · Testing Station (debug builds only). Every panel is live; nothing here is simulated data. */
@Composable
fun TestingStationScreen(viewModel: TestingStationViewModel, onBack: () -> Unit) {
    val face by viewModel.faceState.collectAsStateWithLifecycle()
    val surface by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastResult.collectAsStateWithLifecycle()
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
