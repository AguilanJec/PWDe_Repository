package com.pwde.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.pwde.app.data.model.MappedButton
import com.pwde.app.ui.theme.PwdeTheme

/** "show controls": every mapped button and what presses it, one row each. */
@Composable
fun ControlsList(buttons: List<MappedButton>, onClose: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val colors = PwdeTheme.colors
    GradientCard(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your controls", style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f))
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Hide controls", tint = colors.primary) }
            }
        }
        if (buttons.isEmpty()) {
            Text("No buttons mapped yet.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
        buttons.forEach { button ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap), verticalAlignment = Alignment.CenterVertically) {
                Text(button.label, style = MaterialTheme.typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
                Text(
                    button.trigger?.shortLabel() ?: "Not mapped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (button.trigger == null) colors.warning else colors.primary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
