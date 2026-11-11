package com.zoomi.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zoomi.app.BuildConfigInfo

private data class Credit(val name: String, val detail: String, val url: String)

private val credits = listOf(
    Credit("© OpenStreetMap contributors", "Map data, available under the Open Database License (ODbL).", "https://www.openstreetmap.org/copyright"),
    Credit("Report or fix a map error", "Map data is made by volunteers. If something is wrong, you can fix it in OpenStreetMap.", "https://www.openstreetmap.org/fixthemap"),
    Credit("Valhalla routing (FOSSGIS e.V. server)", "Routes and turn-by-turn instructions. Valhalla is MIT-licensed; the public server is run by FOSSGIS volunteers.", "https://fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen/"),
    Credit("Photon search (komoot)", "Search and address lookup. Photon is Apache-2.0; public server by komoot.", "https://github.com/komoot/photon"),
    Credit("Transitous", "Public transport journeys (MOTIS 2), run by volunteers. Data from many transport operators; tap for the list of sources.", "https://transitous.org/sources/"),
    Credit("Support Transitous", "Transitous runs on donations.", "https://transitous.org/donate/"),
    Credit("EOxCloudless (satellite view)", "EOxCloudless https://cloudless.eox.at by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2024). CC BY-NC-SA 4.0 for non-commercial use.", "https://cloudless.eox.at/documentation/license"),
    Credit("Ferrostar", "Navigation logic (progress, off-route detection, voice). BSD 3-Clause, Stadia Maps.", "https://github.com/stadiamaps/ferrostar"),
    Credit("OkHttp", "HTTP client, Apache License 2.0.", "https://square.github.io/okhttp/"),
    Credit("OpenMapTiles", "Vector tile schema, © OpenMapTiles (CC-BY 4.0).", "https://www.openmaptiles.org/"),
    Credit("OpenFreeMap", "Free vector tile hosting.", "https://openfreemap.org"),
    Credit("MapLibre Native", "Map rendering engine, BSD 2-Clause license.", "https://github.com/maplibre/maplibre-native"),
    Credit("Noto Sans", "Map label font served by OpenFreeMap, SIL Open Font License.", "https://fonts.google.com/noto"),
    Credit("AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines", "Apache License 2.0.", "https://developer.android.com/jetpack/androidx"),
    Credit("Sun position", "NOAA solar calculator equations (J. Meeus, Astronomical Algorithms), computed on the device.", "https://gml.noaa.gov/grad/solcalc/calcdetails.html"),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header("About Zoomi", onBack)
            Text(
                "Zoomi ${BuildConfigInfo.versionName(context)} is free, open-source navigation. It has no ads, " +
                    "no tracking and no analytics, and your location stays on your phone. Licensed under GPL-3.0-or-later.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(
                "What leaves your phone: map tiles from tiles.openfreemap.org (reveals the area you view); " +
                    "search text and your position rounded to ~1 km to the search server; the start and destination " +
                    "of each route you request to the routing server; start and destination of public transport searches to Transitous. " +
                    "In satellite view, imagery tiles from tiles.maps.eox.at (or your custom source), which reveals the area you view. " +
                    "Every server also sees your IP address. " +
                    "Zoomi itself stores nothing on any server. Saved places stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text("Credits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
            credits.forEach { c ->
                Column(Modifier.fillMaxWidth().clickable { open(c.url) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(c.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
