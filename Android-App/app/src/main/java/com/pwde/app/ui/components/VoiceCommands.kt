package com.pwde.app.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pwde.app.sensors.voice.VoiceCommand
import com.pwde.app.ui.voice.LocalVoiceController

/** A screen-level voice command: saying any of [phrases] runs the screen's handler with [id]. */
fun voiceCommand(id: String, vararg phrases: String) = VoiceCommand(id, phrases.toList())

/**
 * Registers [commands] for as long as this screen is shown. [onCommand] receives the matched
 * command's id. Goes through [LocalVoiceController], never SpeechRecognizer directly.
 */
@Composable
fun VoiceCommandsEffect(commands: List<VoiceCommand>, onCommand: (String) -> Unit) {
    val controller = LocalVoiceController.current ?: return
    val latest by rememberUpdatedState(onCommand)
    val owner = remember { Any() }
    DisposableEffect(controller, commands) {
        controller.register(owner, commands) { latest(it.id) }
        onDispose { controller.unregister(owner) }
    }
}

/** Returns a launcher for one runtime permission. A denial never blocks: callers fall back. */
@Composable
fun rememberPermissionRequest(permission: String, onResult: (granted: Boolean) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest(it) }
    return remember(launcher) { { launcher.launch(permission) } }
}

@Composable
fun rememberCameraPermissionRequest(onResult: (granted: Boolean) -> Unit) =
    rememberPermissionRequest(Manifest.permission.CAMERA, onResult)

@Composable
fun rememberMicPermissionRequest(onResult: (granted: Boolean) -> Unit) =
    rememberPermissionRequest(Manifest.permission.RECORD_AUDIO, onResult)
