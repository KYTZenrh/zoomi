package com.zoomi.app.map

import org.json.JSONArray
import org.json.JSONObject

/** A raster imagery source for satellite view. */
data class Imagery(
    val tileUrl: String,          // XYZ template with {z} {x} {y}
    val attribution: String,      // shown on the map, required by every imagery licence
    val maxZoom: Int = 19,
    val tileSize: Int = 256,
    /** Provider logo that must be shown on the map (MapTiler free plan), or null. */
    val logoRes: Int? = null,
) {
    companion object {
        /**
         * EOxCloudless 2024 (Sentinel-2, ~10 m per pixel) by EOX IT Services GmbH.
         * Free for non-commercial use under CC BY-NC-SA 4.0 with visible attribution
         * (cloudless.eox.at/documentation/license, checked 2026-11-12). Native detail ends at zoom 14;
         * MapLibre stretches it beyond, so streets look blurry up close.
         */
        val EOX = Imagery(
            tileUrl = "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2024_3857/default/g/{z}/{y}/{x}.jpg",
            attribution = "EOxCloudless https://cloudless.eox.at by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2024)",
            maxZoom = 14,
        )

        /**
         * Esri World Imagery with the user's OWN ArcGIS API key (Zoomi ships no key).
         * Sharp (sub-metre in many places). Esri requires "Powered by Esri" plus the data sources from the
         * item's Acknowledgments (read 2026-11-12), unobstructed; the layer must not be exported for offline use.
         * Free tier: 2M basemap tiles/month on ArcGIS Location Platform, billed beyond that (check your account).
         */
        /**
         * MapTiler Satellite (dataset satellite-v2) with the user's OWN free MapTiler key.
         * Free plan: personal/non-commercial use, no credit card, 100k requests/month and the service pauses
         * (no billing) when used up. Romania: ~1-2 m/px (Maxar); 5-50 cm aerial in parts of Western Europe.
         * Requires "© MapTiler" text and the MapTiler logo on the map (checked 2026-11-12).
         * [tileSize] is measured from a real tile when the key is saved (see ImageryProbe).
         */
        fun mapTiler(apiKey: String, tileSize: Int) = Imagery(
            tileUrl = "https://api.maptiler.com/tiles/satellite-v2/{z}/{x}/{y}.jpg?key=" +
                java.net.URLEncoder.encode(apiKey.trim(), "UTF-8"),
            attribution = "© MapTiler",
            maxZoom = 20,
            tileSize = tileSize,
            logoRes = com.zoomi.app.R.drawable.maptiler_logo,
        )

        fun esri(apiKey: String) = Imagery(
            tileUrl = "https://ibasemaps-api.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}?token=" +
                java.net.URLEncoder.encode(apiKey.trim(), "UTF-8"),
            attribution = "Powered by Esri · Sources: Esri, Vantor, Earthstar Geographics, and the GIS User Community",
            maxZoom = 19,
        )
    }
}

/**
 * Turns Zoomi's vector style into a "hybrid" satellite style: imagery underneath, land/water fills removed,
 * roads kept but translucent, labels white with a dark halo so they read on photos.
 */
object SatelliteStyle {
    private val FILL_SOURCE_LAYERS = setOf("landuse", "landcover", "park", "water", "building")

    fun apply(styleJson: String, imagery: Imagery): String {
        val style = JSONObject(styleJson)
        style.getJSONObject("sources").put("imagery", JSONObject().apply {
            put("type", "raster")
            put("tiles", JSONArray().put(imagery.tileUrl))
            put("tileSize", imagery.tileSize)
            put("maxzoom", imagery.maxZoom)
            put("attribution", imagery.attribution)
        })
        val layers = style.getJSONArray("layers")
        val out = JSONArray()
        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            val sourceLayer = l.optString("source-layer")
            if (l.optString("type") == "fill" && sourceLayer in FILL_SOURCE_LAYERS) continue
            when (l.optString("type")) {
                "background" -> {
                    out.put(l)
                    out.put(JSONObject().apply {
                        put("id", "imagery")
                        put("type", "raster")
                        put("source", "imagery")
                    })
                    continue
                }
                "line" -> if (l.optString("id").startsWith("road") || l.optString("id") == "road-tunnel") {
                    l.optJSONObject("paint")?.put("line-opacity", 0.6)
                }
                "symbol" -> l.optJSONObject("paint")?.apply {
                    put("text-color", "#FFFFFF")
                    put("text-halo-color", "#101010")
                    put("text-halo-width", 1.8)
                }
            }
            out.put(l)
        }
        style.put("layers", out)
        return style.toString()
    }
}
