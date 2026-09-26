package com.pwde.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.sensors.voice.VoiceState
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme
import com.pwde.app.ui.theme.scaled
import com.pwde.app.ui.voice.LocalVoiceController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/**
 * Floating mic overlay, shown by every [PwdeScreen]. Tapping the mic turns voice on or off (or asks
 * for the microphone first). Nothing is shown while idle: what PWDe heard, and the command it
 * matched, pops up beside the mic for a few seconds and is announced by screen readers.
 *
 * [hint] is what can be said on this screen; it is read out with the mic button rather than shown.
 */
@Composable
fun VoiceMicOverlay(
    hint: String?,
    modifier: Modifier = Modifier,
    /** Show what was heard to the mic's right, for a mic pinned to a left corner (it then never moves). */
    popupAtEnd: Boolean = false,
) {
    val controller = LocalVoiceController.current
    if (controller == null) {
        MicFab(listening = false, level = 0f, icon = Icons.Outlined.MicOff, description = "Voice unavailable here", modifier = modifier)
        return
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val notice by controller.notice.collectAsStateWithLifecycle()
    val requestMic = rememberMicPermissionRequest { granted ->
        controller.onMicPermissionResult()
        if (granted) controller.setVoiceEnabled(true)
    }
    val micAction: () -> Unit = {
        if (!controller.hasMicPermission) requestMic() else controller.setVoiceEnabled(!state.enabled)
    }

    // Pops up only on new speech: whatever was heard before this screen appeared is skipped.
    var heard by remember { mutableStateOf("") }
    var popupVisible by remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        controller.state.map(::heardLine).distinctUntilChanged().drop(1).collectLatest { line ->
            if (line == null) {
                popupVisible = false
                return@collectLatest
            }
            heard = line
            popupVisible = true
            delay(POPUP_MILLIS)
            popupVisible = false
        }
    }

    val mic: @Composable () -> Unit = {
        MicFab(
            listening = state.listening,
            level = state.level,
            icon = if (state.enabled && !state.usesTextFallback) Icons.Outlined.Mic else Icons.Outlined.MicOff,
            description = listOfNotNull(
                when {
                    !controller.hasMicPermission -> "Allow microphone for voice commands"
                    state.usesTextFallback -> "Voice unavailable: ${state.availability.label}"
                    state.enabled -> "Turn voice commands off"
                    else -> "Turn voice commands on"
                },
                hint,
            ).joinToString(". "),
            status = statusLine(state),
            onClick = micAction,
        )
    }
    val popup: @Composable RowScope.() -> Unit = {
        AnimatedVisibility(
            visible = popupVisible,
            modifier = Modifier.weight(1f, fill = false),
            enter = fadeIn() + slideInHorizontally { if (popupAtEnd) -it / 4 else it / 4 },
            exit = fadeOut(),
        ) {
            RecognitionPopup(listOfNotNull(heard, notice).joinToString(" · "))
        }
    }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (popupAtEnd) {
            mic()
            popup()
        } else {
            popup()
            mic()
        }
    }
}

private fun heardLine(state: VoiceState): String? {
    val heard = state.lastTranscript ?: return null
    val matched = state.lastCommand?.label
    return if (matched != null) "Heard \"$heard\" · last command: $matched" else "Heard \"$heard\""
}

/** Read out as the mic button's state; never shown as text. */
private fun statusLine(state: VoiceState): String = when {
    state.usesTextFallback -> state.availability.label
    !state.enabled -> "Voice off"
    state.listening -> "Listening"
    else -> "Voice on, getting ready"
}

/** How long a recognition pop-up stays up. */
private const val POPUP_MILLIS = 3_500L

/** Room scrolling content leaves at its end so the floating mic never covers the last item. */
val MicOverlayClearance: Dp = 72.dp

@Composable
private fun RecognitionPopup(text: String) {
    val colors = PwdeTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.text,
        maxLines = 3,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(PwdeShapes.button)
            .background(colors.surfaceMuted)
            .border(1.dp, colors.primary, PwdeShapes.button)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

/** The mic button with its listening ring, on an opaque base so it reads over any content. */
@Composable
private fun MicFab(
    listening: Boolean,
    level: Float,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = PwdeTheme.colors
    val ring by animateFloatAsState(1f + level * 0.35f, label = "level")
    Box(modifier, contentAlignment = Alignment.Center) {
        if (listening) {
            Box(Modifier.size(MicButtonSize).scale(ring).clip(CircleShape).background(colors.primary.copy(alpha = 0.25f)))
        }
        Box(
            Modifier
                .size(MicButtonSize)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(if (listening) 2.dp else 1.dp, if (listening) colors.primary else colors.secondary.copy(alpha = 0.6f), CircleShape)
                // One screen-reader node: the button's label, its state, and state changes announced.
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    if (status != null) stateDescription = status
                },
        ) {
            RoundIconButton(
                icon = icon,
                description = description,
                background = if (listening) colors.primary.copy(alpha = 0.45f) else colors.secondary.copy(alpha = 0.35f),
                onClick = onClick,
                size = MicButtonSize,
                iconSize = 32.dp,
            )
        }
    }
}

/** The "use voice" mic button: larger than a plain 48dp target so it's easy to find and hit. */
private val MicButtonSize = 60.dp

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    background: Color,
    onClick: (() -> Unit)?,
    size: Dp = MinTouchTarget,
    iconSize: Dp = 24.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = PwdeTheme.colors.text, modifier = Modifier.size(iconSize.scaled().coerceAtMost(size * 0.7f)))
    }
}

/** Compact listening indicator for full-screen views (gameplay) that don't show the mic overlay. */
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
