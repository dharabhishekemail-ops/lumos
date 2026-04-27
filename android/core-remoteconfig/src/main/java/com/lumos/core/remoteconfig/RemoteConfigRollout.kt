package com.lumos.core.remoteconfig

data class RolloutStage(
    val name: String,
    val percent: Int, // 0..100
    val minAppVersion: Int,
    val maxAppVersion: Int? = null,
)

data class RemoteConfigSource(
    val primaryUrl: String,
    val backupUrl: String? = null,
    val timeoutMs: Long = 5000,
)

sealed class ApplyResult {
    data class Applied(val configVersion: String) : ApplyResult()
    data class Rejected(val reason: String) : ApplyResult()
    data class KeptLastKnownGood(val reason: String) : ApplyResult()
}

interface RolloutDecider {
    fun isInRollout(stage: RolloutStage, stableDeviceIdHash: Int): Boolean
}

class PercentRolloutDecider : RolloutDecider {
    override fun isInRollout(stage: RolloutStage, stableDeviceIdHash: Int): Boolean {
        val bucket = kotlin.math.abs(stableDeviceIdHash) % 100
        return bucket < stage.percent
    }
}
