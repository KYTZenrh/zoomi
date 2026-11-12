package com.zoomi.app.regions

import android.util.Log
import com.zoomi.app.net.ZoomiHttp
import com.zoomi.core.regions.RegionManifest
import com.zoomi.core.regions.RegionManifests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class DownloadProgress(val done: Long, val total: Long, val error: String? = null, val finished: Boolean = false)

/**
 * Downloads a region's files with resume (HTTP Range) and SHA-256 verification.
 * region.json is written only after every file verified, so a half-finished region is never
 * treated as installed. Runs while the app process is alive (not a background job).
 */
class RegionDownloader(private val store: RegionStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    /** At most two regions download at the same time ("Download all" queues the rest). */
    private val slots = kotlinx.coroutines.sync.Semaphore(2)
    private val http = ZoomiHttp.client.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    suspend fun loadCatalog(url: String): List<RegionManifest> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Catalog HTTP ${res.code}")
            RegionManifests.parseCatalog(res.body.string())
        }
    }

    fun start(region: RegionManifest) {
        if (jobs[region.id]?.isActive == true) return
        set(region.id, DownloadProgress(0, region.totalSize))
        jobs[region.id] = scope.launch { slots.acquire(); try { body(region) } finally { slots.release() } }
    }

    private suspend fun body(region: RegionManifest) {
        run {
            val dir = File(store.root, region.id).apply { mkdirs() }
            val total = region.totalSize
            try {
                var doneBefore = 0L
                for (f in region.files) {
                    val target = File(dir, f.name)
                    if (!(target.isFile && (f.size == null || target.length() == f.size) && verify(target, f.sha256))) {
                        val parts = f.parts ?: listOf(com.zoomi.core.regions.RegionPart(
                            f.url ?: throw IOException("No URL for ${f.name}"), f.size ?: -1))
                        download(parts, target, f.size) { got -> set(region.id, DownloadProgress(doneBefore + got, total)) }
                        if (!verify(target, f.sha256)) {
                            target.delete()
                            throw IOException("Checksum mismatch for ${f.name}")
                        }
                    }
                    doneBefore += target.length()
                }
                File(dir, "region.json").writeText(RegionManifests.encode(region))
                store.refresh()
                set(region.id, DownloadProgress(total, total, finished = true))
            } catch (e: Exception) {
                Log.w("Zoomi", "region download failed", e)
                val cur = _progress.value[region.id]
                set(region.id, DownloadProgress(cur?.done ?: 0, total, error = e.message ?: e::class.simpleName))
            }
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        _progress.update { it - id }
    }

    private fun set(id: String, p: DownloadProgress) = _progress.update { it + (id to p) }

    /**
     * Downloads [parts] one after another into one file, resuming where a previous attempt stopped.
     * A single-URL file is one part (size -1 = unknown).
     */
    private suspend fun download(parts: List<com.zoomi.core.regions.RegionPart>, target: File, expected: Long?, onProgress: (Long) -> Unit) {
        val part = File(target.parentFile, target.name + ".part")
        var offset = 0L // where the current part starts in the joined file
        for ((i, p) in parts.withIndex()) {
            val have = if (part.isFile) part.length() else 0L
            val partEnd = if (p.size >= 0) offset + p.size else Long.MAX_VALUE
            if (have >= partEnd) { offset = partEnd; continue } // this part is already complete
            val within = have - offset
            if (within < 0) throw IOException("Broken partial download of ${target.name}")
            val req = Request.Builder().url(p.url).apply { if (within > 0) header("Range", "bytes=$within-") }.build()
            http.newCall(req).execute().use { res ->
                val restart = res.code == 200 && within > 0 // server ignored Range
                if (res.code != 200 && res.code != 206) throw IOException("HTTP ${res.code} for ${target.name} (part ${i + 1})")
                if (restart) java.io.RandomAccessFile(part, "rw").use { it.setLength(offset) }
                FileOutputStream(part, true).use { out ->
                    res.body.byteStream().use { input ->
                        val buf = ByteArray(256 * 1024)
                        var got = part.length()
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            if (got - lastReport > 2_000_000) { onProgress(got); lastReport = got }
                        }
                        onProgress(got)
                    }
                }
            }
            offset = if (p.size >= 0) partEnd else part.length()
            if (p.size >= 0 && part.length() != partEnd) throw IOException("Size mismatch for ${target.name} (part ${i + 1})")
        }
        if (expected != null && part.length() != expected) throw IOException("Size mismatch for ${target.name}")
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not save ${target.name}")
    }

    private fun verify(file: File, sha256: String?): Boolean {
        if (sha256 == null) return true
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) } == sha256
    }
}
