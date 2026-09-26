package com.pwde.app.ui.gabai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.data.gabai.Axis
import com.pwde.app.data.gabai.GabAiState
import com.pwde.app.data.model.DEFAULT_LEVEL
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.MAX_LEVEL
import com.pwde.app.data.model.MIN_LEVEL
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.sensors.face.GestureThresholds
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.CameraFeed
import com.pwde.app.ui.components.DemoModeBanner
import com.pwde.app.ui.components.GestureMeter
import com.pwde.app.ui.components.GradientCard
import com.pwde.app.ui.components.IconBadge
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.JoystickView
import com.pwde.app.ui.components.LevelSlider
import com.pwde.app.ui.components.NavCard
import com.pwde.app.ui.components.OptionCard
import com.pwde.app.ui.components.OptionKind
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.PwdeTextField
import com.pwde.app.ui.components.SectionTitle
import com.pwde.app.ui.components.StatusPill
import com.pwde.app.ui.components.StepProgress
import com.pwde.app.ui.components.SwitchRow
import com.pwde.app.ui.components.VoiceCommandsEffect
import com.pwde.app.ui.components.fmt
import com.pwde.app.ui.components.levelWord
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

/**
 * G · GabAI. One route; the screen shown is whatever [GabAiState] the conversation is in, so
 * leaving and coming back (even after a force-close) lands on exactly the same step.
 */
@Composable
fun GabAiScreen(
    viewModel: GabAiViewModel,
    onExit: () -> Unit,
    onDashboard: () -> Unit,
    onPlay: (gameId: String, profileId: Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    BackHandler(onBack = viewModel::back)
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect {
            when (it) {
                GabAiNavigation.Exit -> onExit()
                GabAiNavigation.Dashboard -> onDashboard()
                is GabAiNavigation.Play -> onPlay(it.gameId, it.profileId)
            }
        }
    }
    if (!ui.loaded) return
    when (val state = ui.state) {
        GabAiState.Welcome -> WelcomeStep(viewModel, ui)
        GabAiState.ChooseCalibrationMode -> ChooseModeStep(viewModel, ui)
        is GabAiState.CalibrateCursorAxis -> CursorAxisStep(viewModel, ui, state.axis)
        GabAiState.CalibrateJoystick -> JoystickStep(viewModel, ui)
        GabAiState.CalibrationVoiceSetup -> VoiceStep(viewModel, ui)
        is GabAiState.CalibrationGestureTest -> GestureTestStep(viewModel, ui, state)
        GabAiState.CalibrationGestureReview -> GestureReviewStep(viewModel, ui)
        GabAiState.CalibrationSaved -> CalibrationSavedStep(viewModel, ui)
        GabAiState.ChooseGame -> ChooseGameStep(viewModel, ui)
        GabAiState.ConfirmCalibrationProfile -> ConfirmCalibrationStep(viewModel, ui)
        GabAiState.UploadScreenshot -> ScreenshotStep(viewModel, ui)
        is GabAiState.ButtonMapping -> ButtonMappingStep(viewModel, ui)
        is GabAiState.TriggerAssignment -> TriggerStep(viewModel, ui, state)
        GabAiState.NameAndSaveProfile -> NameAndSaveStep(viewModel, ui)
        GabAiState.ProfileSaved -> ProfileSavedStep(viewModel, ui)
    }
}

/** Shared frame for every GabAI step: GabAI's line on top, then the step's own content. */
@Composable
internal fun GabAiStep(
    viewModel: GabAiViewModel,
    ui: GabAiUiState,
    title: String,
    says: String,
    voiceHint: String,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PwdeScreen(title = title, onBack = viewModel::back, voiceHint = voiceHint, footer = footer) {
        GabAiSays(says)
        ui.message?.let { StatusPill(it, color = PwdeTheme.colors.warning, modifier = Modifier.fillMaxWidth()) }
        content()
    }
}

@Composable
internal fun GabAiSays(text: String) {
    val colors = PwdeTheme.colors
    GradientCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Outlined.AutoAwesome, tint = colors.secondary)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
        }
    }
}

// ---------------- Welcome ----------------

private val WELCOME_COMMANDS = listOf(
    voiceCommand("calibration", "new calibration", "calibration"),
    voiceCommand("game", "new game", "game profile"),
    voiceCommand("continue", "continue", "continue existing"),
)

@Composable
private fun WelcomeStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val gameProfiles by viewModel.gameProfiles.collectAsStateWithLifecycle()
    VoiceCommandsEffect(WELCOME_COMMANDS) { id ->
        when (id) {
            "calibration" -> viewModel.startCalibration()
            "game" -> viewModel.startGameProfile()
            "continue" -> viewModel.resume()
        }
    }
    GabAiStep(
        viewModel, ui,
        title = "GabAI",
        says = "Hi, I'm GabAI! I'll walk you through setting up PWDe one small step at a time. What would you like to do?",
        voiceHint = "Say \"new calibration\", \"new game\" or \"continue\"",
    ) {
        NavCard("New Calibration Profile", "Tune cursor, joystick and voice to you", Icons.Outlined.Tune, viewModel::startCalibration)
        NavCard("New Game Profile", "Map a game's buttons to your moves", Icons.Outlined.SportsEsports, { viewModel.startGameProfile() })
        val resumable = ui.resumable
        if (resumable != null) {
            NavCard("Continue Existing", "Pick up where you left off: ${resumable.state.summary}", Icons.Outlined.History, viewModel::resume)
        } else {
            InfoNote("Nothing unfinished to continue. Anything you start is saved step by step, so you can always come back to it.")
        }
        if (gameProfiles.isNotEmpty()) {
            SectionTitle("Edit a saved game profile")
            gameProfiles.forEach { profile ->
                NavCard(profile.profileName, profile.gameName, Icons.Outlined.SportsEsports, { viewModel.editGameProfile(profile.id) })
            }
        }
    }
}

// ---------------- Calibration branch ----------------

private val MODE_COMMANDS = listOf(
    voiceCommand(FaceOutputMode.CURSOR.name, "cursor", "pointer"),
    voiceCommand(FaceOutputMode.JOYSTICK.name, "joystick"),
)

@Composable
private fun ChooseModeStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    VoiceCommandsEffect(MODE_COMMANDS) { viewModel.chooseMode(FaceOutputMode.valueOf(it)) }
    GabAiStep(
        viewModel, ui,
        title = "Calibration",
        says = "First, how should your head control things? A cursor you point with, or a joystick you tilt?",
        voiceHint = "Say \"cursor\" or \"joystick\"",
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            OptionCard(
                "Cursor", "Turn your head to move a pointer, like a mouse", ui.form.calibrationMode == FaceOutputMode.CURSOR,
                { viewModel.chooseMode(FaceOutputMode.CURSOR) }, icon = Icons.Outlined.Mouse, kind = OptionKind.RADIO,
            )
            OptionCard(
                "Joystick", "Tilt your head to steer in 8 directions", ui.form.calibrationMode == FaceOutputMode.JOYSTICK,
                { viewModel.chooseMode(FaceOutputMode.JOYSTICK) }, icon = Icons.Outlined.Gamepad, kind = OptionKind.RADIO,
            )
        }
    }
}

private val STEP_COMMANDS = listOf(
    voiceCommand("next", "next", "looks good", "done"),
    voiceCommand("faster", "faster", "more"),
    voiceCommand("slower", "slower", "less"),
    voiceCommand("recenter", "recenter", "center"),
)

private fun axisSays(axis: Axis) = when (axis) {
    Axis.UP -> "Look up to move the pointer onto the top target. Change the speed until it feels comfortable."
    Axis.DOWN -> "Now look down to reach the bottom target."
    Axis.LEFT -> "Turn your head left to reach the left target."
    Axis.RIGHT -> "And right, to the right target."
    Axis.DIAGONAL -> "Last one: move to a corner target. If the pointer shakes, add smoothing; if it lags, take some away."
}

@Composable
private fun CursorAxisStep(viewModel: GabAiViewModel, ui: GabAiUiState, axis: Axis) {
    val face by viewModel.faceState.collectAsStateWithLifecycle()
    val surface by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val cursor = ui.form.cursor
    val level = when (axis) {
        Axis.UP -> cursor.speedUp
        Axis.DOWN -> cursor.speedDown
        Axis.LEFT -> cursor.speedLeft
        Axis.RIGHT -> cursor.speedRight
        Axis.DIAGONAL -> cursor.smoothing
    }
    fun set(value: Int) = viewModel.setCursor(
        when (axis) {
            Axis.UP -> cursor.copy(speedUp = value)
            Axis.DOWN -> cursor.copy(speedDown = value)
            Axis.LEFT -> cursor.copy(speedLeft = value)
            Axis.RIGHT -> cursor.copy(speedRight = value)
            Axis.DIAGONAL -> cursor.copy(smoothing = value)
        },
    )
    VoiceCommandsEffect(STEP_COMMANDS) { id ->
        when (id) {
            "next" -> viewModel.axisDone(axis)
            "faster" -> set((level + 1).coerceAtMost(MAX_LEVEL))
            "slower" -> set((level - 1).coerceAtLeast(MIN_LEVEL))
            "recenter" -> viewModel.recenterCursor()
        }
    }
    GabAiStep(
        viewModel, ui,
        title = "Cursor: ${axis.label}",
        says = axisSays(axis),
        voiceHint = "Say \"faster\", \"slower\", \"recenter\" or \"next\"",
        footer = { PwdeButton("Next", { viewModel.axisDone(axis) }, icon = Icons.AutoMirrored.Outlined.ArrowForward, modifier = Modifier.fillMaxWidth()) },
    ) {
        StepProgress(axis.ordinal + 1, Axis.entries.size, "${axis.label} direction")
        DemoModeBanner(face)
        CameraFeed(
            faceState = face,
            surfaceRequest = surface,
            canRequestCamera = viewModel.canRequestCamera,
            onCameraPermissionResult = viewModel::onCameraPermissionResult,
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.45f),
        )
        TargetPad(face.cursor.x, face.cursor.y, face.hasFace, axis)
        PwdeButton("Recenter pointer", viewModel::recenterCursor, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.CenterFocusStrong, modifier = Modifier.fillMaxWidth())
        LevelSlider(if (axis == Axis.DIAGONAL) "Smoothing" else "Speed moving ${axis.label.lowercase()}", level, ::set)
    }
}

/** Where the target sits for each direction. */
private fun targetFor(axis: Axis): Offset = when (axis) {
    Axis.UP -> Offset(0.5f, 0.1f)
    Axis.DOWN -> Offset(0.5f, 0.9f)
    Axis.LEFT -> Offset(0.1f, 0.5f)
    Axis.RIGHT -> Offset(0.9f, 0.5f)
    Axis.DIAGONAL -> Offset(0.9f, 0.1f)
}

@Composable
private fun TargetPad(x: Float, y: Float, active: Boolean, axis: Axis) {
    val colors = PwdeTheme.colors
    val target = targetFor(axis)
    val onTarget = kotlin.math.hypot(x - target.x, y - target.y) < 0.1f
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(PwdeShapes.button)
            .background(colors.surfaceMuted)
            .semantics {
                contentDescription = if (onTarget) "Pointer is on the target" else "Pointer at ${(x * 100).toInt()}% across, ${(y * 100).toInt()}% down"
            },
    ) {
        val t = Offset(target.x * size.width, target.y * size.height)
        drawCircle(colors.primary.copy(alpha = if (onTarget) 0.5f else 0.2f), radius = 26.dp.toPx(), center = t)
        drawCircle(colors.primary, radius = 26.dp.toPx(), center = t, style = Stroke(3.dp.toPx()))
        drawCircle(if (active) colors.secondary else colors.textMuted, radius = 12.dp.toPx(), center = Offset(x * size.width, y * size.height))
    }
    if (onTarget) StatusPill("On target!", icon = Icons.Outlined.CheckCircle)
}

private val JOYSTICK_STEP_COMMANDS = listOf(
    voiceCommand("next", "next", "looks good", "done"),
    voiceCommand("center", "set center", "center here"),
)

@Composable
private fun JoystickStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val face by viewModel.faceState.collectAsStateWithLifecycle()
    val surface by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val joystick = ui.form.joystick
    VoiceCommandsEffect(JOYSTICK_STEP_COMMANDS) { id -> if (id == "next") viewModel.joystickDone() else viewModel.setJoystickCenterHere() }
    GabAiStep(
        viewModel, ui,
        title = "Calibration: Joystick",
        says = "Hold your head comfortably and set that as the center. Then tilt to steer, and tune it until it feels right.",
        voiceHint = "Say \"set center\" or \"next\"",
        footer = { PwdeButton("Next", viewModel::joystickDone, icon = Icons.AutoMirrored.Outlined.ArrowForward, modifier = Modifier.fillMaxWidth()) },
    ) {
        DemoModeBanner(face)
        Row(horizontalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap), verticalAlignment = Alignment.CenterVertically) {
            CameraFeed(
                faceState = face,
                surfaceRequest = surface,
                canRequestCamera = viewModel.canRequestCamera,
                onCameraPermissionResult = viewModel::onCameraPermissionResult,
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JoystickView(face.joystick, Modifier.fillMaxWidth(), active = face.hasFace)
                StatusPill(face.joystick.direction.label, icon = Icons.Outlined.Gamepad)
            }
        }
        PwdeButton("Set center here", viewModel::setJoystickCenterHere, icon = Icons.Outlined.CenterFocusStrong, modifier = Modifier.fillMaxWidth())
        LevelSlider("Sensitivity", joystick.sensitivity, { viewModel.setJoystick(joystick.copy(sensitivity = it)) })
        LevelSlider("Dead zone", joystick.deadZone, { viewModel.setJoystick(joystick.copy(deadZone = it)) })
        LevelSlider("Size", joystick.size, { viewModel.setJoystick(joystick.copy(size = it)) })
    }
}

private val VOICE_STEP_COMMANDS = listOf(
    voiceCommand("next", "next", "looks good", "done"),
    voiceCommand("exact", "exact phrase", "exact"),
    voiceCommand("anywhere", "word anywhere", "anywhere"),
    voiceCommand("immediate", "right away"),
    voiceCommand("after", "after I finish"),
)

@Composable
private fun VoiceStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val form = ui.form
    VoiceCommandsEffect(VOICE_STEP_COMMANDS) { id ->
        when (id) {
            "next" -> viewModel.voiceDone()
            "exact" -> viewModel.setVoice(match = VoiceMatchMode.EXACT)
            "anywhere" -> viewModel.setVoice(match = VoiceMatchMode.WORD_ANYWHERE)
            "immediate" -> viewModel.setVoice(activation = VoiceActivationMode.IMMEDIATE)
            "after" -> viewModel.setVoice(activation = VoiceActivationMode.AFTER_FINISH)
        }
    }
    GabAiStep(
        viewModel, ui,
        title = "Calibration: Voice",
        says = "Now, voice. Choose how strictly I match your words and when I act on them. Then we'll try some face gestures.",
        voiceHint = "Say \"word anywhere\", \"right away\" or \"next\"",
        footer = { PwdeButton("Next", viewModel::voiceDone, icon = Icons.AutoMirrored.Outlined.ArrowForward, modifier = Modifier.fillMaxWidth()) },
    ) {
        SwitchRow("Voice control", form.voiceEnabled, { viewModel.setVoice(enabled = it) }, icon = Icons.Outlined.Mic)
        SectionTitle("How words are matched")
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            VoiceMatchMode.entries.forEach { mode ->
                OptionCard(
                    mode.label, mode.description, mode == form.matchMode, { viewModel.setVoice(match = mode) },
                    icon = Icons.AutoMirrored.Outlined.FormatListBulleted, kind = OptionKind.RADIO,
                )
            }
        }
        SectionTitle("When PWDe acts")
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PwdeTheme.spacing.itemGap)) {
            VoiceActivationMode.entries.forEach { mode ->
                OptionCard(
                    mode.label, mode.description, mode == form.activationMode, { viewModel.setVoice(activation = mode) },
                    icon = Icons.Outlined.Timer, kind = OptionKind.RADIO,
                )
            }
        }
    }
}

private val GESTURE_TEST_COMMANDS = listOf(
    voiceCommand("next", "skip", "next", "can't do it"),
    voiceCommand("finish", "skip the rest", "finish gestures"),
)

/** One gesture at a time: doing it turns it on (and moves on by itself); skipping leaves it off. */
@Composable
private fun GestureTestStep(viewModel: GabAiViewModel, ui: GabAiUiState, test: GabAiState.CalibrationGestureTest) {
    val face by viewModel.faceState.collectAsStateWithLifecycle()
    val surface by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val sensitivity by viewModel.gestureSensitivity.collectAsStateWithLifecycle()
    val colors = PwdeTheme.colors
    val gesture = GabAiState.GESTURE_TEST[test.index]
    val passed = gesture in ui.form.passedGestures
    val level = sensitivity[gesture] ?: DEFAULT_LEVEL
    val measure = face.gesture.measures[gesture]
    VoiceCommandsEffect(GESTURE_TEST_COMMANDS) { id -> if (id == "next") viewModel.nextGesture() else viewModel.skipRemainingGestures() }
    GabAiStep(
        viewModel, ui,
        title = "Gesture: ${gesture.label}",
        says = if (passed) "Got it! ${gesture.label} is on."
        else "${gesture.description}. Can't do it comfortably? Skip it — I'll only turn on the gestures you can do.",
        voiceHint = "Say \"skip\" to move on, or \"skip the rest\" to finish",
        footer = {
            PwdeButton(
                if (passed) "Next" else "Skip",
                viewModel::nextGesture,
                style = if (passed) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        StepProgress(test.index + 1, GabAiState.GESTURE_TEST.size, gesture.label)
        DemoModeBanner(face)
        GradientCard(Modifier.fillMaxWidth()) {
            GestureMeter(gesture, measure, active = passed || gesture in face.gesture.active)
            Text(
                when {
                    passed -> "Detected! This gesture is on."
                    face.isSimulated && measure == null -> "Demo mode can only simulate tilt, nod and shake — skip this one."
                    !face.hasFace -> "Face the camera to try it."
                    else -> "Do the move — the bar passes the white tick when PWDe sees it. Too hard? Raise the sensitivity."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (passed) colors.primary else colors.textMuted,
            )
        }
        CameraFeed(
            faceState = face,
            surfaceRequest = surface,
            canRequestCamera = viewModel.canRequestCamera,
            onCameraPermissionResult = viewModel::onCameraPermissionResult,
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.45f),
        )
        LevelSlider(
            label = "Sensitivity",
            level = level,
            onLevelChange = { viewModel.setGestureSensitivity(gesture, it) },
            valueLabel = "${levelWord(level)} · fires at ${fmt(GestureThresholds.forGesture(gesture, level))}${gesture.unit()}",
        )
        PwdeButton("Skip the rest", viewModel::skipRemainingGestures, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.SkipNext, modifier = Modifier.fillMaxWidth())
    }
}

private fun FacialGesture.unit() = when (this) {
    FacialGesture.TILT_LEFT, FacialGesture.TILT_RIGHT, FacialGesture.NOD, FacialGesture.SHAKE -> "°"
    else -> ""
}

private val GESTURE_REVIEW_COMMANDS = listOf(
    voiceCommand("save", "save", "save profile"),
    voiceCommand("retry", "try again", "retry"),
)

@Composable
private fun GestureReviewStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    val colors = PwdeTheme.colors
    val form = ui.form
    val tests = GabAiState.GESTURE_TEST
    val on = tests.filter { it in form.passedGestures }
    val missed = tests.filterNot { it in form.passedGestures }
    VoiceCommandsEffect(GESTURE_REVIEW_COMMANDS) { id -> if (id == "save") viewModel.saveCalibration() else viewModel.retryMissedGestures() }
    GabAiStep(
        viewModel, ui,
        title = "Calibration: Gestures",
        says = when {
            on.isEmpty() -> "No gestures are on this time, and that's fine — everything else still works. "
            missed.isEmpty() -> "You did every gesture, so they're all on! "
            else -> "You did ${on.size} of ${tests.size} gestures. Only those are on, so the others can't fire by accident. "
        } + "Give this profile a name and save it.",
        voiceHint = if (missed.isEmpty()) "Say \"save\"" else "Say \"try again\" or \"save\"",
        footer = { PwdeButton("Save calibration profile", viewModel::saveCalibration, icon = Icons.Outlined.Save, modifier = Modifier.fillMaxWidth()) },
    ) {
        SectionTitle("On (${on.size})")
        Text(on.joinToString { it.label }.ifEmpty { "None" }, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        if (missed.isNotEmpty()) {
            SectionTitle("Off (${missed.size})")
            Text(missed.joinToString { it.label }, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
            PwdeButton("Try the missed ones again", viewModel::retryMissedGestures, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Replay, modifier = Modifier.fillMaxWidth())
        }
        PwdeTextField("Profile name (optional)", form.calibrationName, viewModel::setCalibrationName)
    }
}

private val SAVED_COMMANDS = listOf(
    voiceCommand("done", "done", "finish"),
    voiceCommand("game", "set up a game", "game profile", "continue"),
)

@Composable
private fun CalibrationSavedStep(viewModel: GabAiViewModel, ui: GabAiUiState) {
    VoiceCommandsEffect(SAVED_COMMANDS) { id -> if (id == "done") viewModel.calibrationDone() else viewModel.continueToGame() }
    val nextGame = ui.form.continueToGame || ui.form.gameId != null
    GabAiStep(
        viewModel, ui,
        title = "Calibration saved",
        says = "Saved \"${ui.form.calibrationName}\"! It's your active setup now. " +
            if (nextGame) "Let's carry on with your game." else "Want to set up a game with it?",
        voiceHint = "Say \"set up a game\" or \"done\"",
    ) {
        PwdeButton(
            if (nextGame) "Continue to the game profile" else "Set up a game with it",
            viewModel::continueToGame,
            icon = Icons.Outlined.SportsEsports,
            modifier = Modifier.fillMaxWidth(),
        )
        PwdeButton("Done", viewModel::calibrationDone, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.CheckCircle, modifier = Modifier.fillMaxWidth())
    }
}
