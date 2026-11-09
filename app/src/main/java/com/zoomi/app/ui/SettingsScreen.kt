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
    onModeChange: (ThemeMode) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            Header("Settings", onBack)

            Section("Map theme")
            ThemeOption(ThemeMode.LIGHT, "Light", "Always light", mode, onModeChange)
            ThemeOption(ThemeMode.DARK, "Dark", "Always dark", mode, onModeChange)
            ThemeOption(
                ThemeMode.AUTO, "Auto",
                "Dark from sunset to sunrise at your position, and inside tunnels", mode, onModeChange,
            )

            Section("Why the theme is what it is now")
            val reason = when (decision.reason) {
                ThemeReason.USER_LIGHT -> "You chose Light"
                ThemeReason.USER_DARK -> "You chose Dark"
                ThemeReason.DAY -> "Daytime at your position"
                ThemeReason.NIGHT -> "Night-time at your position"
                ThemeReason.TUNNEL -> "In a tunnel"
                ThemeReason.SYSTEM_FALLBACK -> "No GPS position yet, following the phone's dark-mode setting"
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
            Text(
                "Auto switches to dark when the sun is 2° below the horizon and back to light when it is 1° above. " +
                    "It never switches more than once a minute. Tunnel mode needs the map to show a tunnel where you are, " +
                    "plus lost GPS or darkness. Light sensor readings alone never switch the theme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}
