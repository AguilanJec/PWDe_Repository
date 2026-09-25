package com.pwde.app.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pwde.app.BuildConfig
import com.pwde.app.R
import com.pwde.app.accessibility.PwdeAccessibilityService
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
import com.pwde.app.ui.components.PwdeToggleButton
import com.pwde.app.ui.components.StatusPill
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
)

class DashboardViewModel(
    settingsRepository: SettingsRepository,
    authRepository: AuthRepository,
    private val faceTracking: FaceTrackingManager,
    voiceCommandManager: VoiceCommandManager,
) : ViewModel() {
    // Re-checked whenever the screen comes back, e.g. after granting camera access elsewhere.
    private val cameraAllowed = MutableStateFlow(faceTracking.hasCameraPermission)

    val state: StateFlow<DashboardUiState> = combine(
        settingsRepository.settings,
        authRepository.authState,
        cameraAllowed,
        voiceCommandManager.state,
    ) { settings, auth, camera, voice ->
        DashboardUiState(
            greetingName = (auth as? AuthState.SignedIn)?.let { it.displayName ?: it.email?.substringBefore('@') },
            inputMode = settings.inputMode,
            cameraAllowed = camera,
            voice = voice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun refresh() {
        cameraAllowed.value = faceTracking.hasCameraPermission
    }
}

enum class DashboardDestination { CONTROLS, VOICE, TESTING_STATION, WATCH_TUTORIAL, GABAI, START_PLAYING }

private const val USE_PWDE = "use_pwde"

private val DASHBOARD_COMMANDS = listOf(
    voiceCommand(USE_PWDE, "use pwde", "turn on pwde"),
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
    val context = LocalContext.current
    // "Use PWDe" can only be flipped in Android Settings, so re-read it whenever we come back.
    var pwdeOn by remember { mutableStateOf(PwdeAccessibilityService.isEnabled(context)) }
    val openPwdeSettings = { context.startActivity(PwdeAccessibilityService.settingsIntent()) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        pwdeOn = PwdeAccessibilityService.isEnabled(context)
        onPauseOrDispose { }
    }
    VoiceCommandsEffect(DASHBOARD_COMMANDS) { id ->
        when {
            id.startsWith("tab:") -> onTab(MainTab.valueOf(id.removePrefix("tab:")))
            id == USE_PWDE -> openPwdeSettings()
            else -> onNavigate(DashboardDestination.valueOf(id))
        }
    }
    PwdeScreen(
        title = null,
        voiceHint = "Say \"start playing\" or a card's name",
        bottomBar = { PwdeBottomNav(MainTab.PLAY, onTab) },
    ) {
        Image(
            painterResource(R.drawable.logo_wordmark),
            contentDescription = "PWDe",
            modifier = Modifier.width(140.dp).padding(top = 8.dp, bottom = 4.dp),
        )
        state.greetingName?.let {
            Text("Welcome back, $it", style = MaterialTheme.typography.titleLarge, color = colors.text)
        }

        PwdeToggleButton(
            text = "Use PWDe",
            checked = pwdeOn,
            onClick = openPwdeSettings,
            icon = Icons.Outlined.PowerSettingsNew,
            details = statusLines(state, pwdeOn),
        )

        PlayYourWayPanel(onStart = { onNavigate(DashboardDestination.START_PLAYING) })

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
        EntryCard("GabAI setup", Icons.Outlined.AutoAwesome, Modifier.fillMaxWidth(), subtitle = "Guided calibration assistant") {
            onNavigate(DashboardDestination.GABAI)
        }
    }
}

/**
 * "Play your way" hero panel over the castle artwork. A scrim in the theme's background colour keeps
 * the text readable in every colour scheme (the art itself is light and busy).
 */
@Composable
private fun PlayYourWayPanel(onStart: () -> Unit) {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentPadding = 0.dp, borderAlpha = 0.8f) {
        Box(Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.play_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.background.copy(alpha = 0.55f), colors.background.copy(alpha = 0.85f)),
                        ),
                    ),
            )
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Play your way", style = MaterialTheme.typography.headlineSmall, color = colors.text)
                Text(
                    "Pick a game and PWDe sets up the controls with you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    modifier = Modifier.padding(top = 8.dp),
                )
                PwdeButton(
                    "Start playing",
                    onStart,
                    icon = Icons.Outlined.SportsEsports,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
        }
    }
}

/**
 * Honest status of PWDe and both input pipelines, shown inside the "Use PWDe" button.
 * Tracking itself only runs on screens that use it.
 */
private fun statusLines(state: DashboardUiState, pwdeOn: Boolean): List<String> {
    val voice = state.voice
    val (voiceText, voiceOk) = when {
        voice.usesTextFallback -> "Voice: typed commands (${voice.availability.label.lowercase()})" to false
        !voice.enabled -> "Voice: off" to false
        voice.listening -> "Voice: listening" to true
        else -> "Voice: on" to true
    }
    val summary = when {
        !pwdeOn -> "PWDe is off · tap to turn it on in Settings"
        voiceOk && state.cameraAllowed -> "PWDe controls are ready"
        else -> "PWDe controls are partly on"
    }
    return listOf(summary, voiceText)
}

@Composable
private fun EntryCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    GradientCard(modifier.heightIn(min = 112.dp), onClick = onClick, contentPadding = 20.dp) {
        IconBadge(icon, size = 40.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text, modifier = Modifier.padding(top = 12.dp))
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PwdeTheme.colors.textMuted)
    }
}

fun InputMode.icon(): ImageVector = when (this) {
    InputMode.HEAD_FACE -> Icons.Outlined.Face
    InputMode.JOYSTICK -> Icons.Outlined.Gamepad
    InputMode.VOICE -> Icons.Outlined.Mic
}