package com.pwde.app.ui.voiceconfig

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.Pager
import com.pwde.app.ui.components.PlaceholderNotice
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.PwdeTextField
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.SwitchRow
import com.pwde.app.ui.theme.PwdeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceConfigViewModel(private val controlsRepository: ControlsRepository) : ViewModel() {
    val config: StateFlow<ControlConfig?> = controlsRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Local copy of phrases while typing, so the field never fights the database round-trip. */
    private val _phrases = MutableStateFlow<Map<VoiceShortcut, String>?>(null)
    val phrases: StateFlow<Map<VoiceShortcut, String>?> = _phrases.asStateFlow()

    init {
        viewModelScope.launch { _phrases.value = controlsRepository.config.first().voiceShortcuts }
    }

    fun setVoiceEnabled(enabled: Boolean) = launch { controlsRepository.setVoiceEnabled(enabled) }
    fun setMatchMode(mode: VoiceMatchMode) = launch { controlsRepository.setVoiceMatchMode(mode) }
    fun setActivationMode(mode: VoiceActivationMode) = launch { controlsRepository.setVoiceActivationMode(mode) }

    fun setPhrase(shortcut: VoiceShortcut, phrase: String) {
        _phrases.update { it.orEmpty() + (shortcut to phrase) }
        launch { controlsRepository.setVoiceShortcut(shortcut, phrase) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** E11/E12 Voice. Settings save to this phone now; listening starts in Prompt 2. */
@Composable
fun VoiceConfigScreen(viewModel: VoiceConfigViewModel, onBack: () -> Unit) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val phrases by viewModel.phrases.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(1) }
    PwdeScreen(
        title = "Voice",
        subtitle = "Say a button's name to press it. Changes save automatically.",
        onBack = onBack,
        voiceHint = "Say \"word anywhere\" or \"right away\"",
        footer = { Pager(page, 2, { page = 1 }, { page = 2 }) },
    ) {
        val current = config ?: return@PwdeScreen
        if (page == 1) {
            SwitchRow("Voice control", current.voiceEnabled, viewModel::setVoiceEnabled, icon = Icons.Outlined.Mic)
            MicLevelPlaceholder()
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
        }
    }
}

/** Static waveform. Real mic levels arrive with the voice engine in Prompt 2 — nothing is recorded. */
@Composable
private fun MicLevelPlaceholder() {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth()) {
        Text("Mic level", style = MaterialTheme.typography.titleMedium, color = colors.text)
        Canvas(
            Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Microphone level placeholder, not listening" },
        ) {
            val bars = 32
            val gap = 4.dp.toPx()
            val barWidth = (size.width - gap * (bars - 1)) / bars
            repeat(bars) { i ->
                val h = size.height * 0.15f
                drawRoundRect(
                    color = colors.textMuted.copy(alpha = 0.5f),
                    topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
        }
        Text("Not listening — mic connects in Prompt 2", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
    PlaceholderNotice(
        "Settings saved, listening off",
        "Your choices are stored on this phone. PWDe will use them once voice recognition is connected.",
    )
}
