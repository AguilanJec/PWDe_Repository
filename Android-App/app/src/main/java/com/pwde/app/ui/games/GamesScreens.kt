package com.pwde.app.ui.games

import com.pwde.app.data.local.GameProfile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.model.Game
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.MainTab
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.PlaceholderNotice
import com.pwde.app.ui.components.PwdeBottomNav
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Which games already have a saved game profile (from Room). */
class GamesViewModel(profileRepository: ProfileRepository) : ViewModel() {
    val gamesWithProfiles: StateFlow<Set<String>> = profileRepository.gameProfiles
        .map { profiles -> profiles.map { it.gameId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}

/** Say a game's name to open it; say a tab's name to switch tabs. */
internal fun gameCommands(current: MainTab) = Game.entries.map { voiceCommand("game:${it.id}", it.displayName) } +
        MainTab.entries.filter { it != current }.map { voiceCommand("tab:${it.name}", it.label) }

@Composable
private fun GameListVoice(current: MainTab, onGame: (Game) -> Unit, onTab: (MainTab) -> Unit) {
    val commands = remember(current) { gameCommands(current) }
    VoiceCommandsEffect(commands) { id ->
        when {
            id.startsWith("game:") -> Game.byId(id.removePrefix("game:"))?.let(onGame)
            id.startsWith("tab:") -> onTab(MainTab.valueOf(id.removePrefix("tab:")))
        }
    }
}

/** D2 Games: tiles show their setup status. */
@Composable
fun GamesScreen(viewModel: GamesViewModel, onGame: (Game) -> Unit, onTab: (MainTab) -> Unit) {
    val withProfiles by viewModel.gamesWithProfiles.collectAsStateWithLifecycle()
    GameListVoice(MainTab.GAMES, onGame, onTab)
    PwdeScreen(
        title = "Games",
        subtitle = "Pick a game to play or set up.",
        voiceHint = "Say a game's name",
        bottomBar = { PwdeBottomNav(MainTab.GAMES, onTab) },
    ) {
        Game.entries.forEach { game -> GameCard(game, hasProfile = game.id in withProfiles, onClick = { onGame(game) }) }
        InfoNote("More games and custom buttons for any game are on the way.")
    }
}

@Composable
fun GameCard(game: Game, hasProfile: Boolean, onClick: () -> Unit) {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth(), onClick = onClick) {
        GameArt(game)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(game.displayName, style = MaterialTheme.typography.titleLarge, color = colors.text)
                Text(game.genre, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            if (hasProfile) StatusPill("Profile ready", icon = Icons.Outlined.CheckCircle)
            else StatusPill("No profile yet", color = colors.textMuted)
        }
    }
}

/** Stand-in artwork (no copyrighted game images are bundled). */
@Composable
private fun GameArt(game: Game) {
    val colors = PwdeTheme.colors
    val icon: ImageVector = if (game == Game.CLASH_ROYALE) Icons.Outlined.Style else Icons.Outlined.SportsEsports
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2.4f)
            .clip(PwdeShapes.button)
            .background(Brush.linearGradient(listOf(colors.secondary.copy(alpha = 0.6f), colors.primary.copy(alpha = 0.35f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(56.dp))
    }
}

/** A game's saved profiles, newest first. */
class GameDetailViewModel(profileRepository: ProfileRepository, game: Game) : ViewModel() {
    val profiles: StateFlow<List<GameProfile>> = profileRepository.gameProfilesFor(game.id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** D3 Game detail: play with a saved game profile (or without one), edit one, or make one with GabAI. */
@Composable
fun GameDetailScreen(
    game: Game,
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
    onPlay: (profileId: Long?) -> Unit,
    onEditProfile: (profileId: Long) -> Unit,
    onSetUpWithGabAi: () -> Unit,
) {
    val colors = PwdeTheme.colors
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val newest = profiles.firstOrNull()
    VoiceCommandsEffect(GAME_DETAIL_COMMANDS) { id -> if (id == "play") onPlay(newest?.id) else onSetUpWithGabAi() }
    PwdeScreen(
        title = game.displayName,
        subtitle = game.genre,
        onBack = onBack,
        voiceHint = "Say \"play\" or \"set up with GabAI\"",
        footer = {
            PwdeButton(
                if (newest != null) "Play with \"${newest.profileName}\"" else "Play ${game.displayName} with PWDe",
                { onPlay(newest?.id) },
                icon = Icons.Outlined.SportsEsports,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        GameArt(game)
        Text(game.description, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        if (profiles.isEmpty()) {
            InfoNote("No game profile yet. GabAI will walk you through mapping this game's buttons to your head, face and voice.")
        } else {
            SectionTitle("Your profiles for this game")
            profiles.forEach { profile ->
                GradientCard(Modifier.fillMaxWidth()) {
                    Text(profile.profileName, style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        PwdeButton("Play", { onPlay(profile.id) }, icon = Icons.Outlined.SportsEsports, modifier = Modifier.weight(1f))
                        PwdeButton("Edit", { onEditProfile(profile.id) }, style = ButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        PwdeButton(
            if (profiles.isEmpty()) "Set up with GabAI" else "New profile with GabAI",
            onSetUpWithGabAi,
            style = ButtonStyle.SECONDARY,
            icon = Icons.Outlined.AutoAwesome,
            modifier = Modifier.fillMaxWidth(),
        )
        InfoNote(
            "Play opens PWDe's live overlay over your game screenshot (or a simulated arena) — your head, face and " +
                    "voice really drive it, but the real game isn't launched.",
            icon = Icons.Outlined.Info,
        )
    }
}

private enum class GameFilter(val label: String, val matches: (Game, Set<String>) -> Boolean) {
    ALL("All games", { _, _ -> true }),
    STRATEGY("Strategy", { g, _ -> g.genre == "Strategy" }),
    MOBA("MOBA", { g, _ -> g.genre == "MOBA" }),
    READY("Profile ready", { g, ids -> g.id in ids }),
}

internal val GAME_DETAIL_COMMANDS = listOf(
    voiceCommand("play", "play", "launch game", "launch", "start"),
    voiceCommand("gabai", "set up with gabai", "new profile", "gabai", "gab ai"),
)

/** Filter: narrow the game list by genre or setup status. */
@Composable
fun FilterScreen(viewModel: GamesViewModel, onGame: (Game) -> Unit, onTab: (MainTab) -> Unit) {
    val withProfiles by viewModel.gamesWithProfiles.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(GameFilter.ALL) }
    val results = Game.entries.filter { filter.matches(it, withProfiles) }
    GameListVoice(MainTab.FILTER, onGame, onTab)
    val filterCommands = remember { GameFilter.entries.map { voiceCommand(it.name, it.label) } }
    VoiceCommandsEffect(filterCommands) { id -> filter = GameFilter.valueOf(id) }
    PwdeScreen(
        title = "Filter",
        subtitle = "Find a game by type or setup status.",
        voiceHint = "Say a filter's name",
        bottomBar = { PwdeBottomNav(MainTab.FILTER, onTab) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            GameFilter.entries.forEach { option ->
                OptionCard(
                    option.label, null, option == filter, { filter = option },
                    kind = OptionKind.RADIO,
                )
            }
        }
        SectionTitle("${results.size} ${if (results.size == 1) "game" else "games"}")
        if (results.isEmpty()) InfoNote("No games match. Game profiles are created with GabAI from the Play tab.")
        results.forEach { game -> GameCard(game, game.id in withProfiles) { onGame(game) } }
    }
}