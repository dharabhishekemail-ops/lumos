package com.lumos.safety

import androidx.compose.runtime.Composable
import com.lumos.safety.ui.SafetyScreen

@Composable
fun SafetyRoute(onBack: () -> Unit) {
    SafetyScreen(onBack = onBack)
}
