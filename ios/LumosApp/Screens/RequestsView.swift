import SwiftUI

struct RequestsView: View {
    struct Req: Identifiable { let id: String; let from: String; let tags: String; let seen: String }
    @State private var items: [Req] = [
        .init(id: "r1", from: "Nova", tags: "music, coffee", seen: "10s"),
        .init(id: "r2", from: "Mira", tags: "art, cinema", seen: "42s"),
    ]
    @State private var toast: String? = nil

    var body: some View {
        List {
            Section {
                Text("Incoming interests (encrypted). Chat unlocks only after mutual accept.")
                    .foregroundStyle(.secondary)
            }
            Section("Inbox") {
                ForEach(items) { r in
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(r.from) • seen \(r.seen) ago").font(.headline)
                        Text(r.tags).foregroundStyle(.secondary)
                        HStack {
                            Button("Accept") { toast = "Accepted • Chat unlocked (E2E)" }
                                .buttonStyle(.borderedProminent)
                            Button("Reject") { toast = "Rejected" }.buttonStyle(.bordered)
                            Button("Block") { toast = "Blocked • Hidden from you" }.buttonStyle(.borderless)
                        }
                    }.padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Requests")
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast).padding(12).background(.thinMaterial)
                    .clipShape(Capsule()).padding(.bottom, 20)
                    .onAppear { DispatchQueue.main.asyncAfter(deadline: .now()+1.4) { self.toast=nil } }
            }
        }
    }
}
