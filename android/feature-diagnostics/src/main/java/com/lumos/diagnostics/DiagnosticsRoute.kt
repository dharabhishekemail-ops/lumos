package com.lumos.diagnostics

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DiagnosticsRoute(onBack: () -> Unit, vm: DiagnosticsViewModel = viewModel()) {
    DiagnosticsScreen(state = vm.state, onAction = vm::onAction, onBack = onBack)
}
