package com.pwde.app.ui.dashboard

import com.pwde.app.data.gabai.GabAiRepository
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
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pwde.app.BuildConfig
import com.pwde.app.R
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.remote.AuthRepository
import com.pwde.app.data.remote.AuthState
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.voice.VoiceCommandManager
import com.pwde.app.sensors.voice.VoiceState
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.navigation.TESTING_STATION_AVAILABLE
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.IconBadge
import com.pwde.app.ui.components.MainTab
import com.pwde.app.ui.components.PwdeBottomNav
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val greetingName: String? = null,
    val inputMode: InputMode = InputMode.HEAD_FACE,
    val cameraAllowed: Boolean = false,
    val voice: VoiceState = VoiceState(),
    /** Where an unfinished GabAI setup stopped, if there is one. */
    val gabAiUnfinished: String? = null,
)

class DashboardViewModel(
    settingsRepository: SettingsRepository,
    authRepository: AuthRepository,
    private val faceTracking: FaceTrackingManager,
    voiceCommandManager: VoiceCommandManager,
    gabAiRepository: GabAiRepository,
) : ViewModel() {
    // Re-checked whenever the screen comes back, e.g. after granting camera access elsewhere.
    private val cameraAllowed = MutableStateFlow(faceTracking.hasCameraPermission)

    val state: StateFlow<DashboardUiState> = combine(
        settingsRepository.settings,
        authRepository.authState,
        cameraAllowed,
        voiceCommandManager.state,
        gabAiRepository.unfinished,
    ) { settings, auth, camera, voice, gabAi ->
        DashboardUiState(
            greetingName = (auth as? AuthState.SignedIn)?.let { it.displayName ?: it.email?.substringBefore('@') },
            inputMode = settings.inputMode,
            cameraAllowed = camera,
            voice = voice,
            gabAiUnfinished = gabAi?.state?.summary,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun refresh() {
        cameraAllowed.value = faceTracking.hasCameraPermission
    }
}

enum class DashboardDestination { CONTROLS, VOICE, TESTING_STATION, WATCH_TUTORIAL, GABAI, START_PLAYING }

private val DASHBOARD_COMMANDS = listOf(
    voiceCommand(DashboardDestination.START_PLAYING.name, "start playing", "play", "start"),
    voiceCommand(DashboardDestination.CONTROLS.name, "controls"),
    voiceCommand(DashboardDestination.VOICE.name, "voice"),
    voiceCommand(DashboardDestination.WATCH_TUTORIAL.name, "watch tutorial", "tutorial"),
    voiceCommand(DashboardDestination.GABAI.name, "gabai", "gab ai", "gabby", "setup assistant"),
) + MainTab.entries.filter { it != MainTab.PLAY }.map { voiceCommand("tab:${it.name}", it.label) } +
    if (TESTING_STATION_AVAILABLE) listOf(voiceCommand(DashboardDestination.TESTING_STATION.name, "testing station", "testing")) else emptyList()

/** The Testing Station is a debug tool: its card only exists in debug builds. */
private val showTestingStation = TESTING_STATION_AVAILABLE && BuildConfig.DEBUG

/** D1 Play – Dashboard. Status first, one big "Start playing", four entry cards, GabAI. */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigate: (DashboardDestination) -> Unit,
    onTab: (MainTab) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PwdeTheme.colors
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    VoiceCommandsEffect(DASHBOARD_COMMANDS) { id ->
        if (id.startsWith("tab:")) onTab(MainTab.valueOf(id.removePrefix("tab:")))
        else onNavigate(DashboardDestination.valueOf(id))
    }
    PwdeScreen(
        title = null,
        voiceHint = "Say \"start playing\" or a card's name",
        bottomBar = { PwdeBottomNav(MainTab.PLAY, onTab) },
    ) {
        Image(painterResource(R.drawable.logo_wordmark), contentDescription = "PWDe", modifier = Modifier.width(140.dp))
        state.greetingName?.let {
            Text("Welcome back, $it", style = MaterialTheme.typography.titleLarge, color = colors.text)
        }

        ServiceStatusBanner(state)

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
            if (showTestingStation) {
                EntryCard("Testing Station", Icons.Outlined.Radar, Modifier.weight(1f), subtitle = "Debug build") {
                    onNavigate(DashboardDestination.TESTING_STATION)
                }
            }
            EntryCard("Watch Tutorial", Icons.Outlined.OndemandVideo, Modifier.weight(1f)) { onNavigate(DashboardDestination.WATCH_TUTORIAL) }
        }
        EntryCard(
            "GabAI setup",
            Icons.Outlined.AutoAwesome,
            Modifier.fillMaxWidth(),
            subtitle = state.gabAiUnfinished?.let { "Continue: $it" } ?: "Guided calibration assistant",
        ) {
            onNavigate(DashboardDestination.GABAI)
        }
    }
}

/** Honest status of both input pipelines. Tracking itself only runs on screens that use it. */
@Composable
private fun ServiceStatusBanner(state: DashboardUiState) {
    val colors = PwdeTheme.colors
    val voice = state.voice
    val (voiceText, voiceOk) = when {
        voice.usesTextFallback -> "Voice: typed commands (${voice.availability.label.lowercase()})" to false
        !voice.enabled -> "Voice: off" to false
        voice.listening -> "Voice: listening" to true
        else -> "Voice: on" to true
    }
    val headText = if (state.cameraAllowed) "Head & face: camera ready" else "Head & face: demo mode (camera off)"
    val allGood = voiceOk && state.cameraAllowed
    val tint = if (allGood) colors.primary else colors.warning
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PwdeShapes.button)
            .background(tint.copy(alpha = 0.10f))
            .border(1.5.dp, tint, PwdeShapes.button)
            .padding(PwdeTheme.spacing.internal)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(Icons.Outlined.PowerSettingsNew, tint = tint)
        Column {
            Text(
                if (allGood) "PWDe controls are ready" else "PWDe controls are partly on",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            Text(headText, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            Text(voiceText, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
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

