package com.pwde.app.play

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pwde.app.data.model.Game

/**
 * Opens the real game app, or its Play Store page when it isn't installed.
 *
 * Detection needs the game's package listed in this app's `<queries>` (AndroidManifest.xml): on
 * Android 11+ package visibility filtering makes `getLaunchIntentForPackage()` return null for an
 * undeclared package, so an *installed* game looks missing. `GameQueriesManifestTest` fails if the
 * manifest and [Game.packageName] drift apart.
 */
object GameLauncher {
    fun isInstalled(context: Context, game: Game): Boolean = launchIntent(context, game) != null

    /** False if the game isn't installed (or isn't visible — see the class KDoc). */
    fun openGame(context: Context, game: Game): Boolean {
        val intent = launchIntent(context, game) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openStore(context: Context, game: Game) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${game.packageName}"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${game.packageName}"))
        try {
            context.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun launchIntent(context: Context, game: Game): Intent? =
        context.packageManager.getLaunchIntentForPackage(game.packageName)
}
