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

/** A border ring as flat lon,lat pairs; [hole] rings cut out of the area (e.g. an enclave). */
data class AreaRing(val hole: Boolean, val lonLat: DoubleArray) {
    /** Even-odd ray casting. */
    fun contains(lat: Double, lon: Double): Boolean {
        var inside = false
        val n = lonLat.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = lonLat[2 * i]; val yi = lonLat[2 * i + 1]
            val xj = lonLat[2 * j]; val yj = lonLat[2 * j + 1]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
    override fun equals(other: Any?) = other is AreaRing && hole == other.hole && lonLat.contentEquals(other.lonLat)
    override fun hashCode() = 31 * hole.hashCode() + lonLat.contentHashCode()
}

/** One piece of a file split for hosting (GitHub release assets must be < 2 GiB). */
data class RegionPart(val url: String, val size: Long)

/** [parts] when present: download them in order into one file; [sha256] is of the joined file. */
data class RegionFile(val name: String, val url: String?, val size: Long?, val sha256: String?, val parts: List<RegionPart>? = null)

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
    /** Continent id for grouping in the list (e.g. "europe"), null if unknown. */
    val group: String? = null,
    /** Real border (simplified), when the catalog has it; the bbox alone overlaps neighbours. */
    val area: List<AreaRing>? = null,
) {
    fun contains(lat: Double, lon: Double): Boolean {
        if (!bbox.contains(lat, lon)) return false
        val rings = area ?: return true
        return rings.any { !it.hole && it.contains(lat, lon) } && rings.none { it.hole && it.contains(lat, lon) }
    }

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
            val parts = (fo["parts"] as? JsonArray)?.mapNotNull { p ->
                val po = p as? JsonObject ?: return@mapNotNull null
                val u = (po["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
                val sz = (po["size"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
                RegionPart(u, sz)
            }?.takeIf { it.isNotEmpty() }
            RegionFile(name, fs("url"), (fo["size"] as? JsonPrimitive)?.longOrNull, fs("sha256")?.lowercase(), parts)
        } ?: return null
        if (files.isEmpty()) return null
        val area = (o["area"] as? JsonArray)?.mapNotNull { r ->
            val ro = r as? JsonObject ?: return@mapNotNull null
            val pts = (ro["ring"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull }?.toDoubleArray() ?: return@mapNotNull null
            if (pts.size < 6 || pts.size % 2 != 0) return@mapNotNull null
            AreaRing((ro["hole"] as? JsonPrimitive)?.content == "true", pts)
        }?.takeIf { rings -> rings.any { !it.hole } }
        return RegionManifest(id, s("name") ?: id, BBox(b[0], b[1], b[2], b[3]), s("osmDate") ?: "?", s("valhallaVersion"), files, s("group"), area)
    }

    fun encode(r: RegionManifest): String = buildJsonObject {
        put("id", r.id); put("name", r.name)
        put("bbox", buildJsonArray { add(r.bbox.minLon); add(r.bbox.minLat); add(r.bbox.maxLon); add(r.bbox.maxLat) })
        put("osmDate", r.osmDate)
        r.valhallaVersion?.let { put("valhallaVersion", it) }
        r.group?.let { put("group", it) }
        r.area?.let { rings ->
            put("area", buildJsonArray {
                rings.forEach { ring -> add(buildJsonObject { put("hole", ring.hole); put("ring", buildJsonArray { ring.lonLat.forEach { add(it) } }) }) }
            })
        }
        put("files", buildJsonArray {
            r.files.forEach { f ->
                add(buildJsonObject {
                    put("name", f.name)
                    f.url?.let { put("url", it) }
                    f.size?.let { put("size", it) }
                    f.sha256?.let { put("sha256", it) }
                    f.parts?.let { ps -> put("parts", buildJsonArray { ps.forEach { p -> add(buildJsonObject { put("url", p.url); put("size", p.size) }) } }) }
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
        regions.filter { r -> points.all { (lat, lon) -> r.contains(lat, lon) } }
            .minByOrNull { (it.bbox.maxLon - it.bbox.minLon) * (it.bbox.maxLat - it.bbox.minLat) }
}
