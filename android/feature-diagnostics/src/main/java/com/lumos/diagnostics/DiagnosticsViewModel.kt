package com.lumos.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class DiagnosticsState(
    val transport: String = "BLE+Wi‑Fi (idle)",
    val session: String = "No active session",
    val config: String = "Last-known-good: builtin",
    val privacy: String = "Crash upload: OFF",
    val exported: Boolean = false,
    val toast: String? = null
)

sealed interface DiagnosticsAction { data object Export : DiagnosticsAction; data object ClearToast: DiagnosticsAction }

class DiagnosticsViewModel : ViewModel() {
    var state by mutableStateOf(DiagnosticsState())
        private set

    fun onAction(a: DiagnosticsAction) {
        state = when(a) {
            DiagnosticsAction.Export -> state.copy(exported = true, toast = "Exported debug bundle (sanitized)")
            DiagnosticsAction.ClearToast -> state.copy(toast = null)
        }
    }
}
