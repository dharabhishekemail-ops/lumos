package com.lumos.feature.operator

import com.lumos.core.remoteconfig.*

interface ConfigApplier {
    fun verifyAndApply(bytes: ByteArray): ApplyResult
    fun currentVersion(): String?
}

class OperatorModeService(
    private val fetcher: RemoteConfigFetcher,
    private val lkg: LastKnownGoodStore,
    private val applier: ConfigApplier,
) {
    suspend fun fetchAndApply(url: String, percent: Int): ApplyResult {
        val bytes = fetcher.fetch(url, 5000) ?: return ApplyResult.KeptLastKnownGood("Fetch failed")
        // In Phase 9 we will gate by rollout decider using stable device id hash
        val res = applier.verifyAndApply(bytes)
        if (res is ApplyResult.Applied) lkg.writeLkg(bytes)
        return res
    }

    fun rollback(): Boolean = lkg.rollbackToPrevious()

    fun activeVersion(): String? = applier.currentVersion()
}
