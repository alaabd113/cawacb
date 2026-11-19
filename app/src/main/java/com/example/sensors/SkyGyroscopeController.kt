package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.PI

class SkyGyroscopeController(
    context: Context,
    private val onOrientationChanged: (azimuthDeg: Float, altitudeDeg: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var smoothAzimuth = 180f
    private var smoothAltitude = 30f
    private var active = false

    val isAvailable: Boolean get() = rotationSensor != null

    fun setActive(enable: Boolean): Boolean {
        if (enable && rotationSensor == null) return false
        if (enable == active) return active
        active = enable
        if (enable) {
            sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sensorManager?.unregisterListener(this)
        }
        return active
    }

    fun release() {
        active = false
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        // Remap for holding phone upright facing the sky
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remappedMatrix
        )
        SensorManager.getOrientation(remappedMatrix, orientationAngles)
        val rawAz = (((orientationAngles[0] * 180.0 / PI) + 360.0) % 360.0).toFloat()
        val rawAlt = (-orientationAngles[1] * 180.0 / PI).toFloat().coerceIn(-12f, 88f)

        // Shortest-arc angular low-pass filter
        var deltaAz = rawAz - smoothAzimuth
        if (deltaAz > 180f) deltaAz -= 360f
        if (deltaAz < -180f) deltaAz += 360f
        smoothAzimuth = ((smoothAzimuth + deltaAz * 0.14f) + 360f) % 360f
        smoothAltitude += (rawAlt - smoothAltitude) * 0.14f

        onOrientationChanged(smoothAzimuth, smoothAltitude)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
