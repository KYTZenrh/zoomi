package com.zoomi.app.location

import android.location.Location
import android.os.SystemClock
import com.zoomi.core.location.DeadReckoner
import com.zoomi.core.location.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The position everything else uses (map, navigation): real GNSS fixes, or, while navigating
 * and the fix is stale (tunnel, GPS dropout), a dead-reckoned position along the route.
 * Estimated positions are flagged with provider [PROVIDER_DR] and a growing accuracy radius.
 */
class PositionEngine(private val gnss: GnssLocationSource) {
    companion object {
        const val PROVIDER_DR = "zoomi-dead-reckoning"
        private const val STALE_MS = 3_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val reckoner = DeadReckoner()
    private var routeActive = false

    private val _position = MutableStateFlow<Location?>(null)
    val position: StateFlow<Location?> = _position.asStateFlow()

    private val _estimated = MutableStateFlow(false)
    val estimated: StateFlow<Boolean> = _estimated.asStateFlow()

    init {
        scope.launch {
            gnss.location.collect { loc ->
                if (loc == null) return@collect
                if (!loc.hasAccuracy() || loc.accuracy <= 30f) {
                    reckoner.onFix(loc.elapsedRealtimeNanos / 1_000_000, loc.latitude, loc.longitude,
                        if (loc.hasSpeed()) loc.speed.toDouble() else null,
                        if (loc.hasAccuracy()) loc.accuracy.toDouble() else null)
                }
                _estimated.value = false
                _position.value = loc
            }
        }
        scope.launch {
            while (true) {
                delay(1_000)
                tick()
            }
        }
    }

    /** Route to follow during GNSS dropouts (null when not navigating). */
    fun setRoute(points: List<LatLon>?) {
        routeActive = points != null
        reckoner.setRoute(points)
        // Re-anchor on the latest real fix.
        gnss.location.value?.let { l ->
            reckoner.onFix(l.elapsedRealtimeNanos / 1_000_000, l.latitude, l.longitude,
                if (l.hasSpeed()) l.speed.toDouble() else null, if (l.hasAccuracy()) l.accuracy.toDouble() else null)
        }
    }

    private fun tick() {
        if (!routeActive) return
        val age = gnss.fixAgeMs() ?: return
        if (age < STALE_MS) return
        val est = reckoner.estimate(SystemClock.elapsedRealtime()) ?: return
        val last = gnss.location.value
        _estimated.value = true
        _position.value = Location(PROVIDER_DR).apply {
            latitude = est.lat
            longitude = est.lon
            bearing = est.bearingDeg
            accuracy = est.accuracyM
            if (last != null && last.hasSpeed()) speed = last.speed
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }
}
