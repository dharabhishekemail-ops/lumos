// LumosProtocol — canonical encode/decode.
//
// Byte-equality contract: For every fixture in `fixtures/golden/`, this codec's
// `encode(typed:) -> Data` MUST produce bytes identical to the Python reference
// in `tools/canonical_codec.py` and to the Android Kotlin codec.
//
// Strategy: build the JSON by hand in canonical key order. We do NOT use
// JSONEncoder for top-level encoding because Swift's JSONEncoder does not
// guarantee key order (it uses the order of the encoder's container writes,
// which is fragile across Swift toolchain versions).

import Foundation

public enum CodecError: Error, Equatable {
    case malformedJSON(String)
    case unknownEnum(field: String, value: String)
    case missingField(String)
    case unknownField(String)
    case outOfRange(field: String)
    case sizeLimit(Int)
    case payloadTypeMismatch
}

public enum ProtocolLimits {
    public static let maxEnvelopeBytes = 32 * 1024     // 32 KiB
    public static let minIdLen = 8
    public static let maxIdLen = 64
}

public enum ProtocolCodec {

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public static func encode(_ msg: TypedMessage) throws -> Data {
        try validateEnvelope(msg.envelope)
        var out = "{\"envelope\":"
        out += encodeEnvelope(msg.envelope)
        out += ",\"payload\":"
        out += try encodePayload(msg.payload)
        out += "}"
        let data = Data(out.utf8)
        if data.count > ProtocolLimits.maxEnvelopeBytes {
            throw CodecError.sizeLimit(data.count)
        }
        return data
    }

    public static func decode(_ raw: Data) throws -> TypedMessage {
        if raw.count > ProtocolLimits.maxEnvelopeBytes {
            throw CodecError.sizeLimit(raw.count)
        }
        let any: Any
        do { any = try JSONSerialization.jsonObject(with: raw, options: []) }
        catch { throw CodecError.malformedJSON("\(error)") }
        guard let root = any as? [String: Any] else { throw CodecError.malformedJSON("root not object") }
        let knownRoot: Set<String> = ["envelope", "payload", "_meta"]
        for k in root.keys where !knownRoot.contains(k) {
            throw CodecError.unknownField("root.\(k)")
        }
        guard let envD = root["envelope"] as? [String: Any] else { throw CodecError.missingField("envelope") }
        guard let payD = root["payload"] as? [String: Any] else { throw CodecError.missingField("payload") }

        let envelope = try decodeEnvelope(envD)
        let payload = try decodePayload(envelope.msgType, payD)
        return TypedMessage(envelope: envelope, payload: payload)
    }

    /// Round-trip: decode then re-encode. For canonical fixtures, output equals input bytes.
    public static func roundTrip(_ raw: Data) throws -> Data {
        return try encode(decode(raw))
    }

    // ------------------------------------------------------------------
    // Envelope
    // ------------------------------------------------------------------

    private static func validateEnvelope(_ e: Envelope) throws {
        if e.v != 1 { throw CodecError.outOfRange(field: "v") }
        try validateId(e.sessionId, "sessionId")
        try validateId(e.messageId, "messageId")
        if e.sentAtMs < 0 || e.sentAtMs > 4_102_444_800_000 {
            throw CodecError.outOfRange(field: "sentAtMs")
        }
    }

    private static func validateId(_ s: String, _ field: String) throws {
        if s.count < ProtocolLimits.minIdLen || s.count > ProtocolLimits.maxIdLen {
            throw CodecError.outOfRange(field: field)
        }
        for u in s.unicodeScalars {
            let v = u.value
            let ok = (v >= 0x30 && v <= 0x39) ||
                     (v >= 0x41 && v <= 0x5A) ||
                     (v >= 0x61 && v <= 0x7A) ||
                     v == 0x5F || v == 0x2D
            if !ok { throw CodecError.outOfRange(field: field) }
        }
    }

    private static func encodeEnvelope(_ e: Envelope) -> String {
        // Canonical order: v, msgType, sessionId, messageId, sentAtMs, transport, payloadEncoding
        var s = "{"
        s += "\"v\":\(e.v),"
        s += "\"msgType\":" + jsonString(e.msgType.rawValue) + ","
        s += "\"sessionId\":" + jsonString(e.sessionId) + ","
        s += "\"messageId\":" + jsonString(e.messageId) + ","
        s += "\"sentAtMs\":\(e.sentAtMs),"
        s += "\"transport\":" + jsonString(e.transport.rawValue) + ","
        s += "\"payloadEncoding\":" + jsonString(e.payloadEncoding.rawValue)
        s += "}"
        return s
    }

    private static func decodeEnvelope(_ d: [String: Any]) throws -> Envelope {
        let known: Set<String> = ["v","msgType","sessionId","messageId","sentAtMs","transport","payloadEncoding"]
        for k in d.keys where !known.contains(k) {
            throw CodecError.unknownField("envelope.\(k)")
        }
        for k in known where d[k] == nil {
            throw CodecError.missingField("envelope.\(k)")
        }
        guard let v = d["v"] as? Int, v == 1 else { throw CodecError.outOfRange(field: "v") }
        guard let mtRaw = d["msgType"] as? String, let mt = MsgType(rawValue: mtRaw)
            else { throw CodecError.unknownEnum(field: "msgType", value: "\(d["msgType"] ?? "")") }
        guard let sid = d["sessionId"] as? String else { throw CodecError.missingField("envelope.sessionId") }
        guard let mid = d["messageId"] as? String else { throw CodecError.missingField("envelope.messageId") }
        try validateId(sid, "sessionId")
        try validateId(mid, "messageId")
        let sentAt: Int64
        if let n = d["sentAtMs"] as? Int { sentAt = Int64(n) }
        else if let n = d["sentAtMs"] as? Int64 { sentAt = n }
        else if let n = d["sentAtMs"] as? NSNumber {
            // Reject bools-as-numbers
            if CFGetTypeID(n) == CFBooleanGetTypeID() { throw CodecError.outOfRange(field: "sentAtMs") }
            sentAt = n.int64Value
        }
        else { throw CodecError.outOfRange(field: "sentAtMs") }
        if sentAt < 0 || sentAt > 4_102_444_800_000 { throw CodecError.outOfRange(field: "sentAtMs") }
        guard let trRaw = d["transport"] as? String, let tr = TransportKind(rawValue: trRaw)
            else { throw CodecError.unknownEnum(field: "transport", value: "\(d["transport"] ?? "")") }
        guard let peRaw = d["payloadEncoding"] as? String, let pe = PayloadEncoding(rawValue: peRaw)
            else { throw CodecError.unknownEnum(field: "payloadEncoding", value: "\(d["payloadEncoding"] ?? "")") }
        return Envelope(v: v, msgType: mt, sessionId: sid, messageId: mid, sentAtMs: sentAt, transport: tr, payloadEncoding: pe)
    }

    // ------------------------------------------------------------------
    // Payload — manual encoding in canonical key order
    // ------------------------------------------------------------------

    private static func encodePayload(_ p: TypedPayload) throws -> String {
        switch p {
        case .hello(let h):
            var parts: [String] = []
            parts.append("\"protocolVersions\":" + intArr(h.protocolVersions))
            parts.append("\"transports\":" + strArr(h.transports.map { $0.rawValue }))
            parts.append("\"aeadSuites\":" + strArr(h.aeadSuites.map { $0.rawValue }))
            parts.append("\"kdfSuites\":" + strArr(h.kdfSuites.map { $0.rawValue }))
            parts.append("\"curves\":" + strArr(h.curves.map { $0.rawValue }))
            parts.append("\"features\":" + strArr(h.features.map { $0.rawValue }))
            parts.append("\"nonce\":" + jsonString(h.nonce))
            return "{" + parts.joined(separator: ",") + "}"
        case .helloAck(let h):
            var p: [String] = []
            p.append("\"selectedProtocolVersion\":\(h.selectedProtocolVersion)")
            p.append("\"selectedTransport\":" + jsonString(h.selectedTransport.rawValue))
            p.append("\"selectedAead\":" + jsonString(h.selectedAead.rawValue))
            p.append("\"selectedKdf\":" + jsonString(h.selectedKdf.rawValue))
            p.append("\"selectedCurve\":" + jsonString(h.selectedCurve.rawValue))
            p.append("\"featuresAccepted\":" + strArr(h.featuresAccepted.map { $0.rawValue }))
            return "{" + p.joined(separator: ",") + "}"
        case .interestRequest(let r):
            var p: [String] = []
            p.append("\"previewProfile\":" + encodePreviewProfile(r.previewProfile))
            p.append("\"interestToken\":" + jsonString(r.interestToken))
            return "{" + p.joined(separator: ",") + "}"
        case .interestResponse(let r):
            if let reason = r.reason {
                return "{\"accepted\":\(r.accepted ? "true" : "false"),\"reason\":\(jsonString(reason))}"
            } else {
                return "{\"accepted\":\(r.accepted ? "true" : "false")}"
            }
        case .matchEstablished(let m):
            return "{\"matchId\":\(jsonString(m.matchId))}"
        case .chatText(let c):
            return "{\"ciphertextB64\":\(jsonString(c.ciphertextB64)),\"aadHashHex\":\(jsonString(c.aadHashHex)),\"ratchetIndex\":\(c.ratchetIndex)}"
        case .chatMediaChunk(let c):
            return "{\"transferId\":\(jsonString(c.transferId)),\"chunkIndex\":\(c.chunkIndex),\"isLast\":\(c.isLast ? "true" : "false"),\"ciphertextB64\":\(jsonString(c.ciphertextB64)),\"sha256Hex\":\(jsonString(c.sha256Hex))}"
        case .chatMediaAck(let a):
            return "{\"transferId\":\(jsonString(a.transferId)),\"highestContiguousChunk\":\(a.highestContiguousChunk)}"
        case .heartbeat(let h):
            return "{\"seq\":\(h.seq)}"
        case .errorMsg(let e):
            if let m = e.message {
                return "{\"code\":\(jsonString(e.code.rawValue)),\"retryable\":\(e.retryable ? "true" : "false"),\"message\":\(jsonString(m))}"
            } else {
                return "{\"code\":\(jsonString(e.code.rawValue)),\"retryable\":\(e.retryable ? "true" : "false")}"
            }
        case .transportMigrate(let m):
            return "{\"proposed\":" + strArr(m.proposed.map { $0.rawValue }) + "}"
        case .goodbye(let g):
            return "{\"reason\":\(jsonString(g.reason.rawValue))}"
        }
    }

    private static func encodePreviewProfile(_ p: PreviewProfile) -> String {
        return "{\"alias\":\(jsonString(p.alias)),\"tags\":\(strArr(p.tags)),\"intent\":\(jsonString(p.intent.rawValue))}"
    }

    private static func decodePayload(_ t: MsgType, _ d: [String: Any]) throws -> TypedPayload {
        switch t {
        case .hello:
            let known: Set<String> = ["protocolVersions","transports","aeadSuites","kdfSuites","curves","features","nonce"]
            try checkKeys(d, known: known, required: known, where_: "payload(HELLO)")
            guard let pv = d["protocolVersions"] as? [Int] else { throw CodecError.outOfRange(field: "protocolVersions") }
            let tr  = try decodeStrArr(d["transports"], TransportKind.self, "transports")
            let ae  = try decodeStrArr(d["aeadSuites"], AeadSuite.self, "aeadSuites")
            let kdf = try decodeStrArr(d["kdfSuites"], KdfSuite.self, "kdfSuites")
            let cv  = try decodeStrArr(d["curves"], CurveSuite.self, "curves")
            let ft  = try decodeStrArr(d["features"], FeatureFlag.self, "features")
            guard let nonce = d["nonce"] as? String else { throw CodecError.missingField("nonce") }
            return .hello(HelloPayload(protocolVersions: pv, transports: tr, aeadSuites: ae,
                                       kdfSuites: kdf, curves: cv, features: ft, nonce: nonce))
        case .helloAck:
            let known: Set<String> = ["selectedProtocolVersion","selectedTransport","selectedAead","selectedKdf","selectedCurve","featuresAccepted"]
            try checkKeys(d, known: known, required: known, where_: "payload(HELLO_ACK)")
            guard let spv = d["selectedProtocolVersion"] as? Int else { throw CodecError.outOfRange(field: "selectedProtocolVersion") }
            let st = try decodeStr(d["selectedTransport"], TransportKind.self, "selectedTransport")
            let sa = try decodeStr(d["selectedAead"], AeadSuite.self, "selectedAead")
            let sk = try decodeStr(d["selectedKdf"], KdfSuite.self, "selectedKdf")
            let sc = try decodeStr(d["selectedCurve"], CurveSuite.self, "selectedCurve")
            let fa = try decodeStrArr(d["featuresAccepted"], FeatureFlag.self, "featuresAccepted")
            return .helloAck(HelloAckPayload(selectedProtocolVersion: spv, selectedTransport: st,
                                             selectedAead: sa, selectedKdf: sk, selectedCurve: sc, featuresAccepted: fa))
        case .interestRequest:
            let known: Set<String> = ["previewProfile","interestToken"]
            try checkKeys(d, known: known, required: known, where_: "payload(INTEREST_REQUEST)")
            guard let pp = d["previewProfile"] as? [String: Any] else { throw CodecError.missingField("previewProfile") }
            let known2: Set<String> = ["alias","tags","intent"]
            try checkKeys(pp, known: known2, required: known2, where_: "previewProfile")
            guard let alias = pp["alias"] as? String else { throw CodecError.missingField("alias") }
            guard let tags = pp["tags"] as? [String] else { throw CodecError.missingField("tags") }
            guard let intentRaw = pp["intent"] as? String, let intent = InterestIntent(rawValue: intentRaw)
                else { throw CodecError.unknownEnum(field: "intent", value: "\(pp["intent"] ?? "")") }
            guard let tok = d["interestToken"] as? String else { throw CodecError.missingField("interestToken") }
            return .interestRequest(InterestRequestPayload(
                previewProfile: PreviewProfile(alias: alias, tags: tags, intent: intent),
                interestToken: tok))
        case .interestResponse:
            let known: Set<String> = ["accepted","reason"]
            try checkKeys(d, known: known, required: ["accepted"], where_: "payload(INTEREST_RESPONSE)")
            guard let acc = d["accepted"] as? Bool else { throw CodecError.missingField("accepted") }
            return .interestResponse(InterestResponsePayload(accepted: acc, reason: d["reason"] as? String))
        case .matchEstablished:
            let known: Set<String> = ["matchId"]
            try checkKeys(d, known: known, required: known, where_: "payload(MATCH_ESTABLISHED)")
            guard let mid = d["matchId"] as? String else { throw CodecError.missingField("matchId") }
            return .matchEstablished(MatchEstablishedPayload(matchId: mid))
        case .chatText:
            let known: Set<String> = ["ciphertextB64","aadHashHex","ratchetIndex"]
            try checkKeys(d, known: known, required: known, where_: "payload(CHAT_TEXT)")
            guard let ct = d["ciphertextB64"] as? String else { throw CodecError.missingField("ciphertextB64") }
            guard let ah = d["aadHashHex"] as? String else { throw CodecError.missingField("aadHashHex") }
            let ri: Int64
            if let n = d["ratchetIndex"] as? Int { ri = Int64(n) }
            else if let n = d["ratchetIndex"] as? Int64 { ri = n }
            else { throw CodecError.outOfRange(field: "ratchetIndex") }
            return .chatText(ChatTextPayload(ciphertextB64: ct, aadHashHex: ah, ratchetIndex: ri))
        case .chatMediaChunk:
            let known: Set<String> = ["transferId","chunkIndex","isLast","ciphertextB64","sha256Hex"]
            try checkKeys(d, known: known, required: known, where_: "payload(CHAT_MEDIA_CHUNK)")
            guard let tid = d["transferId"] as? String else { throw CodecError.missingField("transferId") }
            guard let ci = d["chunkIndex"] as? Int else { throw CodecError.missingField("chunkIndex") }
            guard let il = d["isLast"] as? Bool else { throw CodecError.missingField("isLast") }
            guard let ct = d["ciphertextB64"] as? String else { throw CodecError.missingField("ciphertextB64") }
            guard let sh = d["sha256Hex"] as? String else { throw CodecError.missingField("sha256Hex") }
            return .chatMediaChunk(ChatMediaChunkPayload(transferId: tid, chunkIndex: ci, isLast: il, ciphertextB64: ct, sha256Hex: sh))
        case .chatMediaAck:
            let known: Set<String> = ["transferId","highestContiguousChunk"]
            try checkKeys(d, known: known, required: known, where_: "payload(CHAT_MEDIA_ACK)")
            guard let tid = d["transferId"] as? String else { throw CodecError.missingField("transferId") }
            guard let hcc = d["highestContiguousChunk"] as? Int else { throw CodecError.missingField("highestContiguousChunk") }
            return .chatMediaAck(ChatMediaAckPayload(transferId: tid, highestContiguousChunk: hcc))
        case .heartbeat:
            let known: Set<String> = ["seq"]
            try checkKeys(d, known: known, required: known, where_: "payload(HEARTBEAT)")
            let seq: Int64
            if let n = d["seq"] as? Int { seq = Int64(n) }
            else if let n = d["seq"] as? Int64 { seq = n }
            else { throw CodecError.outOfRange(field: "seq") }
            return .heartbeat(HeartbeatPayload(seq: seq))
        case .errorMsg:
            let known: Set<String> = ["code","retryable","message"]
            try checkKeys(d, known: known, required: ["code","retryable"], where_: "payload(ERROR)")
            let c = try decodeStr(d["code"], ErrorCode.self, "code")
            guard let r = d["retryable"] as? Bool else { throw CodecError.missingField("retryable") }
            return .errorMsg(ErrorPayload(code: c, retryable: r, message: d["message"] as? String))
        case .transportMigrate:
            let known: Set<String> = ["proposed"]
            try checkKeys(d, known: known, required: known, where_: "payload(TRANSPORT_MIGRATE)")
            let prop = try decodeStrArr(d["proposed"], TransportKind.self, "proposed")
            return .transportMigrate(TransportMigratePayload(proposed: prop))
        case .goodbye:
            let known: Set<String> = ["reason"]
            try checkKeys(d, known: known, required: known, where_: "payload(GOODBYE)")
            let r = try decodeStr(d["reason"], GoodbyeReason.self, "reason")
            return .goodbye(GoodbyePayload(reason: r))
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static func checkKeys(_ d: [String: Any], known: Set<String>, required: Set<String>, where_: String) throws {
        for k in d.keys where !known.contains(k) {
            throw CodecError.unknownField("\(where_).\(k)")
        }
        for k in required where d[k] == nil {
            throw CodecError.missingField("\(where_).\(k)")
        }
    }

    private static func decodeStr<E: RawRepresentable>(_ v: Any?, _ t: E.Type, _ field: String) throws -> E where E.RawValue == String {
        guard let s = v as? String, let e = E(rawValue: s) else {
            throw CodecError.unknownEnum(field: field, value: "\(v ?? "")")
        }
        return e
    }

    private static func decodeStrArr<E: RawRepresentable>(_ v: Any?, _ t: E.Type, _ field: String) throws -> [E] where E.RawValue == String {
        guard let arr = v as? [String] else { throw CodecError.outOfRange(field: field) }
        var out: [E] = []
        for s in arr {
            guard let e = E(rawValue: s) else {
                throw CodecError.unknownEnum(field: field, value: s)
            }
            out.append(e)
        }
        return out
    }

    private static func intArr(_ a: [Int]) -> String {
        return "[" + a.map { String($0) }.joined(separator: ",") + "]"
    }

    private static func strArr(_ a: [String]) -> String {
        return "[" + a.map { jsonString($0) }.joined(separator: ",") + "]"
    }

    /// JSON string with ASCII-safe escaping. Matches Python json.dumps(ensure_ascii=True).
    private static func jsonString(_ s: String) -> String {
        var out = "\""
        for u in s.unicodeScalars {
            let v = u.value
            switch v {
            case 0x22: out += "\\\""
            case 0x5C: out += "\\\\"
            case 0x08: out += "\\b"
            case 0x0C: out += "\\f"
            case 0x0A: out += "\\n"
            case 0x0D: out += "\\r"
            case 0x09: out += "\\t"
            default:
                if v < 0x20 { out += String(format: "\\u%04x", v) }
                else if v < 0x7F { out += String(u) }
                else if v <= 0xFFFF { out += String(format: "\\u%04x", v) }
                else {
                    // Encode as surrogate pair (UTF-16)
                    let s = v - 0x10000
                    let hi = 0xD800 + (s >> 10)
                    let lo = 0xDC00 + (s & 0x3FF)
                    out += String(format: "\\u%04x\\u%04x", hi, lo)
                }
            }
        }
        out += "\""
        return out
    }
}
