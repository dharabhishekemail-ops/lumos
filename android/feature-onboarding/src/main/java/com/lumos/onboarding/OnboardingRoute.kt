package com.lumos.onboarding

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OnboardingRoute(onContinue: () -> Unit, vm: OnboardingViewModel = viewModel()) {
    OnboardingScreen(
        state = vm.state,
        onToggleConsent = vm::toggleConsent,
        onContinue = { vm.continueIfAllowed(onContinue) }
    )
}
