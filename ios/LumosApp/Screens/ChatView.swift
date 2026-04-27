import SwiftUI

struct ChatView: View {
    struct Msg: Identifiable { let id: String; let mine: Bool; let text: String; let status: String }
    @State private var peer = "Nova"
    @State private var input = ""
    @State private var transport = "Wi‑Fi local"
    @State private var msgs: [Msg] = [
        .init(id: "1", mine: false, text: "Hey 👋", status: "delivered"),
        .init(id: "2", mine: true, text: "Hi! I liked your tags.", status: "delivered")
    ]
    @State private var toast: String? = nil

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { _ in
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(msgs) { m in
                            HStack {
                                if m.mine { Spacer() }
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(m.text)
                                    Text(m.status).font(.caption).foregroundStyle(.secondary)
                                }
                                .padding(12)
                                .background(m.mine ? Color.purple.opacity(0.12) : Color.gray.opacity(0.12))
                                .clipShape(RoundedRectangle(cornerRadius: DS.corner, style: .continuous))
                                if !m.mine { Spacer() }
                            }
                        }
                    }.padding(DS.pad2)
                }
            }
            Divider()
            HStack(spacing: 10) {
                TextField("Message…", text: $input)
                    .textFieldStyle(.roundedBorder)
                Button("Send") {
                    let t = input.trimmingCharacters(in: .whitespacesAndNewlines)
                    if t.isEmpty { toast = "Type a message first."; return }
                    msgs.append(.init(id: "m\(msgs.count+1)", mine: true, text: t, status: "sending…"))
                    input = ""
                    toast = "Sent (E2E)"
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(12)
        }
        .navigationTitle(peer)
        .toolbar {
            Text(transport).font(.caption).foregroundStyle(.secondary)
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast).padding(12).background(.thinMaterial)
                    .clipShape(Capsule()).padding(.bottom, 20)
                    .onAppear { DispatchQueue.main.asyncAfter(deadline: .now()+1.2) { self.toast=nil } }
            }
        }
    }
}
