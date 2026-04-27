package com.lumos.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumos.designsystem.CardRow
import com.lumos.designsystem.LumosScaffold
import com.lumos.designsystem.PrimaryButton
import com.lumos.designsystem.SectionHeader

@Composable
fun DiscoveryScreen(
    state: DiscoveryState,
    onAction: (DiscoveryAction) -> Unit,
    onOpenRequests: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    LumosScaffold(
        title = "Discovery",
        actions = {
            IconButton(onClick = onOpenRequests) { Icon(Icons.Default.Inbox, contentDescription = "Requests") }
            IconButton(onClick = onOpenChat) { Icon(Icons.Default.Chat, contentDescription = "Chat") }
            IconButton(onClick = onOpenSafety) { Icon(Icons.Default.Shield, contentDescription = "Safety") }
            IconButton(onClick = onOpenDiagnostics) { Icon(Icons.Default.BugReport, contentDescription = "Diagnostics") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.status, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val btn = if (state.visible) "Pause (Go Hidden)" else "Go Visible"
                        Button(onClick = { onAction(DiscoveryAction.ToggleVisible) }) { Text(btn) }
                        OutlinedButton(onClick = { onAction(DiscoveryAction.Refresh) }) { Text("Refresh") }
                    }
                    Text(
                        "Only a limited preview is shown until mutual acceptance. IDs rotate to reduce linkability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.error?.let { AssistChip(onClick = {}, label = { Text(it) }) }

            SectionHeader("Nearby (venue range)")
            if (!state.visible) {
                ElevatedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("You’re hidden.", style = MaterialTheme.typography.titleMedium)
                        Text("Tap “Go Visible” to discover people nearby.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.nearby) { card ->
                        CardRow(
                            title = "${card.alias} • ${card.intent}",
                            subtitle = card.tags,
                            meta = "Seen ${card.freshness} ago",
                            onClick = { onAction(DiscoveryAction.SendInterest(card.id)) }
                        )
                    }
                }
            }
        }
    }
}
