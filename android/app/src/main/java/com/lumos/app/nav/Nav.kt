package com.lumos.app.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lumos.onboarding.OnboardingRoute
import com.lumos.profile.ProfileRoute
import com.lumos.discovery.DiscoveryRoute
import com.lumos.requests.RequestsRoute
import com.lumos.chat.ChatRoute
import com.lumos.safety.SafetyRoute
import com.lumos.diagnostics.DiagnosticsRoute

object Routes {
    const val Onboarding = "onboarding"
    const val Profile = "profile"
    const val Discovery = "discovery"
    const val Requests = "requests"
    const val Chat = "chat"
    const val Safety = "safety"
    const val Diagnostics = "diagnostics"
}

@Composable
fun LumosNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.Onboarding) {
        composable(Routes.Onboarding) { OnboardingRoute(onContinue = { navController.navigate(Routes.Profile) }) }
        composable(Routes.Profile) { ProfileRoute(onDone = { navController.navigate(Routes.Discovery) }) }
        composable(Routes.Discovery) {
            DiscoveryRoute(
                onOpenRequests = { navController.navigate(Routes.Requests) },
                onOpenChat = { navController.navigate(Routes.Chat) },
                onOpenSafety = { navController.navigate(Routes.Safety) },
                onOpenDiagnostics = { navController.navigate(Routes.Diagnostics) }
            )
        }
        composable(Routes.Requests) { RequestsRoute(onBack = { navController.popBackStack() }) }
        composable(Routes.Chat) { ChatRoute(onBack = { navController.popBackStack() }) }
        composable(Routes.Safety) { SafetyRoute(onBack = { navController.popBackStack() }) }
        composable(Routes.Diagnostics) { DiagnosticsRoute(onBack = { navController.popBackStack() }) }
    }
}
