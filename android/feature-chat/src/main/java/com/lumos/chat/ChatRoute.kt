package com.lumos.chat

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatRoute(onBack: () -> Unit, vm: ChatViewModel = viewModel()) {
    ChatScreen(state = vm.state, onAction = vm::onAction, onBack = onBack)
}
