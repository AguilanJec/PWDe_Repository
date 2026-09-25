package com.pwde.app.ui.gabai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.data.gabai.GabAiState
import com.pwde.app.data.model.ButtonTrigger
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.Game
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.model.TriggerType
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.sensors.face.JoystickDirection
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.DemoModeBanner
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeTextField
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.SegmentedToggle
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.games.GameCard
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.data.local.inputModeOrDefault
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

// ---------------- Choose game / calibration ----------------

@Composable
internal fun ChooseGameStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val commands = remember { Game.entries.map { voiceCommand(it.id, it.displayName) } }
    val gameProfiles by viewModel.gameProfiles.collectAsStateWithLifecycle()
    VoiceCommandsEffect(commands) { id -> Game.byId(id)?.let(viewModel::chooseGame) }
    GabAiStep(
        viewModel, ui,
        title = "Game profile",
        says = "Which game are we setting up?",
        voiceHint = "Say a game's name",
    ) {
        Game.entries.forEach { game -> GameCard(game, hasProfile = gameProfiles.any { it.gameId == game.id }) { viewModel.chooseGame(game) } }
    }
}

private val CONFIRM_COMMANDS = listOf(
    voiceCommand("use", "use this one", "next", "confirm"),
    voiceCommand("new", "new calibration", "make a new one"),
)

@Composable
internal fun ConfirmCalibrationStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val profiles by viewModel.calibrationProfiles.collectAsStateWithLifecycle()
    val selected = ui.form.calibrationProfileId
    VoiceCommandsEffect(CONFIRM_COMMANDS) { id -> if (id == "use") viewModel.confirmCalibration() else viewModel.calibrateForThisGame() }
    GabAiStep(
        viewModel, ui,
        title = "Calibration for ${Game.byId(ui.form.gameId)?.displayName ?: "this game"}",
        says = if (profiles.isEmpty()) "This game needs a calibration profile first. Let's make one — it only takes a minute."
        else "Which calibration should this game use? Keep the one I picked, or switch.",
        voiceHint = if (profiles.isEmpty()) "Say \"new calibration\"" else "Say \"use this one\" or \"new calibration\"",
        footer = if (profiles.isEmpty()) null else {
            { PwdeButton("Use this one", viewModel::confirmCalibration, icon = Icons.AutoMirrored.Outlined.ArrowForward, modifier = Modifier.fillMaxWidth()) }
        },
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            profiles.forEach { profile ->
                OptionCard(
                    profile.name,
                    profile.inputModeOrDefault.let { if (it == InputMode.JOYSTICK) "Joystick" else "Cursor" },
                    profile.id == selected,
                    { viewModel.chooseCalibration(profile.id) },
                    icon = Icons.Outlined.Tune,
                    kind = OptionKind.RADIO,
                )
            }
        }
        PwdeButton(
            "Make a new calibration",
            viewModel::calibrateForThisGame,
            style = if (profiles.isEmpty()) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
            icon = Icons.Outlined.Tune,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------- Screenshot ----------------

private val SCREENSHOT_COMMANDS = listOf(
    voiceCommand("pick", "choose screenshot", "pick screenshot", "choose"),
    voiceCommand("blank", "blank screen", "no screenshot"),
    voiceCommand("next", "next", "continue"),
)

@Composable
internal fun ScreenshotStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::importScreenshot) }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val hasShot = ui.form.screenshotPath != null
    VoiceCommandsEffect(SCREENSHOT_COMMANDS) { id ->
        when (id) {
            "pick" -> pick()
            "blank" -> viewModel.useBlankScreen()
            "next" -> if (hasShot) viewModel.screenshotDone()
        }
    }
    GabAiStep(
        viewModel, ui,
        title = "Game screenshot",
        says = "Show me the game: pick a screenshot of it mid-match, with its buttons visible. We'll mark the buttons on it next.",
        voiceHint = "Say \"choose screenshot\", \"blank screen\" or \"next\"",
        footer = {
            PwdeButton("Next", viewModel::screenshotDone, enabled = hasShot, icon = Icons.AutoMirrored.Outlined.ArrowForward, modifier = Modifier.fillMaxWidth())
        },
    ) {
        val shot = ui.screenshot
        if (shot != null) {
            Image(
                shot,
                contentDescription = "Your game screenshot",
                modifier = Modifier.fillMaxWidth().clip(PwdeShapes.card).border(2.dp, PwdeTheme.colors.borderBrush, PwdeShapes.card),
                contentScale = ContentScale.FillWidth,
            )
        }
        PwdeButton(if (shot == null) "Choose screenshot" else "Choose a different one", pick, icon = Icons.Outlined.Image, modifier = Modifier.fillMaxWidth())
        PwdeButton("Use a blank screen instead", viewModel::useBlankScreen, style = ButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
        InfoNote("Take the screenshot in the game first (it stays on this phone). Without one, you'll place buttons on a blank screen.")
    }
}

// ---------------- Button mapping ----------------

private val MAPPING_COMMANDS = listOf(
    voiceCommand("place", "place", "add button", "place here", "add"),
    voiceCommand("delete", "delete", "remove"),
    voiceCommand("rename", "rename", "name it"),
    voiceCommand("up", "move up", "up"),
    voiceCommand("down", "move down", "down"),
    voiceCommand("left", "move left", "left"),
    voiceCommand("right", "move right", "right"),
    voiceCommand("next_button", "next button", "select next"),
    voiceCommand("done", "done", "finished", "next"),
)

private const val NUDGE = 0.02f

@Composable
internal fun ButtonMappingStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val face by viewModel.faceState.collectAsStateWithLifecycle()
    val buttons = ui.form.buttons
    val selected = buttons.firstOrNull { it.id == ui.selectedButtonId }
    VoiceCommandsEffect(MAPPING_COMMANDS) { id ->
        when (id) {
            "place" -> viewModel.addButtonAtPointer()
            "delete" -> viewModel.deleteSelected()
            "rename" -> viewModel.captureLabelByVoice()
            "up" -> viewModel.nudgeSelected(0f, -NUDGE)
            "down" -> viewModel.nudgeSelected(0f, NUDGE)
            "left" -> viewModel.nudgeSelected(-NUDGE, 0f)
            "right" -> viewModel.nudgeSelected(NUDGE, 0f)
            "next_button" -> if (buttons.isNotEmpty()) {
                val i = buttons.indexOfFirst { it.id == ui.selectedButtonId }
                viewModel.selectButton(buttons[(i + 1).mod(buttons.size)].id)
            }
            "done" -> viewModel.buttonsDone()
        }
    }
    GabAiStep(
        viewModel, ui,
        title = "Mark the buttons",
        says = "Tap each on-screen button in the game — or point with your head and say \"place\". Then give each one a name.",
        voiceHint = "Say \"place\", \"rename\", \"move left\" or \"done\"",
        footer = {
            PwdeButton(
                "Done — ${buttons.size} ${if (buttons.size == 1) "button" else "buttons"}",
                viewModel::buttonsDone,
                enabled = buttons.isNotEmpty(),
                icon = Icons.Outlined.CheckCircle,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        DemoModeBanner(face)
        ButtonCanvas(
            screenshot = ui.screenshot,
            buttons = buttons,
            selectedId = ui.selectedButtonId,
            pointer = if (face.hasFace) Offset(face.cursor.x, face.cursor.y) else null,
            onTapEmpty = viewModel::addButton,
            onTapButton = { viewModel.selectButton(it) },
            onDrag = viewModel::moveButton,
        )
        if (selected == null) {
            InfoNote(if (buttons.isEmpty()) "No buttons yet. Tap the game where a button is." else "Tap a button to rename, move or delete it.")
        } else {
            SelectedButtonEditor(viewModel, selected, ui.capturingLabel)
        }
    }
}

@Composable
private fun SelectedButtonEditor(viewModel: GabAiViewModel, button: MappedButton, capturing: Boolean) {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth()) {
        PwdeTextField("Button name", button.label, { viewModel.renameButton(button.id, it) })
        PwdeButton(
            if (capturing) "Listening — say the name…" else "Say its name",
            viewModel::captureLabelByVoice,
            style = ButtonStyle.SECONDARY,
            icon = Icons.Outlined.Mic,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NudgeButton(Icons.AutoMirrored.Outlined.ArrowBack, "Move left") { viewModel.nudgeSelected(-NUDGE, 0f) }
            NudgeButton(Icons.Outlined.ArrowUpward, "Move up") { viewModel.nudgeSelected(0f, -NUDGE) }
            NudgeButton(Icons.Outlined.ArrowDownward, "Move down") { viewModel.nudgeSelected(0f, NUDGE) }
            NudgeButton(Icons.AutoMirrored.Outlined.ArrowForward, "Move right") { viewModel.nudgeSelected(NUDGE, 0f) }
        }
        PwdeButton("Delete button", viewModel::deleteSelected, style = ButtonStyle.DESTRUCTIVE, icon = Icons.Outlined.Delete, modifier = Modifier.fillMaxWidth())
        Text("Tip: drag the circle to move it.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

@Composable
private fun NudgeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(MinTouchTarget)
            .clip(PwdeShapes.button)
            .border(2.dp, PwdeTheme.colors.primary, PwdeShapes.button)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = PwdeTheme.colors.primary)
    }
}

/**
 * The game screenshot (or a blank game-shaped screen) with its buttons drawn on it. Every button
 * looks the same however it got there.
 */
@Composable
internal fun ButtonCanvas(
    screenshot: ImageBitmap?,
    buttons: List<MappedButton>,
    selectedId: Int?,
    pointer: Offset? = null,
    highlightId: Int? = null,
    onTapEmpty: ((Float, Float) -> Unit)? = null,
    onTapButton: ((Int) -> Unit)? = null,
    onDrag: ((Int, Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = PwdeTheme.colors
    val aspect = screenshot?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    val latestButtons by rememberUpdatedState(buttons)
    val latestSelected by rememberUpdatedState(selectedId)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(PwdeShapes.card)
            .background(Color(0xFF1B2A1E))
            .border(2.dp, colors.borderBrush, PwdeShapes.card)
            .semantics { contentDescription = "Game screen with ${buttons.size} marked buttons" }
            .then(
                if (onTapEmpty == null && onTapButton == null) Modifier
                else Modifier.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val x = tap.x / size.width
                        val y = tap.y / size.height
                        val hit = latestButtons.minByOrNull { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
                            ?.takeIf { kotlin.math.hypot((it.x - x) * size.width, (it.y - y) * size.height) < HIT_RADIUS_DP * density }
                        if (hit != null) onTapButton?.invoke(hit.id) else onTapEmpty?.invoke(x, y)
                    }
                },
            )
            .then(
                if (onDrag == null) Modifier
                else Modifier.pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val id = latestSelected ?: return@detectDragGestures
                        change.consume()
                        onDrag(id, change.position.x / size.width, change.position.y / size.height)
                    }
                },
            ),
    ) {
        if (screenshot != null) {
            Image(screenshot, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        } else {
            Text(
                "Blank game screen",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
        val diameter = 44.dp
        buttons.forEach { button ->
            val isSelected = button.id == selectedId || button.id == highlightId
            Box(
                Modifier
                    .offset(
                    x = maxWidth * button.x - diameter / 2,
                    y = maxHeight * button.y - diameter / 2,
                    )
                    .size(diameter),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .size(diameter)
                        .clip(CircleShape)
                        .background(if (isSelected) colors.primary.copy(alpha = 0.55f) else colors.secondary.copy(alpha = 0.4f))
                        .border(3.dp, if (isSelected) colors.primary else Color.White, CircleShape),
                )
                Text(
                    button.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(y = diameter)
                        .background(Color.Black.copy(alpha = 0.6f), PwdeShapes.pill)
                        .padding(horizontal = 6.dp),
                )
            }
        }
        if (pointer != null) {
            val dot = 16.dp
            Box(
                Modifier
                    .offset(x = maxWidth * pointer.x - dot / 2, y = maxHeight * pointer.y - dot / 2)
                    .size(dot)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

private const val HIT_RADIUS_DP = 32f

// ---------------- Trigger assignment ----------------

private val TRIGGER_COMMANDS = listOf(
    voiceCommand("type:VOICE", "voice", "voice command"),
    voiceCommand("type:GESTURE", "gesture", "head gesture"),
    voiceCommand("type:JOYSTICK", "joystick", "joystick action"),
    voiceCommand("next", "next", "done"),
) + FacialGesture.curated.map { voiceCommand("gesture:${it.name}", it.spokenName) } +
    JoystickDirection.entries.filter { it != JoystickDirection.CENTER }.map { voiceCommand("dir:${it.name}", "stick ${it.label.lowercase()}") }

@Composable
internal fun TriggerStep(viewModel: GabAiViewModel, ui: GabAiUiState, state: GabAiState.TriggerAssignment) {
    val buttons = ui.form.buttons
    val button = buttons.getOrNull(state.buttonIndex) ?: return
    var type by rememberSaveable(button.id) { mutableStateOf(button.trigger?.type ?: TriggerType.VOICE) }
    val trigger = button.trigger
    val conflicts = trigger?.let { t -> buttons.filter { it.id != button.id && it.trigger == t }.map { it.label } }.orEmpty()
    fun set(t: ButtonTrigger?) = viewModel.setTrigger(state.buttonIndex, t)
    VoiceCommandsEffect(TRIGGER_COMMANDS) { id ->
        when {
            id.startsWith("type:") -> type = TriggerType.valueOf(id.removePrefix("type:"))
            id.startsWith("gesture:") -> set(ButtonTrigger(TriggerType.GESTURE, id.removePrefix("gesture:"))).also { type = TriggerType.GESTURE }
            id.startsWith("dir:") -> set(ButtonTrigger(TriggerType.JOYSTICK, id.removePrefix("dir:"))).also { type = TriggerType.JOYSTICK }
            id == "next" -> viewModel.triggerDone()
        }
    }
    GabAiStep(
        viewModel, ui,
        title = "Button ${state.buttonIndex + 1} of ${state.totalButtons}",
        says = "How do you want to press \"${button.label}\"?",
        voiceHint = "Say \"voice\", \"gesture\" or \"joystick\", then \"next\"",
        footer = {
            PwdeButton(
                if (state.buttonIndex + 1 < state.totalButtons) "Next button" else "Next",
                viewModel::triggerDone,
                enabled = trigger != null,
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ButtonCanvas(ui.screenshot, buttons, selectedId = null, highlightId = button.id, modifier = Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally))
        SegmentedToggle(TriggerType.entries, type, { it.label.substringBefore(' ') }, { type = it })
        when (type) {
            TriggerType.VOICE -> {
                val phrase = if (trigger?.type == TriggerType.VOICE) trigger.value else ""
                PwdeTextField("What will you say?", phrase, { set(if (it.isBlank()) null else ButtonTrigger(TriggerType.VOICE, it)) })
                if (phrase.isEmpty()) {
                    PwdeButton(
                        "Use \"${button.label.lowercase()}\"",
                        { set(ButtonTrigger(TriggerType.VOICE, button.label.lowercase())) },
                        style = ButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            TriggerType.GESTURE -> ChipGrid(
                items = FacialGesture.curated,
                label = { it.label },
                selected = { trigger?.type == TriggerType.GESTURE && trigger.value == it.name },
                onPick = { set(ButtonTrigger(TriggerType.GESTURE, it.name)) },
            )
            TriggerType.JOYSTICK -> ChipGrid(
                items = JoystickDirection.entries.filter { it != JoystickDirection.CENTER },
                label = { it.label },
                selected = { trigger?.type == TriggerType.JOYSTICK && trigger.value == it.name },
                onPick = { set(ButtonTrigger(TriggerType.JOYSTICK, it.name)) },
            )
        }
        trigger?.let { StatusPill("Pressed by: ${it.describe()}", icon = Icons.Outlined.CheckCircle) }
        if (conflicts.isNotEmpty()) {
            StatusPill("Also used by ${conflicts.joinToString()}", color = PwdeTheme.colors.warning, icon = Icons.Outlined.WarningAmber)
        }
    }
}

@Composable
private fun <T> ChipGrid(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, onPick: (T) -> Unit) {
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            row.forEach { item ->
                OptionCard(label(item), null, selected(item), { onPick(item) }, kind = OptionKind.RADIO, modifier = Modifier.weight(1f))
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

// ---------------- Name, save, done ----------------

@Composable
internal fun NameAndSaveStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val game = Game.byId(ui.form.gameId)
    VoiceCommandsEffect(remember { listOf(voiceCommand("save", "save", "save profile")) }) { viewModel.saveGameProfile() }
    GabAiStep(
        viewModel, ui,
        title = "Name and save",
        says = "All set! Give this ${game?.displayName ?: "game"} profile a name, and I'll save it.",
        voiceHint = "Say \"save\"",
        footer = { PwdeButton("Save game profile", viewModel::saveGameProfile, icon = Icons.Outlined.Save, modifier = Modifier.fillMaxWidth()) },
    ) {
        PwdeTextField("Profile name (optional)", ui.form.profileName, viewModel::setProfileName)
        SectionTitle("Buttons")
        ui.form.buttons.forEach { button ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(button.label, style = MaterialTheme.typography.bodyLarge, color = PwdeTheme.colors.text, modifier = Modifier.weight(1f))
                Text(button.trigger?.describe() ?: "—", style = MaterialTheme.typography.bodyMedium, color = PwdeTheme.colors.primary)
            }
        }
    }
}

private val SAVED_PROFILE_COMMANDS = listOf(
    voiceCommand("play", "play", "play now"),
    voiceCommand("another", "create another", "another"),
    voiceCommand("dashboard", "dashboard", "done"),
)

@Composable
internal fun ProfileSavedStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    VoiceCommandsEffect(SAVED_PROFILE_COMMANDS) { id ->
        when (id) {
            "play" -> viewModel.playNow()
            "another" -> viewModel.createAnother()
            "dashboard" -> viewModel.goToDashboard()
        }
    }
    GabAiStep(
        viewModel, ui,
        title = "Profile saved",
        says = "Saved \"${ui.form.profileName}\"! You can edit it any time from your Profile.",
        voiceHint = "Say \"play now\", \"create another\" or \"dashboard\"",
    ) {
        PwdeButton("Play now", viewModel::playNow, icon = Icons.Outlined.PlayArrow, modifier = Modifier.fillMaxWidth())
        PwdeButton("Create another", { viewModel.createAnother() }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth())
        PwdeButton("Go to Dashboard", viewModel::goToDashboard, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Dashboard, modifier = Modifier.fillMaxWidth())
    }
}
