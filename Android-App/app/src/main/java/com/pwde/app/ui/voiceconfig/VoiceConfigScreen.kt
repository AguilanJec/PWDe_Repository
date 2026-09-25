package com.pwde.app.ui.voiceconfig

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.model.VoiceShortcut
import com.pwde.app.sensors.voice.CommandScope
import com.pwde.app.sensors.voice.StandardCommands
import com.pwde.app.sensors.voice.VoiceCommandManager
import com.pwde.app.sensors.voice.VoiceState
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.Pager
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.PwdeTextField
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.SwitchRow
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.rememberMicPermissionRequest
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.theme.PwdeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceConfigViewModel(
    private val controlsRepository: ControlsRepository,
    private val voiceCommandManager: VoiceCommandManager,
) : ViewModel() {
    val config: StateFlow<ControlConfig?> = controlsRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val voice: StateFlow<VoiceState> = voiceCommandManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), voiceCommandManager.state.value)

    /** Recent mic levels, oldest first, for the scrolling waveform. */
    private val _levels = MutableStateFlow(List(WAVEFORM_BARS) { 0f })
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    /** Local copy of phrases while typing, so the field never fights the database round-trip. */
    private val _phrases = MutableStateFlow<Map<VoiceShortcut, String>?>(null)
    val phrases: StateFlow<Map<VoiceShortcut, String>?> = _phrases.asStateFlow()

    val hasMicPermission: Boolean get() = voiceCommandManager.hasMicPermission

    init {
        viewModelScope.launch { _phrases.value = controlsRepository.config.first().voiceShortcuts }
        viewModelScope.launch {
            voice.collect { state -> _levels.update { (it + if (state.listening) state.level else 0f).takeLast(WAVEFORM_BARS) } }
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        launch { controlsRepository.setVoiceEnabled(enabled) }
        voiceCommandManager.refreshPermissions()
    }

    fun setMatchMode(mode: VoiceMatchMode) = launch { controlsRepository.setVoiceMatchMode(mode) }
    fun setActivationMode(mode: VoiceActivationMode) = launch { controlsRepository.setVoiceActivationMode(mode) }
    fun onMicPermissionResult() = voiceCommandManager.refreshPermissions()

    fun setPhrase(shortcut: VoiceShortcut, phrase: String) {
        _phrases.update { it.orEmpty() + (shortcut to phrase) }
        launch { controlsRepository.setVoiceShortcut(shortcut, phrase) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val WAVEFORM_BARS = 32
    }
}

private val VOICE_CONFIG_COMMANDS = listOf(
    voiceCommand("exact", "exact phrase", "exact"),
    voiceCommand("anywhere", "word anywhere", "anywhere"),
    voiceCommand("immediate", "right away"),
    voiceCommand("after", "after I finish", "after finish"),
    voiceCommand("next_page", "next page", "commands"),
    voiceCommand("previous_page", "previous page", "previous"),
)

/** E11/E12 Voice: live mic, on/off, matching and activation modes, and the full command list. */
@Composable
fun VoiceConfigScreen(viewModel: VoiceConfigViewModel, onBack: () -> Unit) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val levels by viewModel.levels.collectAsStateWithLifecycle()
    val phrases by viewModel.phrases.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(1) }
    val requestMic = rememberMicPermissionRequest { granted ->
        viewModel.onMicPermissionResult()
        if (granted) viewModel.setVoiceEnabled(true)
    }
    VoiceCommandsEffect(VOICE_CONFIG_COMMANDS) { id ->
        when (id) {
            "exact" -> viewModel.setMatchMode(VoiceMatchMode.EXACT)
            "anywhere" -> viewModel.setMatchMode(VoiceMatchMode.WORD_ANYWHERE)
            "immediate" -> viewModel.setActivationMode(VoiceActivationMode.IMMEDIATE)
            "after" -> viewModel.setActivationMode(VoiceActivationMode.AFTER_FINISH)
            "next_page" -> page = 2
            "previous_page" -> page = 1
        }
    }
    PwdeScreen(
        title = "Voice",
        subtitle = "Say a button's name to press it. Changes save automatically.",
        onBack = onBack,
        voiceHint = "Say \"word anywhere\", \"right away\" or \"next page\"",
        footer = { Pager(page, 2, { page = 1 }, { page = 2 }) },
    ) {
        val current = config ?: return@PwdeScreen
        if (page == 1) {
            SwitchRow(
                "Voice control",
                current.voiceEnabled,
                { enabled -> if (enabled && !viewModel.hasMicPermission) requestMic() else viewModel.setVoiceEnabled(enabled) },
                icon = Icons.Outlined.Mic,
            )
            MicLevel(voice, levels)
            SectionTitle("How words are matched")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
                VoiceMatchMode.entries.forEach { mode ->
                    OptionCard(
                        mode.label, mode.description, mode == current.voiceMatchMode, { viewModel.setMatchMode(mode) },
                        icon = Icons.AutoMirrored.Outlined.FormatListBulleted, kind = OptionKind.RADIO,
                    )
                }
            }
            SectionTitle("When PWDe acts")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
                VoiceActivationMode.entries.forEach { mode ->
                    OptionCard(
                        mode.label, mode.description, mode == current.voiceActivationMode, { viewModel.setActivationMode(mode) },
                        icon = Icons.Outlined.Timer, kind = OptionKind.RADIO,
                    )
                }
            }
        } else {
            SectionTitle("Your spoken shortcuts")
            VoiceShortcut.entries.forEach { shortcut ->
                PwdeTextField(
                    label = shortcut.label,
                    value = phrases?.get(shortcut).orEmpty(),
                    onValueChange = { viewModel.setPhrase(shortcut, it) },
                )
            }
            SectionTitle("Works on every screen")
            CommandList(StandardCommands.all.map { it.phrases.joinToString(" / ") })
            val screenCommands = voice.commands.filter { it.scope == CommandScope.SCREEN }
            if (screenCommands.isNotEmpty()) {
                SectionTitle("On this screen")
                CommandList(screenCommands.map { it.phrases.joinToString(" / ") })
            }
            InfoNote("Every screen adds its own commands — usually the words on its buttons and cards.")
        }
    }
}

@Composable
private fun CommandList(lines: List<String>) {
    GradientCard(Modifier.fillMaxWidth()) {
        lines.forEach { Text("\"$it\"", style = MaterialTheme.typography.bodyMedium, color = PwdeTheme.colors.text) }
    }
}

/** Real mic level from the recognizer, plus what was last heard and matched. */
@Composable
private fun MicLevel(voice: VoiceState, levels: List<Float>) {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mic level", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
            StatusPill(
                when {
                    voice.usesTextFallback -> "Typing only"
                    !voice.enabled -> "Off"
                    voice.listening -> "Listening"
                    else -> "Starting"
                },
                color = if (voice.listening) colors.primary else colors.textMuted,
            )
        }
        Canvas(
            Modifier.fillMaxWidth().height(48.dp).semantics {
                contentDescription = if (voice.listening) "Microphone level, listening" else "Microphone not listening"
            },
        ) {
            val gap = 4.dp.toPx()
            val barWidth = (size.width - gap * (levels.size - 1)) / levels.size
            levels.forEachIndexed { i, level ->
                val h = size.height * (0.1f + 0.9f * level)
                drawRoundRect(
                    color = if (voice.listening) colors.primary else colors.textMuted.copy(alpha = 0.5f),
                    topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
        }
        Text(
            when {
                voice.usesTextFallback -> "${voice.availability.label}. Use the keyboard button in the voice bar to type commands."
                voice.lastTranscript != null -> "Heard: \"${voice.lastTranscript}\"" + (voice.lastCommand?.let { " · last command: ${it.label}" } ?: "")
                voice.enabled -> "Say something — what PWDe hears appears here."
                else -> "Voice control is off."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        voice.lastCommand?.let { StatusPill("Matched: ${it.label}", icon = Icons.Outlined.CheckCircle) }
    }
}
