package com.pwde.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.sensors.voice.VoiceState
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import com.pwde.app.ui.voice.LocalVoiceController
import com.pwde.app.sensors.voice.MicAvailability

/**
 * Docked voice bar (Figma "P3 / Voice Bar"). Shows what you can say, whether PWDe is listening
 * (pulsing dot + live mic ring), what it last heard, and offers typed commands when the mic
 * can't be used.
 */
@Composable
fun VoiceHintBar(hint: String, modifier: Modifier = Modifier) {
    val controller = LocalVoiceController.current
    if (controller == null) {
        VoiceBarLayout(hint, "Voice unavailable here", listening = false, level = 0f, micIcon = Icons.Outlined.MicOff, modifier = modifier)
        return
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val notice by controller.notice.collectAsStateWithLifecycle()
    var typing by rememberSaveable { mutableStateOf(false) }
    val requestMic = rememberMicPermissionRequest { granted ->
        controller.onMicPermissionResult()
        if (granted) controller.setVoiceEnabled(true)
    }
    val micAction: () -> Unit = {
        if (!controller.hasMicPermission) requestMic() else controller.setVoiceEnabled(!state.enabled)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VoiceBarLayout(
            hint = heardLine(state) ?: hint,
            status = notice ?: statusLine(state),
            listening = state.listening,
            level = state.level,
            micIcon = if (state.enabled && !state.usesTextFallback) Icons.Outlined.Mic else Icons.Outlined.MicOff,
            micDescription = when {
                !controller.hasMicPermission -> "Allow microphone for voice commands"
                state.enabled -> "Turn voice commands off"
                else -> "Turn voice commands on"
            },
            onMic = micAction,
            onKeyboard = { typing = !typing },
            keyboardHighlighted = state.usesTextFallback,
        )
        if (typing) TypedCommandField(onSend = controller::submitText, onClose = { typing = false })
    }
}

private fun heardLine(state: VoiceState): String? {
    val heard = state.lastTranscript ?: return null
    val matched = state.lastCommand?.label
    return if (matched != null) "Heard \"$heard\" · last command: $matched" else "Heard \"$heard\""
}

private fun statusLine(state: VoiceState): String = when {
    state.usesTextFallback && state.availability == MicAvailability.NO_PERMISSION -> "Type a command instead"
    state.usesTextFallback -> "${state.availability.label} · type a command instead"
    !state.enabled -> "Voice off · tap the mic to turn it on"
    state.listening -> "Listening…"
    else -> "Voice on · getting ready…"
}

@Composable
private fun VoiceBarLayout(
    hint: String,
    status: String,
    listening: Boolean,
    level: Float,
    micIcon: ImageVector,
    modifier: Modifier = Modifier,
    micDescription: String = "Voice commands",
    onMic: (() -> Unit)? = null,
    onKeyboard: (() -> Unit)? = null,
    keyboardHighlighted: Boolean = false,
) {
    val colors = PwdeTheme.colors
    val pulse = rememberInfiniteTransition(label = "listening")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot",
    )
    val ring by animateFloatAsState(1f + level * 0.35f, label = "level")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(PwdeShapes.pill)
            .background(colors.surfaceMuted)
            .border(if (listening) 2.dp else 1.dp, if (listening) colors.primary else colors.secondary.copy(alpha = 0.6f), PwdeShapes.pill)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (listening) colors.primary.copy(alpha = pulseAlpha) else colors.textMuted),
        )
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 2)
            Text(status, style = MaterialTheme.typography.labelSmall, color = if (listening) colors.primary else colors.textMuted, maxLines = 2)
        }
        if (onKeyboard != null) {
            RoundIconButton(
                icon = Icons.Outlined.Keyboard,
                description = "Type a command",
                background = if (keyboardHighlighted) colors.primary.copy(alpha = 0.35f) else Color.Transparent,
                onClick = onKeyboard,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            if (listening) {
                Box(Modifier.size(44.dp).scale(ring).clip(CircleShape).background(colors.primary.copy(alpha = 0.25f)))
            }
            RoundIconButton(
                icon = micIcon,
                description = micDescription,
                background = if (listening) colors.primary.copy(alpha = 0.45f) else colors.secondary.copy(alpha = 0.35f),
                onClick = onMic,
            )
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, background: Color, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = PwdeTheme.colors.text)
    }
}

/** Compact listening indicator for full-screen views (gameplay) that don't show the voice bar. */
@Composable
fun ListeningIndicator(state: VoiceState, modifier: Modifier = Modifier) {
    val colors = PwdeTheme.colors
    val (text, color) = when {
        state.usesTextFallback -> "Voice: unavailable" to colors.warning
        !state.enabled -> "Voice: off" to colors.textMuted
        state.listening -> "Listening…" to colors.primary
        else -> "Voice: starting" to colors.textMuted
    }
    StatusPill(
        text,
        modifier = modifier
            .background(colors.background.copy(alpha = 0.8f), PwdeShapes.pill)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = color,
        icon = if (state.listening) Icons.Outlined.Mic else Icons.Outlined.MicOff,
    )
}

/** Typed fallback: same commands, same matching, no microphone needed. */
@Composable
private fun TypedCommandField(onSend: (String) -> Unit, onClose: () -> Unit) {
    val colors = PwdeTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = PwdeShapes.field,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("Type a command, e.g. \"back\"", color = colors.textMuted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceMuted,
                unfocusedContainerColor = colors.surfaceMuted,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.secondary.copy(alpha = 0.6f),
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                cursorColor = colors.primary,
            ),
        )
        RoundIconButton(Icons.AutoMirrored.Outlined.Send, "Send command", colors.primary.copy(alpha = 0.35f)) { send() }
        RoundIconButton(Icons.Outlined.Close, "Close typing", Color.Transparent, onClose)
    }
}
