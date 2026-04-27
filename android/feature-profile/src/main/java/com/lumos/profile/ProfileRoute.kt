package com.lumos.profile

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ProfileRoute(onDone: () -> Unit, vm: ProfileViewModel = viewModel()) {
    ProfileScreen(
        state = vm.state,
        onAction = vm::onAction,
        onDone = { vm.saveAndContinue(onDone) }
    )
}
