package com.zoomi.core.places

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class PlaceKind { HOME, WORK, FAVORITE }

data class SavedPlace(
    val id: String,
    val kind: PlaceKind,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Saved places list rules + JSON codec (stored locally; synced end-to-end encrypted in milestone e).
 * Only one HOME and one WORK: saving a new one replaces the old.
 */
object SavedPlaces {
    private val json = Json { ignoreUnknownKeys = true }

    fun upsert(list: List<SavedPlace>, place: SavedPlace): List<SavedPlace> {
        val filtered = list.filter { it.id != place.id && (place.kind == PlaceKind.FAVORITE || it.kind != place.kind) }
        return sorted(filtered + place)
    }

    fun remove(list: List<SavedPlace>, id: String): List<SavedPlace> = list.filter { it.id != id }

    fun sorted(list: List<SavedPlace>): List<SavedPlace> =
        list.sortedWith(compareBy<SavedPlace> { it.kind.ordinal }.thenBy { it.name.lowercase() })

    fun encode(list: List<SavedPlace>): String = buildJsonArray {
        list.forEach { p ->
            add(buildJsonObject {
                put("id", p.id); put("kind", p.kind.name); put("name", p.name)
                put("address", p.address); put("lat", p.lat); put("lon", p.lon)
            })
        }
    }.toString()

    fun decode(text: String?): List<SavedPlace> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return sorted(arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content
            val kind = runCatching { PlaceKind.valueOf(s("kind") ?: "") }.getOrNull() ?: return@mapNotNull null
            SavedPlace(
                id = s("id") ?: return@mapNotNull null,
                kind = kind,
                name = s("name") ?: "",
                address = s("address") ?: "",
                lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
            )
        })
    }
}
