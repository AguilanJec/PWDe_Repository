package com.pwde.app.sensors.face

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2

/**
 * Demo-mode stand-in for head tracking: tilting the phone plays the part of tilting your head.
 * Uses the fused gravity sensor (gyro + accelerometer) and falls back to the raw accelerometer.
 * Output is always labelled SIMULATED by the caller — it is never presented as face tracking.
 */
class OrientationHeadTracker(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean get() = sensor != null

    /** Emits (pose, timestampMs). Angles are relative to how the phone was held when this started. */
    fun poses(): Flow<Pair<HeadPose, Long>> = callbackFlow {
        val manager = sensorManager
        val source = sensor
        if (manager == null || source == null) {
            close()
            return@callbackFlow
        }
        val gravity = FloatArray(3)
        var initialised = false
        var baselinePitch = 0f
        var baselineRoll = 0f
        val lowPass = source.type == Sensor.TYPE_ACCELEROMETER

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (lowPass && initialised) {
                    for (i in 0..2) gravity[i] = gravity[i] * 0.8f + event.values[i] * 0.2f
                } else {
                    event.values.copyInto(gravity, endIndex = 3)
                }
                // Phone upright in portrait: gravity is mostly +y. Side tilt moves it into x,
                // tipping the top toward/away from you moves it into z.
                val roll = Math.toDegrees(atan2(gravity[0].toDouble(), gravity[1].toDouble())).toFloat()
                val pitch = Math.toDegrees(atan2(gravity[2].toDouble(), gravity[1].toDouble())).toFloat()
                if (!initialised) {
                    baselinePitch = pitch
                    baselineRoll = roll
                    initialised = true
                }
                val relRoll = -(roll - baselineRoll)
                val relPitch = pitch - baselinePitch
                // No yaw from gravity; side tilt stands in for turning so the pointer can still move sideways.
                trySend(HeadPose(yaw = relRoll, pitch = relPitch, roll = relRoll) to SystemClock.uptimeMillis())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, source, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }
}
