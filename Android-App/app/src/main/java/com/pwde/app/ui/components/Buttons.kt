package com.pwde.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

enum class ButtonStyle { PRIMARY, SECONDARY, DESTRUCTIVE }

/**
 * Full-width 56dp button. Primary = mint→purple gradient; secondary = outlined; destructive = red.
 * The visible text doubles as the button's spoken name for voice control (Prompt 2).
 */
@Composable
fun PwdeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = PwdeTheme.colors
    val (background, border, content) = when (style) {
        ButtonStyle.PRIMARY -> Triple(colors.buttonBrush, null, colors.onAccent)
        ButtonStyle.SECONDARY -> Triple(SolidColor(colors.background.copy(alpha = 0f)), BorderStroke(2.dp, colors.primary), colors.primary)
        ButtonStyle.DESTRUCTIVE -> Triple(SolidColor(colors.danger), null, colors.onAccent)
    }
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(PwdeShapes.button)
            .background(background)
            .then(if (border != null) Modifier.border(border, PwdeShapes.button) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                Box(Modifier.size(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center)
        }
    }
}

/** Back + primary action pair used in step footers. */
@Composable
fun FooterActions(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryIcon: ImageVector? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryIcon: ImageVector? = null,
    primaryEnabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (secondaryText != null && onSecondary != null) {
            PwdeButton(
                text = secondaryText,
                onClick = onSecondary,
                style = ButtonStyle.SECONDARY,
                icon = secondaryIcon,
                modifier = Modifier.weight(1f),
            )
        }
        PwdeButton(
            text = primaryText,
            onClick = onPrimary,
            icon = primaryIcon,
            enabled = primaryEnabled,
            modifier = Modifier.weight(if (secondaryText != null) 1.6f else 1f),
        )
    }
}

/** Small text button with a 48dp touch target, e.g. "Skip". */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(PwdeShapes.button)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = PwdeTheme.colors.primary)
    }
}
