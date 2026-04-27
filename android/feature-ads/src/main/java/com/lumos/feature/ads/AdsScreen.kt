package com.lumos.feature.ads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PromoCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val ctaLabel: String?,
)

@Composable
fun AdsTabScreen(
    items: List<PromoCard>,
    onCta: (PromoCard) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Promotions", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { promo ->
                ElevatedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(promo.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(promo.subtitle, style = MaterialTheme.typography.bodyMedium)
                        if (!promo.ctaLabel.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { onCta(promo) }) { Text((promo.ctaLabel ?: "Learn more")) }
                        }
                    }
                }
            }
        }
    }
}
