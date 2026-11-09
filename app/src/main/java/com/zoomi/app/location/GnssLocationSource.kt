package com.zoomi.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Location straight from the GNSS chip (GPS_PROVIDER). It works with no internet and does
 * not use Google Play Services or network/Wi-Fi positioning.
 */
class GnssLocationSource(context: Context) {
    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(LocationManager::class.java)

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _providerEnabled = MutableStateFlow(true)
    val providerEnabled: StateFlow<Boolean> = _providerEnabled.asStateFlow()

    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { _location.value = location }
        override fun onProviderEnabled(provider: String) { _providerEnabled.value = true }
        override fun onProviderDisabled(provider: String) { _providerEnabled.value = false }
    }

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun start(): Boolean {
        if (running) return true
        if (!hasPermission() || lm == null) return false
        _providerEnabled.value = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        // A recent cached GPS fix gives the theme and camera a starting point. Still GPS-only.
        if (_location.value == null) {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { _location.value = it }
        }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        running = true
        return true
    }

    fun stop() {
        if (!running) return
        lm?.removeUpdates(listener)
        running = false
    }

    /** Age of the latest fix in ms, or null if none. Uses the monotonic clock, not wall time. */
    fun fixAgeMs(): Long? = _location.value?.let {
        (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000
    }
}
