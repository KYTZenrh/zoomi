package com.zoomi.app.regions

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.zoomi.core.regions.RegionFile
import com.zoomi.core.regions.RegionManifest
import com.zoomi.core.regions.RegionManifests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Imports regions the user picked with Android's pickers (Storage Access Framework):
 *  - files: the files of one or more countries, e.g. straight from Downloads as published on the
 *    "regions" release page (romania.region.json, romania-map.pmtiles, romania-routing.tar, and
 *    .001/.002 pieces for very big files), or
 *  - a folder with region.json, map.pmtiles, routing.tar (the data-pipeline layout).
 * Files are copied into Zoomi's storage (pieces joined) and verified against the SHA-256 in the manifest.
 * region.json is written last, so an interrupted import never looks installed.
 */
class RegionImporter(private val context: Context, private val store: RegionStore) {

    /** Folder picked with the folder picker. */
    suspend fun import(treeUri: Uri, onProgress: (done: Long, total: Long) -> Unit): String = withContext(Dispatchers.IO) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val docs = mutableMapOf<String, Uri>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) docs[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
        }
        importDocs(docs, onProgress)
    }

    /** Files picked with the multi-file picker. */
    suspend fun importFiles(uris: List<Uri>, onProgress: (done: Long, total: Long) -> Unit): String = withContext(Dispatchers.IO) {
        val docs = uris.associateBy { u ->
            context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: u.lastPathSegment.orEmpty()
        }
        importDocs(docs, onProgress)
    }

    /** Imports every region whose manifest is among [docs] (display name -> uri). Returns their names. */
    private suspend fun importDocs(docs: Map<String, Uri>, onProgress: (Long, Long) -> Unit): String {
        val cr = context.contentResolver
        val manifests = docs.filterKeys { it == "region.json" || it.endsWith(".region.json") }.map { (name, u) ->
            val text = cr.openInputStream(u)!!.use { it.readBytes().decodeToString() }
            RegionManifests.parseOne(text) ?: throw IOException("$name is not a valid Zoomi region")
        }
        if (manifests.isEmpty()) throw IOException("No .region.json picked. Pick it together with the country's map and routing files.")

        // For each manifest file: the picked documents that hold it, in order (one file, or its .001, .002 ... pieces).
        fun sources(m: RegionManifest, f: RegionFile): List<Uri>? {
            val pieces = f.parts?.map { it.url.substringAfterLast('/') }
            if (pieces != null && pieces.all { it in docs }) return pieces.map { docs.getValue(it) }
            listOfNotNull(f.url?.substringAfterLast('/'), "${m.id}-${f.name}", f.name).forEach { n -> docs[n]?.let { return listOf(it) } }
            return null
        }
        val plan = manifests.map { m ->
            m to m.files.map { f ->
                val want = f.parts?.let { "${it.size} pieces of ${m.id}-${f.name}" } ?: (f.url?.substringAfterLast('/') ?: f.name)
                f to (sources(m, f) ?: throw IOException("${m.name}: also pick $want"))
            }
        }
        val total = manifests.sumOf { it.totalSize }
        var done = 0L
        for ((manifest, files) in plan) {
            val dir = File(store.root, manifest.id).apply { mkdirs() }
            File(dir, "region.json").delete() // not installed while copying
            for ((f, uris) in files) {
                val target = File(dir, f.name)
                val part = File(dir, f.name + ".part")
                val md = MessageDigest.getInstance("SHA-256")
                part.outputStream().use { out ->
                    for (u in uris) cr.openInputStream(u)!!.use { input ->
                        val buf = ByteArray(1 shl 20)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); md.update(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                val sha = md.digest().joinToString("") { "%02x".format(it) }
                if (f.sha256 != null && sha != f.sha256) { part.delete(); throw IOException("${manifest.name}: ${f.name} is damaged (checksum mismatch)") }
                if (f.size != null && part.length() != f.size) { part.delete(); throw IOException("${manifest.name}: ${f.name} has the wrong size") }
                target.delete()
                if (!part.renameTo(target)) throw IOException("Could not save ${f.name}")
            }
            File(dir, "region.json").writeText(RegionManifests.encode(manifest))
        }
        store.refresh()
        return manifests.joinToString { it.name }
    }
}
