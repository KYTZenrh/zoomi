package com.zoomi.app.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.zoomi.core.location.HeadingFusion

/**
 * Vehicle yaw rate from the gyroscope projected on the gravity direction, so it works for any
 * phone mount. Uses no magnetometer. Callbacks arrive on the main thread.
 */
class YawRateSource(context: Context, private val onYaw: (timeMs: Long, degPerSec: Float) -> Unit) {
    private val sm = context.applicationContext.getSystemService(SensorManager::class.java)
    private val gyro: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravity: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val up = FloatArray(3)
    private var haveUp = false

    val available: Boolean get() = gyro != null && gravity != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_GRAVITY -> { up[0] = e.values[0]; up[1] = e.values[1]; up[2] = e.values[2]; haveUp = true }
                Sensor.TYPE_GYROSCOPE -> if (haveUp) {
                    val rate = HeadingFusion.yawRateDegPerSec(e.values[0], e.values[1], e.values[2], up[0], up[1], up[2])
                    // Sensor timestamps are elapsedRealtime nanos.
                    onYaw(e.timestamp / 1_000_000, rate)
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (!available) return
        sm?.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_GAME)
        sm?.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm?.unregisterListener(listener)
        haveUp = false
    }
}
