package com.pwde.app.sensors.face

import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.data.model.FacialGesture

/** Where head data comes from. [SIMULATED] is the phone's motion sensors, never shown as real tracking. */
enum class TrackingSource { CAMERA, SIMULATED }

sealed interface TrackingStatus {
    /** Nobody is watching tracking right now, so the camera is off. */
    data object Idle : TrackingStatus
    data object Starting : TrackingStatus
    data object Live : TrackingStatus
    data object NoFace : TrackingStatus
    data class Unavailable(val reason: String) : TrackingStatus
}

/** Everything one frame of head/face tracking produced. */
data class FaceState(
    val source: TrackingSource = TrackingSource.CAMERA,
    val status: TrackingStatus = TrackingStatus.Idle,
    /** Why the simulated fallback is running (only set when [source] is SIMULATED). */
    val fallbackReason: String? = null,
    val pose: HeadPose? = null,
    /** Face mesh points, interleaved x,y, normalized to the (mirrored) camera image. */
    val landmarks: FloatArray? = null,
    /** Average landmark presence, when the model reports it. */
    val confidence: Float? = null,
    val gesture: GestureReading = GestureReading.NONE,
    val cursor: CursorPosition = CursorPosition.CENTER,
    val joystick: JoystickState = JoystickState(),
    val outputMode: FaceOutputMode = FaceOutputMode.CURSOR,
    val fps: Float = 0f,
) {
    val isSimulated: Boolean get() = source == TrackingSource.SIMULATED
    val hasFace: Boolean get() = pose != null

    // FloatArray needs content equality so StateFlow doesn't treat every frame as new twice.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceState) return false
        return source == other.source && status == other.status && fallbackReason == other.fallbackReason &&
            pose == other.pose && landmarks.contentEquals(other.landmarks) && confidence == other.confidence &&
            gesture == other.gesture && cursor == other.cursor && joystick == other.joystick &&
            outputMode == other.outputMode && fps == other.fps
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (pose?.hashCode() ?: 0)
        result = 31 * result + (landmarks?.contentHashCode() ?: 0)
        result = 31 * result + gesture.hashCode()
        result = 31 * result + cursor.hashCode()
        result = 31 * result + joystick.hashCode()
        return result
    }
}

/** Live tuning for the tracking pipeline, from Room + settings. */
data class TrackingTuning(
    val controls: ControlConfig = ControlConfig(),
    val outputMode: FaceOutputMode = FaceOutputMode.CURSOR,
)

/**
 * Shared per-frame pipeline for camera and simulated input: pose + blendshapes → gestures,
 * cursor and joystick. Frames arrive on one thread; recentering may come from another.
 */
class FaceFrameProcessor {
    private val classifier = GestureClassifier()
    private val cursorMapper = CursorMapper()
    private var cursor = CursorPosition.CENTER
    private var lastFrameMs = 0L
    private var fps = 0f

    @Synchronized
    fun process(
        pose: HeadPose?,
        blendshapes: Map<String, Float>,
        timestampMs: Long,
        tuning: TrackingTuning,
        base: FaceState,
    ): FaceState {
        if (lastFrameMs > 0) {
            val dt = (timestampMs - lastFrameMs).coerceAtLeast(1)
            fps = if (fps == 0f) 1000f / dt else fps * 0.9f + (1000f / dt) * 0.1f
        }
        lastFrameMs = timestampMs

        val controls = tuning.controls
        val neutral = HeadPose(0f, controls.joystick.centerPitch, controls.joystick.centerRoll)
        val gesture = classifier.classify(blendshapes, pose, timestampMs, controls::sensitivityOf, neutral)
        if (pose != null) cursor = cursorMapper.update(pose, controls.cursor) else cursorMapper.resetTracking()
        val joystick = if (pose != null) JoystickMapper.map(pose, controls.joystick)
        else JoystickState(radius = JoystickMapper.radiusFor(controls.joystick.size), deadZone = JoystickMapper.deadZoneFor(controls.joystick.deadZone))

        return base.copy(
            status = if (pose != null) TrackingStatus.Live else TrackingStatus.NoFace,
            pose = pose,
            gesture = gesture,
            cursor = cursor,
            joystick = joystick,
            outputMode = tuning.outputMode,
            fps = fps,
        )
    }

    @Synchronized
    fun recenterCursor() {
        cursorMapper.recenter()
        cursor = CursorPosition.CENTER
    }

    /**
     * Gestures that fire actions this frame. Only gestures the calibration enabled count. Tilt and nod
     * also steer the joystick, so in joystick mode they don't fire actions. (Shake uses yaw, which the joystick ignores.)
     */
    fun actionableStarts(state: FaceState, controls: ControlConfig): Set<FacialGesture> {
        val enabled = state.gesture.started.filterTo(mutableSetOf(), controls::isGestureEnabled)
        return if (state.outputMode == FaceOutputMode.JOYSTICK) {
            enabled - setOf(FacialGesture.TILT_LEFT, FacialGesture.TILT_RIGHT, FacialGesture.NOD)
        } else {
            enabled
        }
    }
}
