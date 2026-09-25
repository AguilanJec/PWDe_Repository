package com.pwde.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Accessible
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.data.prefs.AccessibilityNeed
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.TextSizeOption
import com.pwde.app.ui.components.FooterActions
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.LevelStepper
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.PlaceholderNotice
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.StepProgress
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import com.pwde.app.ui.theme.colorsFor

/** B · Setup. The whole screen renders in the draft theme, so appearance changes preview live. */
@Composable
fun SetupScreen(viewModel: SetupViewModel, onExit: () -> Unit, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    BackHandler { if (!viewModel.back()) onExit() }
    if (!state.loaded) return

    PwdeTheme(colorScheme = state.colorScheme, textSize = state.textSize, layoutMode = state.layoutMode) {
        val (title, subtitle, hint) = when (state.step) {
            SetupStep.NEEDS -> Triple(
                "What do you need help with?",
                "Pick all that apply. You can change this any time.",
                "Say an option's name to tick it",
            )
            SetupStep.APPEARANCE -> Triple(
                "How it looks",
                "Choose colours, text size and layout. The screen updates as you go.",
                "Say \"bigger\" or \"smaller\"",
            )
            SetupStep.INPUT -> Triple(
                "How will you play?",
                "Pick your main way to control games. You can add more later.",
                "Say \"head\", \"joystick\" or \"voice\"",
            )
        }
        PwdeScreen(
            title = title,
            subtitle = subtitle,
            onBack = { if (!viewModel.back()) onExit() },
            voiceHint = hint,
            footer = {
                FooterActions(
                    primaryText = if (state.isLastStep) "Finish" else "Continue",
                    onPrimary = viewModel::continueStep,
                    primaryIcon = if (state.isLastStep) Icons.Outlined.Check else null,
                    secondaryText = "Skip",
                    onSecondary = viewModel::skipStep,
                )
            },
        ) {
            if (state.steps.size > 1) {
                StepProgress(step = state.stepIndex + 1, total = state.steps.size, label = state.step.label)
            }
            when (state.step) {
                SetupStep.NEEDS -> NeedsStep(state.needs, viewModel::toggleNeed)
                SetupStep.APPEARANCE -> AppearanceStep(state, viewModel)
                SetupStep.INPUT -> InputStep(state, viewModel)
            }
        }
    }
}

@Composable
private fun NeedsStep(selected: Set<AccessibilityNeed>, onToggle: (AccessibilityNeed) -> Unit) {
    AccessibilityNeed.entries.forEach { need ->
        OptionCard(
            title = need.label,
            description = need.description,
            selected = need in selected,
            onClick = { onToggle(need) },
            icon = need.icon(),
        )
    }
}

private fun AccessibilityNeed.icon(): ImageVector = when (this) {
    AccessibilityNeed.MOVEMENT -> Icons.AutoMirrored.Outlined.Accessible
    AccessibilityNeed.SEEING -> Icons.Outlined.Visibility
    AccessibilityNeed.HEARING -> Icons.Outlined.Hearing
    AccessibilityNeed.SPEAKING -> Icons.Outlined.RecordVoiceOver
    AccessibilityNeed.OTHER -> Icons.Outlined.MoreHoriz
}

@Composable
private fun AppearanceStep(state: SetupUiState, viewModel: SetupViewModel) {
    SectionTitle("Color scheme")
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorSchemeOption.entries.forEach { option ->
            ColorSchemeTile(option, option == state.colorScheme, { viewModel.setColorScheme(option) }, Modifier.weight(1f))
        }
    }

    val sizes = TextSizeOption.entries
    LevelStepper(
        label = "Text size",
        level = state.textSize.ordinal + 1,
        max = sizes.size,
        valueLabel = "${state.textSize.label} · ${state.textSize.percentLabel}",
        onLevelChange = { viewModel.setTextSize(sizes[(it - 1).coerceIn(0, sizes.lastIndex)]) },
    )
    GradientCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Aa", style = MaterialTheme.typography.headlineMedium, color = PwdeTheme.colors.text)
            Column {
                Text("Live preview", style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
                Text("This is how text will look.", style = MaterialTheme.typography.bodyMedium, color = PwdeTheme.colors.textMuted)
            }
        }
    }

    SectionTitle("Layout")
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
        LayoutMode.entries.forEach { mode ->
            OptionCard(
                title = mode.label,
                description = mode.description,
                selected = mode == state.layoutMode,
                onClick = { viewModel.setLayoutMode(mode) },
                kind = OptionKind.RADIO,
            )
        }
    }
}

@Composable
private fun ColorSchemeTile(option: ColorSchemeOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val swatch = colorsFor(option)
    val colors = PwdeTheme.colors
    Column(
        modifier
            .heightIn(min = 88.dp)
            .clip(PwdeShapes.button)
            .background(swatch.background)
            .border(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.textMuted, PwdeShapes.button)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(swatch.primary))
            Box(Modifier.size(16.dp).clip(CircleShape).background(swatch.secondary))
        }
        Text(option.label, style = MaterialTheme.typography.labelSmall, color = swatch.text, maxLines = 2)
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = swatch.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun InputStep(state: SetupUiState, viewModel: SetupViewModel) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
        InputMode.entries.forEach { mode ->
            OptionCard(
                title = mode.label + if (mode == InputMode.HEAD_FACE) " (recommended)" else "",
                description = mode.description,
                selected = mode == state.inputMode,
                onClick = { viewModel.setInputMode(mode) },
                icon = mode.icon(),
                kind = OptionKind.RADIO,
            )
        }
    }
    SectionTitle("Try it now")
    PlaceholderNotice(
        title = "Live preview available after Prompt 2",
        body = "Camera and microphone aren't connected yet, so PWDe can't follow your ${state.inputMode.label.lowercase()} " +
            "here. Below is a manual simulation only — nothing is being tracked.",
    )
    SimulatedTarget(state.simulatedPosition, viewModel::setSimulatedPosition)
}

/** A manual stand-in for the live preview: the slider moves the pointer. Clearly labelled as simulated. */
@Composable
private fun SimulatedTarget(position: Float, onPositionChange: (Float) -> Unit) {
    val colors = PwdeTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PwdeShapes.card)
            .background(colors.surfaceMuted)
            .padding(PwdeTheme.spacing.internal),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SIMULATED — drag the slider", style = MaterialTheme.typography.labelSmall, color = colors.warning)
        BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp)) {
            val travel = maxWidth - 40.dp
            Box(
                Modifier.align(Alignment.CenterEnd).size(56.dp).clip(CircleShape)
                    .border(3.dp, colors.primary, CircleShape),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = travel * position)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.secondary),
            )
        }
        Slider(
            value = position,
            onValueChange = onPositionChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)
                .semantics { contentDescription = "Simulated pointer position" },
            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary),
        )
        Text(
            if (position > 0.9f) "On target! In Prompt 2 your head or voice will do this."
            else "Move the pointer onto the ring.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.text,
        )
    }
}

private fun InputMode.icon(): ImageVector = when (this) {
    InputMode.HEAD_FACE -> Icons.Outlined.Face
    InputMode.JOYSTICK -> Icons.Outlined.Gamepad
    InputMode.VOICE -> Icons.Outlined.Mic
}
