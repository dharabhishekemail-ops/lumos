package com.lumos.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumos.designsystem.CardRow
import com.lumos.designsystem.LumosScaffold
import com.lumos.designsystem.PrimaryButton
import com.lumos.designsystem.SectionHeader

@Composable
fun DiagnosticsScreen(state: DiagnosticsState, onAction: (DiagnosticsAction) -> Unit, onBack: () -> Unit) {
    LumosScaffold(title = "Diagnostics", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("User-friendly system status. Export includes no sensitive content.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionHeader("Status")
            CardRow("Transport", state.transport, null, onClick = {})
            CardRow("Session", state.session, null, onClick = {})
            CardRow("Config", state.config, null, onClick = {})
            CardRow("Privacy", state.privacy, null, onClick = {})

            SectionHeader("Support")
            PrimaryButton(text = "Export Debug Bundle", onClick = { onAction(DiagnosticsAction.Export) })
            if (state.exported) {
                AssistChip(onClick = {}, label = { Text("Bundle ready to share") })
            }

            state.toast?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(1200)
                    onAction(DiagnosticsAction.ClearToast)
                }
                Snackbar { Text(msg) }
            }
        }
    }
}
