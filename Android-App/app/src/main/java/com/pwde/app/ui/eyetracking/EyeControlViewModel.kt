package com.pwde.app.ui.eyetracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.sensors.eyedid.EyedidCalibration
import com.pwde.app.sensors.eyedid.EyedidFaceTrackingManager
import com.pwde.app.sensors.eyedid.EyedidStatus
import com.pwde.app.sensors.eyedid.EyeControlFaceTrackingManager
import com.pwde.app.sensors.eyedid.GazePoint
import com.pwde.app.sensors.face.FaceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the eye-control screen shows, as one snapshot. */
data class EyeControlView(
    val status: EyedidStatus = EyedidStatus.Idle,
    val calibrated: Boolean = false,
    /** False when this build has no `pwde.eyedid.licenseKey`, so eye control cannot work at all. */
    val hasLicenseKey: Boolean = true,
    /** Whether eye control is the input mode the rest of the app is using right now. */
    val isEyeMode: Boolean = false,
    /** The gaze engine's own output, for the live "where you are looking" check. */
    val face: FaceState = FaceState(),
    val calibrating: Boolean = false,
    /** Where the SDK wants the dot, in device-screen pixels. */
    val dotPx: GazePoint? = null,
    val progress: Float = 0f,
    /** 1-based, for "point 2 of 5". */
    val pointNumber: Int = 0,
    val note: String? = null,
) {
    /** Calibration needs the SDK actually tracking; there is nothing to measure before that. */
    val canCalibrate: Boolean get() = status == EyedidStatus.Tracking && !calibrating

    val advice: String? get() = EyedidText.advice(status)

    /** A gaze the screen can honestly show as "pointing at something" right now. */
    val gazeVisible: Boolean get() = face.dwell != null || face.hasFace
}

/**
 * Drives the calibration of the gaze engine the control pipeline uses.
 *
 * It holds the engine for as long as the screen is open ([EyeControlFaceTrackingManager.eyes]'
 * reference count), because calibrating requires the camera and the tracker — the screen is not a
 * passive viewer of someone else's session.
 *
 * The blob is not saved here: [com.pwde.app.sensors.eyedid.EyedidFaceTrackingManager] persists every
 * sweep the SDK reports, so a calibration cannot be lost by a screen being closed at the wrong moment.
 */
class EyeControlViewModel(
    private val manager: EyeControlFaceTrackingManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val eyes = manager.eyes

    private val _view = MutableStateFlow(
        EyeControlView(status = eyes.status.value, hasLicenseKey = eyes.hasLicenseKey, calibrated = eyes.calibrated.value),
    )
    val view: StateFlow<EyeControlView> = _view.asStateFlow()

    private var settleJob: Job? = null

    /** Complains if a sweep never gets a first dot, so "stuck" is never silent. */
    private var firstPointJob: Job? = null

    init {
        // The screen is a holder in its own right: the engine tracks while it is open even when eye
        // control is not the selected input mode, which is what lets the user calibrate before
        // committing to it.
        eyes.acquire(HOLDER)

        viewModelScope.launch {
            eyes.status.collect { status ->
                _view.update { it.copy(status = status) }
                onStatus(status)
            }
        }
        viewModelScope.launch { eyes.calibrated.collect { done -> _view.update { it.copy(calibrated = done) } } }
        viewModelScope.launch { eyes.state.collect { face -> _view.update { it.copy(face = face) } } }
        // Events, not state: a SharedFlow with no replay drops anything emitted before the first
        // subscriber, so this subscription is what makes the whole sweep work. Without it there is
        // no dot, no progress and no samples collected — the screen just sits on "Getting ready…".
        viewModelScope.launch { eyes.calibration.collect(::onCalibration) }
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.inputMode == InputMode.EYE }
                .distinctUntilChanged()
                .collect { eyeMode -> _view.update { it.copy(isEyeMode = eyeMode) } }
        }
    }

    private fun onStatus(status: EyedidStatus) {
        // A sweep cannot continue through the tracker going away, and leaving the overlay up would
        // look like the app had hung.
        if (status != EyedidStatus.Tracking && _view.value.calibrating) {
            settleJob?.cancel()
            _view.update {
                it.copy(
                    calibrating = false,
                    dotPx = null,
                    progress = 0f,
                    note = "Calibration stopped — the eye engine is no longer tracking.",
                )
            }
        }
    }

    /**
     * Starts the sweep. The dots are drawn by the screen at the position the SDK asks for.
     *
     * The pending state is set **here**, from the request, rather than from a callback: the SDK's
     * first word about a sweep is the first dot, so a screen that waited for a "started" event
     * would show nothing at all — and the sweep would run to completion invisibly.
     */
    fun calibrate() {
        if (!eyes.startCalibration(CalibrationModeType.FIVE_POINT)) {
            _view.update { it.copy(note = "Calibration could not start yet — give the eye engine a moment, then try again.") }
            return
        }
        settleJob?.cancel()
        _view.update {
            it.copy(
                calibrating = true,
                dotPx = null,
                progress = 0f,
                pointNumber = 0,
                note = "Look at each dot and hold your gaze on it until the ring closes. Move only your eyes — keep your head still.",
            )
        }
        armFirstPointWatchdog()
    }

    /**
     * The SDK's first word about a sweep is its first dot, so a sweep that gets under way and then
     * says nothing is indistinguishable from a dead button. This turns that silence into something
     * the user can act on. It disarms itself: it only speaks if the sweep is still waiting for point
     * one, so a normal sweep makes it a no-op however long the user takes on the later dots.
     */
    private fun armFirstPointWatchdog() {
        firstPointJob?.cancel()
        firstPointJob = viewModelScope.launch {
            delay(FIRST_POINT_TIMEOUT_MS)
            if (_view.value.calibrating && _view.value.pointNumber == 0) {
                _view.update {
                    it.copy(
                        note = "The eye engine accepted the calibration but has not offered a dot. Face " +
                            "the camera with your whole face in view, make sure nothing else is using the " +
                            "camera, then stop and try again.",
                    )
                }
            }
        }
    }

    fun cancelCalibration() = eyes.cancelCalibration()

    fun forgetCalibration() {
        eyes.forgetCalibration()
        _view.update { it.copy(calibrated = false, note = "Saved calibration deleted.") }
    }

    /** Makes eye control the input mode everything else uses. */
    fun useEyeControl() {
        viewModelScope.launch {
            settingsRepository.setInputMode(InputMode.EYE)
            _view.update { it.copy(note = "Eye control is on. Look at a button and hold your gaze to press it.") }
        }
    }

    fun dismissNote() = _view.update { it.copy(note = null) }

    private fun onCalibration(event: EyedidCalibration) {
        when (event) {
            is EyedidCalibration.ShowPoint -> {
                _view.update {
                    it.copy(
                        calibrating = true,
                        dotPx = GazePoint(event.x, event.y),
                        progress = 0f,
                        pointNumber = it.pointNumber + 1,
                    )
                }
                // The SDK will not move on until it is told the dot is on screen and the user has had
                // time to look at it; skipping this call stalls the sweep on the first dot.
                settleJob?.cancel()
                settleJob = viewModelScope.launch {
                    delay(SETTLE_MS)
                    eyes.collectCalibrationSamples()
                }
            }

            is EyedidCalibration.Progress -> _view.update { it.copy(progress = event.progress) }

            is EyedidCalibration.Finished -> {
                settleJob?.cancel()
                _view.update {
                    it.copy(
                        calibrating = false,
                        dotPx = null,
                        progress = 0f,
                        calibrated = true,
                        note = "Calibrated. Check the dot below follows your eyes, then turn eye control on.",
                    )
                }
            }

            is EyedidCalibration.Canceled -> {
                settleJob?.cancel()
                // The engine has already kept whatever the SDK reported, so a partial sweep is not
                // wasted effort — the user just calibrates again to improve it.
                _view.update {
                    it.copy(
                        calibrating = false,
                        dotPx = null,
                        progress = 0f,
                        note = if (event.data.isEmpty()) {
                            "Calibration stopped before any point was collected."
                        } else {
                            "Calibration stopped early. What was collected is saved; calibrate again for a better fit."
                        },
                    )
                }
            }
        }
    }

    override fun onCleared() {
        settleJob?.cancel()
        firstPointJob?.cancel()
        eyes.release(HOLDER)
        super.onCleared()
    }

    companion object {
        /** How long the dot is on screen before samples are collected. The SDK docs use 1000 ms. */
        const val SETTLE_MS = 1000L

        /**
         * How long to wait for a sweep's first dot before saying something is wrong. The SDK asks
         * for the first point essentially immediately, so this is a generous ceiling, not a delay.
         */
        const val FIRST_POINT_TIMEOUT_MS = 4_000L

        /** This screen's name in the engine's holder set; see [EyedidFaceTrackingManager.acquire]. */
        private const val HOLDER = "eye-control-screen"
    }
}
