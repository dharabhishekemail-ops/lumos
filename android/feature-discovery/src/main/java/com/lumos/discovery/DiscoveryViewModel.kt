package com.lumos.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.random.Random

data class NearbyCard(
    val id: String,
    val alias: String,
    val tags: String,
    val intent: String,
    val freshness: String
)

data class DiscoveryState(
    val visible: Boolean = false,
    val nearby: List<NearbyCard> = emptyList(),
    val status: String = "Hidden",
    val error: String? = null
)

sealed interface DiscoveryAction {
    data object ToggleVisible : DiscoveryAction
    data object Refresh : DiscoveryAction
    data class SendInterest(val id: String) : DiscoveryAction
}

class DiscoveryViewModel : ViewModel() {
    var state by mutableStateOf(DiscoveryState())
        private set

    fun onAction(a: DiscoveryAction) {
        when (a) {
            DiscoveryAction.ToggleVisible -> {
                val nowVisible = !state.visible
                state = state.copy(
                    visible = nowVisible,
                    status = if (nowVisible) "Visible (BLE + Wi‑Fi scanning)" else "Hidden",
                    nearby = if (nowVisible) fakeNearby() else emptyList(),
                    error = null
                )
            }
            DiscoveryAction.Refresh -> {
                if (!state.visible) {
                    state = state.copy(error = "Tap “Go Visible” to discover people nearby.")
                } else {
                    state = state.copy(nearby = fakeNearby(), error = null)
                }
            }
            is DiscoveryAction.SendInterest -> {
                state = state.copy(status = "Interest sent (encrypted) • Awaiting reply", error = null)
            }
        }
    }

    private fun fakeNearby(): List<NearbyCard> {
        val intents = listOf("Dating", "Friends", "Chat")
        val tags = listOf("music, coffee", "travel, books", "fitness, tech", "art, cinema")
        return (0 until Random.nextInt(3, 8)).map {
            NearbyCard(
                id = "peer_$it",
                alias = listOf("Nova", "Kai", "Riya", "Ayaan", "Mira", "Zoe").random(),
                tags = tags.random(),
                intent = intents.random(),
                freshness = "${Random.nextInt(1, 20)}s"
            )
        }
    }
}
