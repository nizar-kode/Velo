package com.darusc.mousedroid.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.PI

/**
 * High-precision sensor manager translating hand tilt/rotation
 * in the air into relative mouse movement for the laser pointer.
 */
class GyroscopeLaserManager(
    context: Context,
    private val onMouseMove: (dx: Int, dy: Int) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    var isLaserActive = false
        private set

    var sensitivity: Float = 1.2f

    private var lastYaw: Float? = null
    private var lastPitch: Float? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun start() {
        if (isLaserActive) return
        isLaserActive = true
        lastYaw = null
        lastPitch = null

        rotationSensor?.let {
            sensorManager?.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        if (!isLaserActive) return
        isLaserActive = false
        lastYaw = null
        lastPitch = null
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isLaserActive || event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // orientationAngles[0] is azimuth / yaw [-pi, pi]
            // orientationAngles[1] is pitch [-pi/2, pi/2]
            val yaw = orientationAngles[0]
            val pitch = orientationAngles[1]

            val prevY = lastYaw
            val prevP = lastPitch

            if (prevY != null && prevP != null) {
                var dYaw = yaw - prevY
                var dPitch = pitch - prevP

                // Normalize angle wrap-around across -pi to +pi boundary
                if (dYaw > PI.toFloat()) dYaw -= (2f * PI.toFloat())
                if (dYaw < -PI.toFloat()) dYaw += (2f * PI.toFloat())

                // Threshold noise filter (dead-zone)
                val deadzone = 0.0005f
                val dxFloat = if (kotlin.math.abs(dYaw) > deadzone) -dYaw * sensitivity * 1100f else 0f
                val dyFloat = if (kotlin.math.abs(dPitch) > deadzone) -dPitch * sensitivity * 1100f else 0f

                val dx = dxFloat.toInt()
                val dy = dyFloat.toInt()

                if (dx != 0 || dy != 0) {
                    onMouseMove(dx, dy)
                }
            }

            lastYaw = yaw
            lastPitch = pitch
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // Fallback for devices without rotation vector sensor: angular speed in rad/s
            // event.values[0] is x-axis (pitch), event.values[1] is y-axis (roll), event.values[2] is z-axis (yaw)
            val dYaw = event.values[2]
            val dPitch = event.values[0]

            val dx = (-dYaw * sensitivity * 18f).toInt()
            val dy = (-dPitch * sensitivity * 18f).toInt()

            if (dx != 0 || dy != 0) {
                onMouseMove(dx, dy)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
