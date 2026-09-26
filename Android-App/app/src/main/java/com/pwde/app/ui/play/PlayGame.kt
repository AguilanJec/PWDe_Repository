package com.pwde.app.ui.play

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.pwde.app.PwdeApplication
import com.pwde.app.data.model.Game
import com.pwde.app.play.PlayService
import com.pwde.app.play.startPlaying
import kotlinx.coroutines.launch

/**
 * Returns "play [Game] for real": asks for the camera, mic and notification permissions it still
 * needs (a denial never blocks — whatever was granted is used), then starts the live session and
 * opens the game. Pass a profile id, or null for the game's last-played profile.
 */
@Composable
fun rememberPlayGame(): (Game, Long?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<Pair<Game, Long?>>(1) }
    fun go(game: Game, profileId: Long?) {
        scope.launch {
            val repository = (context.applicationContext as PwdeApplication).container.profileRepository
            val message = startPlaying(context, repository, game, profileId)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending[0]?.let { (game, profileId) -> go(game, profileId) }
        pending[0] = null
    }
    return remember(permissions) {
        { game, profileId ->
            val missing = PlayService.permissionsToAsk(context)
            if (missing.isEmpty()) {
                go(game, profileId)
            } else {
                pending[0] = game to profileId
                permissions.launch(missing)
            }
        }
    }
}
