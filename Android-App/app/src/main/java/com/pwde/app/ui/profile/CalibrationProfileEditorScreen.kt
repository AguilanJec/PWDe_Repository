package com.pwde.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.data.local.CalibrationProfile
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.local.enabledGestures
import com.pwde.app.data.local.inputModeOrDefault
import com.pwde.app.data.local.toCalibrationProfile
import com.pwde.app.data.local.toControlConfig
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.LevelSlider
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.PwdeTextField
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.SegmentedToggle
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.SwitchRow
import com.pwde.app.ui.theme.PwdeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CalibrationDraft(
    val original: CalibrationProfile,
    val name: String,
    val inputMode: InputMode,
    val config: ControlConfig,
)

class CalibrationProfileEditorViewModel(
    private val profileId: Long,
    private val profiles: ProfileRepository,
    private val controls: ControlsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _draft = MutableStateFlow<CalibrationDraft?>(null)
    internal val draft: StateFlow<CalibrationDraft?> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profiles.getCalibrationProfile(profileId) ?: return@launch
            val current = controls.config.first()
            _draft.value = CalibrationDraft(
                original = profile,
                name = profile.name,
                inputMode = profile.inputModeOrDefault,
                config = profile.toControlConfig(current),
            )
        }
    }

    fun setName(name: String) = _draft.update { it?.copy(name = name) }
    fun setInputMode(mode: InputMode) = _draft.update { it?.copy(inputMode = mode) }
    fun updateConfig(transform: (ControlConfig) -> ControlConfig) = _draft.update { draft ->
        draft?.copy(config = transform(draft.config))
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val draft = _draft.value ?: return@launch
            val saved = draft.config.toCalibrationProfile(
                name = draft.name.trim().ifEmpty { draft.original.name },
                inputMode = draft.inputMode,
                id = draft.original.id,
            ).copy(
                createdAt = draft.original.createdAt,
                remoteId = draft.original.remoteId,
                lastSyncedAt = draft.original.lastSyncedAt,
            )
            profiles.saveCalibrationProfile(saved)
            if (controls.activeCalibrationProfileId.first() == saved.id) {
                controls.applyCalibration(saved)
                settings.setInputMode(saved.inputModeOrDefault)
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val original = _draft.value?.original ?: return@launch
            controls.clearActiveCalibrationProfile(original.id)
            profiles.deleteCalibrationProfile(original)
            onDeleted()
        }
    }
}

@Composable
fun CalibrationProfileEditorScreen(
    viewModel: CalibrationProfileEditorViewModel,
    onBack: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete calibration?") },
            text = { Text("This calibration will be permanently removed.") },
            confirmButton = {
                PwdeButton(
                    "Delete calibration",
                    { viewModel.delete(onBack) },
                    style = ButtonStyle.DESTRUCTIVE,
                )
            },
            dismissButton = { PwdeButton("Cancel", { confirmDelete = false }, style = ButtonStyle.SECONDARY) },
        )
    }
    PwdeScreen(
        title = "Edit calibration",
        subtitle = draft?.name ?: "Loading calibration",
        onBack = onBack,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
                PwdeButton(
                    "Delete",
                    { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    style = ButtonStyle.DESTRUCTIVE,
                    icon = Icons.Outlined.Delete,
                    enabled = draft != null,
                )
                PwdeButton(
                    "Save",
                    { viewModel.save(onBack) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Save,
                    enabled = draft != null,
                )
            }
        },
    ) {
        val current = draft
        if (current == null) {
            StatusPill("Calibration unavailable")
            return@PwdeScreen
        }
        PwdeTextField("Calibration name", current.name, viewModel::setName)

        SectionTitle("Input mode")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            InputMode.entries.forEach { mode ->
                OptionCard(
                    title = mode.label,
                    description = mode.description,
                    selected = current.inputMode == mode,
                    onClick = { viewModel.setInputMode(mode) },
                    kind = OptionKind.RADIO,
                )
            }
        }

        SectionTitle("Gesture assignments")
        GestureAction.entries.forEach { action ->
            GestureAssignmentPicker(
                action = action,
                selected = current.config.gestureAssignments[action],
                onSelect = { gesture ->
                    viewModel.updateConfig { config ->
                        val assignments = config.gestureAssignments.toMutableMap()
                        if (gesture == null) assignments.remove(action) else assignments[action] = gesture
                        config.copy(gestureAssignments = assignments)
                    }
                },
            )
        }

        SectionTitle("Gesture sensitivity")
        FacialGesture.entries.forEach { gesture ->
            LevelSlider(
                gesture.label,
                current.config.sensitivityOf(gesture),
                { level -> viewModel.updateConfig { it.copy(gestureSensitivity = it.gestureSensitivity + (gesture to level)) } },
                valueLabel = current.config.sensitivityOf(gesture).toString(),
            )
        }

        SectionTitle("Available gestures")
        FacialGesture.curated.forEach { gesture ->
            SwitchRow(
                title = gesture.label,
                description = gesture.description,
                checked = current.config.isGestureEnabled(gesture),
                onCheckedChange = { enabled ->
                    viewModel.updateConfig { config ->
                        val enabledGestures = (config.enabledGestures ?: FacialGesture.curated.toSet()).toMutableSet()
                        if (enabled) enabledGestures.add(gesture) else enabledGestures.remove(gesture)
                        config.copy(enabledGestures = enabledGestures)
                    }
                },
            )
        }

        SectionTitle("Cursor")
        LevelSlider("Moving up", current.config.cursor.speedUp, { setCursor(viewModel, current.config.cursor.copy(speedUp = it)) })
        LevelSlider("Moving down", current.config.cursor.speedDown, { setCursor(viewModel, current.config.cursor.copy(speedDown = it)) })
        LevelSlider("Moving left", current.config.cursor.speedLeft, { setCursor(viewModel, current.config.cursor.copy(speedLeft = it)) })
        LevelSlider("Moving right", current.config.cursor.speedRight, { setCursor(viewModel, current.config.cursor.copy(speedRight = it)) })
        LevelSlider("Smoothing", current.config.cursor.smoothing, { setCursor(viewModel, current.config.cursor.copy(smoothing = it)) })

        SectionTitle("Joystick")
        LevelSlider("Size", current.config.joystick.size, { setJoystick(viewModel, current.config.joystick.copy(size = it)) })
        LevelSlider("Sensitivity", current.config.joystick.sensitivity, { setJoystick(viewModel, current.config.joystick.copy(sensitivity = it)) })
        LevelSlider("Dead zone", current.config.joystick.deadZone, { setJoystick(viewModel, current.config.joystick.copy(deadZone = it)) })
        AngleSlider("Center pitch", current.config.joystick.centerPitch) {
            setJoystick(viewModel, current.config.joystick.copy(centerPitch = it))
        }
        AngleSlider("Center roll", current.config.joystick.centerRoll) {
            setJoystick(viewModel, current.config.joystick.copy(centerRoll = it))
        }

        SectionTitle("Voice")
        SwitchRow(
            title = "Voice commands",
            checked = current.config.voiceEnabled,
            onCheckedChange = { enabled -> viewModel.updateConfig { it.copy(voiceEnabled = enabled) } },
        )
        SegmentedToggle(
            VoiceMatchMode.entries,
            current.config.voiceMatchMode,
            { it.label.removePrefix("Match: ") },
            { mode -> viewModel.updateConfig { it.copy(voiceMatchMode = mode) } },
        )
        SegmentedToggle(
            VoiceActivationMode.entries,
            current.config.voiceActivationMode,
            { it.label.removePrefix("Act: ") },
            { mode -> viewModel.updateConfig { it.copy(voiceActivationMode = mode) } },
        )
    }
}

@Composable
private fun GestureAssignmentPicker(
    action: GestureAction,
    selected: FacialGesture?,
    onSelect: (FacialGesture?) -> Unit,
) {
    var expanded by remember(action) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(action.label, style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.label ?: "Not assigned", modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Not assigned") },
                    onClick = { onSelect(null); expanded = false },
                )
                FacialGesture.entries.forEach { gesture ->
                    DropdownMenuItem(
                        text = { Text(gesture.label) },
                        onClick = { onSelect(gesture); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun AngleSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text, modifier = Modifier.weight(1f))
            Text("${value.toInt()}°", style = MaterialTheme.typography.labelMedium, color = PwdeTheme.colors.primary)
        }
        Slider(
            value = value.coerceIn(-30f, 30f),
            onValueChange = onChange,
            valueRange = -30f..30f,
            steps = 59,
        )
    }
}

private fun setCursor(viewModel: CalibrationProfileEditorViewModel, tuning: CursorTuning) =
    viewModel.updateConfig { it.copy(cursor = tuning) }

private fun setJoystick(viewModel: CalibrationProfileEditorViewModel, tuning: JoystickTuning) =
    viewModel.updateConfig { it.copy(joystick = tuning) }
