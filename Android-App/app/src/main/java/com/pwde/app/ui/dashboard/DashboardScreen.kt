package com.pwde.app.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pwde.app.R
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.remote.AuthRepository
import com.pwde.app.data.remote.AuthState
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.IconBadge
import com.pwde.app.ui.components.MainTab
import com.pwde.app.ui.components.PwdeBottomNav
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val greetingName: String? = null,
    val inputMode: InputMode = InputMode.HEAD_FACE,
)

class DashboardViewModel(settingsRepository: SettingsRepository, authRepository: AuthRepository) : ViewModel() {
    val state: StateFlow<DashboardUiState> = combine(settingsRepository.settings, authRepository.authState) { settings, auth ->
        DashboardUiState(
            greetingName = (auth as? AuthState.SignedIn)?.let { it.displayName ?: it.email?.substringBefore('@') },
            inputMode = settings.inputMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}

enum class DashboardDestination { CONTROLS, VOICE, TESTING_STATION, WATCH_TUTORIAL, GABAI, START_PLAYING }

/** D1 Play – Dashboard. Status first, one big "Start playing", four entry cards, GabAI. */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigate: (DashboardDestination) -> Unit,
    onTab: (MainTab) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PwdeTheme.colors
    PwdeScreen(
        title = null,
        voiceHint = "Say \"start playing\" or a card's name",
        bottomBar = { PwdeBottomNav(MainTab.PLAY, onTab) },
    ) {
        Image(painterResource(R.drawable.logo_wordmark), contentDescription = "PWDe", modifier = Modifier.width(140.dp))
        state.greetingName?.let {
            Text("Welcome back, $it", style = MaterialTheme.typography.titleLarge, color = colors.text)
        }

        ServiceStatusBanner()

        GradientCard(Modifier.fillMaxWidth()) {
            Text("Play your way", style = MaterialTheme.typography.headlineSmall, color = colors.text)
            Text(
                "Pick a game and PWDe sets up the controls with you.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            PwdeButton(
                "Start playing",
                { onNavigate(DashboardDestination.START_PLAYING) },
                icon = Icons.Outlined.SportsEsports,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Using:", style = MaterialTheme.typography.labelMedium, color = colors.text)
            StatusPill(state.inputMode.label, icon = state.inputMode.icon())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            EntryCard("Controls", Icons.Outlined.Tune, Modifier.weight(1f)) { onNavigate(DashboardDestination.CONTROLS) }
            EntryCard("Voice", Icons.Outlined.RecordVoiceOver, Modifier.weight(1f)) { onNavigate(DashboardDestination.VOICE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            EntryCard("Testing Station", Icons.Outlined.Radar, Modifier.weight(1f)) { onNavigate(DashboardDestination.TESTING_STATION) }
            EntryCard("Watch Tutorial", Icons.Outlined.OndemandVideo, Modifier.weight(1f)) { onNavigate(DashboardDestination.WATCH_TUTORIAL) }
        }
        EntryCard("GabAI setup", Icons.Outlined.AutoAwesome, Modifier.fillMaxWidth(), subtitle = "Guided calibration assistant") {
            onNavigate(DashboardDestination.GABAI)
        }
    }
}

/** Honest service status: tracking isn't connected in this build. */
@Composable
private fun ServiceStatusBanner() {
    val colors = PwdeTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PwdeShapes.button)
            .background(colors.warning.copy(alpha = 0.10f))
            .border(1.5.dp, colors.warning, PwdeShapes.button)
            .padding(PwdeTheme.spacing.internal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(Icons.Outlined.PowerSettingsNew, tint = colors.warning)
        Column {
            Text("PWDe controls are off", style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(
                "Head, face and voice tracking connect in the next update (Prompt 2).",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun EntryCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    GradientCard(modifier.heightIn(min = 96.dp), onClick = onClick) {
        IconBadge(icon, size = 40.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text, modifier = Modifier.padding(top = 8.dp))
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PwdeTheme.colors.textMuted)
    }
}

fun InputMode.icon(): ImageVector = when (this) {
    InputMode.HEAD_FACE -> Icons.Outlined.Face
    InputMode.JOYSTICK -> Icons.Outlined.Gamepad
    InputMode.VOICE -> Icons.Outlined.Mic
}

