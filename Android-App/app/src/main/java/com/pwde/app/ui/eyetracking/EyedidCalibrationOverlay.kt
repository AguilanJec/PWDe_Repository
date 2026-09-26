package com.pwde.app.ui.eyetracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pwde.app.sensors.eyedid.GazePoint
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.theme.PwdeTheme

/**
 * The full-screen surface a calibration sweep runs on.
 *
 * It is deliberately its own overlay rather than a panel inside a screen: [dotPx] is a position in
 * **device-screen pixels** that the SDK chose, and the whole point of the sweep is that the user
 * looks at exactly that spot. Drawing the dot inside a smaller card would ask the eyes to travel
 * some other distance than the one being measured, and the calibration would come out confidently
 * wrong.
 *
 * For the same reason there is no camera preview here — it would sit under the corner dots — and the
 * "stop" control lives inside the overlay, because during a sweep the overlay would otherwise cover
 * the screen's own cancel button and leave the user with no way out.
 *
 * @param dotPx where the SDK asked for the dot, in device-screen pixels — or null for the moment
 *   between the sweep starting and the SDK naming its first point. That gap is short but real, and
 *   showing an empty dim with a "get ready" line is the honest thing to do: it confirms the tap
 *   landed instead of looking like the button did nothing.
 * @param progress 0..1 for the point being collected; drawn as a ring filling around the dot.
 * @param index 1-based "point 3 of 5", or null when the count is not known yet.
 * @param message guidance or a diagnosis to show with the dot. It has to live *inside* the overlay:
 *   the overlay covers the screen, so anything the screen says about the sweep is unreadable exactly
 *   when the user needs it.
 */
@Composable
fun EyedidCalibrationOverlay(
    dotPx: GazePoint?,
    progress: Float,
    onCancel: () -> Unit,
    index: Int? = null,
    total: Int? = null,
    message: String? = null,
) {
    val accent = PwdeTheme.colors.primary
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> bounds = coordinates.boundsInWindow() }
            .background(Color.Black.copy(alpha = 0.82f))
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (dotPx != null) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = buildString {
                            append("Calibrating. Look at the white dot")
                            if (index != null && total != null) append(", point $index of $total")
                            append(".")
                        }
                    },
            ) {
                // The SDK works in screen coordinates; this overlay is drawn in its own, so shift across.
                val center = Offset(dotPx.x - bounds.left, dotPx.y - bounds.top)
                val dotRadius = 12.dp.toPx()
                val ringRadius = 34.dp.toPx()
                drawCircle(Color.White, radius = dotRadius, center = center)
                drawCircle(Color.White.copy(alpha = 0.30f), radius = ringRadius, center = center, style = Stroke(2.dp.toPx()))
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (dotPx != null) "Look at the dot" else "Getting ready…",
                style = MaterialTheme.typography.titleMedium,
                color = PwdeTheme.colors.text,
            )
            if (dotPx != null && index != null && total != null) {
                Text(
                    "Point $index of $total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PwdeTheme.colors.textMuted,
                )
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    // A diagnosis is a warning; ordinary guidance is not.
                    color = if (dotPx == null) PwdeTheme.colors.warning else PwdeTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                )
            }
        }
        // Kept out of the way of every corner dot, which is where the user's eyes have to be.
        PwdeButton(
            "Stop calibrating",
            onCancel,
            style = ButtonStyle.SECONDARY,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
        )
    }
}
