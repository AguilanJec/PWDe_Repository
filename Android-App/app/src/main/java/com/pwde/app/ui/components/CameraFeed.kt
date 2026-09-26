package com.pwde.app.ui.components

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pwde.app.sensors.face.FaceState
import com.pwde.app.sensors.face.TrackingStatus
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

const val DEMO_MODE_LABEL = "Demo Mode: Simulated Head Tracking"

/**
 * Bordered live camera frame. Shows the real front-camera preview (optionally with the face
 * mesh), or — when tracking fell back to motion sensors — an unmistakable demo-mode panel.
 */
@Composable
fun CameraFeed(
    faceState: FaceState,
    surfaceRequest: SurfaceRequest?,
    canRequestCamera: Boolean,
    onCameraPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
    showLandmarks: Boolean = false,
    feedAspectRatio: Float = 3f / 4f,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val colors = PwdeTheme.colors
    val requestCamera = rememberCameraPermissionRequest { onCameraPermissionResult() }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(feedAspectRatio)
            .clip(PwdeShapes.card)
            .background(colors.surfaceMuted)
            .border(2.dp, colors.borderBrush, PwdeShapes.card),
        contentAlignment = Alignment.Center,
    ) {
        when {
            faceState.isSimulated -> SimulatedPanel(faceState, canRequestCamera, requestCamera)
            surfaceRequest != null -> {
                CameraXViewfinder(
                    surfaceRequest = surfaceRequest,
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = "Live front camera" },
                )
                if (showLandmarks) LandmarkOverlay(faceState.landmarks)
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Videocam, contentDescription = null, tint = colors.textMuted)
                Text("Starting camera…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
        }
        TrackingStatusChip(faceState, Modifier.align(Alignment.TopStart).padding(10.dp))
        overlay?.invoke(this)
    }
}

@Composable
private fun SimulatedPanel(state: FaceState, canRequestCamera: Boolean, requestCamera: () -> Unit) {
    val colors = PwdeTheme.colors
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        val unavailable = state.status as? TrackingStatus.Unavailable
        Icon(
            if (unavailable != null) Icons.Outlined.VideocamOff else Icons.Outlined.ScreenRotation,
            contentDescription = null,
            tint = colors.warning,
        )
        if (unavailable != null) {
            Text("Head tracking unavailable", style = MaterialTheme.typography.titleMedium, color = colors.text, textAlign = TextAlign.Center)
            Text(unavailable.reason, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, textAlign = TextAlign.Center)
        } else {
            Text(DEMO_MODE_LABEL, style = MaterialTheme.typography.titleMedium, color = colors.warning, textAlign = TextAlign.Center)
            Text(
                "${state.fallbackReason ?: "No camera"}. Tilt your phone to try the controls — your face isn't being tracked.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
        }
        if (canRequestCamera) {
            PwdeButton("Turn on camera", requestCamera, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Videocam)
        }
    }
}

@Composable
private fun LandmarkOverlay(landmarks: FloatArray?) {
    val colors = PwdeTheme.colors
    if (landmarks == null) return
    Canvas(Modifier.fillMaxSize()) {
        val points = ArrayList<Offset>(landmarks.size / 2)
        var i = 0
        while (i + 1 < landmarks.size) {
            points += Offset(landmarks[i] * size.width, landmarks[i + 1] * size.height)
            i += 2
        }
        drawPoints(points, PointMode.Points, colors.primary.copy(alpha = 0.8f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun TrackingStatusChip(state: FaceState, modifier: Modifier = Modifier) {
    val colors = PwdeTheme.colors
    val (text, color) = when {
        state.isSimulated -> "DEMO MODE" to colors.warning
        state.status == TrackingStatus.Live -> "Tracking · ${state.fps.toInt()} fps" to colors.primary
        state.status == TrackingStatus.NoFace -> "No face in view" to colors.warning
        else -> "Starting…" to colors.textMuted
    }
    StatusPill(
        text,
        modifier = modifier.background(colors.background.copy(alpha = 0.7f), PwdeShapes.pill),
        color = color,
        icon = if (state.status == TrackingStatus.NoFace) Icons.Outlined.WarningAmber else Icons.Outlined.Face,
    )
}

/** Banner shown anywhere simulated tracking is driving the controls. Never omit it in demo mode. */
@Composable
fun DemoModeBanner(state: FaceState, modifier: Modifier = Modifier) {
    if (!state.isSimulated) return
    val colors = PwdeTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(PwdeShapes.button)
            .background(colors.warning.copy(alpha = 0.15f))
            .border(1.5.dp, colors.warning, PwdeShapes.button)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.ScreenRotation, contentDescription = null, tint = colors.warning)
        Column {
            Text(DEMO_MODE_LABEL, style = MaterialTheme.typography.labelLarge, color = colors.warning)
            Text(
                state.fallbackReason?.let { "$it — tilting the phone stands in for your head." }
                    ?: "Tilting the phone stands in for your head.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text,
            )
        }
    }
}
