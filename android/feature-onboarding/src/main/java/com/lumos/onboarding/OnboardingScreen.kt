package com.lumos.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumos.designsystem.LumosScaffold
import com.lumos.designsystem.PrimaryButton
import com.lumos.designsystem.SectionHeader

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onToggleConsent: (ConsentKind) -> Unit,
    onContinue: () -> Unit
) {
    LumosScaffold(title = "Welcome to Lumos") { pad ->
        Column(
            modifier = Modifier.padding(pad).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "A shy-friendly, consent-first way to connect nearby — works offline in venues.",
                style = MaterialTheme.typography.bodyLarge
            )

            SectionHeader("Eligibility")
            ConsentRow(
                checked = state.ageConfirmed,
                title = "I confirm I am 18+",
                subtitle = "Adults only. Underage users are blocked.",
                onCheckedChange = { onToggleConsent(ConsentKind.Age) }
            )

            SectionHeader("Consent")
            ConsentRow(
                checked = state.consentCore,
                title = "Agree to Terms + Privacy (Required)",
                subtitle = "Needed to enable local discovery and messaging.",
                onCheckedChange = { onToggleConsent(ConsentKind.Core) }
            )
            ConsentRow(
                checked = state.consentCrash,
                title = "Share crash diagnostics (Optional)",
                subtitle = "Helps improve stability. Off by default.",
                onCheckedChange = { onToggleConsent(ConsentKind.Crash) }
            )
            ConsentRow(
                checked = state.consentAdsPersonalization,
                title = "Personalized ads (Optional)",
                subtitle = "Off by default (privacy-first).",
                onCheckedChange = { onToggleConsent(ConsentKind.Ads) }
            )

            state.error?.let {
                AssistChip(onClick = {}, label = { Text(it) })
            }

            Spacer(Modifier.height(6.dp))
            PrimaryButton(text = "Continue", enabled = state.canContinue, onClick = onContinue)
            Text(
                "No chat or photos until mutual acceptance. You control visibility.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    title: String,
    subtitle: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
