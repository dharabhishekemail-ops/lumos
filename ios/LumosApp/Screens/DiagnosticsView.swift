import SwiftUI

struct DiagnosticsView: View {
    @State private var transport = "BLE+Wi‑Fi (idle)"
    @State private var session = "No active session"
    @State private var config = "Last-known-good: builtin"
    @State private var privacy = "Crash upload: OFF"
    @State private var exported = false
    @State private var toast: String? = nil

    var body: some View {
        List {
            Section {
                Text("User-friendly system status. Export includes no sensitive content.")
                    .foregroundStyle(.secondary)
            }
            Section("Status") {
                LabeledContent("Transport", value: transport)
                LabeledContent("Session", value: session)
                LabeledContent("Config", value: config)
                LabeledContent("Privacy", value: privacy)
            }
            Section("Support") {
                PrimaryButton(title: "Export Debug Bundle") {
                    exported = true
                    toast = "Exported debug bundle (sanitized)"
                }
                if exported {
                    Text("Bundle ready to share").foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Diagnostics")
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast).padding(12).background(.thinMaterial)
                    .clipShape(Capsule()).padding(.bottom, 20)
                    .onAppear { DispatchQueue.main.asyncAfter(deadline: .now()+1.2) { self.toast=nil } }
            }
        }
    }
}
