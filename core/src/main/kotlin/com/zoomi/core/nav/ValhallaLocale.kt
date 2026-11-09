package com.zoomi.core.nav

/**
 * Picks the Valhalla narrative language for the phone's locale.
 * List taken from valhalla/locales on GitHub (checked 2026-11-09).
 */
object ValhallaLocale {
    val SUPPORTED = listOf(
        "ar-SA", "bg-BG", "ca-ES", "cs-CZ", "da-DK", "de-DE", "el-GR", "en-AU", "en-GB", "en-US",
        "es-ES", "et-EE", "fi-FI", "fr-FR", "he-IL", "hi-IN", "hu-HU", "it-IT", "ja-JP", "ko-KR",
        "mn-MN", "nb-NO", "nl-NL", "pl-PL", "pt-BR", "pt-PT", "ro-RO", "ru-RU", "sk-SK", "sl-SI",
        "sv-SE", "tr-TR", "uk-UA", "vi-VN",
    )

    /** Exact tag match, then same language, then en-US. Accepts "ro_RO" and "ro-RO". */
    fun pick(languageTag: String): String {
        val tag = languageTag.replace('_', '-')
        SUPPORTED.firstOrNull { it.equals(tag, ignoreCase = true) }?.let { return it }
        val lang = tag.substringBefore('-').lowercase()
        if (lang == "en") return "en-US"
        if (lang == "pt") return "pt-PT"
        return SUPPORTED.firstOrNull { it.substringBefore('-') == lang } ?: "en-US"
    }
}
