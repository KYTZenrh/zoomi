package com.zoomi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zoomi.app.R
import com.zoomi.app.ThemeDiagnostics
import com.zoomi.app.map.PuckColor
import com.zoomi.core.theme.ThemeDecision
import com.zoomi.core.theme.ThemeMode
import com.zoomi.core.theme.ThemeReason
import java.util.Locale

enum class SettingsPage { HOME, MAP, NAVIGATION, ADVANCED }

/** Everything the Settings pages show. */
data class SettingsValues(
    val mode: ThemeMode,
    val decision: ThemeDecision,
    val diagnostics: ThemeDiagnostics,
    val lightSensorAvailable: Boolean,
    val puckColor: PuckColor,
    val satellite: Boolean,
    val imagerySource: String,
    val maptilerKey: String,
    val keyCheck: String?,
    val esriKey: String,
    val imageryUrl: String,
    val imageryAttribution: String,
    val avoidTolls: Boolean,
    val avoidHighways: Boolean,
    val avoidFerries: Boolean,
    val speedAlert: Boolean,
    val valhallaUrl: String,
    val photonUrl: String,
)

/** What the Settings pages can change. */
class SettingsActions(
    val onModeChange: (ThemeMode) -> Unit,
    val onPuckColor: (PuckColor) -> Unit,
    val onSatellite: (Boolean) -> Unit,
    val onImagerySource: (String) -> Unit,
    val onMaptilerKey: (String) -> Unit,
    val onEsriKey: (String) -> Unit,
    val onImagery: (String, String) -> Unit,
    val onAvoidTolls: (Boolean) -> Unit,
    val onAvoidHighways: (Boolean) -> Unit,
    val onAvoidFerries: (Boolean) -> Unit,
    val onSpeedAlert: (Boolean) -> Unit,
    val onValhallaUrl: (String?) -> Unit,
    val onPhotonUrl: (String?) -> Unit,
    val onOpenVoice: () -> Unit,
    val onOpenRegions: () -> Unit,
    val onOpenAbout: () -> Unit,
)

@Composable
fun SettingsScreen(
    page: SettingsPage,
    onPage: (SettingsPage) -> Unit,
    v: SettingsValues,
    a: SettingsActions,
    onBack: () -> Unit,
) {
    if (page != SettingsPage.HOME) BackHandler { onPage(SettingsPage.HOME) }
    val back = { if (page == SettingsPage.HOME) onBack() else onPage(SettingsPage.HOME) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            when (page) {
                SettingsPage.HOME -> {
                    Header("Settings", back)
                    Category(R.drawable.ic_layers, "Map", "Theme, position colour, satellite") { onPage(SettingsPage.MAP) }
                    Category(R.drawable.ic_route, "Navigation", "Voice, route options, speed alert") { onPage(SettingsPage.NAVIGATION) }
                    Category(R.drawable.ic_download, "Offline maps", "Download countries for use without internet", a.onOpenRegions)
                    Category(R.drawable.ic_settings, "Advanced", "Servers, theme details") { onPage(SettingsPage.ADVANCED) }
                    Category(R.drawable.ic_info, "About & credits", "Version, licences, privacy", a.onOpenAbout)
                }
                SettingsPage.MAP -> {
                    Header("Map", back)
                    MapPage(v, a)
                }
                SettingsPage.NAVIGATION -> {
                    Header("Navigation", back)
                    NavigationPage(v, a)
                }
                SettingsPage.ADVANCED -> {
                    Header("Advanced", back)
                    AdvancedPage(v, a)
                }
            }
        }
    }
}

@Composable
private fun Category(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Map

@Composable
private fun MapPage(v: SettingsValues, a: SettingsActions) {
    Section("Theme")
    Radio("Auto", "Dark at night and in tunnels", v.mode == ThemeMode.AUTO) { a.onModeChange(ThemeMode.AUTO) }
    Radio("Light", null, v.mode == ThemeMode.LIGHT) { a.onModeChange(ThemeMode.LIGHT) }
    Radio("Dark", null, v.mode == ThemeMode.DARK) { a.onModeChange(ThemeMode.DARK) }

    Section("Position colour")
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        val dark = v.decision.dark
        PuckColor.entries.forEach { c ->
            val selected = c == v.puckColor
            Box(
                Modifier.size(40.dp)
                    .border(3.dp, if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
                    .padding(6.dp)
                    .background(Color(c.argb(dark)), CircleShape)
                    .selectable(selected, onClick = { a.onPuckColor(c) }, role = Role.RadioButton)
                    .semantics { contentDescription = c.label },
            )
        }
    }

    Section("Satellite view")
    SwitchRow("Satellite imagery", null, v.satellite, a.onSatellite)
    Radio("MapTiler · recommended", "Sharp · free key, never billed", v.imagerySource == "MAPTILER") { a.onImagerySource("MAPTILER") }
    Radio("EOxCloudless", "Free, no key · blurry up close", v.imagerySource == "EOX") { a.onImagerySource("EOX") }
    Radio("Esri", "Sharp · key may bill above 2M tiles/month", v.imagerySource == "ESRI") { a.onImagerySource("ESRI") }
    Radio("Custom", "Your own tile URL", v.imagerySource == "CUSTOM") { a.onImagerySource("CUSTOM") }
    when (v.imagerySource) {
        "MAPTILER" -> KeyField("MapTiler API key", v.maptilerKey, "Test & save key", v.keyCheck,
            if (v.maptilerKey.isBlank()) "Until a key is saved, EOxCloudless is shown." else null, a.onMaptilerKey)
        "ESRI" -> KeyField("ArcGIS API key", v.esriKey, "Save key", null,
            if (v.esriKey.isBlank()) "Until a key is saved, EOxCloudless is shown." else null, a.onEsriKey)
        "CUSTOM" -> CustomImagery(v, a)
    }
    Note("Needs internet. Keys stay on this phone.")
}

@Composable
private fun KeyField(label: String, saved: String, saveLabel: String, check: String?, hint: String?, onSave: (String) -> Unit) {
    var key by remember(saved) { mutableStateOf(saved) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(key, { key = it }, label = { Text(label) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank() && key != saved) { Text(saveLabel) }
            TextButton(onClick = { key = ""; onSave("") }, enabled = saved.isNotBlank()) { Text("Remove key") }
        }
        check?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = if (it.startsWith("Not saved")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun CustomImagery(v: SettingsValues, a: SettingsActions) {
    var imgUrl by remember(v.imageryUrl) { mutableStateOf(v.imageryUrl) }
    var imgAttr by remember(v.imageryAttribution) { mutableStateOf(v.imageryAttribution) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(imgUrl, { imgUrl = it }, label = { Text("Tile URL with {z} {x} {y}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(imgAttr, { imgAttr = it }, label = { Text("Credit required by that provider") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val valid = imgUrl.startsWith("https://") && "{z}" in imgUrl && "{x}" in imgUrl && "{y}" in imgUrl && imgAttr.isNotBlank()
        TextButton(onClick = { a.onImagery(imgUrl, imgAttr) }, enabled = valid && (imgUrl != v.imageryUrl || imgAttr != v.imageryAttribution)) { Text("Save") }
        if (!valid) Text("Needs https://, {z} {x} {y} and a credit.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- Navigation

@Composable
private fun NavigationPage(v: SettingsValues, a: SettingsActions) {
    Category(R.drawable.ic_compass, "Voice guidance", "Language, speech engine, voice", a.onOpenVoice)
    HorizontalDivider()
    Section("Route options")
    SwitchRow("Avoid tolls", null, v.avoidTolls, a.onAvoidTolls)
    SwitchRow("Avoid motorways", null, v.avoidHighways, a.onAvoidHighways)
    SwitchRow("Avoid ferries", null, v.avoidFerries, a.onAvoidFerries)
    Note("Used when there's another reasonable way.")
    Section("While driving")
    SwitchRow("Speed alert", "Beep when over the speed limit", v.speedAlert, a.onSpeedAlert)
}

// ---------------------------------------------------------------- Advanced

@Composable
private fun AdvancedPage(v: SettingsValues, a: SettingsActions) {
    Section("Servers")
    ServerField("Routing server", v.valhallaUrl, com.zoomi.app.data.SettingsRepository.DEFAULT_VALHALLA,
        "Default: FOSSGIS volunteer server", a.onValhallaUrl)
    ServerField("Search server", v.photonUrl, com.zoomi.app.data.SettingsRepository.DEFAULT_PHOTON,
        "Default: komoot public server", a.onPhotonUrl)

    Section("Theme right now")
    val d = v.diagnostics
    val reason = when (v.decision.reason) {
        ThemeReason.USER_LIGHT -> "You chose Light"
        ThemeReason.USER_DARK -> "You chose Dark"
        ThemeReason.DAY -> "Daytime"
        ThemeReason.NIGHT -> "Night-time"
        ThemeReason.TUNNEL -> "Tunnel / underground"
        ThemeReason.SYSTEM_FALLBACK -> "No GPS yet, using phone setting"
    }
    Info("Current", (if (v.decision.dark) "Dark · " else "Light · ") + reason)
    Info("Sun elevation", d.sunElevationDeg?.let { String.format(Locale.US, "%.1f°", it) } ?: "unknown")
    Info("Ambient light", if (!v.lightSensorAvailable) "no sensor" else d.lux?.let { String.format(Locale.US, "%.0f lux", it) } ?: "waiting…")
    Info("Last GPS fix", d.fixAgeMs?.let { "${it / 1000} s ago" } ?: "none yet")
    Info("Map under you", when (d.onTunnelRoad) { true -> "tunnel"; false -> "road"; null -> "unknown" })
    Info("Route under you", when (d.routeTunnel) { true -> "tunnel"; false -> "road"; null -> "not navigating" })
}

// ---------------------------------------------------------------- Building blocks

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
private fun Radio(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(checked, onClick = { onChange(!checked) }, role = Role.Switch).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
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
private fun ServerField(label: String, value: String, default: String, note: String, onSave: (String?) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val valid = text.startsWith("https://") || text.startsWith("http://")
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true,
            isError = !valid, modifier = Modifier.fillMaxWidth())
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
