package com.zoomi.app.nav

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class TtsEngineInfo(val packageName: String, val label: String)

data class VoiceInfo(
    val name: String,
    val localeTag: String,
    val installed: Boolean,
    val needsNetwork: Boolean,
    /** Only when the engine's voice name says so (Samsung "SMTf"/"SMTm"); Android itself doesn't report gender. */
    val genderHint: String?,
)

/** Guidance language: phone default, or forced Romanian / English. Drives both Valhalla text and the TTS voice. */
enum class GuidanceLanguage(val label: String) { AUTO("Phone language"), RO("Română"), EN("English") }

/**
 * Lists the speech engines and voices installed on the phone, and plays a test phrase.
 * Uses a short-lived TextToSpeech instance per call (released afterwards).
 */
class VoiceCatalog(context: Context) {
    private val appContext = context.applicationContext

    private suspend fun <T> withTts(engine: String?, block: (TextToSpeech) -> T): T? = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Int>()
        val tts = TextToSpeech(appContext, { status -> ready.complete(status) }, engine)
        try {
            val status = withTimeoutOrNull(5_000) { ready.await() }
            if (status == TextToSpeech.SUCCESS) block(tts) else null
        } finally {
            if (!keepAlive.contains(tts)) tts.shutdown()
        }
    }

    // A test phrase must outlive the call that starts it; those instances are released on the next call.
    private val keepAlive = mutableSetOf<TextToSpeech>()

    suspend fun engines(): List<TtsEngineInfo> =
        withTts(null) { tts -> tts.engines.map { TtsEngineInfo(it.name, it.label) } } ?: emptyList()

    suspend fun voices(engine: String?, language: String): List<VoiceInfo> = withTts(engine) { tts ->
        (tts.voices ?: emptySet<Voice>())
            .filter { it.locale.language.equals(language, ignoreCase = true) }
            .map { v ->
                val n = v.name.lowercase()
                VoiceInfo(
                    name = v.name,
                    localeTag = v.locale.toLanguageTag(),
                    installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features,
                    needsNetwork = v.isNetworkConnectionRequired,
                    genderHint = when {
                        "smtf" in n -> "female"
                        "smtm" in n -> "male"
                        else -> null
                    },
                )
            }
            .sortedWith(compareBy<VoiceInfo>({ !it.installed }, { it.needsNetwork }, { it.localeTag }, { it.name }))
    } ?: emptyList()

    /** Speaks a sample instruction with this voice (or the language default when [voiceName] is null). */
    suspend fun test(engine: String?, voiceName: String?, language: String) {
        keepAlive.forEach { it.shutdown() }
        keepAlive.clear()
        withContext(Dispatchers.Main) {
            val ready = CompletableDeferred<Int>()
            val tts = TextToSpeech(appContext, { ready.complete(it) }, engine)
            keepAlive += tts
            if (withTimeoutOrNull(5_000) { ready.await() } != TextToSpeech.SUCCESS) return@withContext
            val voice = voiceName?.let { n -> tts.voices?.firstOrNull { it.name == n } }
            if (voice != null) tts.voice = voice else tts.language = Locale.forLanguageTag(language)
            val text = if (language.startsWith("ro")) "Peste trei sute de metri, virați la dreapta." else "In 300 meters, turn right."
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zoomi-voice-test")
        }
    }

    companion object {
        /** BCP-47 tag for the guidance language. */
        fun languageTag(lang: GuidanceLanguage): String = when (lang) {
            GuidanceLanguage.RO -> "ro-RO"
            GuidanceLanguage.EN -> "en-US"
            GuidanceLanguage.AUTO -> Locale.getDefault().toLanguageTag()
        }
    }
}
