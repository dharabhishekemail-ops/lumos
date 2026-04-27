package com.lumos.safety.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumos.designsystem.CardRow
import com.lumos.designsystem.LumosScaffold
import com.lumos.designsystem.SectionHeader

@Composable
fun SafetyScreen(onBack: () -> Unit) {
    LumosScaffold(title = "Safety Center", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Lumos facilitates introductions only. No obligation to respond, meet, or share identity.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SectionHeader("Meet safely")
            CardRow("Meet in public", "Prefer well-lit public places. Tell a friend.", null, onClick = {})
            CardRow("Control visibility", "Go hidden anytime. You choose when to be visible.", null, onClick = {})
            SectionHeader("Controls")
            CardRow("Block someone", "Stops them from appearing for you on this device.", null, onClick = {})
            CardRow("Report abuse", "Export report bundle for support review.", null, onClick = {})
            SectionHeader("Privacy")
            CardRow("Screenshot reminder", "Respect consent. Screenshots can’t be prevented on all OS.", "Optional", onClick = {})
        }
    }
}
