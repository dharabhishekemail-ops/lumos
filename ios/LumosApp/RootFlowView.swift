import SwiftUI

enum AppRoute: Hashable {
    case onboarding
    case profile
    case discovery
    case requests
    case chat
    case safety
    case diagnostics
}

struct RootFlowView: View {
    @State private var path: [AppRoute] = [.onboarding]

    var body: some View {
        NavigationStack(path: $path) {
            OnboardingView(onContinue: { path.append(.profile) })
                .navigationDestination(for: AppRoute.self) { r in
                    switch r {
                    case .onboarding: OnboardingView(onContinue: { path.append(.profile) })
                    case .profile: ProfileView(onDone: { path.append(.discovery) })
                    case .discovery: DiscoveryView(
                        onOpenRequests: { path.append(.requests) },
                        onOpenChat: { path.append(.chat) },
                        onOpenSafety: { path.append(.safety) },
                        onOpenDiagnostics: { path.append(.diagnostics) }
                    )
                    case .requests: RequestsView()
                    case .chat: ChatView()
                    case .safety: SafetyView()
                    case .diagnostics: DiagnosticsView()
                    }
                }
        }
    }
}
