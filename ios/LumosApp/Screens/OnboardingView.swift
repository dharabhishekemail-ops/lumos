import SwiftUI

struct OnboardingView: View {
    let onContinue: () -> Void
    @State private var age = false
    @State private var core = false
    @State private var crash = false
    @State private var ads = false
    @State private var error: String? = nil

    var canContinue: Bool { age && core }

    var body: some View {
        ZStack {
            DS.gradient(isDark: false).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Welcome to Lumos").font(.largeTitle).bold()
                    Text("A shy-friendly, consent-first way to connect nearby — works offline in venues.")
                        .foregroundStyle(.secondary)

                    GroupBox("Eligibility") {
                        Toggle("I confirm I am 18+", isOn: $age)
                        Text("Adults only. Underage users are blocked.").font(.caption).foregroundStyle(.secondary)
                    }

                    GroupBox("Consent") {
                        Toggle("Agree to Terms + Privacy (Required)", isOn: $core)
                        Toggle("Share crash diagnostics (Optional)", isOn: $crash)
                        Toggle("Personalized ads (Optional)", isOn: $ads)
                        Text("No chat or photos until mutual acceptance. You control visibility.")
                            .font(.caption).foregroundStyle(.secondary)
                    }

                    if let error {
                        Text(error).font(.callout).foregroundStyle(.red)
                    }

                    PrimaryButton(title: "Continue", enabled: canContinue) {
                        if canContinue { onContinue() }
                        else { error = "Confirm age and required consent to continue." }
                    }
                }
                .padding(DS.pad2)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
