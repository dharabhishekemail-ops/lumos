import SwiftUI

struct NearbyCard: Identifiable {
    let id: String
    let alias: String
    let tags: String
    let intent: String
    let freshness: String
}

struct DiscoveryView: View {
    let onOpenRequests: () -> Void
    let onOpenChat: () -> Void
    let onOpenSafety: () -> Void
    let onOpenDiagnostics: () -> Void

    @State private var visible = false
    @State private var status = "Hidden"
    @State private var nearby: [NearbyCard] = []
    @State private var toast: String? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Text("Discovery").font(.largeTitle).bold()
                    Spacer()
                    Button(action: onOpenRequests) { Image(systemName: "tray") }
                    Button(action: onOpenChat) { Image(systemName: "bubble.left.and.bubble.right") }
                    Button(action: onOpenSafety) { Image(systemName: "shield") }
                    Button(action: onOpenDiagnostics) { Image(systemName: "stethoscope") }
                }
                .padding(.horizontal, DS.pad2).padding(.top, 10)

                VStack(alignment: .leading, spacing: 8) {
                    Text(status).font(.headline)
                    HStack {
                        Button(visible ? "Pause (Go Hidden)" : "Go Visible") {
                            visible.toggle()
                            status = visible ? "Visible (BLE + Wi‑Fi scanning)" : "Hidden"
                            nearby = visible ? fakeNearby() : []
                        }
                        .buttonStyle(.borderedProminent)

                        Button("Refresh") {
                            if visible { nearby = fakeNearby() }
                            else { toast = "Tap “Go Visible” to discover nearby." }
                        }
                        .buttonStyle(.bordered)
                    }
                    Text("Only limited preview is shown until mutual acceptance. IDs rotate to reduce linkability.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .padding(14)
                .background(.regularMaterial)
                .clipShape(RoundedRectangle(cornerRadius: DS.corner, style: .continuous))
                .padding(.horizontal, DS.pad2)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Nearby (venue range)").font(.headline).padding(.horizontal, DS.pad2)
                    if !visible {
                        InfoCard(title: "You’re hidden", subtitle: "Tap “Go Visible” to discover people nearby.") {}
                            .padding(.horizontal, DS.pad2)
                    } else {
                        ForEach(nearby) { c in
                            InfoCard(
                                title: "\(c.alias) • \(c.intent)",
                                subtitle: c.tags,
                                meta: "Seen \(c.freshness) ago"
                            ) {
                                toast = "Interest sent (encrypted) • Awaiting reply"
                            }
                            .padding(.horizontal, DS.pad2)
                        }
                    }
                }
            }
            .padding(.bottom, 18)
        }
        .background(DS.gradient(isDark: false))
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast).padding(12).background(.thinMaterial)
                    .clipShape(Capsule()).padding(.bottom, 20)
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { self.toast = nil }
                    }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    private func fakeNearby() -> [NearbyCard] {
        let intents = ["Dating", "Friends", "Chat"]
        let tags = ["music, coffee", "travel, books", "fitness, tech", "art, cinema"]
        return (0..<(Int.random(in: 3...7))).map { i in
            NearbyCard(id: "peer_\(i)",
                       alias: ["Nova","Kai","Riya","Ayaan","Mira","Zoe"].randomElement()!,
                       tags: tags.randomElement()!,
                       intent: intents.randomElement()!,
                       freshness: "\(Int.random(in: 1...20))s")
        }
    }
}
