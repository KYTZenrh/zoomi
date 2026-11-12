package com.zoomi.app.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zoomi.app.CatalogUi
import com.zoomi.app.regions.DownloadProgress
import com.zoomi.app.regions.InstalledRegion
import com.zoomi.core.regions.RegionManifest
import java.util.Locale

private fun mb(bytes: Long) = String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)

@Composable
fun RegionsScreen(
    installed: List<InstalledRegion>,
    catalogUrl: String,
    catalog: CatalogUi,
    progress: Map<String, DownloadProgress>,
    import: com.zoomi.app.ImportUi,
    onImport: () -> Unit,
    onCancelImport: () -> Unit,
    onCatalogUrl: (String) -> Unit,
    onLoadCatalog: () -> Unit,
    onDownload: (RegionManifest) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<InstalledRegion?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header("Offline regions", onBack)
            Text(
                "Map and routing without internet. Search still needs a connection.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionLabel("On this phone")
            if (installed.isEmpty()) Body("No regions yet.")
            installed.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.manifest.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Map data from ${r.manifest.osmDate} · ${mb(r.sizeOnDisk)} · " +
                                listOfNotNull(r.mapFile?.let { "map" }, r.routingFile?.let { "routing" }).joinToString(" + "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { confirmDelete = r }) { Text("Delete") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("Download")
            var url by remember(catalogUrl) { mutableStateOf(catalogUrl) }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Region catalog URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "No official regions yet. Use your own catalog or import a folder.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onCatalogUrl(url) }, enabled = url != catalogUrl) { Text("Save") }
                    OutlinedButton(onClick = onLoadCatalog, enabled = catalogUrl.isNotBlank() && !catalog.loading) { Text("Load catalog") }
                }
                catalog.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            catalog.regions.forEach { r ->
                val p = progress[r.id]
                val isInstalled = installed.any { it.manifest.id == r.id && it.manifest.osmDate == r.osmDate }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge)
                            Text("Data from ${r.osmDate} · ${mb(r.totalSize)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                            isInstalled && p?.finished != false -> Text("Installed", color = MaterialTheme.colorScheme.primary)
                            p != null && p.error == null && !p.finished -> TextButton(onClick = { onCancel(r.id) }) { Text("Cancel") }
                            else -> TextButton(onClick = { onDownload(r) }) { Text(if (p?.error != null) "Retry" else "Download") }
                        }
                    }
                    if (p != null && !p.finished && p.error == null && p.total > 0) {
                        LinearProgressIndicator(progress = { (p.done.toFloat() / p.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        Text("${mb(p.done)} of ${mb(p.total)}", style = MaterialTheme.typography.bodySmall)
                    }
                    p?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("Import from a folder")
            Body("Pick a region folder copied to the phone.")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (import.running) {
                    if (import.total > 0) {
                        LinearProgressIndicator(progress = { (import.done.toFloat() / import.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${mb(import.done)} of ${mb(import.total)}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onCancelImport) { Text("Cancel import") }
                } else {
                    OutlinedButton(onClick = onImport) { Text("Import region folder…") }
                }
                import.message?.let { Text(it, color = if (import.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            }
        }
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${r.manifest.name}?") },
            text = { Text("This removes ${mb(r.sizeOnDisk)} from the phone. You'll need to download it again for offline use.") },
            confirmButton = { TextButton(onClick = { onDelete(r.manifest.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(t: String) = Text(
    t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
)

@Composable
private fun Body(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
