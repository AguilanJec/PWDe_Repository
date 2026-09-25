package com.pwde.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.local.CalibrationProfile
import com.pwde.app.data.local.GameProfile
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.remote.AuthRepository
import com.pwde.app.data.remote.AuthState
import com.pwde.app.data.remote.SyncRepository
import com.pwde.app.data.remote.SyncStatus
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.IconBadge
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.MainTab
import com.pwde.app.ui.components.NavCard
import com.pwde.app.ui.components.PwdeBottomNav
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.PwdeTextField
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.theme.PwdeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val auth: AuthState = AuthState.Guest,
    val cloudAvailable: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.LocalOnly,
    val calibrationProfiles: List<CalibrationProfile> = emptyList(),
    val gameProfiles: List<GameProfile> = emptyList(),
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    syncRepository: SyncRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    val state: StateFlow<ProfileUiState> = combine(
        authRepository.authState,
        syncRepository.status,
        profileRepository.calibrationProfiles,
        profileRepository.gameProfiles,
    ) { auth, sync, calibrations, games ->
        ProfileUiState(auth, authRepository.isCloudAvailable, sync, calibrations, games)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProfileUiState(cloudAvailable = authRepository.isCloudAvailable),
    )

    /** Signing out never deletes local profiles. */
    fun signOut() = authRepository.signOut()

    fun rename(profile: SavedProfile, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (profile) {
                is SavedProfile.Calibration -> profileRepository.saveCalibrationProfile(profile.profile.copy(name = trimmed))
                is SavedProfile.Game -> profileRepository.saveGameProfile(profile.profile.copy(profileName = trimmed))
            }
        }
    }

    fun delete(profile: SavedProfile) {
        viewModelScope.launch {
            when (profile) {
                is SavedProfile.Calibration -> profileRepository.deleteCalibrationProfile(profile.profile)
                is SavedProfile.Game -> profileRepository.deleteGameProfile(profile.profile)
            }
        }
    }
}

/** A saved profile of either kind, for the shared rename/delete UI. */
sealed interface SavedProfile {
    val name: String
    val detail: String

    data class Calibration(val profile: CalibrationProfile) : SavedProfile {
        override val name get() = profile.name
        override val detail get() = profile.inputMode.lowercase().replace('_', ' ')
    }

    data class Game(val profile: GameProfile) : SavedProfile {
        override val name get() = profile.profileName
        override val detail get() = profile.gameName
    }
}

private sealed interface ProfileDialog {
    data class Rename(val profile: SavedProfile) : ProfileDialog
    data class Delete(val profile: SavedProfile) : ProfileDialog
}

private val PROFILE_COMMANDS = listOf(
    voiceCommand("sign_in", "sign in", "sync"),
    voiceCommand("appearance", "appearance"),
    voiceCommand("controls", "controls"),
) + MainTab.entries.filter { it != MainTab.PROFILE }.map { voiceCommand("tab:${it.name}", it.label) }

/** H1 Profile. Local profiles from Room, sign-in entry for guests, sync status for signed-in users. */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onSignIn: () -> Unit,
    onEditAppearance: () -> Unit,
    onControls: () -> Unit,
    onTab: (MainTab) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PwdeTheme.colors
    val signedIn = state.auth as? AuthState.SignedIn
    val name = signedIn?.let { it.displayName ?: it.email } ?: "Guest"
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    VoiceCommandsEffect(PROFILE_COMMANDS) { id ->
        when {
            id.startsWith("tab:") -> onTab(MainTab.valueOf(id.removePrefix("tab:")))
            id == "sign_in" -> if (signedIn == null) onSignIn()
            id == "appearance" -> onEditAppearance()
            id == "controls" -> onControls()
        }
    }
    when (val d = dialog) {
        is ProfileDialog.Rename -> RenameDialog(d.profile, onDismiss = { dialog = null }) { newName ->
            viewModel.rename(d.profile, newName)
            dialog = null
        }
        is ProfileDialog.Delete -> DeleteDialog(d.profile, onDismiss = { dialog = null }) {
            viewModel.delete(d.profile)
            dialog = null
        }
        null -> Unit
    }

    PwdeScreen(
        title = "Profile",
        voiceHint = "Say \"sign in\" or a profile's name",
        bottomBar = { PwdeBottomNav(MainTab.PROFILE, onTab) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(colors.cardBrush).border(2.dp, colors.borderBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = colors.text)
            }
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, color = colors.text)
                Text(
                    if (signedIn != null) "Signed in" else "Guest — everything is saved on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }

        SyncCard(state, onSignIn, viewModel::signOut)

        SectionTitle("Calibration profiles")
        if (state.calibrationProfiles.isEmpty()) {
            InfoNote("No calibration profiles yet. GabAI will help you make one (coming in Prompt 3).")
        } else {
            state.calibrationProfiles.forEach {
                ProfileRow(SavedProfile.Calibration(it), Icons.Outlined.Tune, { p -> dialog = ProfileDialog.Rename(p) }, { p -> dialog = ProfileDialog.Delete(p) })
            }
        }

        SectionTitle("Game profiles")
        if (state.gameProfiles.isEmpty()) {
            InfoNote("No game profiles yet. They're created with GabAI for each game (coming in Prompt 3).")
        } else {
            state.gameProfiles.forEach {
                ProfileRow(SavedProfile.Game(it), Icons.Outlined.SportsEsports, { p -> dialog = ProfileDialog.Rename(p) }, { p -> dialog = ProfileDialog.Delete(p) })
            }
        }

        SectionTitle("Settings")
        NavCard("Appearance", "Colors, text size, layout", Icons.Outlined.Palette, onEditAppearance)
        NavCard("Controls", "Input, gestures, voice", Icons.Outlined.Tune, onControls)
    }
}

/** One saved profile with rename and delete. */
@Composable
private fun ProfileRow(profile: SavedProfile, icon: ImageVector, onRename: (SavedProfile) -> Unit, onDelete: (SavedProfile) -> Unit) {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon)
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(profile.detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PwdeButton("Rename", { onRename(profile) }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Edit, modifier = Modifier.weight(1f))
            PwdeButton("Delete", { onDelete(profile) }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Delete, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RenameDialog(profile: SavedProfile, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PwdeTheme.colors.surface,
        title = { Text("Rename profile", color = PwdeTheme.colors.text) },
        text = { PwdeTextField(label = "Name", value = name, onValueChange = { name = it }) },
        confirmButton = { PwdeButton("Save", { onConfirm(name) }, enabled = name.isNotBlank()) },
        dismissButton = { PwdeButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY) },
    )
}

@Composable
private fun DeleteDialog(profile: SavedProfile, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PwdeTheme.colors.surface,
        title = { Text("Delete \"${profile.name}\"?", color = PwdeTheme.colors.text) },
        text = { Text("This removes it from this phone. It can't be undone.", color = PwdeTheme.colors.textMuted) },
        confirmButton = { PwdeButton("Delete", onConfirm, style = ButtonStyle.DESTRUCTIVE) },
        dismissButton = { PwdeButton("Keep it", onDismiss, style = ButtonStyle.SECONDARY) },
    )
}

@Composable
private fun SyncCard(state: ProfileUiState, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth()) {
        when (state.syncStatus) {
            SyncStatus.LocalOnly -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Outlined.PhoneAndroid)
                    Column(Modifier.weight(1f)) {
                        Text("Saved on this phone", style = MaterialTheme.typography.titleMedium, color = colors.text)
                        Text(
                            "Sign in to sync your profiles across devices. Nothing here is lost when you do.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
                PwdeButton(
                    "Sign in to sync across devices",
                    onSignIn,
                    style = ButtonStyle.SECONDARY,
                    icon = Icons.AutoMirrored.Outlined.Login,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!state.cloudAvailable) {
                    StatusPill("Accounts aren't set up in this build", color = colors.textMuted, icon = Icons.Outlined.CloudOff)
                }
            }
            SyncStatus.NotAvailable -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Outlined.CloudSync)
                    Column(Modifier.weight(1f)) {
                        Text("Sync status", style = MaterialTheme.typography.titleMedium, color = colors.text)
                        StatusPill("Cloud sync not available yet", color = colors.warning, icon = Icons.Outlined.CloudOff)
                    }
                }
                Text(
                    "You're signed in. Your profiles are safe on this phone; syncing them to the cloud is coming in a later update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                PwdeButton("Sign out", onSignOut, style = ButtonStyle.SECONDARY, icon = Icons.AutoMirrored.Outlined.Logout, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
