package com.pwde.app.ui.testingstation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.IconBadge
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

/** F · Testing Station. Panels are in place; every one says so plainly until sensors connect. */
@Composable
fun TestingStationScreen(onBack: () -> Unit) {
    val colors = PwdeTheme.colors
    PwdeScreen(
        title = "Testing Station",
        subtitle = "See exactly what PWDe picks up from you.",
        onBack = onBack,
        voiceHint = "Say \"face\", \"voice\" or \"cursor\"",
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(PwdeShapes.card)
                .background(colors.surfaceMuted)
                .border(2.dp, colors.borderBrush, PwdeShapes.card),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.VideocamOff, contentDescription = null, tint = colors.textMuted)
                Text("Camera feed — not connected", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
        }
        SensorPanel("Face Tracking", Icons.Outlined.Face, "Head position, face in frame, lighting")
        SensorPanel("Gestures", Icons.Outlined.TouchApp, "Smile, open mouth, eyebrow raise, tilt, nod, wink")
        SensorPanel("Voice", Icons.Outlined.Mic, "What PWDe heard and which button it matched")
        SensorPanel("Cursor", Icons.Outlined.Mouse, "Pointer position and speed")
        SensorPanel("Joystick", Icons.Outlined.Gamepad, "Direction and strength")
    }
}

@Composable
private fun SensorPanel(title: String, icon: ImageVector, measures: String) {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(measures, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        StatusPill("No data — sensors not yet connected", color = colors.warning, modifier = Modifier.fillMaxWidth())
    }
}
