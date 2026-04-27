package com.lumos.discovery

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DiscoveryRoute(
    onOpenRequests: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    vm: DiscoveryViewModel = viewModel()
) {
    DiscoveryScreen(
        state = vm.state,
        onAction = vm::onAction,
        onOpenRequests = onOpenRequests,
        onOpenChat = onOpenChat,
        onOpenSafety = onOpenSafety,
        onOpenDiagnostics = onOpenDiagnostics
    )
}
