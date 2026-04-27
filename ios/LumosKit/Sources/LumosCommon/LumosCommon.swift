// LumosCommon — shared identifiers, base types.
// Per Lumos SAS / Crypto Spec: stable IDs, no sensitive content here.

import Foundation

public enum LumosCommon {
    /// Lumos protocol major version. Only v=1 is currently supported.
    public static let protocolMajorVersion: Int = 1

    /// Length-bounded ID generator. Produces sessionId/messageId values that
    /// satisfy the canonical pattern `^[A-Za-z0-9_-]{8,64}$` (Protocol & Interop
    /// Contract v1.0 §3).
    public static func newId(prefix: String = "id") -> String {
        let raw = UUID().uuidString.replacingOccurrences(of: "-", with: "")
        let trimmed = String(raw.prefix(20))
        let p = prefix.replacingOccurrences(of: "_", with: "")
        let candidate = "\(p)_\(trimmed.lowercased())"
        // Guarantee 8..64 chars, all in [A-Za-z0-9_-].
        let filtered = candidate.unicodeScalars.filter { s in
            (s.value >= 0x30 && s.value <= 0x39) ||
            (s.value >= 0x41 && s.value <= 0x5A) ||
            (s.value >= 0x61 && s.value <= 0x7A) ||
            s.value == 0x5F || s.value == 0x2D
        }
        let s = String(String.UnicodeScalarView(filtered))
        if s.count < 8 { return s + String(repeating: "0", count: 8 - s.count) }
        if s.count > 64 { return String(s.prefix(64)) }
        return s
    }
}

/// Bridge for tests + cross-platform parity. iOS produces hex lowercase like Android.
public extension Data {
    func hexLowercase() -> String {
        return self.map { String(format: "%02x", $0) }.joined()
    }
}
