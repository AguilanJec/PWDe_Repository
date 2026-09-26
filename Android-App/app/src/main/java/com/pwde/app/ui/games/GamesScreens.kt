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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import com.pwde.app.R
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pwde.app.ui.components.IconBadge
import androidx.compose.ui.unit.Dp
/**
 * Small square game artwork for list rows, matching IconBadge's size and shape.
 * Falls back to [fallbackIcon] (shown in an IconBadge) when no artwork is bundled.
 */
@Composable
internal fun GameThumbnail(
    game: Game,
    fallbackIcon: ImageVector,
    size: Dp = 40.dp,
) {
    val res = gameArtRes(game)
    if (res == null) {
        IconBadge(fallbackIcon)
        return
    }
    val shape = RoundedCornerShape(12.dp) // keep in sync with IconBadge
    Image(
        painter = painterResource(id = res),
        contentDescription = "${game.displayName} artwork",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .border(1.dp, PwdeTheme.colors.borderBrush, shape),
    )
}
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

/** Returns the drawable resource for a game's artwork, or null if none is bundled. */
internal fun gameArtRes(game: Game): Int? = when (game) {
    Game.MOBILE_LEGENDS -> R.drawable.mobile_legends
    Game.CLASH_ROYALE -> R.drawable.clash_royale
    else -> null
}

/** Game artwork with a scrim so the title/status text stays readable. Falls back to a gradient + icon. */
@Composable
internal fun GameArt(game: Game) {
    val colors = PwdeTheme.colors
    val res = gameArtRes(game)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2.4f)
            .clip(PwdeShapes.button)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.secondary.copy(alpha = 0.6f),
                        colors.primary.copy(alpha = 0.35f),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (res != null) {
            Image(
                painter = painterResource(id = res),
                contentDescription = "${game.displayName} artwork",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            // Dark scrim so text/icons drawn on top remain legible.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                            )
                        )
                    )
            )
        } else {
            val icon: ImageVector =
                if (game == Game.CLASH_ROYALE) Icons.Outlined.Style
                else Icons.Outlined.SportsEsports
            Icon(
                icon,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
/** A game's saved profiles, newest first. */
class GameDetailViewModel(profileRepository: ProfileRepository, game: Game) : ViewModel() {
    val profiles: StateFlow<List<GameProfile>> = profileRepository.gameProfilesFor(game.id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * D3 Game detail: play the real game with a saved game profile (or without one), test a profile on
 * the simulated preview, edit one, or make one with GabAI.
 */
@Composable
fun GameDetailScreen(
    game: Game,
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
    onPlay: (profileId: Long?) -> Unit,
    onTestProfile: (profileId: Long) -> Unit,
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
                        PwdeButton("Test", { onTestProfile(profile.id) }, style = ButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
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
            "Play opens ${game.displayName} and keeps PWDe running on top of it, with a notification to pause or stop. " +
                    "Test tries a profile on its screenshot inside PWDe first.",
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

/** D2 Games: narrow the game list by genre or setup status; tiles show their setup status. */
@Composable
fun GamesScreen(viewModel: GamesViewModel, onGame: (Game) -> Unit, onTab: (MainTab) -> Unit) {
    val withProfiles by viewModel.gamesWithProfiles.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(GameFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = Game.entries.filter { game ->
        filter.matches(game, withProfiles) &&
                (query.isBlank() || listOf(game.displayName, game.genre, game.description).any {
                    it.contains(query.trim(), ignoreCase = true)
                })
    }
    GameListVoice(MainTab.GAMES, onGame, onTab)
    val filterCommands = remember { GameFilter.entries.map { voiceCommand(it.name, it.label) } }
    VoiceCommandsEffect(filterCommands) { id -> filter = GameFilter.valueOf(id) }
    PwdeScreen(
        title = "Games",
        subtitle = "Pick a game to play or set up. Filter by type or setup status.",
        voiceHint = "Say a game's or filter's name",
        bottomBar = { PwdeBottomNav(MainTab.GAMES, onTab) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search games") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                } else null,
                shape = PwdeShapes.field,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PwdeTheme.colors.text,
                    unfocusedTextColor = PwdeTheme.colors.text,
                    cursorColor = PwdeTheme.colors.primary,
                    focusedBorderColor = PwdeTheme.colors.primary,
                    unfocusedBorderColor = PwdeTheme.colors.secondary.copy(alpha = 0.6f),
                ),
            )
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.FilterList, contentDescription = null)
                    Text("Filter", modifier = Modifier.padding(start = 4.dp))
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    GameFilter.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                filter = option
                                menuExpanded = false
                            },
                            trailingIcon = if (option == filter) {
                                { Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected") }
                            } else null,
                        )
                    }
                }
            }
        }
        SectionTitle("${results.size} ${if (results.size == 1) "game" else "games"}")
        if (results.isEmpty()) InfoNote("No games match this search and filter.")
        results.forEach { game -> GameCard(game, game.id in withProfiles) { onGame(game) } }
    }
}