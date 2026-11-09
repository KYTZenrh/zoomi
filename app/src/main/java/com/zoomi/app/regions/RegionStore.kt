package com.zoomi.app.regions

import android.content.Context
import com.zoomi.core.regions.RegionManifest
import com.zoomi.core.regions.RegionManifests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** An installed region: manifest + the files actually present on disk. */
data class InstalledRegion(val manifest: RegionManifest, val dir: File) {
    val mapFile: File? get() = manifest.mapFile?.let { File(dir, it.name) }?.takeIf { it.isFile }
    val routingFile: File? get() = manifest.routingFile?.let { File(dir, it.name) }?.takeIf { it.isFile }
    val sizeOnDisk: Long get() = dir.listFiles()?.sumOf { it.length() } ?: 0L
}

/**
 * Offline regions live in the app's external files dir:
 *   Android/data/com.zoomi.app/files/regions/<id>/{region.json, map.pmtiles, routing.tar}
 * (app-private, removed on uninstall). Files must be written by the app itself: on Android 11+ the app
 * cannot read folders that another user (e.g. adb) created there, so imports go through [RegionImporter].
 * A region counts as installed only once region.json exists; downloads write it last.
 */
class RegionStore(context: Context) {
    val root: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "regions").apply { mkdirs() }

    private val _installed = MutableStateFlow<List<InstalledRegion>>(emptyList())
    val installed: StateFlow<List<InstalledRegion>> = _installed.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _installed.value = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val m = File(dir, "region.json").takeIf { it.isFile }?.readText()?.let(RegionManifests::parseOne)
            m?.takeIf { it.id == dir.name }?.let { InstalledRegion(it, dir) }
        }?.sortedBy { it.manifest.name } ?: emptyList()
    }

    /** Region whose map covers this point (smallest first). */
    fun mapRegionAt(lat: Double, lon: Double): InstalledRegion? =
        _installed.value.filter { it.mapFile != null && it.manifest.bbox.contains(lat, lon) }
            .minByOrNull { (it.manifest.bbox.maxLon - it.manifest.bbox.minLon) * (it.manifest.bbox.maxLat - it.manifest.bbox.minLat) }

    /** Region with routing data covering all points. */
    fun routingRegionFor(points: List<Pair<Double, Double>>): InstalledRegion? {
        val withRouting = _installed.value.filter { it.routingFile != null }
        val m = RegionManifests.covering(withRouting.map { it.manifest }, points) ?: return null
        return withRouting.first { it.manifest.id == m.id }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(root, id).takeIf { it.canonicalPath.startsWith(root.canonicalPath) }?.deleteRecursively()
        refresh()
    }
}
