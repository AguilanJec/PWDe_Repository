package com.pwde.app.sensors.face

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Device tilt from the rotation sensor: the phone tilts where the head would, so the user can steer
 * the joystick without pointing a camera at their face. The camera never opens for this.
 *
 * `TYPE_ROTATION_VECTOR` is preferred because it is fused with the magnetometer, which pins the frame
 * the tilt is measured in; a phone without one falls back to `TYPE_GAME_ROTATION_VECTOR`. Only the
 * sensor's own X and Y axes are used (see [GyroPoseMath]), so the yaw a game-rotation-vector slowly
 * loses cannot reach the stick.
 *
 * The pose is the movement from the neutral the phone was held in when this started, so no absolute
 * heading is ever needed and the user can hold the phone however is comfortable. [rebaseline]
 * re-takes that neutral — "set center here" — and the neutral is also re-taken on its own whenever the
 * phone rests near it, so drift and a change of grip cannot leave the stick creeping off centre.
 */
class GyroHeadTracker(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    /** Set by [rebaseline] and consumed by the next sample, on the sensor's own thread. */
    private val rebaselineRequested = AtomicBoolean(false)

    val isAvailable: Boolean get() = sensor != null

    /** The next sample becomes the new neutral: "wherever the phone is now is straight ahead". */
    fun rebaseline() {
        rebaselineRequested.set(true)
    }

    /**
     * Emits (pose, timestampMs). Every pose is relative to the neutral, so the first sample after
     * starting — or after a [rebaseline] — is [HeadPose.NEUTRAL].
     */
    fun poses(): Flow<Pair<HeadPose, Long>> = callbackFlow {
        val manager = sensorManager
        val source = sensor
        if (manager == null || source == null) {
            close()
            return@callbackFlow
        }
        val current = FloatArray(9)
        var neutral: FloatArray? = null
        var nearNeutralSince: Long? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(current, event.values)
                val now = SystemClock.uptimeMillis()
                // Consumed before the null check, so an explicit recenter is never swallowed just
                // because the first sample happened to arrive at the same moment.
                val recenterRequested = rebaselineRequested.getAndSet(false)
                if (neutral == null || recenterRequested) {
                    // The neutral is taken as it is: it is the pose the user is holding now, not a
                    // reading to be averaged into.
                    neutral = current.clone()
                    nearNeutralSince = null
                    if (recenterRequested) Log.i(TAG, "Neutral re-anchored on request")
                    trySend(HeadPose.NEUTRAL to now)
                    return
                }
                val anchor = neutral ?: return
                // Self-heal. The sensor drifts slowly and a change of grip moves the neutral, either
                // of which would leave the stick permanently off centre and creeping. While the
                // phone rests at the neutral the stick is centred anyway, so re-anchor to it.
                val rel = GyroPoseMath.relativeDegrees(anchor, current)
                if (rel[0] * rel[0] + rel[1] * rel[1] < AUTO_RECENTER_DEGREES * AUTO_RECENTER_DEGREES) {
                    val since = nearNeutralSince ?: now
                    nearNeutralSince = since
                    if (now - since >= AUTO_RECENTER_MS) {
                        neutral = current.clone()
                        nearNeutralSince = null
                        Log.i(TAG, "Neutral re-anchored: the phone rested at it for ${AUTO_RECENTER_MS}ms")
                        trySend(HeadPose.NEUTRAL to now)
                        return
                    }
                } else {
                    nearNeutralSince = null
                }
                trySend(GyroPoseMath.pose(anchor, current) to now)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, source, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }

    private companion object {
        private const val TAG = "GyroHeadTracker"

        /**
         * How close to the neutral the phone must be resting before the neutral is re-taken.
         *
         * **This must stay below the smallest dead-zone setting** — 1° at Dead zone level 1 — because
         * otherwise it fires while the stick is genuinely deflected. Re-anchoring zeroes the
         * deflection, so the stick is yanked to the middle and then jerks back out: a centre-to-side
         * flicker every couple of seconds, which is exactly what a mis-set radius looks like.
         *
         * The reference can use a larger 1.5° radius only because its dead zone is 3.2° in the same
         * units, i.e. it stays at about half of it. This is that same half, against this app's
         * smallest dead zone rather than against a fixed full-scale tilt.
         */
        const val AUTO_RECENTER_DEGREES = 0.5f

        /** How long the phone must rest near the neutral before it becomes the new neutral. */
        const val AUTO_RECENTER_MS = 2_500L
    }
}
