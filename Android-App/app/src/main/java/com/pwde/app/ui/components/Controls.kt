package com.pwde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

/** "Step 1 of 3 · What you need" + segmented progress bar. */
@Composable
fun StepProgress(step: Int, total: Int, label: String, modifier: Modifier = Modifier) {
    val colors = PwdeTheme.colors
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Step $step of $total, $label"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Step $step of $total · $label", style = MaterialTheme.typography.labelMedium, color = colors.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(PwdeShapes.pill)
                        .background(if (index < step) colors.primary else colors.surfaceMuted),
                )
            }
        }
    }
}

/**
 * −  ▮▮▮▮▯▯▯▯▯▯  +  stepper (Figma "P3 / Stepper") in place of fine-motor sliders.
 * [enabled] = false renders it inert.
 */
@Composable
fun LevelStepper(
    label: String,
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: String = levelWord(level),
    max: Int = 10,
    enabled: Boolean = true,
) {
    val colors = PwdeTheme.colors
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = false) { stateDescription = "$valueLabel, $level of $max" },
        ) {
            StepperButton(
                description = "Less $label",
                enabled = enabled && level > 1,
                onClick = { onLevelChange(level - 1) },
            ) { Icon(Icons.Filled.Remove, contentDescription = null, tint = colors.primary) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(max) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < level) colors.primary else colors.surfaceMuted),
                    )
                }
            }
            StepperButton(
                description = "More $label",
                enabled = enabled && level < max,
                onClick = { onLevelChange(level + 1) },
            ) { Icon(Icons.Filled.Add, contentDescription = null, tint = colors.primary) }
        }
    }
}

@Composable
private fun StepperButton(description: String, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(MinTouchTarget)
            .clip(PwdeShapes.button)
            .border(2.dp, PwdeTheme.colors.primary.copy(alpha = if (enabled) 1f else 0.35f), PwdeShapes.button)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

fun levelWord(level: Int): String = when {
    level <= 3 -> "Low"
    level <= 7 -> "Medium"
    else -> "High"
}

/**
 * Two-or-more segment toggle (e.g. Basic / Advanced). [selected] may be null, which renders nothing
 * highlighted — used when a value has been edited away from every preset.
 */
@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PwdeTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(PwdeShapes.button)
            .background(colors.surfaceMuted)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchTarget)
                    .clip(PwdeShapes.button)
                    .background(if (isSelected) colors.primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.onAccent else colors.text,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** "‹ Previous   Page 1 of 2   Next ›" pager (Figma "P3 / Pager"). */
@Composable
fun Pager(page: Int, pageCount: Int, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PwdeButton("Previous", onPrevious, style = ButtonStyle.SECONDARY, enabled = page > 1, modifier = Modifier.weight(1f))
        Text(
            "Page $page of $pageCount",
            style = MaterialTheme.typography.labelMedium,
            color = PwdeTheme.colors.text,
        )
        PwdeButton("Next", onNext, style = ButtonStyle.SECONDARY, enabled = page < pageCount, modifier = Modifier.weight(1f))
    }
}
