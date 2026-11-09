package com.zoomi.app.regions

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.zoomi.core.regions.RegionManifests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Imports a region from a folder the user picks with Android's folder picker (Storage Access
 * Framework), e.g. Downloads/zoomi-romania/ containing region.json, map.pmtiles, routing.tar.
 * Files are copied into Zoomi's storage and verified against the SHA-256 in region.json.
 * region.json is written last, so an interrupted import never looks installed.
 */
class RegionImporter(private val context: Context, private val store: RegionStore) {

    suspend fun import(treeUri: Uri, onProgress: (done: Long, total: Long) -> Unit): String = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val docs = mutableMapOf<String, Uri>()
        cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) docs[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
        }
        val manifestUri = docs["region.json"] ?: throw IOException("This folder has no region.json")
        val text = cr.openInputStream(manifestUri)!!.use { it.readBytes().decodeToString() }
        val manifest = RegionManifests.parseOne(text) ?: throw IOException("region.json is not a valid Zoomi region")
        val missing = manifest.files.map { it.name }.filter { it !in docs }
        if (missing.isNotEmpty()) throw IOException("Missing in this folder: ${missing.joinToString()}")

        val dir = File(store.root, manifest.id).apply { mkdirs() }
        File(dir, "region.json").delete() // not installed while copying
        val total = manifest.totalSize
        var done = 0L
        for (f in manifest.files) {
            val target = File(dir, f.name)
            val part = File(dir, f.name + ".part")
            val md = MessageDigest.getInstance("SHA-256")
            cr.openInputStream(docs.getValue(f.name))!!.use { input ->
                part.outputStream().use { out ->
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
            if (f.sha256 != null && sha != f.sha256) { part.delete(); throw IOException("Checksum mismatch in ${f.name}: the file is damaged") }
            if (f.size != null && part.length() != f.size) { part.delete(); throw IOException("Size mismatch in ${f.name}") }
            target.delete()
            if (!part.renameTo(target)) throw IOException("Could not save ${f.name}")
        }
        File(dir, "region.json").writeText(RegionManifests.encode(manifest))
        store.refresh()
        manifest.name
    }
}
