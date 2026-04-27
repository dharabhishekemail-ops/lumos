package com.lumos.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumos.designsystem.LumosScaffold
import com.lumos.designsystem.PrimaryButton
import com.lumos.designsystem.SectionHeader

@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
    onDone: () -> Unit
) {
    LumosScaffold(title = "Your Profile") { pad ->
        Column(
            modifier = Modifier.padding(pad).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader("Basics")
            OutlinedTextField(
                value = state.alias,
                onValueChange = { onAction(ProfileAction.Alias(it)) },
                label = { Text("Alias") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.bio,
                onValueChange = { onAction(ProfileAction.Bio(it)) },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            SectionHeader("Intent & Tags")
            IntentSelector(selected = state.intent, onChange = { onAction(ProfileAction.Intent(it)) })
            OutlinedTextField(
                value = state.tags,
                onValueChange = { onAction(ProfileAction.Tags(it)) },
                label = { Text("Interests/Tags (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader("Visibility")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Start hidden by default", style = MaterialTheme.typography.titleMedium)
                    Text("You must explicitly tap “Go Visible” in a venue.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = !state.visibleByDefault,
                    onCheckedChange = { onAction(ProfileAction.Visible(!it)) }
                )
            }

            state.error?.let { AssistChip(onClick = {}, label = { Text(it) }) }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(text = "Save & Continue", enabled = true, onClick = onDone)
        }
    }
}

@Composable
private fun IntentSelector(selected: String, onChange: (String) -> Unit) {
    val options = listOf("Dating", "Friends", "Chat", "Browsing")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick = { onChange(opt) },
                label = { Text(opt) }
            )
        }
    }
}
