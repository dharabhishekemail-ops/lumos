package com.lumos.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class ProfileState(
    val alias: String = "",
    val bio: String = "",
    val intent: String = "Dating",
    val tags: String = "",
    val visibleByDefault: Boolean = false,
    val error: String? = null
)

sealed interface ProfileAction {
    data class Alias(val v: String): ProfileAction
    data class Bio(val v: String): ProfileAction
    data class Intent(val v: String): ProfileAction
    data class Tags(val v: String): ProfileAction
    data class Visible(val v: Boolean): ProfileAction
}

class ProfileViewModel : ViewModel() {
    var state by mutableStateOf(ProfileState())
        private set

    fun onAction(a: ProfileAction) {
        state = when(a) {
            is ProfileAction.Alias -> state.copy(alias = a.v.take(32), error = null)
            is ProfileAction.Bio -> state.copy(bio = a.v.take(220), error = null)
            is ProfileAction.Intent -> state.copy(intent = a.v, error = null)
            is ProfileAction.Tags -> state.copy(tags = a.v.take(80), error = null)
            is ProfileAction.Visible -> state.copy(visibleByDefault = a.v, error = null)
        }
    }

    fun saveAndContinue(onDone: () -> Unit) {
        if (state.alias.isBlank()) {
            state = state.copy(error = "Please choose an alias.")
            return
        }
        onDone()
    }
}
