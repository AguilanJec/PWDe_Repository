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
import com.pwde.app.data.local.CalibrationProfile
import com.pwde.app.data.local.ControlJson
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.GameProfile
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.local.toCalibrationProfile
import com.pwde.app.data.model.ButtonTrigger
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.data.model.Game
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.voice.VoiceCommandManager
import com.pwde.app.ui.common.FaceTrackingViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    /** Waiting for the next thing the user says to become the selected button's name. */
    val capturingLabel: Boolean = false,
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
) : FaceTrackingViewModel(faceTracking) {
    private val _ui = MutableStateFlow(GabAiUiState())
    val ui: StateFlow<GabAiUiState> = _ui.asStateFlow()

    val calibrationProfiles: StateFlow<List<CalibrationProfile>> = profileRepository.calibrationProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val gameProfiles: StateFlow<List<GameProfile>> = profileRepository.gameProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _navigation = Channel<GabAiNavigation>(Channel.BUFFERED)
    val navigation: Flow<GabAiNavigation> = _navigation.receiveAsFlow()

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
            // "Rename by voice": the next thing said becomes the selected button's name.
            voiceCommandManager.results.collect { result ->
                if (!result.isFinal || !_ui.value.capturingLabel) return@collect
                val id = _ui.value.selectedButtonId ?: return@collect
                val label = result.transcript.trim().replaceFirstChar { it.uppercase() }
                _ui.update { it.copy(capturingLabel = false) }
                renameButton(id, label)
            }
        }
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

    fun setCalibrationName(name: String) = updateForm { it.copy(calibrationName = name) }

    fun saveCalibration() = viewModelScope.launch {
        val form = _ui.value.form
        val name = form.calibrationName.trim().ifEmpty { defaultCalibrationName(form) }
        val config = controlsRepository.config.first().copy(
            cursor = form.cursor,
            joystick = form.joystick,
            voiceEnabled = form.voiceEnabled,
            voiceMatchMode = form.matchMode,
            voiceActivationMode = form.activationMode,
        )
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

    private fun defaultCalibrationName(form: GabAiForm): String =
        "My ${form.calibrationMode.label.lowercase()} setup ${calibrationProfiles.value.size + 1}"

    private fun defaultProfileName(game: Game): String = "${game.displayName} profile ${gameProfiles.value.count { it.gameId == game.id } + 1}"
}
