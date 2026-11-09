package com.zoomi.core.regions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class BBox(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    fun contains(lat: Double, lon: Double) = lat in minLat..maxLat && lon in minLon..maxLon
}

data class RegionFile(val name: String, val url: String?, val size: Long?, val sha256: String?)

/**
 * A downloadable / installed offline region: one map file (PMTiles, OpenMapTiles schema) and one
 * routing file (Valhalla tile extract .tar), both built from the same OSM extract.
 */
data class RegionManifest(
    val id: String,
    val name: String,
    val bbox: BBox,
    val osmDate: String,
    val valhallaVersion: String?,
    val files: List<RegionFile>,
) {
    val mapFile get() = files.firstOrNull { it.name.endsWith(".pmtiles") }
    val routingFile get() = files.firstOrNull { it.name.endsWith(".tar") }
    val totalSize get() = files.sumOf { it.size ?: 0L }
}

object RegionManifests {
    private val json = Json { ignoreUnknownKeys = true }
    private val ID = Regex("^[a-z0-9-]{1,40}$")
    private val FILE = Regex("^[A-Za-z0-9._-]{1,80}$")

    /** Parses one region object. Rejects unsafe ids / file names (they become paths on disk). */
    fun parse(o: JsonObject): RegionManifest? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val id = s("id")?.takeIf { ID.matches(it) } ?: return null
        val b = (o["bbox"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: return null
        if (b.size != 4 || b[0] >= b[2] || b[1] >= b[3]) return null
        val files = (o["files"] as? JsonArray)?.mapNotNull { f ->
            val fo = f as? JsonObject ?: return@mapNotNull null
            fun fs(k: String) = (fo[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val name = fs("name")?.takeIf { FILE.matches(it) && it != "region.json" } ?: return@mapNotNull null
            RegionFile(name, fs("url"), (fo["size"] as? JsonPrimitive)?.longOrNull, fs("sha256")?.lowercase())
        } ?: return null
        if (files.isEmpty()) return null
        return RegionManifest(id, s("name") ?: id, BBox(b[0], b[1], b[2], b[3]), s("osmDate") ?: "?", s("valhallaVersion"), files)
    }

    fun encode(r: RegionManifest): String = buildJsonObject {
        put("id", r.id); put("name", r.name)
        put("bbox", buildJsonArray { add(r.bbox.minLon); add(r.bbox.minLat); add(r.bbox.maxLon); add(r.bbox.maxLat) })
        put("osmDate", r.osmDate)
        r.valhallaVersion?.let { put("valhallaVersion", it) }
        put("files", buildJsonArray {
            r.files.forEach { f ->
                add(buildJsonObject {
                    put("name", f.name)
                    f.url?.let { put("url", it) }
                    f.size?.let { put("size", it) }
                    f.sha256?.let { put("sha256", it) }
                })
            }
        })
    }.toString()

    fun parseOne(text: String): RegionManifest? =
        runCatching { parse(json.parseToJsonElement(text) as JsonObject) }.getOrNull()

    /** Catalog: {"regions":[ ... ]} */
    fun parseCatalog(text: String): List<RegionManifest> = runCatching {
        val root = json.parseToJsonElement(text) as JsonObject
        root["regions"]!!.jsonArray.mapNotNull { (it as? JsonObject)?.let(::parse) }
    }.getOrDefault(emptyList())

    /** The region to use for a set of points: the smallest one containing all of them. */
    fun covering(regions: List<RegionManifest>, points: List<Pair<Double, Double>>): RegionManifest? =
        regions.filter { r -> points.all { (lat, lon) -> r.bbox.contains(lat, lon) } }
            .minByOrNull { (it.bbox.maxLon - it.bbox.minLon) * (it.bbox.maxLat - it.bbox.minLat) }
}
