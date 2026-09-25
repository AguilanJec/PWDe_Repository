package com.pwde.app.ui.gameplay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.sensors.face.FaceState
import com.pwde.app.sensors.face.TrackingStatus
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.DemoModeBanner
import com.pwde.app.ui.components.JoystickView
import com.pwde.app.ui.components.ListeningIndicator
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.rememberCameraPermissionRequest
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

/**
 * D4/D5 Playing view: a simulated game background under a live PWDe overlay — listening state,
 * detected gesture, cursor or joystick, and what each gesture/voice command just did. Back (touch
 * or voice), "exit", or an Exit gesture returns to the menu. No real game is launched.
 */
@Composable
fun PlayingScreen(viewModel: GameplayViewModel, onExit: () -> Unit) {
    val face by viewModel.faceState.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val lastEvent by viewModel.lastEvent.collectAsStateWithLifecycle()
    val requestCamera = rememberCameraPermissionRequest { viewModel.onCameraPermissionResult() }
    val colors = PwdeTheme.colors
    BackHandler(onBack = onExit)
    LaunchedEffect(viewModel) { viewModel.exitRequests.collect { onExit() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF1B2A1E))) {
        SimulatedBackground()
        val active = face.hasFace && !paused
        val screenWidth = maxWidth
        if (face.outputMode == FaceOutputMode.JOYSTICK) {
            SimulatedAvatar(face, active)
        } else {
            CursorLayer(face, active, lastEvent)
        }

        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    "SIMULATED GAME — ${viewModel.game?.displayName ?: "preview"}",
                    color = colors.warning,
                    modifier = Modifier.weight(1f).background(colors.background.copy(alpha = 0.8f), PwdeShapes.pill),
                )
                ListeningIndicator(voice)
            }
            DemoModeBanner(face, Modifier.background(colors.background.copy(alpha = 0.85f), PwdeShapes.button))
            if (face.isSimulated && viewModel.canRequestCamera) {
                PwdeButton("Turn on camera", requestCamera, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Videocam)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (face.outputMode == FaceOutputMode.JOYSTICK) {
                    JoystickView(
                        face.joystick,
                        Modifier.align(Alignment.BottomStart).size(screenWidth * face.joystick.radius * 2f),
                        active = active,
                    )
                }
            }
            OverlayPanel(face, voiceLine(voice.lastTranscript, voice.lastCommand?.label), lastEvent, paused, viewModel::togglePause, onExit)
        }
    }
}

private fun voiceLine(transcript: String?, command: String?): String = when {
    command != null -> "Voice: \"$command\""
    transcript != null -> "Heard: \"$transcript\""
    else -> "Voice: say \"pause\", \"select\" or \"exit\""
}

@Composable
private fun OverlayPanel(
    face: FaceState,
    voiceText: String,
    lastEvent: OverlayEvent?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = PwdeTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PwdeShapes.card)
            .background(colors.background.copy(alpha = 0.92f))
            .border(2.dp, colors.primary, PwdeShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PWDe", style = MaterialTheme.typography.titleLarge, color = colors.primary, modifier = Modifier.weight(1f))
            StatusPill(
                when {
                    paused -> "Paused"
                    face.status == TrackingStatus.Live -> if (face.isSimulated) "Demo tracking" else "Tracking"
                    face.status == TrackingStatus.NoFace -> "No face in view"
                    face.status is TrackingStatus.Unavailable -> "Tracking unavailable"
                    else -> "Starting…"
                },
                color = if (paused || face.status != TrackingStatus.Live) colors.warning else colors.primary,
                icon = Icons.Outlined.Face,
            )
        }
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text("Gesture: ${face.gesture.primary?.label ?: "—"}", style = MaterialTheme.typography.bodyMedium, color = colors.text)
            Text(voiceText, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            Text(
                "Last action: ${lastEvent?.text ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (lastEvent?.kind == OverlayEvent.Kind.IGNORED) colors.textMuted else colors.primary,
            )
            Text(
                if (face.outputMode == FaceOutputMode.JOYSTICK) "Joystick: ${face.joystick.direction.label}"
                else "Pointer: ${(face.cursor.x * 100).toInt()}%, ${(face.cursor.y * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PwdeButton(
                if (paused) "Resume" else "Pause",
                onTogglePause,
                icon = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                modifier = Modifier.weight(1f),
            )
            PwdeButton(
                "Exit to PWDe",
                onExit,
                style = ButtonStyle.SECONDARY,
                icon = Icons.AutoMirrored.Outlined.ExitToApp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Head pointer across the whole screen, with a ripple wherever "Select" fires. */
@Composable
private fun CursorLayer(face: FaceState, active: Boolean, lastEvent: OverlayEvent?) {
    val colors = PwdeTheme.colors
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(lastEvent?.id) {
        if (lastEvent?.kind == OverlayEvent.Kind.SELECT) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(450))
        }
    }
    Canvas(
        Modifier.fillMaxSize().semantics {
            contentDescription = "Pointer at ${(face.cursor.x * 100).toInt()} percent across, ${(face.cursor.y * 100).toInt()} percent down"
        },
    ) {
        val center = Offset(face.cursor.x * size.width, face.cursor.y * size.height)
        if (ripple.value < 1f) {
            drawCircle(colors.primary.copy(alpha = 1f - ripple.value), radius = 24.dp.toPx() + 50.dp.toPx() * ripple.value, center = center, style = Stroke(4.dp.toPx()))
        }
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 16.dp.toPx(), center = center)
        drawCircle(if (active) colors.primary else colors.textMuted, radius = 12.dp.toPx(), center = center)
        drawCircle(Color.White, radius = 12.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
    }
}

/** A marker the joystick steers around the arena, so joystick input has something to move. */
@Composable
private fun SimulatedAvatar(face: FaceState, active: Boolean) {
    val colors = PwdeTheme.colors
    val density = LocalDensity.current
    var position by remember { mutableStateOf(Offset(0.5f, 0.6f)) }
    val joystick = face.joystick
    // Read by the frame loop without restarting it on every tracking frame.
    val stick by rememberUpdatedState(Offset(joystick.x, joystick.y))
    LaunchedEffect(active) {
        var last = 0L
        while (active) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    position = Offset(
                        (position.x + stick.x * AVATAR_SPEED * dt).coerceIn(0.05f, 0.95f),
                        (position.y + stick.y * AVATAR_SPEED * dt).coerceIn(0.1f, 0.9f),
                    )
                }
                last = now
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = 36.dp
        Box(
            Modifier
                .offset(
                    x = with(density) { (constraints.maxWidth * position.x).toDp() } - size / 2,
                    y = with(density) { (constraints.maxHeight * position.y).toDp() } - size / 2,
                )
                .size(size)
                .clip(CircleShape)
                .background(if (active) colors.secondary else colors.textMuted)
                .border(3.dp, Color.White, CircleShape)
                .semantics { contentDescription = "Your character, moving ${joystick.direction.label}" },
        )
    }
}

/** Screen fractions per second at full deflection. */
private const val AVATAR_SPEED = 0.45f

/** A plain drawn "arena" so the overlay has something to sit on. Clearly not a real game. */
@Composable
private fun SimulatedBackground() {
    val colors = PwdeTheme.colors
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Simulated game background" }) {
        val step = 48.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            y += step
        }
        drawRect(colors.secondary.copy(alpha = 0.18f), topLeft = Offset(0f, size.height * 0.48f), size = Size(size.width, size.height * 0.04f))
        drawCircle(colors.primary.copy(alpha = 0.25f), radius = size.minDimension * 0.08f, center = Offset(size.width * 0.25f, size.height * 0.8f))
        drawCircle(colors.primary.copy(alpha = 0.25f), radius = size.minDimension * 0.08f, center = Offset(size.width * 0.75f, size.height * 0.8f))
        drawCircle(colors.danger.copy(alpha = 0.25f), radius = size.minDimension * 0.08f, center = Offset(size.width * 0.25f, size.height * 0.2f))
        drawCircle(colors.danger.copy(alpha = 0.25f), radius = size.minDimension * 0.08f, center = Offset(size.width * 0.75f, size.height * 0.2f))
    }
}
