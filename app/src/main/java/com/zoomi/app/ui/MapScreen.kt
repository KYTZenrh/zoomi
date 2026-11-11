package com.zoomi.app.ui

import android.content.Intent
import android.location.Location
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationState
import com.zoomi.app.R
import com.zoomi.app.TripUi
import com.zoomi.app.map.CameraMode
import com.zoomi.app.map.Puck
import com.zoomi.app.map.ZoomiMap
import com.zoomi.app.nav.SpeedLimit
import com.zoomi.core.places.PlaceKind
import com.zoomi.core.search.Place
import org.maplibre.android.geometry.LatLng
import uniffi.ferrostar.TripState
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    dark: Boolean,
    tilesUrl: String,
    imagery: com.zoomi.app.map.Imagery?,
    onToggleSatellite: () -> Unit,
    online: Boolean,
    inRegion: Boolean,
    positionEstimated: Boolean,
    location: Location?,
    course: Float?,
    courseUp: Boolean,
    gpsEnabled: Boolean,
    hasPermission: Boolean,
    trip: TripUi,
    navState: NavigationState,
    speedLimit: SpeedLimit?,
    voiceMuted: Boolean,
    onRequestPermission: () -> Unit,
    onToggleCourseUp: () -> Unit,
    onTunnelRoad: (Boolean?) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onLongPress: (Double, Double) -> Unit,
    onMode: (com.zoomi.core.nav.TravelMode) -> Unit,
    onSelectRoute: (Int) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onSavePlace: (Place, PlaceKind, String) -> Unit,
    onCloseTrip: () -> Unit,
    onToggleMute: () -> Unit,
    onShareEta: () -> Unit,
) {
    var userPanned by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val navigating = trip as? TripUi.Navigating
    val nav = navState.tripState as? TripState.Navigating

    // Reset free-pan when the trip state changes (e.g. preview -> navigation).
    LaunchedEffect(trip::class) { userPanned = false }

    val cameraMode = when {
        userPanned -> CameraMode.FREE
        trip is TripUi.Preview -> CameraMode.OVERVIEW
        navigating != null -> CameraMode.NAVIGATE
        else -> CameraMode.FOLLOW
    }

    // While navigating, draw the vehicle snapped to the route with the route's course.
    val puck = if (navigating != null && nav != null) {
        val s = nav.snappedUserLocation
        Puck(s.coordinates.lat, s.coordinates.lng, s.courseOverGround?.degrees?.toFloat() ?: course,
            location?.takeIf { it.hasAccuracy() }?.accuracy)
    } else location?.let { Puck(it.latitude, it.longitude, course, it.takeIf { l -> l.hasAccuracy() }?.accuracy) }

    // While navigating, draw only the road still ahead: the part already driven disappears.
    val routePolyline = androidx.compose.runtime.remember(navState.routeGeometry) {
        navState.routeGeometry.takeIf { it.size >= 2 }
            ?.let { g -> com.zoomi.core.location.Polyline(g.map { com.zoomi.core.location.LatLon(it.lat, it.lng) }) }
    }
    val remaining = if (navigating != null && nav != null && routePolyline != null) {
        val here = nav.snappedUserLocation.coordinates
        routePolyline.sliceFrom(routePolyline.project(com.zoomi.core.location.LatLon(here.lat, here.lng)))
            .map { LatLng(it.lat, it.lon) }
    } else null

    val routes: List<List<LatLng>> = when (trip) {
        is TripUi.Preview ->
            if (trip.mode == com.zoomi.core.nav.TravelMode.TRANSIT) trip.itineraries.map { it.geometry.map { p -> LatLng(p.lat, p.lon) } }
            else trip.routes.map { r -> r.route.geometry.map { LatLng(it.lat, it.lng) } }
        is TripUi.Navigating -> listOf(remaining ?: navState.routeGeometry.map { LatLng(it.lat, it.lng) })
        else -> emptyList()
    }
    val selected = (trip as? TripUi.Preview)?.selected ?: 0
    val destination = when (trip) {
        is TripUi.Preview -> LatLng(trip.destination.lat, trip.destination.lon)
        is TripUi.Navigating -> LatLng(trip.destination.lat, trip.destination.lon)
        else -> null
    }
    val bottomPadPx = with(LocalDensity.current) { 300.dp.roundToPx() }

    Box(Modifier.fillMaxSize()) {
        ZoomiMap(
            dark = dark,
            tilesUrl = tilesUrl,
            imagery = imagery,
            puck = puck,
            cameraMode = cameraMode,
            courseUp = courseUp,
            routes = routes,
            selectedRoute = selected,
            destination = destination,
            brandColor = MaterialTheme.colorScheme.primary.toArgb(),
            overviewPaddingBottomPx = bottomPadPx,
            onUserPanned = { userPanned = true },
            onTunnelRoad = onTunnelRoad,
            onLongPress = { if (navigating == null) onLongPress(it.latitude, it.longitude) },
            onRouteTap = onSelectRoute,
            modifier = Modifier.fillMaxSize(),
        )

        val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)

        // Connectivity / position-quality pills (shown in every state, under the top banner).
        Column(insets.align(Alignment.CenterEnd).padding(end = 12.dp), horizontalAlignment = Alignment.End) {
            if (!online) Pill(if (inRegion) "Offline · using downloaded region" else "Offline · no region here", null)
            if (positionEstimated) Pill("GPS lost · position estimated", null)
        }

        if (navigating != null && nav != null) {
            NavigationOverlay(
                trip = nav,
                rerouting = navState.isCalculatingNewRoute,
                speedLimit = speedLimit,
                currentSpeedKmh = location?.takeIf { it.hasSpeed() }?.let { mpsToKmh(it.speed) },
                muted = voiceMuted,
                onToggleMute = onToggleMute,
                onShareEta = onShareEta,
                onEnd = onCloseTrip,
            )
            if (userPanned) {
                RoundButton(R.drawable.ic_locate, "Back to navigation", highlighted = true,
                    modifier = insets.align(Alignment.CenterEnd).padding(end = 16.dp), onClick = { userPanned = false })
            }
            return@Box
        }

        // Top: search bar (idle) + status pills.
        Column(insets.align(Alignment.TopCenter).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (trip is TripUi.Idle && hasPermission) {
                Surface(
                    onClick = onOpenSearch,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 8.dp,
                ) {
                    Text("Where to?", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp))
                }
            }
            val status: String? = when {
                !hasPermission -> null
                !gpsEnabled -> "Location is off"
                location == null -> "Searching for GPS…"
                else -> null
            }
            if (status != null) {
                Pill(status, onClick = if (!gpsEnabled) {
                    { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                } else null)
            }
            if (location != null && location.hasAccuracy() && location.accuracy > 30f) {
                Pill("GPS weak · ±${location.accuracy.roundToInt()} m", null)
            }
        }

        if (!hasPermission) {
            Surface(
                insets.align(Alignment.Center).padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp, shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Zoomi needs precise location", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Your position comes from the phone's GPS chip. It stays on the device, except the " +
                            "start point of a route you request, which is sent to the routing server.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRequestPermission) { Text("Allow location") }
                }
            }
        }

        when (trip) {
            is TripUi.Preview -> RoutePreviewCard(
                preview = trip,
                onMode = onMode,
                onSelect = onSelectRoute,
                onStart = onStart,
                onRetry = onRetry,
                onSave = onSavePlace,
                onClose = onCloseTrip,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
            )
            is TripUi.Arrived -> ArrivedCard(trip.destination.title, onDone = onCloseTrip,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
            else -> {
                Column(
                    insets.align(Alignment.BottomEnd).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    RoundButton(
                        icon = R.drawable.ic_compass,
                        label = if (courseUp) "Heading up (tap for north up)" else "North up (tap for heading up)",
                        rotation = if (courseUp) -(course ?: 0f) else 0f,
                        onClick = onToggleCourseUp,
                    )
                    RoundButton(R.drawable.ic_layers, if (imagery != null) "Map view (tap for standard)" else "Satellite view",
                        highlighted = imagery != null, onClick = onToggleSatellite)
                    RoundButton(R.drawable.ic_settings, "Settings", onClick = onOpenSettings)
                    if (userPanned) {
                        RoundButton(R.drawable.ic_locate, "Follow my position", highlighted = true, onClick = { userPanned = false })
                    }
                }
                Surface(
                    // Stays left of the round buttons so credits are never covered (Esri/EOX require that).
                    modifier = insets.align(Alignment.BottomStart).padding(start = 8.dp, end = 88.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    onClick = onOpenAbout,
                ) {
                    Text("© OpenStreetMap" + if (imagery != null) " · " + imagery.attribution else "",
                        fontSize = 12.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: (() -> Unit)?) {
    val mod = Modifier.padding(top = 8.dp)
    val content: @Composable () -> Unit = {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
    if (onClick != null) {
        Surface(modifier = mod, shape = CircleShape, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary, onClick = onClick, content = content)
    } else {
        Surface(mod, shape = CircleShape, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface, content = content)
    }
}

@Composable
internal fun RoundButton(
    icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(60.dp),
        shape = CircleShape,
        shadowElevation = 6.dp,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon), contentDescription = label,
                modifier = Modifier.size(28.dp).rotate(rotation),
                tint = if (icon == R.drawable.ic_compass) Color.Unspecified else LocalContentColor.current,
            )
        }
    }
}
