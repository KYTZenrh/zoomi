package com.zoomi.app

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zoomi.app.sensors.LightSensorSource
import com.zoomi.core.location.HeadingFusion
import com.zoomi.app.sensors.YawRateSource
import com.zoomi.app.location.PositionEngine
import com.zoomi.core.theme.AutoThemeController
import com.zoomi.core.theme.SolarCalculator
import com.zoomi.core.theme.ThemeDecision
import com.zoomi.core.theme.ThemeInputs
import com.zoomi.core.theme.ThemeMode
import com.zoomi.core.theme.ThemeReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Live values shown on the Settings screen so you can see why the theme is what it is. */
data class ThemeDiagnostics(
    val sunElevationDeg: Double? = null,
    val lux: Float? = null,
    val fixAgeMs: Long? = null,
    val onTunnelRoad: Boolean? = null,
    val inTunnel: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = app.zoomi.settings
    val gnss = app.zoomi.gnss
    private val light = LightSensorSource(app)
    private val controller = AutoThemeController()
    private val fusion = HeadingFusion()
    private val yaw = YawRateSource(app) { t, rate -> onYaw(t, rate) }
    private val engine = app.zoomi.position

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTO)
    val courseUp: StateFlow<Boolean> =
        settings.courseUp.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Position for the map: real GPS, or dead-reckoned along the route in tunnels. */
    val location: StateFlow<Location?> = engine.position
    val positionEstimated: StateFlow<Boolean> = engine.estimated
    val gpsEnabled: StateFlow<Boolean> = gnss.providerEnabled

    private val _course = MutableStateFlow<Float?>(null)
    val course: StateFlow<Float?> = _course.asStateFlow()

    private val _decision = MutableStateFlow(ThemeDecision(false, ThemeReason.SYSTEM_FALLBACK))
    val decision: StateFlow<ThemeDecision> = _decision.asStateFlow()

    private val _diagnostics = MutableStateFlow(ThemeDiagnostics())
    val diagnostics: StateFlow<ThemeDiagnostics> = _diagnostics.asStateFlow()

    /** Set by the map: whether the road under the puck is drawn as a tunnel. */
    @Volatile var onTunnelRoad: Boolean? = null
    /** Set by the UI from the system configuration. */
    @Volatile var systemDark: Boolean = false

    private val _hasPermission = MutableStateFlow(gnss.hasPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    init {
        viewModelScope.launch {
            engine.position.collect { loc ->
                if (loc != null) {
                    if (loc.provider == PositionEngine.PROVIDER_DR) {
                        _course.value = loc.bearing // follow the road while dead reckoning
                    } else {
                        _course.value = fusion.onGnss(
                            if (loc.hasSpeed()) loc.speed else null,
                            if (loc.hasBearing()) loc.bearing else null,
                            if (loc.hasBearingAccuracy()) loc.bearingAccuracyDegrees else null,
                        ).degrees
                    }
                }
                evaluateTheme()
            }
        }
        viewModelScope.launch {
            // Timers (GPS-loss, sun hysteresis) need evaluation even without new events.
            while (true) {
                evaluateTheme()
                delay(1_000)
            }
        }
        viewModelScope.launch { themeMode.collect { evaluateTheme() } }
    }

    private fun evaluateTheme() {
        val loc = gnss.location.value
        val now = System.currentTimeMillis()
        val lux = light.lux.value
        val fixAge = gnss.fixAgeMs()
        val input = ThemeInputs(
            nowMs = now,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            lux = lux,
            lastFixAgeMs = fixAge,
            onTunnelRoad = onTunnelRoad,
            systemDark = systemDark,
            speedMps = loc?.takeIf { it.hasSpeed() }?.speed,
            accuracyM = loc?.takeIf { it.hasAccuracy() }?.accuracy,
        )
        _decision.value = controller.decide(themeMode.value, input)
        _diagnostics.value = ThemeDiagnostics(
            sunElevationDeg = loc?.let { SolarCalculator.elevationDegrees(now, it.latitude, it.longitude) },
            lux = lux,
            fixAgeMs = fixAge,
            onTunnelRoad = onTunnelRoad,
            inTunnel = controller.inTunnel,
        )
    }

    private fun onYaw(timeMs: Long, rate: Float) {
        if (engine.estimated.value) return
        val d = fusion.onGyro(timeMs, rate).degrees
        val cur = _course.value
        // Avoid recomposing 50x/s: publish only meaningful changes.
        if (d == null || cur == null || angleDiff(d, cur) >= 1f) _course.value = d
    }

    private fun angleDiff(a: Float, b: Float): Float { val d = kotlin.math.abs(a - b) % 360f; return if (d > 180f) 360f - d else d }

    fun onPermissionResult() {
        _hasPermission.value = gnss.hasPermission()
        if (_hasPermission.value) gnss.start()
    }

    fun onStart() {
        _hasPermission.value = gnss.hasPermission()
        gnss.start()
        light.start()
        yaw.start()
    }

    /** @param keepGps true while navigating: guidance continues in the background via the foreground service. */
    fun onStop(keepGps: Boolean) {
        if (!keepGps) { gnss.stop(); yaw.stop() }
        light.stop()
    }

    val valhallaUrl: StateFlow<String> =
        settings.valhallaUrl.stateIn(viewModelScope, SharingStarted.Eagerly, com.zoomi.app.data.SettingsRepository.DEFAULT_VALHALLA)
    val photonUrl: StateFlow<String> =
        settings.photonUrl.stateIn(viewModelScope, SharingStarted.Eagerly, com.zoomi.app.data.SettingsRepository.DEFAULT_PHOTON)
    val satellite: StateFlow<Boolean> = settings.satellite.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val imagery: StateFlow<com.zoomi.app.map.Imagery> =
        settings.imagery.stateIn(viewModelScope, SharingStarted.Eagerly, com.zoomi.app.map.Imagery.EOX)
    val imageryCustomUrl: StateFlow<String> = settings.imageryCustomUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val imageryCustomAttribution: StateFlow<String> = settings.imageryCustomAttribution.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val imagerySource: StateFlow<String> = settings.imagerySource.stateIn(viewModelScope, SharingStarted.Eagerly, "EOX")
    val esriKey: StateFlow<String> = settings.esriKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setImagerySource(source: String) = viewModelScope.launch { settings.setImagerySource(source) }
    fun setEsriKey(key: String) = viewModelScope.launch { settings.setEsriKey(key) }
    fun setSatellite(on: Boolean) = viewModelScope.launch { settings.setSatellite(on) }
    fun setImagery(url: String, attribution: String) = viewModelScope.launch { settings.setImagery(url, attribution) }
    fun setValhallaUrl(url: String?) = viewModelScope.launch { settings.setValhallaUrl(url) }
    fun setPhotonUrl(url: String?) = viewModelScope.launch { settings.setPhotonUrl(url) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setCourseUp(value: Boolean) = viewModelScope.launch { settings.setCourseUp(value) }
}
