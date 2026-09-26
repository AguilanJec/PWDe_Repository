package com.pwde.app.play

import android.content.Context
import com.pwde.app.accessibility.PwdeAccessibilityService
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.model.Game

/**
 * Starts playing the real [Game]: picks the profile (the one given, else the last played, else the
 * newest), starts [PlayService] and opens the game. Returns what to tell the user.
 */
suspend fun startPlaying(context: Context, profileRepository: ProfileRepository, game: Game, profileId: Long?): String {
    if (!GameLauncher.isInstalled(context, game)) {
        GameLauncher.openStore(context, game)
        return "${game.displayName} isn't installed — opening the Play Store"
    }
    if (!PlayService.canRun(context)) {
        return "PWDe needs the camera or microphone to control ${game.displayName}"
    }
    val profile = profileId?.let { profileRepository.getGameProfile(it) } ?: profileRepository.lastPlayedGameProfile(game.id)
    profile?.let { profileRepository.markGameProfilePlayed(it.id) }
    PlayService.start(context, game, profile?.id)
    GameLauncher.openGame(context, game)
    return when {
        !PwdeAccessibilityService.isEnabled(context) ->
            "Turn on \"Use PWDe\" in Accessibility settings so PWDe can press buttons in ${game.displayName}"
        profile != null -> "Playing ${game.displayName} with \"${profile.profileName}\""
        else -> "Playing ${game.displayName} — no game profile yet, so only the pointer works"
    }
}
