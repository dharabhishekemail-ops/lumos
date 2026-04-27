import SwiftUI

enum DS {
    static let corner: CGFloat = 18
    static let pad: CGFloat = 16
    static let pad2: CGFloat = 20
    static let buttonH: CGFloat = 52

    static func gradient(isDark: Bool) -> LinearGradient {
        let c1 = isDark ? Color(red: 0.08, green: 0.08, blue: 0.11) : Color(red: 0.96, green: 0.97, blue: 0.99)
        let c2 = isDark ? Color(red: 0.05, green: 0.05, blue: 0.07) : Color.white
        return LinearGradient(colors: [c1, c2], startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}

struct PrimaryButton: View {
    let title: String
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.headline)
                .frame(maxWidth: .infinity, minHeight: DS.buttonH)
        }
        .buttonStyle(.borderedProminent)
        .disabled(!enabled)
    }
}

struct InfoCard: View {
    let title: String
    let subtitle: String
    var meta: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(title).font(.headline)
                    Spacer()
                    if let meta { Text(meta).font(.caption).foregroundStyle(.secondary)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(.thinMaterial).clipShape(Capsule())
                    }
                }
                Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: DS.corner, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
