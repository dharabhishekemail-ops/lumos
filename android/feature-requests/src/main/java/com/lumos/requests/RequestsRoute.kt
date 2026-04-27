package com.lumos.requests

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RequestsRoute(onBack: () -> Unit, vm: RequestsViewModel = viewModel()) {
    RequestsScreen(state = vm.state, onAction = vm::onAction, onBack = onBack)
}
