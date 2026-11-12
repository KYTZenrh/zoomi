package com.zoomi.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zoomi.app.regions.DownloadProgress
import com.zoomi.app.regions.InstalledRegion
import com.zoomi.app.regions.RegionDownloader
import com.zoomi.core.regions.RegionManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ImportUi(val running: Boolean = false, val done: Long = 0, val total: Long = 0, val message: String? = null, val error: Boolean = false)

data class CatalogUi(val loading: Boolean = false, val regions: List<RegionManifest> = emptyList(), val error: String? = null)

class RegionsViewModel(app: Application) : AndroidViewModel(app) {
    private val z = app.zoomi
    private val downloader = RegionDownloader(z.regions)
    private val importer = com.zoomi.app.regions.RegionImporter(app, z.regions)
    private val _import = MutableStateFlow(ImportUi())
    val importState: StateFlow<ImportUi> = _import.asStateFlow()
    private var importJob: kotlinx.coroutines.Job? = null

    fun importFrom(uri: android.net.Uri) = runImport { p -> importer.import(uri, p) }
    fun importFiles(uris: List<android.net.Uri>) { if (uris.isNotEmpty()) runImport { p -> importer.importFiles(uris, p) } }

    private fun runImport(block: suspend ((Long, Long) -> Unit) -> String) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            _import.value = ImportUi(running = true)
            _import.value = try {
                val name = block { d, t -> _import.value = ImportUi(running = true, done = d, total = t) }
                ImportUi(message = "$name imported.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportUi(message = "Import failed: ${e.message}", error = true)
            }
        }
    }

    fun cancelImport() { importJob?.cancel(); _import.value = ImportUi(message = "Import cancelled.") }

    val installed: StateFlow<List<InstalledRegion>> = z.regions.installed
    val progress: StateFlow<Map<String, DownloadProgress>> = downloader.progress
    val catalogUrl: StateFlow<String> = z.settings.catalogUrl.stateIn(
        viewModelScope, SharingStarted.Eagerly, com.zoomi.app.data.SettingsRepository.DEFAULT_CATALOG)

    private val _catalog = MutableStateFlow(CatalogUi())
    val catalog: StateFlow<CatalogUi> = _catalog.asStateFlow()

    init { viewModelScope.launch { z.regions.refresh() } }

    fun setCatalogUrl(url: String) = viewModelScope.launch {
        z.settings.setCatalogUrl(url)
        _catalog.value = CatalogUi()
        loadCatalog().join()
    }

    fun loadCatalog() = viewModelScope.launch {
        val url = z.settings.catalogUrl.first().ifBlank { return@launch }
        _catalog.value = CatalogUi(loading = true)
        _catalog.value = try {
            val r = downloader.loadCatalog(url)
            CatalogUi(regions = r, error = if (r.isEmpty()) "No countries are available yet." else null)
        } catch (e: Exception) {
            CatalogUi(error = "Couldn't load the list of countries. Check your internet connection.")
        }
    }

    fun download(r: RegionManifest) = downloader.start(r)
    fun cancel(id: String) = downloader.cancel(id)
    fun rescan() = viewModelScope.launch { z.regions.refresh() }

    fun delete(id: String) = viewModelScope.launch {
        z.navigation.routeProvider.closeEngine()
        z.regions.delete(id)
    }
}
