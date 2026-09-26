package com.pwde.app.sensors.eyedid

import android.content.Context
import android.util.Log
import android.view.TextureView
import camp.visual.eyedid.gazetracker.GazeTracker
import camp.visual.eyedid.gazetracker.callback.CalibrationCallback
import camp.visual.eyedid.gazetracker.callback.InitializationCallback
import camp.visual.eyedid.gazetracker.callback.StatusCallback
import camp.visual.eyedid.gazetracker.callback.TrackingCallback
import camp.visual.eyedid.gazetracker.constant.AccuracyCriteria
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType
import camp.visual.eyedid.gazetracker.constant.GazeTrackerOptions
import camp.visual.eyedid.gazetracker.constant.InitializationErrorType
import camp.visual.eyedid.gazetracker.constant.StatusErrorType
import camp.visual.eyedid.gazetracker.metrics.BlinkInfo
import camp.visual.eyedid.gazetracker.metrics.FaceInfo
import camp.visual.eyedid.gazetracker.metrics.GazeInfo
import camp.visual.eyedid.gazetracker.metrics.UserStatusInfo
import camp.visual.eyedid.gazetracker.metrics.state.TrackingState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the engine is in the SDK's lifecycle, plus the reason whenever it is not working. */
sealed interface EyedidStatus {
    /** Never started, or released. */
    data object Idle : EyedidStatus

    /** The licence is being checked against VisualCamp's server. Needs a network connection. */
    data object Initializing : EyedidStatus

    /** Licence accepted. Ready to track, not tracking yet. */
    data object Ready : EyedidStatus

    /** Camera open, gaze flowing. */
    data object Tracking : EyedidStatus

    /** No key in `local.properties`, so there is nothing to authenticate with. */
    data object NoLicenceKey : EyedidStatus

    /**
     * The SDK refused to start. [advice] names the cause and the fix rather than repeating the enum
     * name — these failures are almost all configuration, and the raw code is unreadable.
     */
    data class Failed(val error: InitializationErrorType, val advice: String) : EyedidStatus

    /**
     * Tracking stopped on its own: the camera was taken by another app, or failed to open. Distinct
     * from [Failed] because the licence is fine and retrying usually works.
     */
    data class Interrupted(val error: StatusErrorType, val advice: String) : EyedidStatus
}

/**
 * One SDK frame, exactly as reported. Interpreting it — smoothing it, holding it through a blink —
 * is [EyedidPointer]'s job, which is pure and unit-tested; nothing here decides anything.
 *
 * Coordinates are device-screen pixels with the origin at the screen's top-left, per the SDK docs.
 */
data class EyedidFrame(
    val timestampMs: Long,
    val gazeX: Float,
    val gazeY: Float,
    val trackingState: TrackingState,
    val faceScore: Float,
    val pitch: Float,
    val yaw: Float,
    val roll: Float,
    val blinking: Boolean,
)

/** Milestones of the SDK-driven calibration sweep. */
sealed interface EyedidCalibration {
    /**
     * Draw the dot at these device-screen pixels, give the user a moment to look at it, then call
     * [EyedidGazeEngine.collectSamples]. The SDK will not advance to the next point until it has
     * been told the dot is on screen, so skipping that call stalls calibration on the first dot.
     *
     * The dot must be drawn where this says: the SDK measures the user's eyes against the position
     * it asked for, so a dot drawn somewhere more convenient produces a calibration that is
     * confidently wrong.
     *
     * There is deliberately no "started" member. `CalibrationCallback` has exactly four methods —
     * progress, next point, finished, canceled — so a "started" event would never be emitted, and
     * any screen waiting for it before showing the dot would show nothing at all while the sweep ran
     * invisibly. The first word the SDK says about a sweep is [ShowPoint].
     */
    data class ShowPoint(val x: Float, val y: Float) : EyedidCalibration

    /** 0..1 for the point currently being collected. */
    data class Progress(val progress: Float) : EyedidCalibration

    /** Done. [data] is the opaque blob to persist and hand back via [EyedidGazeEngine.applyCalibration]. */
    data class Finished(val data: List<Double>) : EyedidCalibration

    /** Stopped early. [data] holds whichever points did complete, and is empty if none did. */
    data class Canceled(val data: List<Double>) : EyedidCalibration
}

/**
 * Thin lifecycle wrapper around the SeeSo/Eyedid `GazeTracker`.
 *
 * The SDK is callback-driven and delivers those callbacks on its own threads; everything here is a
 * flow instead, so no UI code ever runs on an SDK thread. It owns at most one tracker, and the
 * tracker holds the front camera — [release] must be called when the screen goes away, because the
 * SDK cannot be re-initialised while an old instance is still alive.
 *
 * Several methods are no-ops before the licence check has finished (a documented SDK behaviour:
 * `startTracking()` before init simply does nothing). [wantTracking] exists so that an early
 * `startTracking()` is replayed from `onInitialized` instead of being silently lost.
 */
class EyedidGazeEngine {

    private var tracker: GazeTracker? = null
    private var initializing = false
    private var wantTracking = false

    /** Held so the preview can be attached before or after the licence check completes. */
    private var preview: TextureView? = null

    private val _status = MutableStateFlow<EyedidStatus>(EyedidStatus.Idle)
    val status: StateFlow<EyedidStatus> = _status.asStateFlow()

    /** Gaze frames. At most one is buffered: a stale frame is worthless, the newest one is not. */
    private val _frames = MutableSharedFlow<EyedidFrame>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<EyedidFrame> = _frames.asSharedFlow()

    private val _calibration = MutableSharedFlow<EyedidCalibration>(extraBufferCapacity = 4)
    val calibration: SharedFlow<EyedidCalibration> = _calibration.asSharedFlow()

    /** The SDK's own version string, for the diagnostics panel. */
    val sdkVersion: String get() = GazeTracker.getVersionName()

    private val initializationCallback = object : InitializationCallback {
        override fun onInitialized(tracker: GazeTracker?, error: InitializationErrorType) {
            initializing = false
            if (tracker == null) {
                _status.value = EyedidStatus.Failed(error, describeInitialization(error))
                Log.w(TAG, "init failed: $error")
                return
            }
            this@EyedidGazeEngine.tracker = tracker
            tracker.setTrackingCallback(trackingCallback)
            tracker.setCalibrationCallback(calibrationCallback)
            tracker.setStatusCallback(statusCallback)
            if (!tracker.setTrackingFPS(TRACKING_FPS)) {
                Log.w(TAG, "SDK refused ${TRACKING_FPS}fps; keeping its default")
            }
            // The preview view usually exists before the licence check finishes, but not always.
            preview?.let { tracker.setCameraPreview(it) }
            _status.value = EyedidStatus.Ready
            Log.i(TAG, "initialised, SDK ${GazeTracker.getVersionName()}")
            if (wantTracking) tracker.startTracking()
        }
    }

    private val trackingCallback = object : TrackingCallback {
        override fun onMetrics(
            timestamp: Long,
            gazeInfo: GazeInfo,
            faceInfo: FaceInfo,
            blinkInfo: BlinkInfo,
            userStatusInfo: UserStatusInfo,
        ) {
            _frames.tryEmit(
                EyedidFrame(
                    timestampMs = timestamp,
                    gazeX = gazeInfo.x,
                    gazeY = gazeInfo.y,
                    trackingState = gazeInfo.trackingState,
                    faceScore = faceInfo.score,
                    pitch = faceInfo.pitch,
                    yaw = faceInfo.yaw,
                    roll = faceInfo.roll,
                    blinking = blinkInfo.isBlink,
                ),
            )
        }

        override fun onDrop(timestamp: Long) {
            // Frames are dropped when the device cannot keep up. Not worth surfacing per frame;
            // the visible symptom is a lower, choppier update rate.
            Log.d(TAG, "dropped frame at $timestamp")
        }
    }

    private val calibrationCallback = object : CalibrationCallback {
        override fun onCalibrationNextPoint(x: Float, y: Float) {
            _calibration.tryEmit(EyedidCalibration.ShowPoint(x, y))
        }

        override fun onCalibrationProgress(progress: Float) {
            _calibration.tryEmit(EyedidCalibration.Progress(progress))
        }

        override fun onCalibrationFinished(calibrationData: DoubleArray) {
            _calibration.tryEmit(EyedidCalibration.Finished(calibrationData.toList()))
        }

        override fun onCalibrationCanceled(calibrationData: DoubleArray) {
            _calibration.tryEmit(EyedidCalibration.Canceled(calibrationData.toList()))
        }
    }

    private val statusCallback = object : StatusCallback {
        override fun onStarted() {
            _status.value = EyedidStatus.Tracking
        }

        override fun onStopped(error: StatusErrorType) {
            // ERROR_NONE is our own stopTracking() coming back. The camera errors mean the camera
            // was taken away (another app, a permission change) and the user needs to know why.
            // No `else`: a new SDK value fails the build here rather than being mislabelled.
            _status.value = when (error) {
                StatusErrorType.ERROR_NONE -> EyedidStatus.Ready
                StatusErrorType.ERROR_CAMERA_START -> EyedidStatus.Interrupted(error, describeStatus(error))
                StatusErrorType.ERROR_CAMERA_INTERRUPT -> EyedidStatus.Interrupted(error, describeStatus(error))
            }
        }
    }

    /**
     * Starts the licence check and builds the tracker. Safe to call more than once: only the first
     * call does anything. [licenseKey] is blank in a checkout that has not set one, which is
     * reported as [EyedidStatus.NoLicenceKey] rather than as a licence failure.
     */
    fun initialize(context: Context, licenseKey: String) {
        if (initializing || tracker != null) return
        if (licenseKey.isBlank()) {
            _status.value = EyedidStatus.NoLicenceKey
            return
        }
        initializing = true
        _status.value = EyedidStatus.Initializing
        // useBlink drives BlinkInfo, which is what lets the UI explain a missing gaze as a blink.
        val options = GazeTrackerOptions.Builder().setUseBlink(true).build()
        GazeTracker.initGazeTracker(context.applicationContext, licenseKey, initializationCallback, options)
    }

    /** Opens the camera and starts gaze tracking. Remembered if the licence check is still running. */
    fun startTracking() {
        wantTracking = true
        tracker?.startTracking()
    }

    /** Closes the camera. The tracker stays alive, so tracking can be started again. */
    fun stopTracking() {
        wantTracking = false
        tracker?.stopTracking()
    }

    /** Hands the SDK its camera preview surface. Works before or after [initialize] completes. */
    fun setCameraPreview(preview: TextureView) {
        this.preview = preview
        tracker?.setCameraPreview(preview)
    }

    /**
     * Begins the calibration sweep. [mode] decides how many dots: `FIVE_POINT` is the SDK's default
     * and what the widget offers.
     *
     * [criteria] is the SDK's only accuracy knob: `LOW`, `DEFAULT`, `HIGH`, and it defaults to `HIGH`
     * by request.
     *
     * **Known behaviour: on the dev device `HIGH` stopped the sweep completing.** It is not a
     * "slower but more careful" setting. The likeliest reason is that it wants more samples than the
     * single `collectSamples()` burst we ask for per dot — that call is made once, 1000 ms after the
     * dot appears — so the criterion is never satisfied and the SDK never advances past a point. It
     * may also be gated to an advanced-tier licence, which a `dev_…` key is not.
     *
     * If a sweep stalls: "Stop calibrating" always works, and this is the one line to put back to
     * `AccuracyCriteria.DEFAULT`, which is known good.
     *
     * @return false if the tracker is not ready yet, so the caller can say so instead of waiting.
     */
    fun startCalibration(
        mode: CalibrationModeType = CalibrationModeType.DEFAULT,
        criteria: AccuracyCriteria = AccuracyCriteria.HIGH,
    ): Boolean {
        val started = tracker?.startCalibration(mode, criteria) ?: false
        Log.i(TAG, "startCalibration($mode, $criteria) -> $started")
        return started
    }

    /**
     * Tells the SDK the dot is on screen and the user has had time to look at it, which is what
     * makes it collect samples and move on to the next point.
     */
    fun collectSamples() {
        tracker?.startCollectSamples()
    }

    /** Abandons the sweep. The SDK still reports whatever completed, via [EyedidCalibration.Canceled]. */
    fun cancelCalibration() {
        tracker?.stopCalibration()
    }

    /** Re-applies a saved blob so the user calibrates once rather than on every visit. */
    fun applyCalibration(data: List<Double>) {
        if (data.isEmpty()) return
        tracker?.setCalibrationData(data.toDoubleArray())
        Log.i(TAG, "applied stored calibration (${data.size} values)")
    }

    /** Releases the camera. Never use this instance again afterwards. */
    fun release() {
        wantTracking = false
        initializing = false
        preview = null
        tracker?.let { GazeTracker.releaseGazeTracker(it) }
        tracker = null
        _status.value = EyedidStatus.Idle
    }

    /**
     * Every licence failure, in words. The `when` has no `else` on purpose: a new SDK error value
     * then fails the build here instead of silently becoming a generic message.
     */
    private fun describeInitialization(error: InitializationErrorType): String = when (error) {
        InitializationErrorType.ERROR_NONE ->
            "The licence was accepted."
        InitializationErrorType.ERROR_INIT ->
            "The SDK could not start its camera pipeline. Close other apps that use the camera and try again."
        InitializationErrorType.ERROR_CAMERA_PERMISSION ->
            "The camera permission is missing. Allow the camera and try again."
        InitializationErrorType.AUTH_INVALID_KEY ->
            "The licence key is wrong. Copy it again from manage.seeso.io -> Console -> SDK."
        InitializationErrorType.AUTH_INVALID_ENV_USED_DEV_IN_PROD ->
            "This is a development key running in a release build. Use the production key, or build debug."
        InitializationErrorType.AUTH_INVALID_ENV_USED_PROD_IN_DEV ->
            "This is a production key running in a debug build. Use the development key, or build release."
        InitializationErrorType.AUTH_INVALID_PACKAGE_NAME ->
            "The key is registered to a different package name. It must be com.pwde.app."
        InitializationErrorType.AUTH_INVALID_APP_SIGNATURE ->
            "The key is registered to a different signing certificate. Install the build the key was made for."
        InitializationErrorType.AUTH_EXCEEDED_FREE_TIER ->
            "The free-tier allowance for this key is used up."
        InitializationErrorType.AUTH_DEACTIVATED_KEY ->
            "This licence key has been deactivated."
        InitializationErrorType.AUTH_INVALID_ACCESS ->
            "The licence server rejected the key (invalid access)."
        InitializationErrorType.AUTH_UNKNOWN_ERROR ->
            "The licence server returned an unknown error. Trying again often works."
        InitializationErrorType.AUTH_SERVER_ERROR ->
            "The licence server had a temporary error. Trying again often works."
        InitializationErrorType.AUTH_CANNOT_FIND_HOST ->
            "Could not reach the licence server. Check the network and try again."
        InitializationErrorType.AUTH_WRONG_LOCAL_TIME ->
            "The device clock differs too much from the server's. Set date and time to automatic."
        InitializationErrorType.AUTH_INVALID_KEY_FORMAT ->
            "The key is malformed — it was probably pasted with quotes or cut short."
        InitializationErrorType.AUTH_EXPIRE_KEY ->
            "This licence key has expired."
        InitializationErrorType.ERROR_NOT_ADVANCED_TIER ->
            "This feature needs an advanced-tier licence. Basic keys cannot use it."
    }

    private fun describeStatus(error: StatusErrorType): String = when (error) {
        StatusErrorType.ERROR_NONE -> "Stopped."
        StatusErrorType.ERROR_CAMERA_START ->
            "The camera could not be opened. Another app is probably using it — close it and start again."
        StatusErrorType.ERROR_CAMERA_INTERRUPT ->
            "The camera was taken by another app. Start tracking again when it is free."
    }

    private companion object {
        const val TAG = "EyedidGaze"
        const val TRACKING_FPS = 30
    }
}
