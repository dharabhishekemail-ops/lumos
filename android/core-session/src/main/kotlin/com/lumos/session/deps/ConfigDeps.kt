
package com.lumos.session.deps

/** Minimal config runtime contract expected from config governance spec. */
interface ConfigRuntime {
    fun current(): SignedConfig
}

data class SignedConfig(
    val schemaVersion: Int,
    val retry: RetryConfig,
    val dedupeWindow: DedupeConfig,
    val rateLimits: RateLimitConfig
)

data class RetryConfig(
    val baseDelayMs: Long,
    val maxDelayMs: Long,
    val maxAttemptsText: Int,
    val jitterPct: Int
)

data class DedupeConfig(
    val windowSize: Int,
    val ttlMs: Long
)

data class RateLimitConfig(
    val interestsPer10Min: Int,
    val messagesPerMin: Int
)
