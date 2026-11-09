package com.zoomi.app.map

/**
 * Zoomi's own map style, written for driving: few POIs, high-contrast roads, large labels.
 * Uses the OpenMapTiles vector schema served by OpenFreeMap (no key, no request limit).
 * Offline regions (milestone c) will use the same schema, so this style works for both.
 */
object ZoomiMapStyle {

    const val SOURCE = "openmaptiles"
    const val LAYER_ROAD = "road"
    const val LAYER_ROAD_TUNNEL = "road-tunnel"

    private const val TILEJSON = "https://tiles.openfreemap.org/planet"
    private const val GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"

    const val ATTRIBUTION =
        "<a href=\\\"https://openfreemap.org\\\">OpenFreeMap</a> " +
            "<a href=\\\"https://www.openmaptiles.org/\\\">&copy; OpenMapTiles</a> " +
            "Data from <a href=\\\"https://www.openstreetmap.org/copyright\\\">&copy; OpenStreetMap contributors</a>"

    data class Palette(
        val background: String,
        val residential: String,
        val park: String,
        val wood: String,
        val water: String,
        val building: String,
        val casing: String,
        val minor: String,
        val secondary: String,
        val primary: String,
        val motorway: String,
        val tunnel: String,
        val path: String,
        val rail: String,
        val ferry: String,
        val boundary: String,
        val text: String,
        val textHalo: String,
        val waterText: String,
    )

    val LIGHT = Palette(
        background = "#F4F1EA", residential = "#EEE9E0", park = "#D3EACB", wood = "#C6E2B8",
        water = "#A8D4F5", building = "#E2DCD2", casing = "#CFC6B8", minor = "#FFFFFF",
        secondary = "#FFF4CC", primary = "#FFE08A", motorway = "#FFC266", tunnel = "#ECE7DE",
        path = "#B9AE9C", rail = "#B0AAA2", ferry = "#4C8FD6", boundary = "#A99BC9",
        text = "#2E2B26", textHalo = "#FFFFFF", waterText = "#3D6E9E",
    )

    val DARK = Palette(
        background = "#12161F", residential = "#161B25", park = "#16251D", wood = "#14221B",
        water = "#1C3550", building = "#202632", casing = "#07090D", minor = "#3A4352",
        secondary = "#4A4F5C", primary = "#7A6A3C", motorway = "#A8803E", tunnel = "#2A303C",
        path = "#4B5160", rail = "#454B57", ferry = "#4C8FD6", boundary = "#6F6497",
        text = "#DCE2EC", textHalo = "#12161F", waterText = "#7FA8D0",
    )

    // Relative road width per class. Final width = factor * zoom curve.
    private const val WIDTH_FACTOR =
        """["match",["get","class"],"motorway",1.25,"trunk",1.1,"primary",1.0,"secondary",0.85,"tertiary",0.75,"minor",0.55,"service",0.35,0.4]"""

    private fun width(scale: Double) =
        """["interpolate",["exponential",1.6],["zoom"],5,["*",$WIDTH_FACTOR,${0.4 * scale}],10,["*",$WIDTH_FACTOR,${1.6 * scale}],14,["*",$WIDTH_FACTOR,${5.0 * scale}],18,["*",$WIDTH_FACTOR,${26.0 * scale}]]"""

    private fun roadColor(p: Palette) =
        """["match",["get","class"],"motorway","${p.motorway}","trunk","${p.primary}","primary","${p.primary}","secondary","${p.secondary}","tertiary","${p.secondary}","${p.minor}"]"""

    private const val CAR_ROADS =
        """["match",["get","class"],["motorway","trunk","primary","secondary","tertiary","minor","service"],true,false]"""

    fun json(p: Palette): String = """
{
  "version": 8,
  "name": "Zoomi",
  "sources": {
    "$SOURCE": { "type": "vector", "url": "$TILEJSON", "attribution": "$ATTRIBUTION" }
  },
  "glyphs": "$GLYPHS",
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "${p.background}" } },
    { "id": "landuse-residential", "type": "fill", "source": "$SOURCE", "source-layer": "landuse",
      "filter": ["==",["get","class"],"residential"], "paint": { "fill-color": "${p.residential}" } },
    { "id": "landcover-wood", "type": "fill", "source": "$SOURCE", "source-layer": "landcover",
      "filter": ["==",["get","class"],"wood"], "paint": { "fill-color": "${p.wood}" } },
    { "id": "landcover-grass", "type": "fill", "source": "$SOURCE", "source-layer": "landcover",
      "filter": ["match",["get","class"],["grass","farmland"],true,false],
      "paint": { "fill-color": "${p.park}", "fill-opacity": 0.5 } },
    { "id": "park", "type": "fill", "source": "$SOURCE", "source-layer": "park", "paint": { "fill-color": "${p.park}" } },
    { "id": "water", "type": "fill", "source": "$SOURCE", "source-layer": "water", "paint": { "fill-color": "${p.water}" } },
    { "id": "waterway", "type": "line", "source": "$SOURCE", "source-layer": "waterway", "minzoom": 8,
      "paint": { "line-color": "${p.water}", "line-width": ["interpolate",["linear"],["zoom"],8,0.5,16,3] } },
    { "id": "building", "type": "fill", "source": "$SOURCE", "source-layer": "building", "minzoom": 14,
      "paint": { "fill-color": "${p.building}", "fill-opacity": ["interpolate",["linear"],["zoom"],14,0,15,1] } },
    { "id": "boundary-country", "type": "line", "source": "$SOURCE", "source-layer": "boundary",
      "filter": ["all",["==",["get","admin_level"],2],["!=",["get","maritime"],1]],
      "paint": { "line-color": "${p.boundary}", "line-width": 1.5, "line-dasharray": [4,2] } },

    { "id": "ferry", "type": "line", "source": "$SOURCE", "source-layer": "transportation",
      "filter": ["==",["get","class"],"ferry"],
      "paint": { "line-color": "${p.ferry}", "line-width": 1.5, "line-dasharray": [3,2] } },
    { "id": "path", "type": "line", "source": "$SOURCE", "source-layer": "transportation", "minzoom": 15,
      "filter": ["match",["get","class"],["path","track"],true,false],
      "paint": { "line-color": "${p.path}", "line-width": 1.2, "line-dasharray": [2,1.5] } },
    { "id": "rail", "type": "line", "source": "$SOURCE", "source-layer": "transportation", "minzoom": 10,
      "filter": ["all",["==",["get","class"],"rail"],["!=",["get","brunnel"],"tunnel"]],
      "paint": { "line-color": "${p.rail}", "line-width": 1.2 } },

    { "id": "$LAYER_ROAD_TUNNEL", "type": "line", "source": "$SOURCE", "source-layer": "transportation",
      "filter": ["all",$CAR_ROADS,["==",["get","brunnel"],"tunnel"]],
      "layout": { "line-cap": "butt", "line-join": "round" },
      "paint": { "line-color": "${p.tunnel}", "line-width": ${width(1.0)}, "line-opacity": 0.9 } },
    { "id": "road-casing", "type": "line", "source": "$SOURCE", "source-layer": "transportation",
      "filter": ["all",$CAR_ROADS,["!=",["get","brunnel"],"tunnel"]],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "${p.casing}", "line-width": ${width(1.35)} } },
    { "id": "$LAYER_ROAD", "type": "line", "source": "$SOURCE", "source-layer": "transportation",
      "filter": ["all",$CAR_ROADS,["!=",["get","brunnel"],"tunnel"]],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": ${roadColor(p)}, "line-width": ${width(1.0)} } },

    { "id": "road-label", "type": "symbol", "source": "$SOURCE", "source-layer": "transportation_name", "minzoom": 13,
      "layout": { "symbol-placement": "line", "text-field": ["get","name"], "text-font": ["Noto Sans Regular"],
                  "text-size": ["interpolate",["linear"],["zoom"],13,11,18,16] },
      "paint": { "text-color": "${p.text}", "text-halo-color": "${p.textHalo}", "text-halo-width": 1.5 } },
    { "id": "water-label", "type": "symbol", "source": "$SOURCE", "source-layer": "water_name",
      "layout": { "text-field": ["get","name"], "text-font": ["Noto Sans Regular"], "text-size": 12 },
      "paint": { "text-color": "${p.waterText}", "text-halo-color": "${p.textHalo}", "text-halo-width": 1 } },
    { "id": "place-minor", "type": "symbol", "source": "$SOURCE", "source-layer": "place", "minzoom": 11,
      "filter": ["match",["get","class"],["suburb","village","hamlet","neighbourhood","quarter"],true,false],
      "layout": { "text-field": ["get","name"], "text-font": ["Noto Sans Regular"], "text-size": 13 },
      "paint": { "text-color": "${p.text}", "text-halo-color": "${p.textHalo}", "text-halo-width": 1.5 } },
    { "id": "place-major", "type": "symbol", "source": "$SOURCE", "source-layer": "place",
      "filter": ["match",["get","class"],["city","town"],true,false],
      "layout": { "text-field": ["get","name"], "text-font": ["Noto Sans Bold"],
                  "text-size": ["interpolate",["linear"],["zoom"],5,12,12,18] },
      "paint": { "text-color": "${p.text}", "text-halo-color": "${p.textHalo}", "text-halo-width": 2 } },
    { "id": "place-country", "type": "symbol", "source": "$SOURCE", "source-layer": "place", "maxzoom": 8,
      "filter": ["==",["get","class"],"country"],
      "layout": { "text-field": ["get","name"], "text-font": ["Noto Sans Bold"], "text-size": 14 },
      "paint": { "text-color": "${p.text}", "text-halo-color": "${p.textHalo}", "text-halo-width": 2 } }
  ]
}
""".trimIndent()
}
