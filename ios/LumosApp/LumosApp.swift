import SwiftUI
import LumosCommon
import LumosProtocol
import LumosCryptoAPI
import LumosConfig
import LumosTransport
import LumosSession
import LumosMedia

@main
struct LumosApp: App {

    @StateObject private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootFlowView()
                .environmentObject(container)
        }
    }
}

/// Central wiring container. Holds long-lived subsystems and exposes them to views.
/// Per Lumos SAS v1.0 §6 (composition root pattern).
final class AppContainer: ObservableObject {

    let crypto: CryptoFacade
    let configRuntime: ConfigRuntime
    let localCapabilities: LocalCapabilities

    init() {
        self.crypto = CryptoFacade()
        // Trust anchors must be embedded by the build pipeline. Empty in dev.
        // The runtime fails closed (refuses any signed config) if no anchors are present.
        let verifier = (try? CryptoKitEd25519Verifier(trustAnchors: [:])) ?? AnyVerifier()
        self.configRuntime = ConfigRuntime(verifier: verifier)
        self.localCapabilities = LocalCapabilities.defaultLocal
    }
}

/// Internal fallback verifier — only used if trust-anchor loading itself fails.
/// Refuses every signature so the app falls back to LKG / built-in defaults.
private final class AnyVerifier: Ed25519Verifier {
    func verify(message: Data, signature: Data, keyId: String) throws -> Bool { false }
}
