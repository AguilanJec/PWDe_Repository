package com.pwde.app.ui.eyetracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.sensors.eyedid.EyedidStatus
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.TextAction
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

private val EYE_CONTROL_COMMANDS = listOf(
    voiceCommand("calibrate", "calibrate", "calibrate eyes", "recalibrate"),
    voiceCommand("forget", "forget calibration", "delete calibration"),
    voiceCommand("use", "use eye control", "turn on eye control", "eye control on"),
)

/**
 * Eye control: calibrate the gaze engine, check the pointer agrees with where the user is looking,
 * then switch the app over to it.
 *
 * The order matters and the screen enforces it. Until the SDK has been calibrated its gaze points
 * are uncalibrated pixels, so offering "use eye control" first would hand the user a pointer that
 * wanders — the thing that makes people conclude eye control does not work.
 */
@Composable
fun EyeControlScreen(viewModel: EyeControlViewModel, onBack: () -> Unit) {
    val state by viewModel.view.collectAsStateWithLifecycle()

    VoiceCommandsEffect(EYE_CONTROL_COMMANDS) { id ->
        when (id) {
            "calibrate" -> if (state.canCalibrate) viewModel.calibrate()
            "forget" -> viewModel.forgetCalibration()
            "use" -> viewModel.useEyeControl()
        }
    }

    Box(Modifier.fillMaxSize()) {
        PwdeScreen(
            title = "Eye control",
            subtitle = "Look at a button and hold your gaze there to press it.",
            onBack = onBack,
            voiceHint = "Say \"calibrate\", \"use eye control\" or \"forget calibration\"",
        ) {
            StatusCard(state, viewModel)

            GazeCheckCard(state)

            GradientCard(Modifier.fillMaxWidth()) {
                Text("Calibration", style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
                when {
                    !state.hasLicenseKey -> Text(
                        "Calibration is not available because eye control is not set up in this build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PwdeTheme.colors.textMuted,
                    )

                    state.calibrating -> PwdeButton(
                        "Stop calibrating",
                        viewModel::cancelCalibration,
                        style = ButtonStyle.SECONDARY,
                    )

                    else -> PwdeButton(
                        if (state.calibrated) "Calibrate again" else "Calibrate your eyes",
                        onClick = viewModel::calibrate,
                        enabled = state.canCalibrate,
                        icon = Icons.Outlined.CenterFocusStrong,
                    )
                }
                if (state.calibrated && !state.calibrating) {
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
                if (!state.canCalibrate && state.hasLicenseKey && !state.calibrating) {
                    Text(
                        "Calibration can start once the eye engine is tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PwdeTheme.colors.textMuted,
                    )
                }
            }

            GradientCard(Modifier.fillMaxWidth()) {
                Text("Use it", style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
                if (state.isEyeMode) {
                    StatusPill("Eye control is on", color = PwdeTheme.colors.success, icon = Icons.Outlined.RemoveRedEye)
                    Text(
                        "Look straight at a mapped button and hold your gaze there. The ring fills, then the press fires.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PwdeTheme.colors.textMuted,
                    )
                } else {
                    PwdeButton(
                        "Turn on eye control",
                        viewModel::useEyeControl,
                        enabled = state.calibrated,
                        icon = Icons.Outlined.RemoveRedEye,
                    )
                    Text(
                        "Calibrate first: until then the pointer would not follow where you look.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PwdeTheme.colors.textMuted,
                    )
                }
                Text(
                    "Face gestures and the head joystick are not available while eye control is on — the " +
                        "gaze engine does not see the face shape they need.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PwdeTheme.colors.textMuted,
                )
            }

            state.note?.let { InfoNote(it) }

            InfoNote(
                "The most common way to ruin eye calibration is to turn your head towards each dot. Move " +
                    "only your eyes and keep your head still. If your head follows the dot, your eyes barely " +
                    "move in their sockets and the fit has almost no signal to learn from — which is what " +
                    "makes the pointer wander afterwards.",
            )
            InfoNote(
                "Sit so the front camera can see your whole face, about an arm's length away, in the same " +
                    "position you will play in: the calibration describes your eyes at one distance and one " +
                    "posture. You only need to do this once per device.",
            )
            InfoNote(
                "The front camera can only be used by one thing at a time. If a live session is running " +
                    "over a game, or another screen is still watching your face, stop it first — otherwise " +
                    "the eye engine cannot open the camera and will say so.",
            )
        }

        // Shown from the moment the sweep starts, not from the first dot: `dotPx` is null in the gap
        // between the two, and that gap is exactly where a tap used to look like it did nothing.
        if (state.calibrating) {
            EyedidCalibrationOverlay(
                dotPx = state.dotPx,
                progress = state.progress,
                onCancel = viewModel::cancelCalibration,
                index = state.pointNumber.takeIf { it > 0 },
                total = TOTAL_POINTS,
                // Read from the state as it changes, so the watchdog's diagnosis appears over the
                // overlay rather than behind it.
                message = state.note,
            )
        }
    }
}

/** Licence and tracking state, with whatever the user should do about it. */
@Composable
private fun StatusCard(state: EyeControlView, viewModel: EyeControlViewModel) {
    GradientCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap),
        ) {
            Text("Eye engine", style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
            StatusPill(
                EyedidText.label(state.status),
                color = if (state.status == EyedidStatus.Tracking) PwdeTheme.colors.success else PwdeTheme.colors.primary,
                icon = if (state.advice != null) Icons.Outlined.WarningAmber else null,
            )
        }
        state.advice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = PwdeTheme.colors.danger)
        }
        if (state.calibrated) {
            Text(
                "A calibration is saved on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = PwdeTheme.colors.success,
            )
        }
        state.note?.let { TextAction("Dismiss", viewModel::dismissNote) }
    }
}

/**
 * The honest version of a camera preview: the gaze dot placed on a scaled-down drawing of the whole
 * screen. Drawing it inside a small box without saying so would imply the box *is* the screen, and a
 * user checking their calibration would be checking it against the wrong reference.
 */
@Composable
private fun GazeCheckCard(state: EyeControlView) {
    val configuration = LocalConfiguration.current
    val screenAspect = if (configuration.screenHeightDp > 0) {
        configuration.screenWidthDp.toFloat() / configuration.screenHeightDp
    } else {
        9f / 19.5f
    }
    val colors = PwdeTheme.colors

    GradientCard(Modifier.fillMaxWidth()) {
        Text("Where you are looking", style = MaterialTheme.typography.titleMedium, color = colors.text)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = PwdeTheme.spacing.internal)
                .aspectRatio(screenAspect)
                .clip(PwdeShapes.card)
                .background(colors.surfaceMuted)
                .border(2.dp, colors.borderBrush, PwdeShapes.card),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val centre = Offset(size.width / 2f, size.height / 2f)
                drawLine(colors.borderBrush, Offset(0f, centre.y), Offset(size.width, centre.y), strokeWidth = 1.dp.toPx())
                drawLine(colors.borderBrush, Offset(centre.x, 0f), Offset(centre.x, size.height), strokeWidth = 1.dp.toPx())

                val dwell = state.face.dwell
                if (dwell != null) {
                    // Pinned to the spot the hold started on, not to the drifting cursor: the ring is
                    // the target, so it must not slide around while the eyes wander on it.
                    val target = Offset(dwell.x * size.width, dwell.y * size.height)
                    val radius = 22.dp.toPx()
                    drawArc(
                        color = colors.primary,
                        startAngle = -90f,
                        sweepAngle = 360f * dwell.progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(target.x - radius, target.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                }

                if (state.gazeVisible) {
                    val gaze = Offset(state.face.cursor.x * size.width, state.face.cursor.y * size.height)
                    drawCircle(Color.Black.copy(alpha = 0.35f), radius = 11.dp.toPx(), center = gaze)
                    drawCircle(colors.primary, radius = 8.dp.toPx(), center = gaze)
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = gaze, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Text(
            if (state.gazeVisible) "Your screen, scaled down. Move your eyes and the dot should follow."
            else "Your screen, scaled down. Nothing is being tracked yet — the dot appears once a face is seen.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

/** `CalibrationModeType.FIVE_POINT`, which is what [EyeControlViewModel.calibrate] asks the SDK for. */
private const val TOTAL_POINTS = 5
