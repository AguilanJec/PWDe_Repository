package com.pwde.app.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.LevelStepper
import com.pwde.app.ui.components.NavCard
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.Pager
import com.pwde.app.ui.components.PlaceholderNotice
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.SegmentedToggle
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.dashboard.icon
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

enum class ControlsDestination { INPUT, GESTURES, CURSOR, JOYSTICK, VOICE, CUSTOM_BUTTONS }

/** E1 Controls hub: six compact cards. */
@Composable
fun ControlsHubScreen(onBack: () -> Unit, onOpen: (ControlsDestination) -> Unit) {
    PwdeScreen(
        title = "Controls",
        subtitle = "Everything that controls your games.",
        onBack = onBack,
        voiceHint = "Say a card's name, like \"joystick\"",
    ) {
        NavCard("Input", "Head, joystick or voice", Icons.Outlined.Face, { onOpen(ControlsDestination.INPUT) })
        NavCard("Gestures", "Which face move does what", Icons.Outlined.TouchApp, { onOpen(ControlsDestination.GESTURES) })
        NavCard("Cursor speed", "How fast the pointer moves", Icons.Outlined.Mouse, { onOpen(ControlsDestination.CURSOR) })
        NavCard("Joystick", "Size, sensitivity, dead zone", Icons.Outlined.Gamepad, { onOpen(ControlsDestination.JOYSTICK) })
        NavCard("Voice", "Commands and matching", Icons.Outlined.RecordVoiceOver, { onOpen(ControlsDestination.VOICE) })
        NavCard("Custom buttons", "Make your own buttons", Icons.Outlined.Dashboard, { onOpen(ControlsDestination.CUSTOM_BUTTONS) }, badge = "Coming in Prompt 3")
    }
}

/** Input mode picker. Saved to settings right away. */
@Composable
fun InputModeScreen(viewModel: InputModeViewModel, onBack: () -> Unit) {
    val selected by viewModel.inputMode.collectAsStateWithLifecycle()
    PwdeScreen(
        title = "Input",
        subtitle = "Your main way to control games. Saved automatically.",
        onBack = onBack,
        voiceHint = "Say \"head\", \"joystick\" or \"voice\"",
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            InputMode.entries.forEach { mode ->
                OptionCard(
                    title = mode.label,
                    description = mode.description,
                    selected = mode == selected,
                    onClick = { viewModel.select(mode) },
                    icon = mode.icon(),
                    kind = OptionKind.RADIO,
                )
            }
        }
        PlaceholderNotice(
            "Calibration comes next",
            "Centering, range and live testing for each input arrive with camera and mic support in Prompt 2.",
        )
    }
}

private const val ACTIONS_PER_PAGE = 4

/** E2/E3 Gestures: 8 actions over two pages, conflicts flagged. */
@Composable
fun GesturesScreen(viewModel: GesturesViewModel, onBack: () -> Unit, onChoose: (GestureAction) -> Unit) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(1) }
    val pages = GestureAction.entries.chunked(ACTIONS_PER_PAGE)
    PwdeScreen(
        title = "Gestures",
        subtitle = "Pick a face move for each action.",
        onBack = onBack,
        voiceHint = "Say \"change select\" or \"next page\"",
        footer = { Pager(page, pages.size, { page-- }, { page++ }) },
    ) {
        val current = config ?: return@PwdeScreen
        pages[page - 1].forEach { action ->
            val gesture = current.gestureAssignments[action]
            val conflict = gesture?.let { current.conflictsFor(action, it) }.orEmpty()
            GradientCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(action.label, style = MaterialTheme.typography.titleMedium, color = PwdeTheme.colors.text)
                        Text(gesture?.label ?: "Not set", style = MaterialTheme.typography.bodySmall, color = PwdeTheme.colors.textMuted)
                        if (conflict.isNotEmpty()) {
                            StatusPill(
                                "Same as ${conflict.joinToString { it.label }}",
                                color = PwdeTheme.colors.warning,
                                icon = Icons.Outlined.WarningAmber,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    PwdeButton(
                        if (gesture == null) "Add" else "Change",
                        { onChoose(action) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        PlaceholderNotice(
            "Saved, not active yet",
            "Your picks are stored on this phone. PWDe starts reacting to them once face tracking lands in Prompt 2.",
        )
    }
}

/** E4/E5 Choose a gesture from the catalog. Moves already in use are marked. */
@Composable
fun ChooseGestureScreen(viewModel: ChooseGestureViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PwdeTheme.colors
    PwdeScreen(
        title = "Gesture for \"${state.action.label}\"",
        subtitle = "Pick one. Moves already in use are marked.",
        onBack = onBack,
        voiceHint = "Say a gesture's name",
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PwdeButton("Clear", viewModel::clear, style = ButtonStyle.SECONDARY, enabled = state.selected != null, modifier = Modifier.weight(1f))
                PwdeButton("Done", onBack, modifier = Modifier.weight(1f))
            }
        },
    ) {
        FacialGesture.entries.chunked(2).forEach { row ->
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
                row.forEach { gesture ->
                    GestureTile(
                        gesture = gesture,
                        selected = gesture == state.selected,
                        usedBy = state.usedBy[gesture].orEmpty(),
                        onClick = { viewModel.select(gesture) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.selected != null && state.usedBy[state.selected].orEmpty().isNotEmpty()) {
            Text(
                "Heads up: this move also triggers ${state.usedBy[state.selected].orEmpty().joinToString { it.label }}.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.warning,
            )
        }
    }
}

@Composable
private fun GestureTile(
    gesture: FacialGesture,
    selected: Boolean,
    usedBy: List<GestureAction>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PwdeTheme.colors
    Column(
        modifier
            .heightIn(min = 120.dp)
            .clip(PwdeShapes.card)
            .background(colors.cardBrush)
            .border(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.secondary.copy(alpha = 0.5f), PwdeShapes.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(colors.surface),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Face, contentDescription = null, tint = colors.primary) }
        Text(gesture.label, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Text(gesture.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        when {
            selected -> StatusPill("Selected", icon = Icons.Outlined.CheckCircle)
            usedBy.isNotEmpty() -> StatusPill("Used: ${usedBy.joinToString { it.label }}", color = colors.warning)
        }
    }
}

private enum class Detail(val label: String) { BASIC("Basic"), ADVANCED("Advanced") }

/** E6–E8 Cursor speed. Layout in place; values are wired to tracking in Prompt 2. */
@Composable
fun CursorSpeedScreen(onBack: () -> Unit) {
    var detail by rememberSaveable { mutableStateOf(Detail.BASIC) }
    PwdeScreen(
        title = "Cursor speed",
        subtitle = "How the pointer follows your head.",
        onBack = onBack,
        voiceHint = "Say \"faster\", \"slower\" or \"advanced\"",
    ) {
        SegmentedToggle(Detail.entries, detail, { it.label }, { detail = it })
        PlaceholderNotice(
            "Tuning connects in Prompt 2",
            "These controls show the layout only. They'll adjust the real pointer once head tracking is connected.",
        )
        if (detail == Detail.BASIC) {
            InertStepper("Speed", 5)
            InertStepper("Smoothing", 7)
        } else {
            InertStepper("Moving up", 5)
            InertStepper("Moving down", 5)
            InertStepper("Moving left", 3)
            InertStepper("Moving right", 3)
            InertStepper("Pointer smoothing", 7)
            InertStepper("Gesture smoothing", 5)
        }
    }
}

/** E9/E10 Joystick. Layout in place; values are wired in Prompt 2. */
@Composable
fun JoystickScreen(onBack: () -> Unit) {
    var detail by rememberSaveable { mutableStateOf(Detail.BASIC) }
    PwdeScreen(
        title = "Joystick",
        subtitle = "Your virtual joystick for moving in games.",
        onBack = onBack,
        voiceHint = "Say \"bigger\", \"more sensitive\" or \"advanced\"",
    ) {
        SegmentedToggle(Detail.entries, detail, { it.label }, { detail = it })
        PlaceholderNotice(
            "Tuning connects in Prompt 2",
            "The live joystick preview and these settings start working once tilt and head tracking are connected.",
        )
        if (detail == Detail.BASIC) {
            InertStepper("Size", 5)
            InertStepper("Sensitivity", 5)
        } else {
            InertStepper("Dead zone", 3)
            InertStepper("Visibility", 10)
            InertStepper("Release delay", 5)
        }
    }
}

@Composable
private fun InertStepper(label: String, level: Int) {
    LevelStepper(label = label, level = level, onLevelChange = {}, enabled = false)
}

/** Custom buttons land with GabAI game profiles in Prompt 3. */
@Composable
fun CustomButtonsScreen(onBack: () -> Unit) {
    PwdeScreen(title = "Custom buttons", subtitle = "Make your own on-screen buttons.", onBack = onBack) {
        PlaceholderNotice(
            "No custom buttons yet",
            "You'll be able to name a button, place it over a game, and trigger it by voice or a face gesture.",
            tag = "Coming in Prompt 3",
        )
    }
}
