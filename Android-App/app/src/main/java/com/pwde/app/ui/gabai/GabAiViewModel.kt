package com.pwde.app.ui.gabai

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.gabai.Axis
import com.pwde.app.data.gabai.GabAiFlow
import com.pwde.app.data.gabai.GabAiForm
import com.pwde.app.data.gabai.GabAiRepository
import com.pwde.app.data.gabai.GabAiSession
import com.pwde.app.data.gabai.GabAiState
import com.pwde.app.data.gabai.HudDetector
import com.pwde.app.data.gabai.detectedToButtons
import com.pwde.app.data.local.CalibrationProfile
import com.pwde.app.data.local.ControlJson
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.GameProfile
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.local.enabledGestures
import com.pwde.app.data.local.toCalibrationProfile
import com.pwde.app.data.model.ButtonTrigger
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.Game
import com.pwde.app.data.model.isEnabledBy
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.model.TriggerType
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.voice.Dictation
import com.pwde.app.sensors.voice.VoiceCommandManager
import com.pwde.app.ui.common.FaceTrackingViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How GabAI was opened. */
sealed interface GabAiStart {
    data object Welcome : GabAiStart
    data class NewGameProfile(val gameId: String?) : GabAiStart
    data class EditGameProfile(val profileId: Long) : GabAiStart
}

sealed interface GabAiNavigation {
    data object Exit : GabAiNavigation
    data object Dashboard : GabAiNavigation
    data class Play(val gameId: String, val profileId: Long) : GabAiNavigation
}

data class GabAiUiState(
    val loaded: Boolean = false,
    /** The conversation in progress; null on the Welcome screen before anything starts. */
    val session: GabAiSession? = null,
    /** An unfinished session the user can resume from Welcome. */
    val resumable: GabAiSession? = null,
    val screenshot: ImageBitmap? = null,
    val selectedButtonId: Int? = null,
    /** Prompting the user to say "assign <name>" for the selected button. */
    val capturingLabel: Boolean = false,
    /** The screenshot is being sent to the backend to find its buttons. */
    val detectingButtons: Boolean = false,
    val message: String? = null,
) {
    val state: GabAiState get() = session?.state ?: GabAiState.Welcome
    val form: GabAiForm get() = session?.form ?: GabAiForm()
}

/**
 * Orchestrates GabAI on top of what already exists: the pure [GabAiFlow] decides where to go,
 * [GabAiRepository] persists every step (so a force-close resumes exactly here), calibration
 * steps write straight to the live controls so tracking previews them, and saving goes through
 * [ProfileRepository].
 */
class GabAiViewModel(
    private val gabAiRepository: GabAiRepository,
    private val profileRepository: ProfileRepository,
    private val controlsRepository: ControlsRepository,
    private val settingsRepository: SettingsRepository,
    private val voiceCommandManager: VoiceCommandManager,
    faceTracking: FaceTrackingManager,
    start: GabAiStart,
    private val hudDetector: HudDetector = HudDetector.None,
) : FaceTrackingViewModel(faceTracking) {
    private val _ui = MutableStateFlow(GabAiUiState())
    val ui: StateFlow<GabAiUiState> = _ui.asStateFlow()

    val calibrationProfiles: StateFlow<List<CalibrationProfile>> = profileRepository.calibrationProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val gameProfiles: StateFlow<List<GameProfile>> = profileRepository.gameProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Gesture sensitivities from the working controls, so the gesture test can tune them live. */
    val gestureSensitivity: StateFlow<Map<FacialGesture, Int>> = controlsRepository.config
        .map { it.gestureSensitivity }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Gestures a button can use: those the chosen calibration's gesture test enabled. */
    val triggerGestures: StateFlow<List<FacialGesture>> = combine(
        _ui.map { it.form.calibrationProfileId }.distinctUntilChanged(),
        calibrationProfiles,
    ) { id, profiles ->
        val enabled = profiles.firstOrNull { it.id == id }?.enabledGestures
        FacialGesture.curated.filter { it.isEnabledBy(enabled) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FacialGesture.curated)

    private val _navigation = Channel<GabAiNavigation>(Channel.BUFFERED)
    val navigation: Flow<GabAiNavigation> = _navigation.receiveAsFlow()

    /** Screenshots are sent to the detection backend to pre-place buttons. */
    val autoDetectsButtons: Boolean get() = hudDetector.isAvailable

    private var loadedScreenshotPath: String? = null
    private var nextButtonId = 1

    /** Session writes run one at a time, in order, so a late save can never undo a completion. */
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch { for (write in writes) write() }
        viewModelScope.launch {
            val resumable = gabAiRepository.unfinishedSession()
            _ui.update { it.copy(loaded = true, resumable = resumable) }
            when (start) {
                GabAiStart.Welcome -> Unit
                is GabAiStart.NewGameProfile -> startGameProfile(start.gameId)
                is GabAiStart.EditGameProfile -> editGameProfile(start.profileId)
            }
        }
        viewModelScope.launch {
            // "assign <words>" / "use <words>" names the selected button or sets the voice trigger; "retry" undoes it.
            voiceCommandManager.results.collect { result ->
                if (!result.isFinal) return@collect
                when (val parsed = Dictation.parse(result.transcript)) {
                    is Dictation.Parsed.Assign -> assignByVoice(parsed.words)
                    Dictation.Parsed.Retry -> retryAssignment()
                    null -> Unit
                }
            }
        }
        viewModelScope.launch {
            // Watch the camera only while a gesture test step is showing.
            _ui.map { it.state as? GabAiState.CalibrationGestureTest }.distinctUntilChanged().collectLatest { test ->
                if (test != null) awaitGesture(test)
            }
        }
        viewModelScope.launch {
            _ui.map { dictationTarget(it) != null }.distinctUntilChanged().collect {
                voiceCommandManager.setDictating(this@GabAiViewModel, it)
            }
        }
    }

    override fun onCleared() {
        voiceCommandManager.setDictating(this, false)
        super.onCleared()
    }

    // ---- Welcome ----

    fun startCalibration() = viewModelScope.launch {
        val current = controlsRepository.config.first()
        val inputMode = settingsRepository.settings.first().inputMode
        val form = GabAiForm(
            calibrationMode = if (inputMode == InputMode.JOYSTICK) FaceOutputMode.JOYSTICK else FaceOutputMode.CURSOR,
            cursor = current.cursor,
            joystick = current.joystick,
            voiceEnabled = current.voiceEnabled,
            matchMode = current.voiceMatchMode,
            activationMode = current.voiceActivationMode,
        )
        begin(GabAiFlow.newCalibration(), form)
    }

    fun startGameProfile(gameId: String? = null) = viewModelScope.launch {
        val form = GabAiForm(gameId = gameId, calibrationProfileId = profileRepository.calibrationProfiles.first().firstOrNull()?.id)
        begin(GabAiFlow.newGameProfile(form), form)
    }

    fun resume() {
        val session = _ui.value.resumable ?: return
        _ui.update { it.copy(session = session, resumable = null) }
        onSessionLoaded(session.form)
    }

    fun editGameProfile(profileId: Long) = viewModelScope.launch {
        val profile = profileRepository.getGameProfile(profileId) ?: return@launch
        val form = GabAiForm(
            gameId = profile.gameId,
            calibrationProfileId = profile.calibrationProfileId,
            screenshotPath = profile.thumbnailPath,
            buttons = ControlJson.decodeButtons(profile.buttonMappingsJson),
            profileName = profile.profileName,
            editingGameProfileId = profile.id,
        )
        begin(GabAiState.ButtonMapping(form.buttons.size), form)
    }

    /** Starting something new replaces any unfinished session. */
    private fun begin(state: GabAiState, form: GabAiForm) {
        _ui.value.resumable?.let { old -> write { gabAiRepository.complete(old.id) } }
        val session = GabAiSession(gabAiRepository.newSessionId(), state, form)
        _ui.update { it.copy(session = session, resumable = null, selectedButtonId = null, message = null) }
        onSessionLoaded(form)
        write { gabAiRepository.save(session) }
    }

    private fun onSessionLoaded(form: GabAiForm) {
        nextButtonId = (form.buttons.maxOfOrNull { it.id } ?: 0) + 1
        loadScreenshot(form.screenshotPath)
    }

    fun back() {
        val ui = _ui.value
        val session = ui.session
        if (session == null) {
            _navigation.trySend(GabAiNavigation.Exit)
            return
        }
        val previous = GabAiFlow.back(session.state, session.form)
        if (previous == null || previous == GabAiState.Welcome) {
            // The session stays saved, so Welcome offers to continue it.
            _ui.update { it.copy(session = null, resumable = if (session.state == GabAiState.ProfileSaved) null else session) }
        } else {
            go(previous)
        }
    }

    // ---- Calibration branch ----

    fun chooseMode(mode: FaceOutputMode) {
        updateForm { it.copy(calibrationMode = mode) }
        viewModelScope.launch {
            settingsRepository.setInputMode(if (mode == FaceOutputMode.JOYSTICK) InputMode.JOYSTICK else InputMode.HEAD_FACE)
        }
        go(GabAiFlow.modeChosen(mode))
    }

    fun setCursor(tuning: CursorTuning) {
        updateForm { it.copy(cursor = tuning) }
        viewModelScope.launch { controlsRepository.setCursorTuning(tuning) }
    }

    fun axisDone(axis: Axis) = go(GabAiFlow.axisDone(axis))

    fun setJoystick(tuning: JoystickTuning) {
        updateForm { it.copy(joystick = tuning) }
        viewModelScope.launch { controlsRepository.setJoystickTuning(tuning) }
    }

    fun setJoystickCenterHere() {
        val pose = faceState.value.pose
        if (pose == null) {
            message("No head found — face the camera, then try again.")
            return
        }
        updateForm { it.copy(joystick = it.joystick.copy(centerPitch = pose.pitch, centerRoll = pose.roll)) }
        viewModelScope.launch { controlsRepository.setJoystickCenter(pose.pitch, pose.roll) }
        message("Center saved.")
    }

    fun joystickDone() = go(GabAiFlow.joystickDone())

    fun setVoice(enabled: Boolean? = null, match: VoiceMatchMode? = null, activation: VoiceActivationMode? = null) {
        updateForm {
            it.copy(
                voiceEnabled = enabled ?: it.voiceEnabled,
                matchMode = match ?: it.matchMode,
                activationMode = activation ?: it.activationMode,
            )
        }
        viewModelScope.launch {
            enabled?.let { controlsRepository.setVoiceEnabled(it) }
            match?.let { controlsRepository.setVoiceMatchMode(it) }
            activation?.let { controlsRepository.setVoiceActivationMode(it) }
            if (enabled == true) voiceCommandManager.refreshPermissions()
        }
    }

    fun voiceDone() = go(GabAiFlow.voiceDone(_ui.value.form))

    /**
     * Passes the gesture once the user performs it, then moves on. The gesture must start while
     * this step is showing, so a move still held from the previous step doesn't count.
     */
    private suspend fun awaitGesture(test: GabAiState.CalibrationGestureTest) {
        val gesture = GabAiState.GESTURE_TEST[test.index]
        if (gesture in _ui.value.form.passedGestures) return
        var released = false
        faceTracking.state.first { face ->
            val active = gesture in face.gesture.active
            if (!active) released = true
            active && released
        }
        updateForm { it.copy(passedGestures = it.passedGestures + gesture) }
        // Long enough to see "Got it!" before the next gesture.
        delay(GESTURE_PASSED_PAUSE_MS)
        val session = _ui.value.session ?: return
        if (session.state == test) go(GabAiFlow.gestureTested(test, session.form))
    }

    /** Next gesture; one not performed yet stays off. */
    fun nextGesture() {
        val session = _ui.value.session ?: return
        val test = session.state as? GabAiState.CalibrationGestureTest ?: return
        go(GabAiFlow.gestureTested(test, session.form))
    }

    fun skipRemainingGestures() = go(GabAiFlow.gestureTestEnded())

    fun retryMissedGestures() = go(GabAiFlow.retryMissedGestures(_ui.value.form))

    fun setGestureSensitivity(gesture: FacialGesture, level: Int) {
        viewModelScope.launch { controlsRepository.setGestureSensitivity(gesture, level) }
    }

    fun setCalibrationName(name: String) = updateForm { it.copy(calibrationName = name) }

    fun saveCalibration() = viewModelScope.launch {
        val form = _ui.value.form
        val name = form.calibrationName.trim().ifEmpty { defaultCalibrationName(form) }
        val current = controlsRepository.config.first()
        val config = current.copy(
            cursor = form.cursor,
            joystick = form.joystick,
            voiceEnabled = form.voiceEnabled,
            voiceMatchMode = form.matchMode,
            voiceActivationMode = form.activationMode,
            enabledGestures = form.passedGestures,
        ).let { config ->
            // An action mapped to a gesture the user couldn't perform would never fire; unmap it.
            config.copy(gestureAssignments = config.gestureAssignments.filterValues(config::isGestureEnabled))
        }
        // It's the active setup from now on, gestures included.
        controlsRepository.replace(config)
        val inputMode = if (form.calibrationMode == FaceOutputMode.JOYSTICK) InputMode.JOYSTICK else InputMode.HEAD_FACE
        val id = profileRepository.saveCalibrationProfile(config.toCalibrationProfile(name, inputMode, id = form.savedCalibrationId ?: 0))
        updateForm { it.copy(calibrationName = name, savedCalibrationId = id, calibrationProfileId = id) }
        go(GabAiFlow.calibrationSaved())
    }

    fun calibrationDone() = finish(GabAiNavigation.Dashboard)

    fun continueToGame() = go(GabAiFlow.continueToGame(_ui.value.form))

    // ---- Game-profile branch ----

    fun chooseGame(game: Game) {
        updateForm { it.copy(gameId = game.id) }
        go(GabAiFlow.gameChosen())
    }

    fun chooseCalibration(id: Long) = updateForm { it.copy(calibrationProfileId = id) }

    fun confirmCalibration() {
        val form = _ui.value.form
        val chosen = form.calibrationProfileId
        if (chosen == null || calibrationProfiles.value.none { it.id == chosen }) {
            message("Pick a calibration profile, or make a new one.")
            return
        }
        viewModelScope.launch {
            calibrationProfiles.value.firstOrNull { it.id == chosen }?.let { profile ->
                // Button mapping previews with the calibration this game will use.
                controlsRepository.applyCalibration(profile)
            }
        }
        go(GabAiFlow.calibrationConfirmed())
    }

    /** No calibration yet (or the user wants a new one): calibrate, then come back here. */
    fun calibrateForThisGame() {
        val session = _ui.value.session ?: return
        viewModelScope.launch {
            val current = controlsRepository.config.first()
            val updated = session.form.copy(
                continueToGame = true,
                passedGestures = emptySet(),
                cursor = current.cursor,
                joystick = current.joystick,
                voiceEnabled = current.voiceEnabled,
                matchMode = current.voiceMatchMode,
                activationMode = current.voiceActivationMode,
            )
            persist(session.copy(state = GabAiFlow.newCalibration(), form = updated))
        }
    }

    fun importScreenshot(uri: Uri) = viewModelScope.launch {
        val path = gabAiRepository.importScreenshot(uri)
        if (path == null) {
            message("That file couldn't be opened as an image. Try another screenshot.")
        } else {
            updateForm { it.copy(screenshotPath = path) }
            loadScreenshot(path)
            detectButtons(path)
        }
    }

    /** Pre-places the buttons the backend model finds. Never overwrites buttons the user already placed. */
    private suspend fun detectButtons(path: String) {
        val gameId = _ui.value.form.gameId ?: return
        if (!hudDetector.isAvailable || _ui.value.form.buttons.isNotEmpty()) return
        _ui.update { it.copy(detectingButtons = true) }
        val found = runCatching { hudDetector.detect(path, gameId) }
        _ui.update { it.copy(detectingButtons = false) }
        // The user may have picked another screenshot, or started placing buttons, while this ran.
        if (_ui.value.form.screenshotPath != path || _ui.value.form.buttons.isNotEmpty()) return
        found.onSuccess { detected ->
            if (detected.isEmpty()) {
                message("No buttons found on this screenshot — you can place them yourself next.")
                return
            }
            val buttons = detectedToButtons(detected, nextButtonId)
            nextButtonId += buttons.size
            editButtons { buttons }
            val stick = if (buttons.any { it.trigger == ButtonTrigger.MOVEMENT }) ", including the movement joystick" else ""
            message("Found ${buttons.size} button${if (buttons.size == 1) "" else "s"}$stick. Check them on the next step.")
        }.onFailure {
            message("Couldn't reach button detection — you can place buttons yourself next.")
        }
    }

    fun useBlankScreen() {
        updateForm { it.copy(screenshotPath = null) }
        loadScreenshot(null)
        screenshotDone()
    }

    fun screenshotDone() = go(GabAiFlow.screenshotDone(_ui.value.form))

    fun addButton(x: Float, y: Float) {
        val button = MappedButton(nextButtonId++, "Button ${_ui.value.form.buttons.size + 1}", x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        editButtons { it + button }
        _ui.update { it.copy(selectedButtonId = button.id) }
    }

    /** Hands-free placement: drop a button wherever the head pointer is. */
    fun addButtonAtPointer() {
        val face = faceState.value
        if (!face.hasFace) {
            message("No head found — face the camera, then say \"place\" again.")
            return
        }
        addButton(face.cursor.x, face.cursor.y)
    }

    fun selectButton(id: Int?) = _ui.update { it.copy(selectedButtonId = id, capturingLabel = false) }

    fun moveButton(id: Int, x: Float, y: Float) =
        editButtons { list -> list.map { if (it.id == id) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it } }

    fun nudgeSelected(dx: Float, dy: Float) {
        val id = _ui.value.selectedButtonId ?: return
        val button = _ui.value.form.buttons.firstOrNull { it.id == id } ?: return
        moveButton(id, button.x + dx, button.y + dy)
    }

    fun renameButton(id: Int, label: String) =
        editButtons { list -> list.map { if (it.id == id) it.copy(label = label) else it } }

    fun deleteSelected() {
        val id = _ui.value.selectedButtonId ?: return
        editButtons { list -> list.filterNot { it.id == id } }
        _ui.update { it.copy(selectedButtonId = null, capturingLabel = false) }
    }

    fun captureLabelByVoice() {
        if (_ui.value.selectedButtonId == null) {
            message("Select a button first.")
            return
        }
        _ui.update { it.copy(capturingLabel = true) }
        message("Say \"assign\" and the name, like \"assign skill one\".")
    }

    // ---- Spoken assignment ----

    /** What "assign <words>" would change right now, if anything. */
    private sealed interface DictationTarget {
        data class Label(val buttonId: Int) : DictationTarget
        data class Trigger(val buttonIndex: Int) : DictationTarget
    }

    /** What the last spoken assignment replaced, so "retry" can put it back. */
    private sealed interface Assignment {
        data class Label(val buttonId: Int, val previous: String) : Assignment
        data class Trigger(val buttonIndex: Int, val previous: ButtonTrigger?) : Assignment
    }

    private var lastAssignment: Assignment? = null

    private fun dictationTarget(ui: GabAiUiState): DictationTarget? = when (val state = ui.state) {
        is GabAiState.ButtonMapping -> ui.selectedButtonId?.let { DictationTarget.Label(it) }
        is GabAiState.TriggerAssignment -> DictationTarget.Trigger(state.buttonIndex)
        else -> null
    }

    private fun assignByVoice(words: String) {
        val buttons = _ui.value.form.buttons
        when (val target = dictationTarget(_ui.value) ?: return) {
            is DictationTarget.Label -> {
                val button = buttons.firstOrNull { it.id == target.buttonId } ?: return
                lastAssignment = Assignment.Label(button.id, button.label)
                val label = words.replaceFirstChar { it.uppercase() }
                renameButton(button.id, label)
                _ui.update { it.copy(capturingLabel = false) }
                message("Named it \"$label\". Say \"retry\" to try again.")
            }
            is DictationTarget.Trigger -> {
                val button = buttons.getOrNull(target.buttonIndex) ?: return
                lastAssignment = Assignment.Trigger(target.buttonIndex, button.trigger)
                setTrigger(target.buttonIndex, ButtonTrigger(TriggerType.VOICE, words))
                message("Say \"$words\" to press ${button.label}. Say \"retry\" to try again.")
            }
        }
    }

    private fun retryAssignment() {
        val target = dictationTarget(_ui.value) ?: return
        when (val last = lastAssignment) {
            is Assignment.Label -> if (target == DictationTarget.Label(last.buttonId)) renameButton(last.buttonId, last.previous)
            is Assignment.Trigger -> if (target == DictationTarget.Trigger(last.buttonIndex)) setTrigger(last.buttonIndex, last.previous)
            null -> Unit
        }
        lastAssignment = null
        if (target is DictationTarget.Label) {
            _ui.update { it.copy(capturingLabel = true) }
            message("Listening again — say \"assign\" and the name.")
        } else {
            message("Listening again — say \"assign\" and what you'll say to press it.")
        }
    }

    fun buttonsDone() {
        if (_ui.value.form.buttons.isEmpty()) {
            message("Place at least one button first.")
            return
        }
        _ui.update { it.copy(selectedButtonId = null, capturingLabel = false) }
        go(GabAiFlow.buttonsDone(_ui.value.form))
    }

    fun setTrigger(buttonIndex: Int, trigger: ButtonTrigger?) =
        editButtons { list -> list.mapIndexed { i, b -> if (i == buttonIndex) b.copy(trigger = trigger) else b } }

    fun triggerDone() {
        val state = _ui.value.state as? GabAiState.TriggerAssignment ?: return
        val button = _ui.value.form.buttons.getOrNull(state.buttonIndex)
        if (button?.trigger == null) {
            message("Choose how to press \"${button?.label}\" first.")
            return
        }
        go(GabAiFlow.triggerDone(state))
    }

    fun setProfileName(name: String) = updateForm { it.copy(profileName = name) }

    fun saveGameProfile() = viewModelScope.launch {
        val form = _ui.value.form
        val game = Game.byId(form.gameId) ?: return@launch
        val existing = form.editingGameProfileId?.let { profileRepository.getGameProfile(it) }
        val name = form.profileName.trim().ifEmpty { defaultProfileName(game) }
        val profile = GameProfile(
            id = form.editingGameProfileId ?: 0,
            gameId = game.id,
            gameName = game.displayName,
            profileName = name,
            // A calibration deleted meanwhile would break the foreign key; save without the link instead.
            calibrationProfileId = form.calibrationProfileId?.takeIf { profileRepository.getCalibrationProfile(it) != null },
            buttonMappingsJson = ControlJson.encodeButtons(form.buttons),
            thumbnailPath = form.screenshotPath,
            createdAt = existing?.createdAt ?: 0,
            updatedAt = 0,
            remoteId = existing?.remoteId,
            lastSyncedAt = existing?.lastSyncedAt,
        )
        val id = profileRepository.saveGameProfile(profile)
        updateForm { it.copy(profileName = name, savedGameProfileId = id) }
        go(GabAiFlow.profileSaved())
        _ui.value.session?.let { done -> write { gabAiRepository.complete(done.id) } }
    }

    fun goToDashboard() = finish(GabAiNavigation.Dashboard)

    fun playNow() {
        val form = _ui.value.form
        val gameId = form.gameId ?: return
        val profileId = form.savedGameProfileId ?: return
        finish(GabAiNavigation.Play(gameId, profileId))
    }

    /** Another game profile, keeping the same calibration profile. */
    fun createAnother() = viewModelScope.launch {
        val calibration = _ui.value.form.calibrationProfileId
        val form = GabAiForm(calibrationProfileId = calibration)
        begin(GabAiFlow.createAnother(), form)
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    // ---- plumbing ----

    private fun finish(navigation: GabAiNavigation) {
        _ui.value.session?.let { done -> write { gabAiRepository.complete(done.id) } }
        _ui.update { it.copy(session = null, resumable = null) }
        _navigation.trySend(navigation)
    }

    private fun go(state: GabAiState) {
        val session = _ui.value.session ?: return
        persist(session.copy(state = state))
    }

    private fun updateForm(transform: (GabAiForm) -> GabAiForm) {
        val session = _ui.value.session ?: return
        persist(session.copy(form = transform(session.form)))
    }

    /** Button edits also keep ButtonMapping's count in step. */
    private fun editButtons(transform: (List<MappedButton>) -> List<MappedButton>) {
        val session = _ui.value.session ?: return
        val buttons = transform(session.form.buttons)
        val state = if (session.state is GabAiState.ButtonMapping) GabAiState.ButtonMapping(buttons.size) else session.state
        persist(session.copy(state = state, form = session.form.copy(buttons = buttons)))
    }

    private fun persist(session: GabAiSession) {
        _ui.update { it.copy(session = session, message = null) }
        write { gabAiRepository.save(session) }
    }

    private fun write(block: suspend () -> Unit) {
        writes.trySend(block)
    }

    private fun message(text: String) = _ui.update { it.copy(message = text) }

    private fun loadScreenshot(path: String?) {
        if (path == loadedScreenshotPath && (path == null || _ui.value.screenshot != null)) return
        loadedScreenshotPath = path
        viewModelScope.launch {
            val bitmap = gabAiRepository.loadScreenshot(path)?.asImageBitmap()
            if (loadedScreenshotPath == path) _ui.update { it.copy(screenshot = bitmap) }
        }
    }

    private companion object {
        const val GESTURE_PASSED_PAUSE_MS = 1_200L
    }

    private fun defaultCalibrationName(form: GabAiForm): String =
        "My ${form.calibrationMode.label.lowercase()} setup ${calibrationProfiles.value.size + 1}"

    private fun defaultProfileName(game: Game): String = "${game.displayName} profile ${gameProfiles.value.count { it.gameId == game.id } + 1}"
}
