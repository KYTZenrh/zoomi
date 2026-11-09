package com.zoomi.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zoomi.app.TripUi
import com.zoomi.app.Unreachable
import com.zoomi.core.geo.GeoMath
import com.zoomi.core.location.LatLon
import com.zoomi.core.nav.Crossing
import com.zoomi.core.nav.CrossingKind
import com.zoomi.core.nav.Formatting
import com.zoomi.core.nav.RouteCompare
import com.zoomi.core.nav.RouteFailure
import com.zoomi.core.nav.TravelMode
import com.zoomi.core.places.PlaceKind
import com.zoomi.core.search.Place
import com.zoomi.core.transit.Itinerary
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun RoutePreviewCard(
    preview: TripUi.Preview,
    onMode: (TravelMode) -> Unit,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onSave: (Place, PlaceKind, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var saveOpen by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 12.dp) {
        Column {
        Column(
            Modifier.weight(1f, fill = false).heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(preview.destination.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (preview.destination.subtitle.isNotBlank()) {
                Text(preview.destination.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            ModeChips(preview.mode, onMode)

            when {
                preview.loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(if (preview.mode == TravelMode.TRANSIT) "Finding connections…" else "Finding routes…")
                }
                preview.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preview.error, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                }
                preview.mode == TravelMode.TRANSIT -> TransitList(preview.itineraries, preview.selected, onSelect)
                else -> {
                    preview.unreachable?.let { UnreachablePanel(it, preview.mode, preview.destination, onMode) }
                    if (preview.routes.isNotEmpty()) RouteChips(preview, onSelect)
                    preview.crossings?.let { CrossingsRow(it) }
                }
            }

            }
            // Actions stay visible even when the content above scrolls.
            Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (preview.mode != TravelMode.TRANSIT) {
                    Button(onClick = onStart, enabled = !preview.loading && preview.routes.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text(if (preview.unreachable != null && preview.routes.isNotEmpty()) "Go to nearest point" else "Start",
                            style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Text("Itinerary only: no turn-by-turn for public transport.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
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
private fun ModeChips(mode: TravelMode, onMode: (TravelMode) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TravelMode.entries.forEach { m ->
            val sel = m == mode
            Surface(
                onClick = { onMode(m) },
                shape = RoundedCornerShape(50),
                color = if (sel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (sel) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) { Text(m.label, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = if (sel) FontWeight.Bold else null) }
        }
    }
}

@Composable
private fun RouteChips(preview: TripUi.Preview, onSelect: (Int) -> Unit) {
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
    Text(
        "Arrive around ${Formatting.arrivalClock(System.currentTimeMillis(), sel.durationSec)}" +
            (if (preview.mode == TravelMode.CAR) " · without live traffic" else "") +
            (if (preview.offline) " · offline route" else ""),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CrossingsRow(crossings: List<Crossing>) {
    if (crossings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("On this route", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        // Ferries matter most (schedules!), then the longest bridges/tunnels; short viaducts are noise.
        val ordered = crossings.filter { it.kind == CrossingKind.FERRY } +
            crossings.filter { it.kind != CrossingKind.FERRY }.sortedByDescending { it.lengthKm }
        val shown = ordered.take(4)
        shown.forEach { c ->
            val what = when (c.kind) { CrossingKind.FERRY -> "Ferry"; CrossingKind.BRIDGE -> "Bridge"; CrossingKind.TUNNEL -> "Tunnel" }
            Text("$what${c.name?.let { ": $it" } ?: ""} · ${Formatting.distance(c.lengthKm * 1000)}",
                style = MaterialTheme.typography.bodyMedium)
        }
        if (ordered.size > shown.size) {
            Text("+${ordered.size - shown.size} more bridges/tunnels", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (crossings.any { it.kind == CrossingKind.FERRY }) {
            Text("Ferry times and prices aren't known to Zoomi; check with the operator before you go.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UnreachablePanel(u: Unreachable, mode: TravelMode, dest: Place, onMode: (TravelMode) -> Unit) {
    val how = when (mode) { TravelMode.CAR -> "by car"; TravelMode.BIKE -> "by bike"; TravelMode.WALK -> "on foot"; TravelMode.TRANSIT -> "" }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Can't reach this destination $how", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                when {
                    u.gapKm != null -> "The closest the road network gets is ${Formatting.distance(u.gapKm * 1000)} away " +
                        "(e.g. it's on an island or across the sea with no ferry in the map data)."
                    u.reason == RouteFailure.NO_ROAD_NEAR_DESTINATION -> "There's no road or path near this point in the map data."
                    else -> "No connection exists in the map data, even with ferries."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text("How would you like to travel?", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onMode(TravelMode.TRANSIT) }) { Text("Public transport") }
                if (mode != TravelMode.WALK) OutlinedButton(onClick = { onMode(TravelMode.WALK) }) { Text("Walk") }
            }
            if (u.airportsNearYou.isNotEmpty() || u.airportsNearDestination.isNotEmpty()) {
                Text("Nearest airports (information only; Zoomi has no flight data)",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                AirportList("Near you", u.airportsNearYou, null)
                AirportList("Near the destination", u.airportsNearDestination, LatLon(dest.lat, dest.lon))
            }
        }
    }
}

@Composable
private fun AirportList(title: String, airports: List<Place>, ref: LatLon?) {
    if (airports.isEmpty()) return
    Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    airports.forEach { a ->
        val dist = ref?.let { " · " + Formatting.distance(GeoMath.haversine(it, LatLon(a.lat, a.lon))) } ?: ""
        Text(
            "${a.title}$dist", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun TransitList(its: List<Itinerary>, selected: Int, onSelect: (Int) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        its.forEachIndexed { i, it ->
            val sel = i == selected
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(16.dp),
                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${hhmm.format(it.start)} → ${hhmm.format(it.end)} · ${Formatting.duration(it.durationSec.toDouble())}" +
                        (if (it.transfers > 0) " · ${it.transfers} transfer${if (it.transfers > 1) "s" else ""}" else ""),
                        fontWeight = FontWeight.Bold)
                    Text(it.legs.filterNot { l -> l.isWalk }.joinToString("  ›  ") { l -> modeName(l.mode) + (l.line?.let { n -> " $n" } ?: "") },
                        style = MaterialTheme.typography.bodySmall)
                    if (sel) it.legs.forEach { l ->
                        Text(
                            "${hhmm.format(l.start)}  " + if (l.isWalk) "Walk to ${l.to} (${Formatting.duration((l.end.epochSecond - l.start.epochSecond).toDouble())})"
                            else "${modeName(l.mode)} ${l.line ?: ""}${l.headsign?.let { h -> " → $h" } ?: ""}: ${l.from} → ${l.to}" +
                                (l.agency?.let { a -> " · $a" } ?: "") + if (l.realTime) " · live" else " · timetable",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://transitous.org/sources/"))) }) {
            Text("Public transport data: Transitous and its sources", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun modeName(m: String) = when (m) {
    "BUS", "COACH" -> "Bus"; "TRAM" -> "Tram"; "SUBWAY", "METRO" -> "Metro"; "RAIL", "REGIONAL_RAIL", "LONG_DISTANCE",
    "HIGHSPEED_RAIL", "NIGHT_RAIL", "REGIONAL_FAST_RAIL" -> "Train"
    "FERRY" -> "Ferry"; "TROLLEYBUS" -> "Trolleybus"; "FUNICULAR" -> "Funicular"; "AERIAL_LIFT" -> "Cable car"
    else -> m.lowercase().replaceFirstChar { it.uppercase() }
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
