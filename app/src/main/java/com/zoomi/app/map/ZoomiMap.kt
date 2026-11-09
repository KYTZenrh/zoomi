package com.zoomi.app.map

import android.graphics.RectF
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private const val PUCK_SOURCE = "zoomi-puck"
private const val PUCK_DOT = "zoomi-puck-dot"
private const val PUCK_ARROW = "zoomi-puck-arrow-layer"

/**
 * The map. Handles style (light/dark), the position marker, follow-camera, and
 * reports whether the road under the vehicle is drawn as a tunnel.
 */
@Composable
fun ZoomiMap(
    dark: Boolean,
    location: Location?,
    course: Float?,
    follow: Boolean,
    courseUp: Boolean,
    brandColor: Int,
    onUserPanned: () -> Unit,
    onTunnelRoad: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val pannedCallback by rememberUpdatedState(onUserPanned)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false // own "© OpenStreetMap" label opens full credits
            m.uiSettings.isCompassEnabled = false // our own compass button handles orientation
            m.uiSettings.isRotateGesturesEnabled = true
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) pannedCallback()
            }
            m.cameraPosition = CameraPosition.Builder().target(LatLng(45.9432, 24.9668)).zoom(5.5).build()
            map = m
        }
    }

    // (Re)load style when the theme changes; the puck layers are re-added each time.
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        val json = ZoomiMapStyle.json(if (dark) ZoomiMapStyle.DARK else ZoomiMapStyle.LIGHT)
        m.setStyle(Style.Builder().fromJson(json)) { s ->
            s.addImage(PuckIcons.ARROW, PuckIcons.arrow(context.resources.displayMetrics.density, brandColor))
            s.addSource(GeoJsonSource(PUCK_SOURCE))
            s.addLayer(
                CircleLayer(PUCK_DOT, PUCK_SOURCE).withProperties(
                    PropertyFactory.circleRadius(9f),
                    PropertyFactory.circleColor(brandColor),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f),
                ).withFilter(Expression.not(Expression.has("bearing")))
            )
            s.addLayer(
                SymbolLayer(PUCK_ARROW, PUCK_SOURCE).withProperties(
                    PropertyFactory.iconImage(PuckIcons.ARROW),
                    PropertyFactory.iconRotate(Expression.get("bearing")),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ).withFilter(Expression.has("bearing"))
            )
            style = s
        }
    }

    // Marker + camera + tunnel query on every new fix.
    LaunchedEffect(style, location, course, follow, courseUp) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        val point = Point.fromLngLat(loc.longitude, loc.latitude)
        val feature = Feature.fromGeometry(point)
        if (course != null) feature.addNumberProperty("bearing", course)
        s.getSourceAs<GeoJsonSource>(PUCK_SOURCE)?.setGeoJson(feature)

        if (follow) {
            val builder = CameraPosition.Builder()
                .target(LatLng(loc.latitude, loc.longitude))
                .zoom(maxOf(m.cameraPosition.zoom, 15.5))
            if (courseUp && course != null) builder.bearing(course.toDouble()).tilt(45.0)
            else builder.bearing(0.0).tilt(0.0)
            m.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()), 900)
        }
        onTunnelRoad(if (follow) queryTunnel(m, loc) else null)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** True if a tunnel road is drawn within ~6 dp of the vehicle, false if not, null if unknown. */
private fun queryTunnel(map: MapLibreMap, loc: Location): Boolean? {
    val p = map.projection.toScreenLocation(LatLng(loc.latitude, loc.longitude))
    val r = 6f * android.content.res.Resources.getSystem().displayMetrics.density
    val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
    if (map.cameraPosition.zoom < 13.0) return null
    if (!map.projection.visibleRegion.latLngBounds.contains(LatLng(loc.latitude, loc.longitude))) return null
    val tunnels = map.queryRenderedFeatures(box, ZoomiMapStyle.LAYER_ROAD_TUNNEL)
    return tunnels.isNotEmpty()
}
