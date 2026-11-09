package com.zoomi.app.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ambient light in lux. The value stays null on devices without a light sensor. */
class LightSensorSource(context: Context) {
    private val sm = context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _lux = MutableStateFlow<Float?>(null)
    val lux: StateFlow<Float?> = _lux.asStateFlow()

    val available: Boolean get() = sensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) { _lux.value = event.values.firstOrNull() }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        sensor?.let { sm?.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sm?.unregisterListener(listener)
    }
}
