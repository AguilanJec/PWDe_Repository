package com.pwde.app.ui.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Accessible
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.accessibility.PwdeAccessibilityService
import com.pwde.app.data.prefs.AccessibilityNeed
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.TextSizeOption
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.FooterActions
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.StepProgress
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.rememberCameraPermissionRequest
import com.pwde.app.ui.components.rememberMicPermissionRequest
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import com.pwde.app.ui.theme.colorsFor
import kotlin.math.roundToInt

private val SETUP_COMMANDS = listOf(
    voiceCommand("continue", "continue", "next", "finish"),
    voiceCommand("skip", "skip"),
    voiceCommand("bigger", "bigger", "larger"),
    voiceCommand("smaller", "smaller"),
) + AccessibilityNeed.entries.map { voiceCommand("need:${it.name}", it.label) }

private val PERMISSION_COMMANDS = listOf(
    voiceCommand("allow", "allow"),
    voiceCommand("camera", "allow camera", "camera"),
    voiceCommand("mic", "allow microphone", "microphone"),
    voiceCommand("accessibility", "allow accessibility service", "accessibility service", "accessibility"),
    voiceCommand("overlay", "allow display over apps", "display over apps", "display"),
    voiceCommand("app_settings", "open app settings"),
)
@Composable
fun SetupScreen(viewModel: SetupViewModel, onExit: () -> Unit, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    VoiceCommandsEffect(SETUP_COMMANDS) { id ->
        when {
            id == "continue" -> viewModel.continueStep()
            id == "skip" -> viewModel.skipStep()
            id == "bigger" -> TextSizeOption.entries.getOrNull(state.textSize.ordinal + 1)?.let(viewModel::setTextSize)
            id == "smaller" -> TextSizeOption.entries.getOrNull(state.textSize.ordinal - 1)?.let(viewModel::setTextSize)
            id.startsWith("need:") -> viewModel.toggleNeed(AccessibilityNeed.valueOf(id.removePrefix("need:")))
        }
    }
    BackHandler { if (!viewModel.back()) onExit() }
    if (!state.loaded) return

    PwdeTheme(colorScheme = state.colorScheme, textSize = state.textSize, layoutMode = state.layoutMode) {
        val (title, subtitle, hint) = when (state.step) {
            SetupStep.APPEARANCE -> Triple(
                "How it looks",
                "Choose colours, text size and layout. The screen updates as you go.",
                "Say \"bigger\" or \"smaller\"",
            )
            SetupStep.NEEDS -> Triple(
                "What do you need help with?",
                "Pick all that apply. You can change this any time.",
                "Say an option's name to tick it",
            )
            SetupStep.PERMISSIONS -> Triple(
                "Allow PWDe to help",
                "PWDe needs these to see, hear and press buttons for you.",
                "Say \"allow\" to open the next one",
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
                SetupStep.APPEARANCE -> AppearanceStep(state, viewModel)
                SetupStep.NEEDS -> NeedsStep(state.needs, viewModel::toggleNeed)
                SetupStep.PERMISSIONS -> PermissionsStep()
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

    TextSizeSlider(state.textSize, viewModel::setTextSize)
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

/** Snaps to each [TextSizeOption]; the draft theme around the screen rescales as it moves. */
@Composable
private fun TextSizeSlider(selected: TextSizeOption, onSelect: (TextSizeOption) -> Unit) {
    val colors = PwdeTheme.colors
    val sizes = TextSizeOption.entries
    val valueLabel = "${selected.label} · ${selected.percentLabel}"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Text size", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            Slider(
                value = selected.ordinal.toFloat(),
                onValueChange = { onSelect(sizes[it.roundToInt().coerceIn(0, sizes.lastIndex)]) },
                valueRange = 0f..sizes.lastIndex.toFloat(),
                steps = sizes.size - 2,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchTarget)
                    .semantics {
                        contentDescription = "Text size"
                        stateDescription = valueLabel
                    },
                colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary),
            )
            Text("A", style = MaterialTheme.typography.titleLarge, color = colors.text)
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

/**
 * B5 · Permissions. Camera and microphone are runtime permissions PWDe can request directly;
 * accessibility service and display-over-apps can only be switched on by the user in Android
 * Settings, so those cards open the relevant settings screen instead. All four are re-checked
 * on resume so changes made in Settings show up, and all four stay optional — PWDe falls back
 * to a demo mode and typed commands when one is missing.
 */
@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var camera by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }
    var mic by remember { mutableStateOf(context.isGranted(Manifest.permission.RECORD_AUDIO)) }
    var accessibility by remember { mutableStateOf(PwdeAccessibilityService.isEnabled(context)) }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        camera = context.isGranted(Manifest.permission.CAMERA)
        mic = context.isGranted(Manifest.permission.RECORD_AUDIO)
        accessibility = PwdeAccessibilityService.isEnabled(context)
        overlay = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }
    val requestCamera = rememberCameraPermissionRequest { granted ->
        camera = granted
        if (!granted) denied = true
    }
    val requestMic = rememberMicPermissionRequest { granted ->
        mic = granted
        if (!granted) denied = true
    }
    val openAccessibilitySettings = { context.startActivity(PwdeAccessibilityService.settingsIntent()) }
    val openOverlaySettings = {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    // Say "allow" to open whichever of these isn't granted yet, in this order.
    val openNext = {
        when {
            !camera -> requestCamera()
            !mic -> requestMic()
            !accessibility -> openAccessibilitySettings()
            !overlay -> openOverlaySettings()
            else -> Unit
        }
    }
    VoiceCommandsEffect(PERMISSION_COMMANDS) { id ->
        when (id) {
            "allow" -> openNext()
            "camera" -> if (!camera) requestCamera()
            "mic" -> if (!mic) requestMic()
            "accessibility" -> if (!accessibility) openAccessibilitySettings()
            "overlay" -> if (!overlay) openOverlaySettings()
            "app_settings" -> openAppSettings()
        }
    }

    OptionCard(
        title = "Camera",
        description = if (camera) "Allowed" else "Tracks your head, face and eyes",
        selected = camera,
        onClick = { if (!camera) requestCamera() },
        icon = Icons.Outlined.PhotoCamera,
    )
    OptionCard(
        title = "Microphone",
        description = if (mic) "Allowed" else "Hears your voice commands",
        selected = mic,
        onClick = { if (!mic) requestMic() },
        icon = Icons.Outlined.Mic,
    )
    OptionCard(
        title = "Accessibility service",
        description = if (accessibility) "Allowed" else "Lets PWDe press buttons in games",
        selected = accessibility,
        onClick = { if (!accessibility) openAccessibilitySettings() },
        icon = Icons.AutoMirrored.Outlined.Accessible,
    )
    OptionCard(
        title = "Display over apps",
        description = if (overlay) "Allowed" else "Shows your controls on top of games",
        selected = overlay,
        onClick = { if (!overlay) openOverlaySettings() },
        icon = Icons.Outlined.Layers,
    )
    InfoNote("Your face never leaves this phone. Nothing is recorded or uploaded.", icon = Icons.Outlined.Shield)
    if (denied && !(camera && mic)) {
        PwdeButton("Open app settings", openAppSettings, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Settings, modifier = Modifier.fillMaxWidth())
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
