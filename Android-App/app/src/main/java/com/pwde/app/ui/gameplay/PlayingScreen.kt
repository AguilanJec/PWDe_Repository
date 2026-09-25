package com.pwde.app.ui.gameplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pwde.app.data.model.Game
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

/**
 * D4/D5 Playing view skeleton: simulated background + static PWDe overlay. Back or "Exit to PWDe"
 * returns to the menu. The real overlay over a running game lands in Prompts 2–3.
 */
@Composable
fun PlayingScreen(game: Game?, onExit: () -> Unit) {
    var paused by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onExit)
    val colors = PwdeTheme.colors

    Box(Modifier.fillMaxSize().background(Color(0xFF1B2A1E))) {
        SimulatedBackground()
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill("SIMULATED GAME — ${game?.displayName ?: "preview"}", color = colors.warning, modifier = Modifier.weight(1f))
            }
            Column(
                Modifier
                    .clip(PwdeShapes.card)
                    .background(colors.background.copy(alpha = 0.92f))
                    .border(2.dp, colors.primary, PwdeShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("PWDe", style = MaterialTheme.typography.titleLarge, color = colors.primary)
                Text(
                    if (paused) "Paused — your head, face and voice won't move anything."
                    else "Overlay preview. Tracking isn't connected yet (Prompt 2), so nothing is being controlled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                )
                PwdeButton(
                    if (paused) "Resume" else "Pause",
                    { paused = !paused },
                    icon = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    modifier = Modifier.fillMaxWidth(),
                )
                PwdeButton(
                    "Exit to PWDe",
                    onExit,
                    style = ButtonStyle.SECONDARY,
                    icon = Icons.AutoMirrored.Outlined.ExitToApp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

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
