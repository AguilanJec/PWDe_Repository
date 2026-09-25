package com.pwde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

/** Gradient card with an accent border. Clickable when [onClick] is given. */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = PwdeTheme.spacing.internal,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PwdeTheme.colors
    Column(
        modifier
            .clip(PwdeShapes.card)
            .background(colors.cardBrush)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) colors.primary else colors.secondary.copy(alpha = 0.55f),
                shape = PwdeShapes.card,
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Flat muted panel for notes and settings rows. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(PwdeShapes.button)
            .background(PwdeTheme.colors.surfaceMuted)
            .padding(PwdeTheme.spacing.internal),
        content = content,
    )
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = PwdeTheme.colors.primary) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f))
            .border(1.5.dp, tint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

enum class OptionKind { CHECKBOX, RADIO }

/** Selectable option card (Figma "CB / Option Card"): icon badge, title, description, indicator. */
@Composable
fun OptionCard(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: OptionKind = OptionKind.CHECKBOX,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = PwdeTheme.colors
    val selectionModifier = when (kind) {
        OptionKind.CHECKBOX -> Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
        OptionKind.RADIO -> Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(PwdeShapes.card)
            .background(colors.cardBrush)
            .border(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.secondary.copy(alpha = 0.5f), PwdeShapes.card)
            .then(selectionModifier)
            .padding(horizontal = PwdeTheme.spacing.internal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        if (trailing != null) trailing() else SelectionIndicator(selected, kind)
    }
}

@Composable
fun SelectionIndicator(selected: Boolean, kind: OptionKind) {
    val colors = PwdeTheme.colors
    val shape = if (kind == OptionKind.RADIO) CircleShape else PwdeShapes.field
    Box(
        Modifier
            .size(26.dp)
            .clip(shape)
            .background(if (selected) colors.primary else Color.Transparent)
            .border(2.dp, if (selected) colors.primary else colors.textMuted, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            if (kind == OptionKind.CHECKBOX) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
            } else {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors.onAccent))
            }
        }
    }
}

/** Card used on hubs: icon, title, subtitle, chevron (Figma "P3 / Nav Card"). */
@Composable
fun NavCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val colors = PwdeTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(PwdeShapes.card)
            .background(colors.cardBrush)
            .border(1.dp, colors.secondary.copy(alpha = 0.55f), PwdeShapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PwdeTheme.spacing.internal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            if (badge != null) StatusPill(badge, color = colors.warning, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.text)
    }
}

/** Small rounded pill label. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, color: Color = PwdeTheme.colors.primary, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(PwdeShapes.pill)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, PwdeShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Plain-language info note with an (i) icon. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(PwdeShapes.button)
            .background(PwdeTheme.colors.surfaceMuted)
            .padding(PwdeTheme.spacing.internal),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = PwdeTheme.colors.primary, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = PwdeTheme.colors.text)
    }
}

/**
 * Honest "not built yet" marker for anything that depends on hardware or later prompts.
 * Never replace this with made-up data.
 */
@Composable
fun PlaceholderNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tag: String = "Coming soon",
) {
    val colors = PwdeTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(PwdeShapes.button)
            .background(colors.warning.copy(alpha = 0.10f))
            .border(1.5.dp, colors.warning, PwdeShapes.button)
            .padding(PwdeTheme.spacing.internal)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusPill(tag, color = colors.warning, icon = Icons.Outlined.Construction)
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
    }
}

/** Labelled switch row; the whole row is the 48dp+ touch target. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = PwdeTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(PwdeShapes.button)
            .background(colors.surfaceMuted)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = PwdeTheme.spacing.internal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics { contentDescription = "" }.size(width = 52.dp, height = MinTouchTarget),
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                checkedThumbColor = colors.onAccent,
                uncheckedTrackColor = colors.surface,
                uncheckedThumbColor = colors.textMuted,
                uncheckedBorderColor = colors.textMuted,
            ),
        )
    }
}
