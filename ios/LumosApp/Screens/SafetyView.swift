import SwiftUI

struct SafetyView: View {
    var body: some View {
        List {
            Section {
                Text("Lumos facilitates introductions only. No obligation to respond, meet, or share identity.")
                    .foregroundStyle(.secondary)
            }
            Section("Meet safely") {
                Text("Meet in public • Prefer well-lit public places. Tell a friend.")
                Text("Control visibility • Go hidden anytime.")
            }
            Section("Controls") {
                Text("Block someone • Stops them from appearing for you on this device.")
                Text("Report abuse • Export report bundle for support review.")
            }
            Section("Privacy") {
                Text("Screenshot reminder • Respect consent. Screenshots can’t be prevented on all OS.")
            }
        }
        .navigationTitle("Safety Center")
    }
}
