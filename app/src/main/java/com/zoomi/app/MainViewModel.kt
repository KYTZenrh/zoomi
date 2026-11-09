package com.zoomi.app

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zoomi.app.data.SettingsRepository
import com.zoomi.app.location.GnssLocationSource
import com.zoomi.app.sensors.LightSensorSource
import com.zoomi.core.location.CourseTracker
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
    private val settings = SettingsRepository(app)
    val gnss = GnssLocationSource(app)
    private val light = LightSensorSource(app)
    private val controller = AutoThemeController()
    private val courseTracker = CourseTracker()

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTO)
    val courseUp: StateFlow<Boolean> =
        settings.courseUp.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val location: StateFlow<Location?> = gnss.location
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
            gnss.location.collect { loc ->
                if (loc != null) {
                    _course.value = courseTracker.update(
                        if (loc.hasSpeed()) loc.speed else null,
                        if (loc.hasBearing()) loc.bearing else null,
                        if (loc.hasBearingAccuracy()) loc.bearingAccuracyDegrees else null,
                    )
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

    fun onPermissionResult() {
        _hasPermission.value = gnss.hasPermission()
        if (_hasPermission.value) gnss.start()
    }

    fun onStart() {
        _hasPermission.value = gnss.hasPermission()
        gnss.start()
        light.start()
    }

    fun onStop() {
        gnss.stop()
        light.stop()
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setCourseUp(value: Boolean) = viewModelScope.launch { settings.setCourseUp(value) }
}
