
package com.lumos.session.impl

import com.lumos.session.deps.ConfigRuntime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

/** Deterministic-friendly retry policy; provide Random seed in tests via constructor. */
class RetryPolicy(private val config: ConfigRuntime, private val rng: Random = Random.Default) {

    fun nextDelayMs(attempt: Int): Long {
        val c = config.current().retry
        val pow = 1L shl min(attempt, 10)
        val raw = c.baseDelayMs * pow
        val capped = min(raw, c.maxDelayMs)
        val jitter = (capped * (c.jitterPct.coerceIn(0, 100)) / 100.0).toLong()
        val delta = if (jitter <= 0) 0 else rng.nextLong(0, jitter + 1)
        return capped - (jitter / 2) + delta
    }
}

/** Simple LRU-ish dedupe window with TTL. */
class DedupeWindow(private val config: ConfigRuntime) {
    private data class Entry(val ts: Long)

    private val map = ConcurrentHashMap<String, Entry>()

    fun seenBefore(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        prune(nowMs)
        val existing = map.putIfAbsent(key, Entry(nowMs))
        return existing != null
    }

    private fun prune(nowMs: Long) {
        val c = config.current().dedupeWindow
        val ttl = c.ttlMs
        if (map.size <= c.windowSize) return
        // Remove expired first
        map.entries.removeIf { nowMs - it.value.ts > ttl }
        // If still too big, remove oldest deterministically by timestamp ordering
        if (map.size > c.windowSize) {
            val victims = map.entries.sortedBy { it.value.ts }.take(map.size - c.windowSize)
            victims.forEach { map.remove(it.key) }
        }
    }
}
