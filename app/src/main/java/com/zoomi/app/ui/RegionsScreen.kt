package com.zoomi.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zoomi.app.CatalogUi
import com.zoomi.app.regions.DownloadProgress
import com.zoomi.app.regions.InstalledRegion
import com.zoomi.core.regions.RegionManifest
import com.zoomi.core.regions.RegionManifests
import java.util.Locale

private fun size(bytes: Long) =
    if (bytes >= 1_073_741_824) String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    else String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)

private val CONTINENTS = linkedMapOf(
    "europe" to "Europe", "asia" to "Asia", "africa" to "Africa", "north-america" to "North America",
    "central-america" to "Central America", "south-america" to "South America",
    "australia-oceania" to "Australia & Oceania", "antarctica" to "Antarctica",
)

@Composable
fun RegionsScreen(
    installed: List<InstalledRegion>,
    catalogUrl: String,
    catalog: CatalogUi,
    progress: Map<String, DownloadProgress>,
    import: com.zoomi.app.ImportUi,
    /** Current position, to suggest the country you're in. */
    here: Pair<Double, Double>?,
    onImport: () -> Unit,
    onCancelImport: () -> Unit,
    onCatalogUrl: (String) -> Unit,
    onLoadCatalog: () -> Unit,
    onDownload: (RegionManifest) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<InstalledRegion?>(null) }
    var confirmAll by remember { mutableStateOf<Pair<String, List<RegionManifest>>?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    fun isInstalled(r: RegionManifest) = installed.any { it.manifest.id == r.id && it.manifest.osmDate == r.osmDate }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header("Offline maps", onBack)
            Body("Map and routing without internet. Search still needs a connection.")

            // Suggestion: the country you're in.
            // Only regions with a real border: a bounding box can't tell Iasi (Romania) from Moldova.
            val mine = here?.let { (lat, lon) -> RegionManifests.covering(catalog.regions.filter { it.area != null }, listOf(lat to lon)) }
            if (mine != null && !isInstalled(mine)) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("You're in ${mine.name}", style = MaterialTheme.typography.titleMedium)
                        Text("${size(mine.totalSize)} · map data from ${mine.osmDate}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RegionProgress(progress[mine.id])
                        if (progress[mine.id]?.let { !it.finished && it.error == null } == true)
                            OutlinedButton(onClick = { onCancel(mine.id) }) { Text("Cancel") }
                        else Button(onClick = { onDownload(mine) }) { Text("Download ${mine.name}") }
                    }
                }
            }

            if (installed.isNotEmpty()) {
                SectionLabel("On this phone")
                installed.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.manifest.name, style = MaterialTheme.typography.bodyLarge)
                            val update = catalog.regions.firstOrNull { it.id == r.manifest.id && it.osmDate > r.manifest.osmDate }
                            Text(
                                "${size(r.sizeOnDisk)} · data from ${r.manifest.osmDate}" + if (update != null) " · update available" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        catalog.regions.firstOrNull { it.id == r.manifest.id && it.osmDate > r.manifest.osmDate }?.let { u ->
                            TextButton(onClick = { onDownload(u) }) { Text("Update") }
                        }
                        TextButton(onClick = { confirmDelete = r }) { Text("Delete") }
                    }
                }
            }

            SectionLabel("All countries")
            when {
                catalog.loading -> Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text("Loading the list…")
                }
                catalog.error != null -> Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(catalog.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onLoadCatalog) { Text("Try again") }
                }
            }
            val groups = catalog.regions.groupBy { it.group ?: "other" }
            val order = CONTINENTS.keys.filter { it in groups } + groups.keys.filter { it !in CONTINENTS }
            order.forEach { g ->
                val list = groups.getValue(g).sortedBy { it.name }
                val expanded = open == g
                Row(
                    Modifier.fillMaxWidth().clickable { open = if (expanded) null else g }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(CONTINENTS[g] ?: g.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                        Text("${list.size} countries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleLarge)
                }
                if (expanded) {
                    val missing = list.filter { !isInstalled(it) }
                    if (missing.size > 1) TextButton(
                        onClick = { confirmAll = (CONTINENTS[g] ?: g) to missing },
                        modifier = Modifier.padding(start = 12.dp),
                    ) { Text("Download all (${size(missing.sumOf { it.totalSize })})") }
                    list.forEach { r -> CountryRow(r, isInstalled(r), progress[r.id], onDownload, onCancel) }
                }
                HorizontalDivider()
            }

            // Rarely needed: own catalog, or a folder copied over USB.
            Row(
                Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Advanced", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (advanced) "▾" else "▸", style = MaterialTheme.typography.titleLarge)
            }
            if (advanced) {
                var url by remember(catalogUrl) { mutableStateOf(catalogUrl) }
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(url, { url = it }, label = { Text("Region list URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onCatalogUrl(url) }, enabled = url != catalogUrl) { Text("Save") }
                        TextButton(onClick = { onCatalogUrl("") }) { Text("Reset to default") }
                    }
                    Text("Import a region folder copied to the phone", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    if (import.running) {
                        if (import.total > 0) {
                            LinearProgressIndicator(progress = { (import.done.toFloat() / import.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text("${size(import.done)} of ${size(import.total)}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onCancelImport) { Text("Cancel import") }
                    } else {
                        OutlinedButton(onClick = onImport) { Text("Choose folder…") }
                    }
                    import.message?.let { Text(it, color = if (import.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${r.manifest.name}?") },
            text = { Text("Frees ${size(r.sizeOnDisk)}. You can download it again later.") },
            confirmButton = { TextButton(onClick = { onDelete(r.manifest.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    confirmAll?.let { (name, list) ->
        AlertDialog(
            onDismissRequest = { confirmAll = null },
            title = { Text("Download all of $name?") },
            text = { Text("${list.size} countries, ${size(list.sumOf { it.totalSize })} in total. Use Wi-Fi and check your free space. " +
                "Offline routes work within one country at a time.") },
            confirmButton = { TextButton(onClick = { list.forEach(onDownload); confirmAll = null }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { confirmAll = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CountryRow(r: RegionManifest, installed: Boolean, p: DownloadProgress?, onDownload: (RegionManifest) -> Unit, onCancel: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.bodyLarge)
                Text(size(r.totalSize), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                installed && p?.finished != false -> Text("Downloaded", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                p != null && p.error == null && !p.finished -> TextButton(onClick = { onCancel(r.id) }) { Text("Cancel") }
                else -> TextButton(onClick = { onDownload(r) }) { Text(if (p?.error != null) "Retry" else "Download") }
            }
        }
        RegionProgress(p)
    }
}

@Composable
private fun RegionProgress(p: DownloadProgress?) {
    p ?: return
    if (!p.finished && p.error == null && p.total > 0) {
        LinearProgressIndicator(progress = { (p.done.toFloat() / p.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Text("${size(p.done)} of ${size(p.total)}", style = MaterialTheme.typography.bodySmall)
    }
    p.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SectionLabel(t: String) = Text(
    t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
)

@Composable
private fun Body(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
