package com.pwde.app.ui.eyetracking

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.sensors.eyedid.EyedidGazeMapping
import com.pwde.app.sensors.eyedid.EyedidStatus
import com.pwde.app.sensors.eyedid.GazeLostReason
import com.pwde.app.sensors.eyedid.GazePoint
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.TextAction
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.rememberCameraPermissionRequest
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.theme.PwdeTheme
import java.util.Locale

private val EYE_TRACKING_COMMANDS = listOf(
    voiceCommand("start", "start tracking"),
    voiceCommand("stop", "stop tracking"),
    voiceCommand("calibrate", "calibrate eyes", "calibrate"),
)

/**
 * A test rig for the SeeSo/Eyedid gaze SDK: licence status, live camera preview with the gaze dot
 * drawn on it, the raw numbers the SDK reports, and the five-point calibration sweep.
 *
 * It exists to answer one question on a real device — does this SDK actually see my eyes? — without
 * wiring it into gameplay first. Needs a real device: the emulator has no usable front camera and
 * the licence is bound to the app's signing certificate.
 */
@Composable
fun EyeTrackingScreen(viewModel: EyeTrackingViewModel, onBack: () -> Unit) {
    val state by viewModel.view.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /** Where the preview box sits on the screen, so screen pixels can become box fractions. */
    var previewBounds by remember { mutableStateOf(Rect.Zero) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestCamera = rememberCameraPermissionRequest { granted ->
        cameraGranted = granted
        // The engine is already running by now; granting the permission is what lets it open the
        // camera, and the screen re-reads its status on the next emission.
    }

    LaunchedEffect(Unit) {
        // The engine starts by itself: this screen holds the gaze for as long as it is open, so the
        // only thing it can be waiting on is the camera permission.
        if (!cameraGranted) requestCamera()
    }

    VoiceCommandsEffect(EYE_TRACKING_COMMANDS) { id ->
        when (id) {
            "start" -> viewModel.startTracking()
            "stop" -> viewModel.stopTracking()
            "calibrate" -> if (state.canCalibrate) viewModel.calibrate()
        }
    }

    val dotFraction = state.dotPx?.let { previewBounds.fractionOf(it) }
    val lostLabel = when {
        dotFraction != null -> "tracking"
        // The SDK is fine; there is simply nowhere to draw yet.
        state.dotPx != null -> EyeTrackingView.reasonLabel(GazeLostReason.SCREEN_NOT_MEASURED)
        else -> state.lostLabel
    }

    Box(Modifier.fillMaxSize()) {
        PwdeScreen(
            title = "Eye tracking",
            subtitle = "SeeSo / Eyedid gaze SDK — test rig",
            onBack = onBack,
            voiceHint = "Say \"start tracking\", \"stop tracking\" or \"calibrate eyes\"",
        ) {
            LicenceCard(state, viewModel, cameraGranted = cameraGranted, onRequestCamera = requestCamera)

            GradientCard(Modifier.fillMaxWidth()) {
                Text("Camera & gaze dot", style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .padding(top = PwdeTheme.spacing.internal),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> TextureView(ctx).also(viewModel::attachPreview) },
                    )
                    // The dot is drawn in Compose over the SDK's own preview surface.
                    Canvas(Modifier.fillMaxSize()) {
                        val fraction = dotFraction ?: return@Canvas
                        val center = Offset(fraction.x * size.width, fraction.y * size.height)
                        val accent = Color(0xFF6EE7B7)
                        drawCircle(accent.copy(alpha = 0.30f), radius = 26.dp.toPx(), center = center)
                        drawCircle(accent, radius = 10.dp.toPx(), center = center)
                    }
                }
                Text(
                    "dot: $lostLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dotFraction != null) PwdeTheme.colors.success else PwdeTheme.colors.textMuted,
                )
            }

            GradientCard(Modifier.fillMaxWidth()) {
                Text("Controls", style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
                if (state.trackingNow) {
                    PwdeButton("Stop tracking", viewModel::stopTracking, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Stop)
                } else {
                    PwdeButton(
                        "Start tracking",
                        viewModel::startTracking,
                        enabled = state.status == EyedidStatus.Ready,
                        icon = Icons.Outlined.PlayArrow,
                    )
                }
                PwdeButton(
                    if (state.calibrating) "Cancel calibration" else "Calibrate (5 points)",
                    onClick = { if (state.calibrating) viewModel.cancelCalibration() else viewModel.calibrate() },
                    enabled = state.canCalibrate || state.calibrating,
                    style = ButtonStyle.SECONDARY,
                    icon = Icons.Outlined.CenterFocusStrong,
                )
                if (state.calibrated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap),
                    ) {
                        Text(
                            "Calibrated on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = PwdeTheme.colors.success,
                        )
                        TextAction("Forget", viewModel::forgetCalibration)
                    }
                }
            }

            GradientCard(Modifier.fillMaxWidth()) {
                Text("What the SDK reports", style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
                Readout("licence status", EyedidText.label(state.status))
                Readout("tracking", state.tracking)
                Readout("dot", lostLabel)
                Readout("gaze (screen px)", state.gazePx?.let { "${num(it.x)}, ${num(it.y)}" } ?: "—")
                Readout("gaze (box fraction)", dotFraction?.let { "${num(it.x, 3)}, ${num(it.y, 3)}" } ?: "—")
                Readout("face confidence", num(state.faceScore, 2))
                Readout("head pose", "pitch ${num(state.pitch)} · yaw ${num(state.yaw)} · roll ${num(state.roll)}")
                Readout("blinking", if (state.blinking) "yes" else "no")
                Readout("sdk version", viewModel.sdkVersion)
            }

            state.note?.let { InfoNote(it) }

            InfoNote(
                "Calibration needs the tracker running. Look at each dot until the ring closes — the dot " +
                    "is drawn where the SDK asked for it, because that position is what it measures your " +
                    "eyes against.",
            )
            InfoNote(
                "The front camera can only be used by one thing at a time. If PWDe is playing a live " +
                    "session over a game, stop it first or the SDK will not be able to open the camera.",
            )
        }

        // Full-screen during a sweep: the dot must be drawn at the exact screen position the SDK
        // asked for, so it cannot live inside the preview card.
        if (state.calibrating) {
            EyedidCalibrationOverlay(
                dotPx = state.calibrationDotPx,
                progress = state.calibrationProgress,
                onCancel = viewModel::cancelCalibration,
                message = state.note,
            )
        }
    }
}

@Composable
private fun LicenceCard(
    state: EyeTrackingView,
    viewModel: EyeTrackingViewModel,
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
) {
    val advice = EyedidText.advice(state.status) ?: EyedidText.developerAdvice(state.status)

    GradientCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            Text("Licence", style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
            StatusPill(
                EyedidText.label(state.status),
                color = if (state.trackingNow) PwdeTheme.colors.success else PwdeTheme.colors.primary,
                icon = if (advice != null) Icons.Outlined.WarningAmber else null,
            )
        }
        if (!viewModel.hasLicenceKey) {
            Text(
                "No key found in local.properties.",
                style = MaterialTheme.typography.bodyMedium,
                color = PwdeTheme.colors.warning,
            )
        }
        if (!cameraGranted) {
            Text(
                "PWDe does not have the camera permission yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = PwdeTheme.colors.warning,
            )
            PwdeButton("Allow camera", onRequestCamera, style = ButtonStyle.SECONDARY)
        }
        advice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = PwdeTheme.colors.danger)
        }
    }
}

/** One label/value line in the diagnostics panel. */
@Composable
private fun Readout(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PwdeTheme.colors.textMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = PwdeTheme.colors.text)
    }
}

/** Screen pixels -> fraction of this rect, using the one place the two spaces meet. */
private fun Rect.fractionOf(point: GazePoint): GazePoint? =
    EyedidGazeMapping.toBoxFraction(point.x, point.y, left, top, width.toInt(), height.toInt())

private fun num(value: Float, digits: Int = 1): String =
    String.format(Locale.US, "%.${digits}f", value)
