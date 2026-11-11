package com.zoomi.app.map

import android.graphics.PointF
import android.graphics.RectF
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val PUCK_SOURCE = "zoomi-puck"
private const val PUCK_DOT = "zoomi-puck-dot"
private const val PUCK_ACCURACY = "zoomi-puck-accuracy"
private const val PUCK_ARROW = "zoomi-puck-arrow-layer"
private const val ROUTE_SOURCE = "zoomi-routes"
private const val ROUTE_ALT = "zoomi-route-alt"
private const val ROUTE_ALT_CASING = "zoomi-route-alt-casing"
private const val ROUTE_SEL = "zoomi-route-sel"
private const val ROUTE_SEL_CASING = "zoomi-route-sel-casing"
private const val DEST_SOURCE = "zoomi-dest"
private const val DEST_LAYER = "zoomi-dest-layer"

/**
 * Where to draw the vehicle. [course] null = plain dot (no trustworthy heading).
 * [accuracyM] draws the GPS accuracy circle around it (the true position is likely inside).
 */
data class Puck(val lat: Double, val lon: Double, val course: Float?, val accuracyM: Float? = null)

/** Camera behaviour. */
enum class CameraMode { FOLLOW, FREE, OVERVIEW, NAVIGATE }

@Composable
fun ZoomiMap(
    dark: Boolean,
    tilesUrl: String,
    /** Non-null = satellite (hybrid) view with this imagery. */
    imagery: Imagery?,
    puck: Puck?,
    cameraMode: CameraMode,
    courseUp: Boolean,
    routes: List<List<LatLng>>,
    selectedRoute: Int,
    destination: LatLng?,
    brandColor: Int,
    overviewPaddingBottomPx: Int,
    onUserPanned: () -> Unit,
    onTunnelRoad: (Boolean?) -> Unit,
    onLongPress: (LatLng) -> Unit,
    onRouteTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val panned by rememberUpdatedState(onUserPanned)
    val longPress by rememberUpdatedState(onLongPress)
    val routeTap by rememberUpdatedState(onRouteTap)
    val density = context.resources.displayMetrics.density

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
            m.uiSettings.isCompassEnabled = false // own compass button handles orientation
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) panned()
            }
            m.addOnMapLongClickListener { latLng -> longPress(latLng); true }
            m.addOnMapClickListener { latLng ->
                val p = m.projection.toScreenLocation(latLng)
                val r = 16f * density
                val hit = m.queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), ROUTE_ALT)
                val idx = hit.firstOrNull()?.getNumberProperty("idx")?.toInt()
                if (idx != null) { routeTap(idx); true } else false
            }
            m.cameraPosition = CameraPosition.Builder().target(LatLng(45.9432, 24.9668)).zoom(5.5).build()
            map = m
        }
    }

    // (Re)load style when the theme changes; our own layers are re-added each time.
    LaunchedEffect(map, dark, tilesUrl, imagery) {
        val m = map ?: return@LaunchedEffect
        style = null
        val base = ZoomiMapStyle.json(if (dark) ZoomiMapStyle.DARK else ZoomiMapStyle.LIGHT, tilesUrl)
        val json = imagery?.let { SatelliteStyle.apply(base, it) } ?: base
        m.setStyle(Style.Builder().fromJson(json)) { s ->
            addOverlayLayers(s, density, brandColor, dark)
            style = s
        }
    }

    // Routes + destination.
    LaunchedEffect(style, routes, selectedRoute, destination) {
        val s = style ?: return@LaunchedEffect
        val features = routes.mapIndexedNotNull { i, pts ->
            if (pts.size < 2) return@mapIndexedNotNull null
            Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
                addNumberProperty("idx", i)
                addBooleanProperty("sel", i == selectedRoute)
            }
        }
        s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
        val dest = listOfNotNull(destination?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) })
        s.getSourceAs<GeoJsonSource>(DEST_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(dest))
    }

    // Overview: fit routes (or the destination) when they change.
    LaunchedEffect(map, routes, destination, cameraMode) {
        val m = map ?: return@LaunchedEffect
        if (cameraMode != CameraMode.OVERVIEW) return@LaunchedEffect
        m.setPadding(0, 0, 0, 0)
        val all = routes.flatten() + listOfNotNull(destination)
        if (all.size < 2) {
            destination?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15.0), 700) }
            return@LaunchedEffect
        }
        val bounds = LatLngBounds.Builder().includes(all).build()
        val pad = (48 * density).toInt()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, pad * 2, pad, overviewPaddingBottomPx + pad), 800)
    }

    // Marker + follow camera + tunnel query on every new position.
    LaunchedEffect(style, puck, cameraMode, courseUp) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        val p = puck ?: return@LaunchedEffect
        val feature = Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat))
        if (p.course != null) feature.addNumberProperty("bearing", p.course)
        // Accuracy radius in pixels at zoom 22 (MapLibre uses 512 px tiles: 78271.517 m/px at z0 on the equator).
        val metersPerPxZ22 = 78271.517 * Math.cos(Math.toRadians(p.lat)) / 4_194_304.0
        feature.addNumberProperty("r22", (p.accuracyM ?: 0f) / metersPerPxZ22)
        s.getSourceAs<GeoJsonSource>(PUCK_SOURCE)?.setGeoJson(feature)

        val navigating = cameraMode == CameraMode.NAVIGATE
        val follow = cameraMode == CameraMode.FOLLOW || navigating
        if (follow) {
            val builder = CameraPosition.Builder()
                .target(LatLng(p.lat, p.lon))
                .zoom(if (navigating) 17.0 else maxOf(m.cameraPosition.zoom, 15.5))
            if ((courseUp || navigating) && p.course != null) {
                builder.bearing(p.course.toDouble()).tilt(if (navigating) 50.0 else 45.0)
            } else {
                builder.bearing(0.0).tilt(0.0)
            }
            // While navigating, put the vehicle in the lower part of the screen to show more road ahead.
            if (navigating) m.setPadding(0, mapView.height / 3, 0, 0) else m.setPadding(0, 0, 0, 0)
            m.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()), 900)
        }
        onTunnelRoad(if (follow) queryTunnel(m, p, density) else null)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addOverlayLayers(s: Style, density: Float, brandColor: Int, dark: Boolean) {
    s.addImage(PuckIcons.ARROW, PuckIcons.arrow(density, brandColor))
    s.addSource(GeoJsonSource(ROUTE_SOURCE))
    s.addSource(GeoJsonSource(DEST_SOURCE))
    s.addSource(GeoJsonSource(PUCK_SOURCE))

    val width = Expression.interpolate(
        Expression.exponential(1.5f), Expression.zoom(),
        Expression.stop(8, 3f), Expression.stop(14, 7f), Expression.stop(18, 16f),
    )
    val casingWidth = Expression.interpolate(
        Expression.exponential(1.5f), Expression.zoom(),
        Expression.stop(8, 5f), Expression.stop(14, 10f), Expression.stop(18, 22f),
    )
    val isSel = Expression.eq(Expression.get("sel"), Expression.literal(true))
    val notSel = Expression.eq(Expression.get("sel"), Expression.literal(false))
    val altColor = if (dark) "#6B7385" else "#9AA3B5"
    val casingColor = if (dark) "#0B0E14" else "#FFFFFF"

    // Routes go under labels so street names stay readable.
    fun addBelowLabels(layer: Layer) {
        if (s.getLayer("road-label") != null) s.addLayerBelow(layer, "road-label") else s.addLayer(layer)
    }
    fun line(id: String, filter: Expression, color: String, w: Expression) =
        LineLayer(id, ROUTE_SOURCE).withFilter(filter).withProperties(
            PropertyFactory.lineColor(color), PropertyFactory.lineWidth(w),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    val brandHex = String.format("#%06X", 0xFFFFFF and brandColor)
    addBelowLabels(line(ROUTE_ALT_CASING, notSel, casingColor, casingWidth))
    addBelowLabels(line(ROUTE_ALT, notSel, altColor, width))
    addBelowLabels(line(ROUTE_SEL_CASING, isSel, casingColor, casingWidth))
    addBelowLabels(line(ROUTE_SEL, isSel, brandHex, width))

    s.addLayer(
        CircleLayer(DEST_LAYER, DEST_SOURCE).withProperties(
            PropertyFactory.circleRadius(10f), PropertyFactory.circleColor("#E5484D"),
            PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f),
        )
    )
    // GPS accuracy circle: scales with zoom so it covers the same ground area; never smaller than a halo.
    s.addLayer(
        CircleLayer(PUCK_ACCURACY, PUCK_SOURCE).withProperties(
            PropertyFactory.circleRadius(
                Expression.max(
                    Expression.literal(16f),
                    Expression.interpolate(
                        Expression.exponential(2f), Expression.zoom(),
                        Expression.stop(0, Expression.division(Expression.get("r22"), Expression.literal(4_194_304f))),
                        Expression.stop(22, Expression.get("r22")),
                    ),
                )
            ),
            PropertyFactory.circleColor(brandColor),
            PropertyFactory.circleOpacity(0.15f),
            PropertyFactory.circleStrokeColor(brandColor),
            PropertyFactory.circleStrokeOpacity(0.45f),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        )
    )
    s.addLayer(
        CircleLayer(PUCK_DOT, PUCK_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f), PropertyFactory.circleColor(brandColor),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE), PropertyFactory.circleStrokeWidth(3f),
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
}

/** True if a tunnel road is drawn within ~6 dp of the vehicle, false if not, null if unknown. */
private fun queryTunnel(map: MapLibreMap, p: Puck, density: Float): Boolean? {
    if (map.cameraPosition.zoom < 13.0) return null
    val ll = LatLng(p.lat, p.lon)
    if (!map.projection.visibleRegion.latLngBounds.contains(ll)) return null
    val pt: PointF = map.projection.toScreenLocation(ll)
    val r = 6f * density
    return map.queryRenderedFeatures(RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r), ZoomiMapStyle.LAYER_ROAD_TUNNEL).isNotEmpty()
}
