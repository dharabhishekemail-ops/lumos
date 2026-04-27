import SwiftUI

struct ProfileView: View {
    let onDone: () -> Void
    @State private var alias = ""
    @State private var bio = ""
    @State private var intent = "Dating"
    @State private var tags = ""
    @State private var startVisible = false
    @State private var error: String? = nil

    let intents = ["Dating", "Friends", "Chat", "Browsing"]

    var body: some View {
        Form {
            Section("Basics") {
                TextField("Alias", text: $alias)
                TextField("Bio", text: $bio, axis: .vertical).lineLimit(3...6)
            }
            Section("Intent & Tags") {
                Picker("Intent", selection: $intent) {
                    ForEach(intents, id: \.self) { Text($0) }
                }
                TextField("Interests/Tags (comma separated)", text: $tags)
            }
            Section("Visibility") {
                Toggle("Start visible by default", isOn: $startVisible)
                Text("Recommended: start hidden; tap “Go Visible” in a venue.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if let error {
                Text(error).foregroundStyle(.red)
            }
            Section {
                PrimaryButton(title: "Save & Continue") {
                    if alias.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        error = "Please choose an alias."
                    } else {
                        onDone()
                    }
                }
            }
        }
        .navigationTitle("Your Profile")
    }
}
