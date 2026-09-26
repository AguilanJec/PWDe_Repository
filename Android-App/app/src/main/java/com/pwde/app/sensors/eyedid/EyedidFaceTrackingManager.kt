package com.pwde.app.sensors.eyedid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.TextureView
import androidx.core.content.ContextCompat
import camp.visual.eyedid.gazetracker.constant.AccuracyCriteria
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType
import camp.visual.eyedid.gazetracker.metrics.state.TrackingState
import androidx.camera.core.SurfaceRequest
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.sensors.face.CursorPosition
import com.pwde.app.sensors.face.DwellProgress
import com.pwde.app.sensors.face.FaceState
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.GazeDwell
import com.pwde.app.sensors.face.HeadPose
import com.pwde.app.sensors.face.TrackingSource
import com.pwde.app.sensors.face.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [FaceTrackingManager] backed by the SeeSo/Eyedid `GazeTracker` — the eye-control engine.
 *
 * Everything the rest of the app sees is [FaceState] with [FaceState.cursor] set to the calibrated
 * gaze point, so eye control reuses the same pointer, cursor overlay and press handling as head
 * control. What it does *not* provide is the SDK's business to provide:
 * - there is no face mesh, so [gestureEvents] stays empty and facial gestures are unavailable while
 *   eye control is selected;
 * - there is no joystick, so [captureJoystickCenter] is false.
 *
 * ## The engine is a process-wide singleton that owns the front camera
 * `GazeTracker` is static — two live instances are not possible — so this class is the single owner
 * and the camera is handed to it only while somebody wants the gaze. That "somebody" is
 * reference-counted by [acquire]/[release] rather than inferred from flow subscriptions, because
 * there are two independent consumers (the input-mode router, and the diagnostics screen) and a
 * mistake here is a camera that never opens or two engines fighting for it. See [acquire].
 *
 * @param licenseKey `BuildConfig.EYEDID_LICENSE_KEY`. Blank means the checkout has no key: the
 *   engine reports [EyedidStatus.NoLicenceKey] and eye control stays unavailable instead of failing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EyedidFaceTrackingManager(
    context: Context,
    private val store: EyedidCalibrationStore,
    private val licenseKey: String,
    private val dwellHoldMs: Long = EyedidDwellTracker.DEFAULT_HOLD_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : FaceTrackingManager {

    private val appContext = context.applicationContext
    private val engine = EyedidGazeEngine()

    /** Smoothed gaze, in screen fractions (the engine reports pixels). */
    private val pointer = EyedidPointer()

    /** Turns a gaze that has stopped moving into a press. */
    private val dwell = EyedidDwellTracker(holdMs = dwellHoldMs)

    private val _dwellEvents = MutableSharedFlow<GazeDwell>(extraBufferCapacity = 8)
    private val _calibrated = MutableStateFlow(false)
    private val _status = MutableStateFlow<EyedidStatus>(EyedidStatus.Idle)

    /** Who currently wants the gaze. Reference-counted; see [acquire]. */
    private val holders = MutableStateFlow<Set<String>>(emptySet())

    /** Bumped when the permission answer changes, so the session re-reads it without a resubscribe. */
    private val permissionTick = MutableStateFlow(0)

    private var appliedStoredCalibration = false

    /** The SDK's lifecycle, for the UI to explain. Mirrors [EyedidGazeEngine.status]. */
    val status: StateFlow<EyedidStatus> = _status.asStateFlow()

    /** True once a calibration blob is stored on this device (loaded at startup or just captured). */
    val calibrated: StateFlow<Boolean> = _calibrated.asStateFlow()

    /** Raw SDK frames, for the diagnostics screen. Not used by the control pipeline. */
    val frames: SharedFlow<EyedidFrame> get() = engine.frames

    /** Calibration milestones, for the calibration UI. */
    val calibration: SharedFlow<EyedidCalibration> get() = engine.calibration

    /** True when `pwde.eyedid.licenseKey` is missing, so the UI can point at `local.properties`. */
    val hasLicenseKey: Boolean get() = licenseKey.isNotBlank()

    /** The SDK's own version string, for the diagnostics panel. "unknown" if it will not say. */
    val sdkVersion: String get() = runCatching { engine.sdkVersion }.getOrDefault("unknown")

    override val dwellEvents: SharedFlow<GazeDwell> = _dwellEvents.asSharedFlow()

    override val surfaceRequest: StateFlow<SurfaceRequest?> = MutableStateFlow(null)

    /** The SDK reports no face mesh, so nothing can be triggered by a facial gesture in this mode. */
    override val gestureEvents: SharedFlow<FacialGesture> = MutableSharedFlow()

    override val hasCameraPermission: Boolean
        get() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override val state: StateFlow<FaceState> = combine(holders, permissionTick) { wanted, _ -> wanted.isNotEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { wanted -> if (wanted) gazeSession() else flowOf(FaceState(status = TrackingStatus.Idle)) }
        .stateIn(scope, SharingStarted.Eagerly, FaceState(status = TrackingStatus.Idle))

    init {
        scope.launch {
            engine.status.collect { eyedid ->
                _status.value = eyedid
                when (eyedid) {
                    // Ready is the first moment there is a tracker to hand the saved blob to.
                    EyedidStatus.Ready -> applyStoredCalibration()
                    EyedidStatus.Tracking -> Unit
                    EyedidStatus.Idle, EyedidStatus.Initializing, EyedidStatus.NoLicenceKey -> Unit
                    is EyedidStatus.Failed, is EyedidStatus.Interrupted -> Unit
                }
            }
        }
        scope.launch {
            engine.calibration.collect { event ->
                // Persist here rather than in the UI: the blob must survive however the sweep ended,
                // including a partial one the user cancelled. Anything the SDK handed back is worth
                // more than starting from nothing next time.
                val data = when (event) {
                    is EyedidCalibration.Finished -> event.data
                    is EyedidCalibration.Canceled -> event.data
                    is EyedidCalibration.ShowPoint, is EyedidCalibration.Progress -> emptyList()
                }
                if (data.isNotEmpty()) {
                    store.save(data)
                    _calibrated.value = true
                }
            }
        }
    }

    /**
     * Says "I need the gaze". The engine opens the camera on the first call and closes it on the
     * matching [release], so the front camera is only ever held by whatever is actually using it.
     *
     * [holder] is not a token — it is a name, so that acquiring twice from the same place (a
     * recomposition, a re-subscribed flow) cannot leak a count that never comes back down.
     */
    fun acquire(holder: String) {
        if (holder in holders.value) return
        Log.i(TAG, "gaze wanted by $holder")
        // `update` rather than a read-modify-write: holders arrive from flow operators and from
        // ViewModels, which are not on the same thread, and a lost update here is a camera that
        // never opens or never closes.
        holders.update { it + holder }
    }

    /** Says "I am done with the gaze"; the camera closes when the last holder leaves. */
    fun release(holder: String) {
        if (holder !in holders.value) return
        Log.i(TAG, "gaze released by $holder")
        holders.update { it - holder }
    }

    /**
     * Opens the camera, or closes it, without touching the reference count. Used by the calibration
     * screen, which needs the tracker alive but is not a long-lived holder.
     */
    fun startTracking() = engine.startTracking()

    fun stopTracking() = engine.stopTracking()

    /** Hands the SDK its camera preview surface. Optional: tracking works without one. */
    fun setCameraPreview(preview: TextureView) = engine.setCameraPreview(preview)

    /**
     * Starts the SDK's calibration sweep. The dots must be drawn where [EyedidCalibration.ShowPoint]
     * says, because that is the position the SDK measures the eyes against.
     *
     * [criteria] defaults to `HIGH` by request — see [EyedidGazeEngine.startCalibration] for the
     * known failure mode and the one-line way back to `AccuracyCriteria.DEFAULT`.
     *
     * @return false if the tracker is not ready yet, so the UI can say so instead of appearing hung.
     */
    fun startCalibration(
        mode: CalibrationModeType = CalibrationModeType.FIVE_POINT,
        criteria: AccuracyCriteria = AccuracyCriteria.HIGH,
    ): Boolean = engine.startCalibration(mode, criteria)

    fun cancelCalibration() = engine.cancelCalibration()

    /** Tells the SDK the dot is on screen and the user has had time to look at it. */
    fun collectCalibrationSamples() = engine.collectSamples()

    fun forgetCalibration() {
        store.clear()
        appliedStoredCalibration = false
        _calibrated.value = false
    }

    override fun refreshPermissions() {
        // The session checks the permission when it starts, so granting it later has to restart the
        // session or eye control would stay stuck on "needs the camera" until the screen was left.
        permissionTick.update { it + 1 }
    }

    /**
     * Eye control's pointer is absolute — the gaze *is* the position — so there is nothing to
     * recentre, and pretending otherwise would move the pointer somewhere the user is not looking.
     */
    override fun recenterCursor() = Unit

    /** The Eyedid SDK reports head angles but no face mesh, so it cannot supply a joystick neutral. */
    override suspend fun captureJoystickCenter(): Boolean = false

    /**
     * Feeds the saved blob to the SDK the first time a tracker exists, so the user calibrates once
     * rather than on every launch.
     */
    private fun applyStoredCalibration() {
        if (appliedStoredCalibration) return
        appliedStoredCalibration = true
        val saved = store.load()
        if (saved.isEmpty()) return
        engine.applyCalibration(saved)
        _calibrated.value = true
        Log.i(TAG, "applied the stored calibration (${saved.size} values)")
    }

    /**
     * One run of the SDK. Starts on the first holder and ends when the last one leaves, which is
     * what closes the camera — there is no "tell the other engine to stop" anywhere in this design.
     */
    private fun gazeSession(): Flow<FaceState> = channelFlow {
        if (!hasCameraPermission) {
            send(FaceState(status = TrackingStatus.Unavailable("Eye control needs the camera. Allow it and try again.")))
            awaitClose { }
            return@channelFlow
        }
        val face = screenSize()
        if (face.widthPx <= 0 || face.heightPx <= 0) {
            send(FaceState(status = TrackingStatus.Unavailable("This device did not report a screen size, so gaze cannot be placed.")))
            awaitClose { }
            return@channelFlow
        }

        send(FaceState(status = TrackingStatus.Starting))
        // The mode can flip and flip back; giving the other engine a moment to let go of the camera
        // avoids an "camera in use" that would only ever look like eye control not working.
        delay(HANDOVER_DELAY_MS)

        pointer.trackingStarted()
        dwell.reset()
        engine.initialize(appContext, licenseKey)
        engine.startTracking()

        // A refused licence or a camera taken by another app produces no frames at all, so the frame
        // loop alone would leave the UI saying "starting…" forever. These are the only statuses worth
        // publishing: they are the ones the user has to act on. Everything else is already covered by
        // the frames themselves, and emitting for it would race the frame loop and flicker.
        launch {
            _status.collect { eyedid ->
                val reason = when (eyedid) {
                    is EyedidStatus.Failed -> eyedid.advice
                    is EyedidStatus.Interrupted -> eyedid.advice
                    EyedidStatus.NoLicenceKey -> NO_LICENSE_REASON
                    EyedidStatus.Idle, EyedidStatus.Initializing, EyedidStatus.Ready, EyedidStatus.Tracking -> null
                }
                reason?.let {
                    send(
                        FaceState(
                            source = TrackingSource.CAMERA,
                            status = TrackingStatus.Unavailable(it),
                            fallbackReason = it,
                            outputMode = FaceOutputMode.CURSOR,
                        ),
                    )
                }
            }
        }

        var lastFrameAt = 0L
        var fps = 0f
        var cursor = CursorPosition.CENTER
        var loggedSdkClock = false
        var lastDwellLogAt = 0L

        engine.frames.collect { frame ->
            // Every time-based decision below uses *our* monotonic clock, never the SDK's frame
            // timestamp: that value's unit is undocumented, and a hold window measured in the wrong
            // unit fails silently in opposite directions — 800 "milliseconds" of microseconds fills
            // instantly, of nanoseconds never fills at all. Our own clock cannot be wrong.
            val now = SystemClock.uptimeMillis()
            if (!loggedSdkClock) {
                loggedSdkClock = true
                Log.i(TAG, "SDK frame timestamp ${frame.timestampMs}, uptime $now (delta ${frame.timestampMs - now})")
            }
            if (lastFrameAt > 0) {
                val dt = (now - lastFrameAt).coerceAtLeast(1)
                fps = if (fps == 0f) 1000f / dt else fps * 0.9f + (1000f / dt) * 0.1f
            }
            lastFrameAt = now

            // Only SUCCESS carries a gaze; the other states leave x/y stale, and using a stale point
            // would make the pointer drift on its own while the eyes are unreadable.
            val reading = if (frame.trackingState == TrackingState.SUCCESS) {
                EyedidGazeMapping.toBoxFraction(
                    x = frame.gazeX,
                    y = frame.gazeY,
                    boxLeftPx = 0f,
                    boxTopPx = 0f,
                    boxWidthPx = face.widthPx,
                    boxHeightPx = face.heightPx,
                )?.let { GazePoint(it.x, it.y) }
            } else {
                null
            }
            val faceVisible = frame.trackingState != TrackingState.FACE_MISSING
            val smoothed = pointer.update(reading, faceVisible, now)

            // The pointer knows the difference between a blink (it is holding a point) and a real
            // loss, and the tracker turns that into "keep counting" or "start again".
            val fired = dwell.frame(reading, smoothed, now)
            if (fired != null) {
                Log.i(TAG, "dwell fired at ${fired.x}, ${fired.y}")
                _dwellEvents.tryEmit(fired)
            }
            // Once a second, so a hold that never *completes* can be told apart from one that never
            // *starts*: progress should climb, and the target should stop moving. A target that keeps
            // changing means the eyes are wandering further than the dwell `radius`.
            if (now - lastDwellLogAt >= DWELL_LOG_INTERVAL_MS) {
                lastDwellLogAt = now
                Log.i(
                    TAG,
                    "gaze: target=${dwell.target?.let { "${it.x}, ${it.y}" } ?: "none"} " +
                        "progress=${(dwell.progress * 100).toInt()}% fps=${fps.toInt()} " +
                        "state=${frame.trackingState} face=${frame.faceScore}",
                )
            }

            smoothed?.let { cursor = CursorPosition(it.x, it.y) }

            send(
                FaceState(
                    source = TrackingSource.CAMERA,
                    status = if (smoothed != null) TrackingStatus.Live else TrackingStatus.NoFace,
                    // The SDK's own angles. Shown for diagnostics; eye control never steers with the
                    // head, so their sign conventions do not reach the control pipeline.
                    pose = if (faceVisible) HeadPose(frame.yaw, frame.pitch, frame.roll) else null,
                    confidence = frame.faceScore.takeIf { faceVisible },
                    cursor = cursor,
                    outputMode = FaceOutputMode.CURSOR,
                    dwell = dwell.target?.let { DwellProgress(it.x, it.y, dwell.progress) },
                    fallbackReason = when {
                        // Calibration is what turns pixels into a screen position; uncalibrated, the
                        // pointer still moves but it is not pointing at anything the user chose.
                        !_calibrated.value && _status.value == EyedidStatus.Tracking ->
                            "Eye control is not calibrated yet"
                        else -> null
                    },
                    fps = fps,
                ),
            )
        }

        awaitClose {
            engine.stopTracking()
            pointer.trackingStopped()
            dwell.reset()
        }
    }

    /** The rotated screen size in pixels, which is the space the SDK reports gaze in. */
    private fun screenSize(): ScreenSize {
        val manager = appContext.getSystemService(DisplayManager::class.java) ?: return ScreenSize(0, 0)
        val display = manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return ScreenSize(0, 0)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return ScreenSize(metrics.widthPixels, metrics.heightPixels)
    }

    private data class ScreenSize(val widthPx: Int, val heightPx: Int)

    private companion object {
        const val TAG = "EyedidFace"

        /** How long a new session waits for the previous engine to give the camera back. */
        const val HANDOVER_DELAY_MS = 300L

        const val NO_LICENSE_REASON =
            "Eye control is not set up in this build, so the pointer cannot follow your eyes. Every other input mode works."

        /** How often the gaze/dwell summary is logged while a session runs. */
        const val DWELL_LOG_INTERVAL_MS = 1_000L
    }
}
