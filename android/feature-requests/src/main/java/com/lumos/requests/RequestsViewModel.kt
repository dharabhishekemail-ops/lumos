package com.lumos.requests

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class RequestItem(val id: String, val from: String, val tags: String, val whenSeen: String)

data class RequestsState(
    val inbox: List<RequestItem> = listOf(
        RequestItem("r1", "Nova", "music, coffee", "10s"),
        RequestItem("r2", "Mira", "art, cinema", "42s"),
    ),
    val toast: String? = null
)

sealed interface RequestsAction {
    data class Accept(val id: String): RequestsAction
    data class Reject(val id: String): RequestsAction
    data class Block(val id: String): RequestsAction
    data object ClearToast: RequestsAction
}

class RequestsViewModel : ViewModel() {
    var state by mutableStateOf(RequestsState())
        private set

    fun onAction(a: RequestsAction) {
        state = when(a) {
            is RequestsAction.Accept -> state.copy(toast = "Accepted • Chat unlocked (E2E)"); 
            is RequestsAction.Reject -> state.copy(toast = "Rejected")
            is RequestsAction.Block -> state.copy(toast = "Blocked • Hidden from you")
            RequestsAction.ClearToast -> state.copy(toast = null)
        }
    }
}
