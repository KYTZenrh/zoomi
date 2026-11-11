package com.zoomi.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zoomi.app.nav.GuidanceLanguage
import com.zoomi.app.nav.TtsEngineInfo
import com.zoomi.app.nav.VoiceCatalog
import com.zoomi.app.nav.VoiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoiceUi(
    val language: GuidanceLanguage = GuidanceLanguage.AUTO,
    val engines: List<TtsEngineInfo> = emptyList(),
    val engine: String? = null,
    val voices: List<VoiceInfo> = emptyList(),
    val voice: String? = null,
    val loading: Boolean = true,
)

class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val z = app.zoomi
    private val catalog = VoiceCatalog(app)

    private val engines = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
    private val voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    private val loading = MutableStateFlow(true)

    val ui: StateFlow<VoiceUi> = combine(
        combine(z.settings.guidanceLanguage, z.settings.ttsEngine, z.settings.ttsVoice) { l, e, v -> Triple(l, e, v) },
        engines, voices, loading,
    ) { (l, e, v), es, vs, ld -> VoiceUi(l, es, e, vs, v, ld) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceUi())

    init {
        viewModelScope.launch { engines.value = catalog.engines() }
        viewModelScope.launch {
            combine(z.settings.guidanceLanguage, z.settings.ttsEngine) { l, e -> l to e }.collect { (l, e) ->
                loading.value = true
                voices.value = catalog.voices(e, VoiceCatalog.languageTag(l).substringBefore('-'))
                loading.value = false
            }
        }
    }

    fun setLanguage(l: GuidanceLanguage) = viewModelScope.launch { z.settings.setGuidanceLanguage(l) }
    fun setEngine(pkg: String?) = viewModelScope.launch { z.settings.setTtsEngine(pkg) }
    fun setVoice(name: String?) = viewModelScope.launch { z.settings.setTtsVoice(name) }
    fun test(name: String?) = viewModelScope.launch {
        catalog.test(ui.value.engine, name, VoiceCatalog.languageTag(ui.value.language))
    }
    fun refresh() = viewModelScope.launch {
        loading.value = true
        voices.value = catalog.voices(ui.value.engine, VoiceCatalog.languageTag(ui.value.language).substringBefore('-'))
        loading.value = false
    }
}
