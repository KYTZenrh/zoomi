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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zoomi.app.R
import com.zoomi.app.ThemeDiagnostics
import com.zoomi.core.theme.ThemeDecision
import com.zoomi.core.theme.ThemeMode
import com.zoomi.core.theme.ThemeReason
import java.util.Locale

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    decision: ThemeDecision,
    diagnostics: ThemeDiagnostics,
    lightSensorAvailable: Boolean,
    valhallaUrl: String,
    photonUrl: String,
    onModeChange: (ThemeMode) -> Unit,
    onValhallaUrl: (String?) -> Unit,
    onPhotonUrl: (String?) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRegions: () -> Unit,
    onOpenVoice: () -> Unit,
    satellite: Boolean,
    imageryUrl: String,
    imageryAttribution: String,
    onSatellite: (Boolean) -> Unit,
    onImagery: (String, String) -> Unit,
    imagerySource: String,
    esriKey: String,
    onImagerySource: (String) -> Unit,
    onEsriKey: (String) -> Unit,
    maptilerKey: String,
    keyCheck: String?,
    onMaptilerKey: (String) -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            Header("Settings", onBack)

            Text(
                "Offline regions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenRegions).padding(20.dp),
            )
            HorizontalDivider()

            Text(
                "Voice guidance",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenVoice).padding(20.dp),
            )
            HorizontalDivider()

            Section("Map theme")
            ThemeOption(ThemeMode.LIGHT, "Light", "Always light", mode, onModeChange)
            ThemeOption(ThemeMode.DARK, "Dark", "Always dark", mode, onModeChange)
            ThemeOption(
                ThemeMode.AUTO, "Auto",
                "Dark at night and in tunnels", mode, onModeChange,
            )

            Section("Theme right now")
            val reason = when (decision.reason) {
                ThemeReason.USER_LIGHT -> "You chose Light"
                ThemeReason.USER_DARK -> "You chose Dark"
                ThemeReason.DAY -> "Daytime at your position"
                ThemeReason.NIGHT -> "Night-time at your position"
                ThemeReason.TUNNEL -> "In a tunnel"
                ThemeReason.SYSTEM_FALLBACK -> "No GPS yet, using phone setting"
            }
            Info("Current", (if (decision.dark) "Dark · " else "Light · ") + reason)
            Info("Sun elevation", diagnostics.sunElevationDeg?.let { String.format(Locale.US, "%.1f°", it) } ?: "unknown (no GPS fix)")
            Info(
                "Ambient light",
                if (!lightSensorAvailable) "no light sensor"
                else diagnostics.lux?.let { String.format(Locale.US, "%.0f lux", it) } ?: "waiting…",
            )
            Info("Last GPS fix", diagnostics.fixAgeMs?.let { "${it / 1000} s ago" } ?: "none yet")
            Info(
                "Road under you", when (diagnostics.onTunnelRoad) {
                    true -> "tunnel"
                    false -> "not a tunnel"
                    null -> "unknown (map not following you)"
                }
            )

            Section("Satellite view")
            Row(
                Modifier.fillMaxWidth().selectable(selected = satellite, onClick = { onSatellite(!satellite) }, role = Role.Switch)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Satellite imagery", modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = satellite, onCheckedChange = null)
            }
            Text("Imagery source", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
            listOf(
                "EOX" to ("EOxCloudless" to "Free, no key · blurry up close"),
                "MAPTILER" to ("MapTiler · recommended" to "Sharp · free key, never billed"),
                "ESRI" to ("Esri" to "Sharp · key may bill above 2M tiles/month"),
                "CUSTOM" to ("Custom" to "Your own tile URL"),
            ).forEach { (id, t) ->
                Row(
                    Modifier.fillMaxWidth().selectable(imagerySource == id, onClick = { onImagerySource(id) }, role = Role.RadioButton)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = imagerySource == id, onClick = null, modifier = Modifier.padding(8.dp))
                    Column {
                        Text(t.first, style = MaterialTheme.typography.bodyLarge)
                        Text(t.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            when (imagerySource) {
                "MAPTILER" -> {
                    var key by remember(maptilerKey) { mutableStateOf(maptilerKey) }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(key, { key = it }, label = { Text("MapTiler API key") }, singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onMaptilerKey(key) }, enabled = key.isNotBlank() && key != maptilerKey) { Text("Test & save key") }
                            TextButton(onClick = { key = ""; onMaptilerKey("") }, enabled = maptilerKey.isNotBlank()) { Text("Remove key") }
                        }
                        keyCheck?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Not saved")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                        if (maptilerKey.isBlank()) Text("Until a working key is saved, EOxCloudless is shown.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "ESRI" -> {
                    var key by remember(esriKey) { mutableStateOf(esriKey) }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(key, { key = it }, label = { Text("ArcGIS API key") }, singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onEsriKey(key) }, enabled = key != esriKey) { Text("Save key") }
                            TextButton(onClick = { key = ""; onEsriKey("") }, enabled = esriKey.isNotBlank()) { Text("Remove key") }
                        }
                        if (esriKey.isBlank()) Text("Until a key is saved, EOxCloudless is shown.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                "CUSTOM" -> {
                    var imgUrl by remember(imageryUrl) { mutableStateOf(imageryUrl) }
                    var imgAttr by remember(imageryAttribution) { mutableStateOf(imageryAttribution) }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(imgUrl, { imgUrl = it }, label = { Text("Tile URL with {z} {x} {y}") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(imgAttr, { imgAttr = it }, label = { Text("Attribution required by that provider") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        val valid = imgUrl.startsWith("https://") && "{z}" in imgUrl && "{x}" in imgUrl && "{y}" in imgUrl && imgAttr.isNotBlank()
                        TextButton(onClick = { onImagery(imgUrl, imgAttr) }, enabled = valid && (imgUrl != imageryUrl || imgAttr != imageryAttribution)) { Text("Save") }
                        if (!valid) Text("Needs https://, {z} {x} {y} and an attribution.", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Needs internet. Keys stay on this phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            Section("Servers")
            ServerField(
                label = "Routing server",
                value = valhallaUrl,
                default = com.zoomi.app.data.SettingsRepository.DEFAULT_VALHALLA,
                note = "Default: FOSSGIS volunteer server",
                onSave = onValhallaUrl,
            )
            ServerField(
                label = "Search server",
                value = photonUrl,
                default = com.zoomi.app.data.SettingsRepository.DEFAULT_PHOTON,
                note = "Default: komoot public server",
                onSave = onPhotonUrl,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "About & attributions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout).padding(20.dp),
            )
        }
    }
}

@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeOption(value: ThemeMode, title: String, subtitle: String, current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = value == current, onClick = { onChange(value) }, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == current, onClick = null, modifier = Modifier.padding(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServerField(label: String, value: String, default: String, note: String, onSave: (String?) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val valid = text.startsWith("https://") || text.startsWith("http://")
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true,
            isError = !valid, modifier = Modifier.fillMaxWidth(),
        )
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSave(text) }, enabled = valid && text != value) { Text("Save") }
            TextButton(onClick = { text = default; onSave(null) }, enabled = value != default) { Text("Reset to default") }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}
