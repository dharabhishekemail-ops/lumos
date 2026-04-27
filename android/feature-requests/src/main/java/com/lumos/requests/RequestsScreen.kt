package com.lumos.requests

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumos.designsystem.LumosScaffold

@Composable
fun RequestsScreen(state: RequestsState, onAction: (RequestsAction) -> Unit, onBack: () -> Unit) {
    LumosScaffold(title = "Requests", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Incoming interests (encrypted). Chat unlocks only after mutual accept.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.inbox) { it ->
                    ElevatedCard {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${it.from} • seen ${it.whenSeen} ago", style = MaterialTheme.typography.titleMedium)
                            Text(it.tags, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { onAction(RequestsAction.Accept(it.id)) }) { Text("Accept") }
                                OutlinedButton(onClick = { onAction(RequestsAction.Reject(it.id)) }) { Text("Reject") }
                                TextButton(onClick = { onAction(RequestsAction.Block(it.id)) }) { Text("Block") }
                            }
                        }
                    }
                }
            }
            state.toast?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(1500)
                    onAction(RequestsAction.ClearToast)
                }
                Snackbar { Text(msg) }
            }
        }
    }
}
