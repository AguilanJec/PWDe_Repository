package com.pwde.app.ui.eyetracking

import android.os.SystemClock
import android.view.TextureView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType
import camp.visual.eyedid.gazetracker.metrics.state.TrackingState
import com.pwde.app.sensors.eyedid.EyedidCalibration
import com.pwde.app.sensors.eyedid.EyedidFaceTrackingManager
import com.pwde.app.sensors.eyedid.EyedidFrame
import com.pwde.app.sensors.eyedid.EyedidPointer
import com.pwde.app.sensors.eyedid.EyedidStatus
import com.pwde.app.sensors.eyedid.EyeControlFaceTrackingManager
import com.pwde.app.sensors.eyedid.GazeLostReason
import com.pwde.app.sensors.eyedid.GazePoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the eye-tracking screen shows, as one immutable snapshot. A single state object rather
 * than a flow per field, because the screen is a diagnostic panel that reads it as a whole.
 *
 * Points are in **device-screen pixels** (what the SDK reports). The screen converts them into
 * fractions of the box it has measured — it is the only place that knows how big that box is.
 */
data class EyeTrackingView(
    val status: EyedidStatus = EyedidStatus.Idle,
    val dotPx: GazePoint? = null,
    val lostReason: GazeLostReason? = GazeLostReason.NOT_TRACKING,
    val gazePx: GazePoint? = null,
    val tracking: String = "not started",
    val faceScore: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val blinking: Boolean = false,
    val calibrating: Boolean = false,
    val calibrationDotPx: GazePoint? = null,
    val calibrationProgress: Float = 0f,
    val calibrated: Boolean = false,
    val note: String? = null,
) {
    val trackingNow: Boolean get() = status == EyedidStatus.Tracking

    /** Calibration can only start once the SDK is actually tracking. */
    val canCalibrate: Boolean get() = trackingNow && !calibrating

    val lostLabel: String get() = if (dotPx != null) "tracking" else reasonLabel(lostReason)

    companion object {
        fun reasonLabel(reason: GazeLostReason?): String = when (reason) {
            null -> "tracking"
            GazeLostReason.NOT_TRACKING -> "not tracking"
            GazeLostReason.NO_FACE -> "no face"
            GazeLostReason.EYES_LOST -> "eyes lost"
            GazeLostReason.SCREEN_NOT_MEASURED -> "preview not measured yet"
        }
    }
}

/**
 * Reports what the gaze SDK is doing, for the diagnostics rig.
 *
 * It owns no engine of its own. `GazeTracker` is a process-wide singleton, so a second instance would
 * fight the one eye control is using for the same camera — the rig drives the *shared* engine through
 * the same reference count the control pipeline uses, and is just another consumer of its frames.
 *
 * The pointer here works in raw screen pixels, unlike the control pipeline's, which normalises to
 * screen fractions first. That is deliberate: the rig exists to show the numbers the SDK produced.
 */
class EyeTrackingViewModel(private val manager: EyeControlFaceTrackingManager) : ViewModel() {
    private val eyes: EyedidFaceTrackingManager = manager.eyes
    private val pointer = EyedidPointer()

    private val _view = MutableStateFlow(
        EyeTrackingView(status = eyes.status.value, calibrated = eyes.calibrated.value),
    )
    val view: StateFlow<EyeTrackingView> = _view.asStateFlow()

    /** Blank when `pwde.eyedid.licenseKey` is missing from `local.properties`. */
    val hasLicenceKey: Boolean = eyes.hasLicenseKey

    val sdkVersion: String = eyes.sdkVersion

    private var settleJob: Job? = null

    init {
        // Even in another input mode, the rig wants the gaze: it is the point of the screen.
        eyes.acquire(HOLDER)
        viewModelScope.launch {
            eyes.status.collect { status ->
                _view.update { it.copy(status = status) }
                onStatus(status)
            }
        }
        viewModelScope.launch { eyes.calibrated.collect { done -> _view.update { it.copy(calibrated = done) } } }
        viewModelScope.launch { eyes.frames.collect(::onFrame) }
        viewModelScope.launch { eyes.calibration.collect(::onCalibration) }
    }

    private fun onStatus(status: EyedidStatus) {
        when (status) {
            EyedidStatus.Tracking -> pointer.trackingStarted()
            // Ready is where init lands, and where a stop lands. The dot must not survive either.
            EyedidStatus.Ready, EyedidStatus.Idle, EyedidStatus.Initializing, EyedidStatus.NoLicenceKey,
            is EyedidStatus.Failed, is EyedidStatus.Interrupted -> pointer.trackingStopped()
        }
    }

    private fun onFrame(frame: EyedidFrame) {
        // Only a SUCCESS frame carries a usable gaze point; the other two states leave x/y stale.
        val reading = if (frame.trackingState == TrackingState.SUCCESS) {
            GazePoint(frame.gazeX, frame.gazeY)
        } else null
        val faceVisible = frame.trackingState != TrackingState.FACE_MISSING
        // Our own clock, not the SDK's frame timestamp: the hold window is measured in milliseconds
        // and the SDK's timestamp unit is undocumented (see EyedidFaceTrackingManager).
        val dot = pointer.update(reading, faceVisible, SystemClock.uptimeMillis())

        _view.update {
            it.copy(
                dotPx = dot,
                lostReason = pointer.reason,
                gazePx = reading,
                tracking = trackingLabel(frame.trackingState),
                faceScore = frame.faceScore,
                pitch = frame.pitch,
                yaw = frame.yaw,
                roll = frame.roll,
                blinking = frame.blinking,
            )
        }
    }

    private fun onCalibration(event: EyedidCalibration) {
        when (event) {
            is EyedidCalibration.ShowPoint -> {
                _view.update {
                    it.copy(
                        calibrating = true,
                        calibrationDotPx = GazePoint(event.x, event.y),
                        calibrationProgress = 0f,
                    )
                }
                settleJob?.cancel()
                settleJob = viewModelScope.launch {
                    // The SDK will not move to the next point until it is told the dot is on screen
                    // and the user has had time to look at it. Without this the sweep stalls here.
                    delay(CALIBRATION_SETTLE_MS)
                    eyes.collectCalibrationSamples()
                }
            }

            is EyedidCalibration.Progress ->
                _view.update { it.copy(calibrationProgress = event.progress) }

            is EyedidCalibration.Finished -> {
                settleJob?.cancel()
                // The engine has already persisted the blob; this only reports it.
                _view.update {
                    it.copy(
                        calibrating = false,
                        calibrationDotPx = null,
                        calibrationProgress = 0f,
                        calibrated = true,
                        note = "Calibrated — ${event.data.size} values saved on this device.",
                    )
                }
            }

            is EyedidCalibration.Canceled -> {
                settleJob?.cancel()
                _view.update {
                    it.copy(
                        calibrating = false,
                        calibrationDotPx = null,
                        calibrationProgress = 0f,
                        note = if (event.data.isEmpty()) {
                            "Calibration cancelled before any point completed."
                        } else {
                            "Calibration stopped early — kept the ${event.data.size} values collected."
                        },
                    )
                }
            }
        }
    }

    /** Hands the SDK its preview surface. The engine attaches it whenever it exists. */
    fun attachPreview(preview: TextureView) = eyes.setCameraPreview(preview)

    fun startTracking() = eyes.startTracking()

    fun stopTracking() = eyes.stopTracking()

    fun calibrate() {
        // The SDK has no "calibration started" callback — its first word is the first dot — so the
        // pending state comes from the request. Waiting for that callback left the sweep running
        // with nothing on screen to show for it.
        if (!eyes.startCalibration(CalibrationModeType.FIVE_POINT)) {
            _view.update { it.copy(note = "Calibration could not start — start tracking first.") }
            return
        }
        settleJob?.cancel()
        _view.update {
            it.copy(
                calibrating = true,
                calibrationDotPx = null,
                calibrationProgress = 0f,
                // The gaze dot would be misleading during a sweep: the SDK is not tracking, it is measuring.
                dotPx = null,
                note = "Look at each dot until the ring fills. Move only your eyes — keep your head still.",
            )
        }
    }

    fun cancelCalibration() = eyes.cancelCalibration()

    fun forgetCalibration() {
        eyes.forgetCalibration()
        _view.update { it.copy(calibrated = false, note = "Saved calibration deleted. Calibrate again to use it.") }
    }

    private fun trackingLabel(state: TrackingState): String = when (state) {
        TrackingState.SUCCESS -> "gaze + face"
        TrackingState.GAZE_MISSING -> "face only, no gaze"
        TrackingState.FACE_MISSING -> "no face"
    }

    override fun onCleared() {
        settleJob?.cancel()
        eyes.release(HOLDER)
        super.onCleared()
    }

    companion object {
        /**
         * How long the dot is on screen before samples are collected. The SDK documentation uses
         * 1000 ms and warns that the user must have time to recognise the point; collecting too
         * early calibrates against where the eye was still travelling.
         */
        const val CALIBRATION_SETTLE_MS = 1000L

        /** This screen's name in the engine's holder set. */
        private const val HOLDER = "eye-diagnostics"
    }
}
