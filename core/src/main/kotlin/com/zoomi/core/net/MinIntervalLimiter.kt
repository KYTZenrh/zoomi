package com.zoomi.core.net

/**
 * Spaces out requests to a public server so we never exceed its published rate
 * (e.g. FOSSGIS Valhalla and komoot Photon: 1 request per second).
 *
 * [reserve] books the next free slot and returns how long the caller must wait first.
 * Thread-safe.
 */
class MinIntervalLimiter(private val minIntervalMs: Long) {
    private var nextFreeMs: Long = Long.MIN_VALUE

    @Synchronized
    fun reserve(nowMs: Long): Long {
        val start = if (nextFreeMs == Long.MIN_VALUE) nowMs else maxOf(nowMs, nextFreeMs)
        nextFreeMs = start + minIntervalMs
        return start - nowMs
    }
}
