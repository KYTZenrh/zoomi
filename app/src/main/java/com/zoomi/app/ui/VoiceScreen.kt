package com.zoomi.app.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zoomi.app.VoiceUi
import com.zoomi.app.nav.GuidanceLanguage

@Composable
fun VoiceScreen(
    ui: VoiceUi,
    onLanguage: (GuidanceLanguage) -> Unit,
    onEngine: (String?) -> Unit,
    onVoice: (String?) -> Unit,
    onTest: (String?) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header("Voice guidance", onBack)

            Label("Language")
            Text("Applies from the next route.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            GuidanceLanguage.entries.forEach { l ->
                Row(
                    Modifier.fillMaxWidth().selectable(l == ui.language, onClick = { onLanguage(l) }, role = Role.RadioButton)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = l == ui.language, onClick = null, modifier = Modifier.padding(8.dp))
                    Text(l.label)
                }
            }

            Label("Speech engine")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Phone default", ui.engine == null) { onEngine(null) }
                ui.engines.forEach { e -> Chip(e.label, ui.engine == e.packageName) { onEngine(e.packageName) } }
            }

            Label("Voice")
            Text(
                "Tap Test to hear each voice.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            VoiceRow("Engine default", null, ui.voice == null, onVoice, onTest)
            if (!ui.loading && ui.voices.isEmpty()) {
                Text("This engine has no voice for this language yet.", color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            ui.voices.forEachIndexed { i, v ->
                val tags = listOfNotNull(v.genderHint, v.localeTag, if (!v.installed) "download" else null, if (v.needsNetwork) "online" else "offline")
                VoiceRow("Voice ${i + 1} · ${tags.joinToString(" · ")}", v.name, ui.voice == v.name, onVoice, onTest, sub = v.name)
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val i = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply { ui.engine?.let { setPackage(it) } }
                    runCatching { context.startActivity(i) }
                }) { Text("Get more voices") }
                TextButton(onClick = onRefresh) { Text("Refresh list") }
            }
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontWeight = if (selected) FontWeight.Bold else null) }
}

@Composable
private fun VoiceRow(title: String, name: String?, selected: Boolean, onVoice: (String?) -> Unit, onTest: (String?) -> Unit, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = { onVoice(name) }, role = Role.RadioButton).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        TextButton(onClick = { onTest(name) }) { Text("Test") }
    }
}
