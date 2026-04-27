package com.lumos.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class OnboardingState(
    val ageConfirmed: Boolean = false,
    val consentCore: Boolean = false,
    val consentCrash: Boolean = false,
    val consentAdsPersonalization: Boolean = false,
    val canContinue: Boolean = false,
    val error: String? = null
)

class OnboardingViewModel : ViewModel() {
    var state by mutableStateOf(OnboardingState())
        private set

    fun toggleConsent(kind: ConsentKind) {
        state = when (kind) {
            ConsentKind.Age -> state.copy(ageConfirmed = !state.ageConfirmed, error = null)
            ConsentKind.Core -> state.copy(consentCore = !state.consentCore, error = null)
            ConsentKind.Crash -> state.copy(consentCrash = !state.consentCrash, error = null)
            ConsentKind.Ads -> state.copy(consentAdsPersonalization = !state.consentAdsPersonalization, error = null)
        }.let { it.copy(canContinue = it.ageConfirmed && it.consentCore) }
    }

    fun continueIfAllowed(onContinue: () -> Unit) {
        if (state.canContinue) onContinue()
        else state = state.copy(error = "Please confirm age and accept required consent to continue.")
    }
}

enum class ConsentKind { Age, Core, Crash, Ads }
