package com.pwde.app.ui.gabai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.IconBadge
import com.pwde.app.ui.components.NavCard
import com.pwde.app.ui.components.PlaceholderNotice
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.theme.PwdeTheme

enum class GabAiChoice(val id: String, val title: String, val subtitle: String, val icon: ImageVector) {
    NEW_CALIBRATION("calibration", "New Calibration Profile", "Tune cursor, joystick and voice to you", Icons.Outlined.Tune),
    NEW_GAME("game", "New Game Profile", "Map a game's buttons to your moves", Icons.Outlined.SportsEsports),
    CONTINUE("continue", "Continue Existing", "Pick up a profile you started", Icons.Outlined.History);

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** G1 GabAI – Start. The state machine behind each choice is built in Prompt 3. */
@Composable
fun GabAiWelcomeScreen(onBack: () -> Unit, onChoose: (GabAiChoice) -> Unit) {
    val colors = PwdeTheme.colors
    PwdeScreen(
        title = "GabAI",
        subtitle = "Your setup assistant.",
        onBack = onBack,
        voiceHint = "Say \"new calibration\", \"new game\" or \"continue\"",
    ) {
        GradientCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Outlined.AutoAwesome, tint = colors.secondary)
                Text(
                    "Hi, I'm GabAI! I'll walk you through setting up PWDe one small step at a time. What would you like to do?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text,
                )
            }
        }
        GabAiChoice.entries.forEach { choice ->
            NavCard(choice.title, choice.subtitle, choice.icon, { onChoose(choice) })
        }
    }
}

@Composable
fun GabAiComingScreen(choice: GabAiChoice?, onBack: () -> Unit) {
    PwdeScreen(
        title = choice?.title ?: "GabAI",
        onBack = onBack,
        footer = { PwdeButton("Back to GabAI", onBack, modifier = Modifier.fillMaxWidth()) },
    ) {
        PlaceholderNotice(
            title = "GabAI's guided steps aren't built yet",
            body = when (choice) {
                GabAiChoice.NEW_CALIBRATION -> "You'll choose a mode, then tune cursor, joystick and voice matching, and save it as a profile."
                GabAiChoice.NEW_GAME -> "You'll pick a game, take a screenshot, and map each on-screen button to a voice command or gesture."
                else -> "You'll be able to reopen a saved profile and keep tuning it."
            },
            tag = "Coming in Prompt 3",
        )
    }
}
