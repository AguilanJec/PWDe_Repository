package com.pwde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

/**
 * Modal panel in PWDe's own styling, for content that is too big for the screen behind it.
 *
 * Deliberately not `AlertDialog`: that brings Android's own chrome, button row and text styles,
 * which fight the themes in `ui/theme` and break down at the larger text sizes PWDe supports. This
 * is a plain [Dialog] wrapped around the same cards, spacing and buttons the rest of the app uses.
 *
 * Every way out routes to [onDismiss] — the close button, a tap outside, and the back gesture — so
 * callers only have to handle one callback. The body scrolls, so a long panel stays usable on a
 * short screen.
 */
@Composable
fun PwdeDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PwdeTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier
                .fillMaxWidth()
                // Bounded so the body scrolls instead of growing past the screen; taller content is
                // reachable by scrolling rather than by turning the device.
                .heightIn(max = 560.dp)
                .clip(PwdeShapes.card)
                .background(colors.cardBrush)
                .border(1.dp, colors.secondary.copy(alpha = 0.55f), PwdeShapes.card)
                .semantics { paneTitle = title }
                .padding(PwdeTheme.spacing.internal),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = colors.textMuted)
                }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap),
                content = content,
            )
        }
    }
}
