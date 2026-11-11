package com.zoomi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomi.app.ui.AboutScreen
import com.zoomi.app.ui.MapScreen
import com.zoomi.app.ui.RegionsScreen
import androidx.compose.runtime.remember
import com.zoomi.app.ui.SearchScreen
import com.zoomi.app.ui.SettingsScreen
import com.zoomi.app.ui.ZoomiTheme
import org.maplibre.android.MapLibre

private enum class Screen { MAP, SEARCH, SETTINGS, ABOUT, REGIONS, VOICE }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val tripVm: TripViewModel by viewModels()
    private val regionsVm: RegionsViewModel by viewModels()
    private val voiceVm: VoiceViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermissionResult() }

    /** Notification permission is only needed to SHOW the navigation notification; guidance works without it. */
    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { tripVm.startNavigation() }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(regionsVm::importFrom) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext)
        val hasLightSensor = getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_LIGHT) != null

        setContent {
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) { vm.systemDark = systemDark }

            val decision by vm.decision.collectAsStateWithLifecycle()
            val dark = decision.dark
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }

            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val location by vm.location.collectAsStateWithLifecycle()
            val course by vm.course.collectAsStateWithLifecycle()
            val courseUp by vm.courseUp.collectAsStateWithLifecycle()
            val gpsEnabled by vm.gpsEnabled.collectAsStateWithLifecycle()
            val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
            val diagnostics by vm.diagnostics.collectAsStateWithLifecycle()
            val valhallaUrl by vm.valhallaUrl.collectAsStateWithLifecycle()
            val photonUrl by vm.photonUrl.collectAsStateWithLifecycle()

            val trip by tripVm.trip.collectAsStateWithLifecycle()
            val search by tripVm.search.collectAsStateWithLifecycle()
            val saved by tripVm.savedPlaces.collectAsStateWithLifecycle()
            val navState by tripVm.navState.collectAsStateWithLifecycle()
            val speedLimit by tripVm.speedLimit.collectAsStateWithLifecycle()
            val muted by tripVm.voiceMuted.collectAsStateWithLifecycle()
            var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
            val online by zoomi.network.online.collectAsStateWithLifecycle()
            val installed by regionsVm.installed.collectAsStateWithLifecycle()
            val estimated by vm.positionEstimated.collectAsStateWithLifecycle()
            val satellite by vm.satellite.collectAsStateWithLifecycle()
            val imagery by vm.imagery.collectAsStateWithLifecycle()
            // Inside a downloaded region the map is drawn from the local file (works offline, saves data).
            val mapRegion = remember(location?.latitude?.times(100)?.toInt(), location?.longitude?.times(100)?.toInt(), installed) {
                location?.let { zoomi.regions.mapRegionAt(it.latitude, it.longitude) }
            }
            val tilesUrl = mapRegion?.mapFile?.let { com.zoomi.app.map.ZoomiMapStyle.localTiles(it.absolutePath) }
                ?: com.zoomi.app.map.ZoomiMapStyle.ONLINE_TILES

            LaunchedEffect(Unit) { if (!vm.gnss.hasPermission()) requestLocation() }

            // Keep the screen on while navigating.
            val navigating = trip is TripUi.Navigating
            LaunchedEffect(navigating) {
                if (navigating) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            ZoomiTheme(dark) {
                // The map stays composed underneath so it isn't recreated when coming back.
                MapScreen(
                    dark = dark,
                    tilesUrl = tilesUrl,
                    imagery = if (satellite) imagery else null,
                    onToggleSatellite = { vm.setSatellite(!satellite) },
                    online = online,
                    inRegion = mapRegion != null,
                    positionEstimated = estimated,
                    location = location,
                    course = course,
                    courseUp = courseUp,
                    gpsEnabled = gpsEnabled,
                    hasPermission = hasPermission,
                    trip = trip,
                    navState = navState,
                    speedLimit = speedLimit,
                    voiceMuted = muted,
                    onRequestPermission = ::requestLocation,
                    onToggleCourseUp = { vm.setCourseUp(!courseUp) },
                    onTunnelRoad = { vm.onTunnelRoad = it },
                    onOpenSearch = { screen = Screen.SEARCH },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenAbout = { screen = Screen.ABOUT },
                    onLongPress = { lat, lon -> tripVm.chooseMapPoint(lat, lon) },
                    onMode = tripVm::setMode,
                    onSelectRoute = tripVm::selectRoute,
                    onStart = ::startNavigation,
                    onRetry = tripVm::retryRoutes,
                    onSavePlace = { p, k, n -> tripVm.savePlace(p, k, n) },
                    onCloseTrip = tripVm::endTrip,
                    onToggleMute = { tripVm.setVoiceMuted(!muted) },
                    onShareEta = ::shareEta,
                )
                if (navigating) BackHandler { /* don't leave navigation by accident; use End */ }
                else if (trip !is TripUi.Idle && screen == Screen.MAP) BackHandler { tripVm.endTrip() }

                when (screen) {
                    Screen.MAP -> Unit
                    Screen.SEARCH -> {
                        BackHandler { screen = Screen.MAP }
                        SearchScreen(
                            search = search,
                            saved = saved,
                            onQuery = tripVm::onQueryChange,
                            onPick = { tripVm.chooseDestination(it); screen = Screen.MAP },
                            onPickSaved = { tripVm.goTo(it); screen = Screen.MAP },
                            onDeleteSaved = { tripVm.deletePlace(it.id) },
                            onBack = { screen = Screen.MAP },
                        )
                    }
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.MAP }
                        SettingsScreen(
                            mode = mode,
                            decision = decision,
                            diagnostics = diagnostics,
                            lightSensorAvailable = hasLightSensor,
                            valhallaUrl = valhallaUrl,
                            photonUrl = photonUrl,
                            onModeChange = vm::setThemeMode,
                            onValhallaUrl = vm::setValhallaUrl,
                            onPhotonUrl = vm::setPhotonUrl,
                            onOpenAbout = { screen = Screen.ABOUT },
                            onOpenRegions = { screen = Screen.REGIONS },
                            onOpenVoice = { screen = Screen.VOICE },
                            satellite = satellite,
                            imageryUrl = vm.imageryCustomUrl.collectAsStateWithLifecycle().value,
                            imageryAttribution = vm.imageryCustomAttribution.collectAsStateWithLifecycle().value,
                            onSatellite = vm::setSatellite,
                            onImagery = vm::setImagery,
                            imagerySource = vm.imagerySource.collectAsStateWithLifecycle().value,
                            esriKey = vm.esriKey.collectAsStateWithLifecycle().value,
                            onImagerySource = vm::setImagerySource,
                            onEsriKey = vm::setEsriKey,
                            onBack = { screen = Screen.MAP },
                        )
                    }
                    Screen.VOICE -> {
                        BackHandler { screen = Screen.SETTINGS }
                        val voiceUi by voiceVm.ui.collectAsStateWithLifecycle()
                        com.zoomi.app.ui.VoiceScreen(
                            ui = voiceUi,
                            onLanguage = voiceVm::setLanguage,
                            onEngine = voiceVm::setEngine,
                            onVoice = voiceVm::setVoice,
                            onTest = voiceVm::test,
                            onRefresh = voiceVm::refresh,
                            onBack = { screen = Screen.SETTINGS },
                        )
                    }
                    Screen.REGIONS -> {
                        BackHandler { screen = Screen.SETTINGS }
                        val catalogUrl by regionsVm.catalogUrl.collectAsStateWithLifecycle()
                        val catalog by regionsVm.catalog.collectAsStateWithLifecycle()
                        val progress by regionsVm.progress.collectAsStateWithLifecycle()
                        val importState by regionsVm.importState.collectAsStateWithLifecycle()
                        RegionsScreen(
                            installed = installed,
                            catalogUrl = catalogUrl,
                            catalog = catalog,
                            progress = progress,
                            import = importState,
                            onImport = { importLauncher.launch(null) },
                            onCancelImport = regionsVm::cancelImport,
                            onCatalogUrl = regionsVm::setCatalogUrl,
                            onLoadCatalog = regionsVm::loadCatalog,
                            onDownload = regionsVm::download,
                            onCancel = regionsVm::cancel,
                            onDelete = regionsVm::delete,
                            onRescan = regionsVm::rescan,
                            onBack = { screen = Screen.SETTINGS },
                        )
                    }
                    Screen.ABOUT -> {
                        BackHandler { screen = Screen.MAP }
                        AboutScreen(onBack = { screen = Screen.MAP })
                    }
                }
            }
        }
    }

    private fun requestLocation() {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun startNavigation() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) // starts navigation in the callback
        } else {
            tripVm.startNavigation()
        }
    }

    private fun shareEta() {
        val text = tripVm.etaShareText() ?: return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share ETA"))
    }

    override fun onStart() {
        super.onStart()
        vm.onStart()
    }

    override fun onStop() {
        vm.onStop(keepGps = tripVm.trip.value is TripUi.Navigating)
        super.onStop()
    }
}
