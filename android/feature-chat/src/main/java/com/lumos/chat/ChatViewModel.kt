package com.lumos.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class ChatMsg(val id: String, val mine: Boolean, val text: String, val status: String)

data class ChatState(
    val peer: String = "Nova",
    val input: String = "",
    val items: List<ChatMsg> = listOf(
        ChatMsg("1", false, "Hey 👋", "delivered"),
        ChatMsg("2", true, "Hi! I liked your tags.", "delivered"),
    ),
    val transport: String = "Wi‑Fi local",
    val toast: String? = null
)

sealed interface ChatAction {
    data class Input(val v: String): ChatAction
    data object Send: ChatAction
    data object ClearToast: ChatAction
}

class ChatViewModel : ViewModel() {
    var state by mutableStateOf(ChatState())
        private set

    fun onAction(a: ChatAction) {
        state = when(a) {
            is ChatAction.Input -> state.copy(input = a.v.take(400))
            ChatAction.Send -> {
                val txt = state.input.trim()
                if (txt.isEmpty()) state.copy(toast = "Type a message first.")
                else {
                    val new = ChatMsg(id = "m${state.items.size+1}", mine = true, text = txt, status = "sending…")
                    state.copy(items = state.items + new, input = "", toast = "Sent (E2E)")
                }
            }
            ChatAction.ClearToast -> state.copy(toast = null)
        }
    }
}
