package com.zoomi.app.nav

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * A short double beep when you drive more than [toleranceKmh] over the limit for [holdMs].
 * At most once per [repeatMs] while you stay over it. Uses the navigation (alarm-free) audio stream.
 */
class SpeedAlert(
    private val toleranceKmh: Int = 5,
    private val holdMs: Long = 3_000,
    private val repeatMs: Long = 60_000,
) {
    @Volatile var enabled = true
    private var overSince: Long? = null
    private var lastBeep = 0L
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }

    fun update(now: Long, limitKmh: Int?, speedKmh: Float?) {
        val over = enabled && limitKmh != null && speedKmh != null && speedKmh > limitKmh + toleranceKmh
        if (!over) { overSince = null; return }
        val since = overSince ?: now.also { overSince = it }
        if (now - since >= holdMs && now - lastBeep >= repeatMs) {
            lastBeep = now
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        }
    }
}
