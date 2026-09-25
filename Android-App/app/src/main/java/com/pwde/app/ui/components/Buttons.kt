package com.pwde.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pwde.app.ui.theme.ControlHeight
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import com.pwde.app.ui.theme.iconSizeFor
import com.pwde.app.ui.theme.scaled

enum class ButtonStyle { PRIMARY, SECONDARY, DESTRUCTIVE }

/**
 * Full-width 56dp button. Primary = mint→purple gradient; secondary = outlined; destructive = red.
 * The visible text doubles as the button's spoken name: screens register it as a voice command.
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
            .heightIn(min = ControlHeight)
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
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(iconSizeFor(ControlHeight).scaled()))
                Box(Modifier.size(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Primary-style (gradient, 56dp) button with a switch on its end, e.g. "Use PWDe". The whole button
 * is one switch for TalkBack; [onClick] decides what a tap does (it may open Settings, not flip).
 */
@Composable
fun PwdeToggleButton(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    details: List<String>? = null,
) {
    val colors = PwdeTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ControlHeight)
            .clip(PwdeShapes.button)
            .background(colors.buttonBrush)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onClick() })
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(iconSizeFor(ControlHeight).scaled()))
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onAccent)
            if (!details.isNullOrEmpty()) {
                details.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onAccent.copy(alpha = 0.85f))
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.onAccent,
                checkedThumbColor = colors.primary,
                checkedBorderColor = colors.onAccent,
                uncheckedTrackColor = colors.onAccent.copy(alpha = 0.25f),
                uncheckedThumbColor = colors.onAccent,
                uncheckedBorderColor = colors.onAccent,
            ),
        )
    }
}

/**
 * Square outlined icon-only button, e.g. the GabAI nudge arrows. Same height, corner radius and
 * border as a secondary [PwdeButton]; the icon grows with [size] and with the user's text size.
 */
@Composable
fun PwdeIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ControlHeight,
) {
    val colors = PwdeTheme.colors
    Box(
        modifier
            .size(size)
            .clip(PwdeShapes.button)
            .border(2.dp, colors.primary, PwdeShapes.button)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(iconSizeFor(size, 0.5f).scaled()))
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
