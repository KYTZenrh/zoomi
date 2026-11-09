package com.zoomi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zoomi.app.TripUi
import com.zoomi.core.nav.Formatting
import com.zoomi.core.nav.RouteCompare
import com.zoomi.core.places.PlaceKind
import com.zoomi.core.search.Place

@Composable
fun RoutePreviewCard(
    preview: TripUi.Preview,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onSave: (Place, PlaceKind, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var saveOpen by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 12.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(preview.destination.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (preview.destination.subtitle.isNotBlank()) {
                Text(preview.destination.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            when {
                preview.loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Finding routes…")
                }
                preview.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preview.error, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                }
                else -> {
                    val fastest = preview.routes.minOf { it.durationSec }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        preview.routes.forEachIndexed { i, r ->
                            val sel = i == preview.selected
                            Surface(
                                onClick = { onSelect(i) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                border = if (sel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Text(Formatting.duration(r.durationSec), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("${Formatting.distance(r.distanceM)} · ${RouteCompare.deltaLabel(r.durationSec, fastest)}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    val sel = preview.routes[preview.selected]
                    Text("Arrive around ${Formatting.arrivalClock(System.currentTimeMillis(), sel.durationSec)} " +
                        "· without live traffic" + if (preview.offline) " · offline route" else "", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onStart, enabled = !preview.loading && preview.routes.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Start", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = { saveOpen = true }) { Text("Save") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }

    if (saveOpen) SavePlaceDialog(preview.destination, onDismiss = { saveOpen = false }) { kind, name ->
        onSave(preview.destination, kind, name); saveOpen = false
    }
}

@Composable
private fun SavePlaceDialog(place: Place, onDismiss: () -> Unit, onSave: (PlaceKind, String) -> Unit) {
    var kind by remember { mutableStateOf(PlaceKind.FAVORITE) }
    var name by remember { mutableStateOf(place.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save place") },
        text = {
            Column {
                listOf(PlaceKind.HOME to "Home", PlaceKind.WORK to "Work", PlaceKind.FAVORITE to "Favourite").forEach { (k, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = { kind = k })
                        Text(label)
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Saved only on this phone.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(kind, name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
