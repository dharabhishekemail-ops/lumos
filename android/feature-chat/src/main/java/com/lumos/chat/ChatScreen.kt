package com.lumos.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumos.designsystem.LumosScaffold

@Composable
fun ChatScreen(state: ChatState, onAction: (ChatAction) -> Unit, onBack: () -> Unit) {
    LumosScaffold(title = state.peer, onBack = onBack, actions = {
        AssistChip(onClick = {}, label = { Text(state.transport) })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items) { msg ->
                    Bubble(msg)
                }
            }
            Divider()
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = { onAction(ChatAction.Input(it)) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message…") },
                    singleLine = true
                )
                Button(onClick = { onAction(ChatAction.Send) }, modifier = Modifier.height(52.dp)) {
                    Text("Send")
                }
            }

            state.toast?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(1200)
                    onAction(ChatAction.ClearToast)
                }
                Snackbar { Text(msg) }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
            color = if (m.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(m.text)
                Spacer(Modifier.height(4.dp))
                Text(m.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
