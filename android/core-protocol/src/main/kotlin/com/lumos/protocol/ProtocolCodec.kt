package com.lumos.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonical Lumos protocol codec for Android (Kotlin).
 *
 * Byte-equality contract: For every fixture in `fixtures/golden/`, this codec's
 * `encode(TypedMessage): ByteArray` MUST produce bytes identical to the iOS
 * Swift codec (LumosKit/Sources/LumosProtocol/ProtocolCodec.swift) and to the
 * Python reference (tools/canonical_codec.py).
 *
 * Per Lumos Protocol & Interop Contract v1.0 §3.
 *
 * Rules:
 *   - UTF-8, no BOM, no trailing newline.
 *   - Compact: no spaces, separators are `,` between elements and `:` between key/value.
 *   - Envelope key order: v, msgType, sessionId, messageId, sentAtMs, transport, payloadEncoding.
 *   - Payload key order: per msgType, see [encPayload].
 *   - String escaping: ASCII-safe; non-ASCII codepoints emitted as \uXXXX.
 *   - Integers: bare digits, no `.0`, no scientific notation.
 *   - Booleans: lowercase `true`/`false`.
 *
 * Why hand-built instead of org.json.JSONObject? JSONObject does not guarantee
 * key insertion order across Android versions, and its toString() defaults
 * differ subtly from Python json.dumps. Hand-built emission removes that risk.
 */

class ProtocolCodecException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)

object ProtocolCodec {

    const val MAX_ENVELOPE_BYTES: Int = 32 * 1024
    const val MIN_ID_LEN: Int = 8
    const val MAX_ID_LEN: Int = 64
    private val ID_RE = Regex("^[A-Za-z0-9_\\-]{8,64}$")

    fun encode(m: TypedMessage): ByteArray {
        validateEnvelope(m.envelope)
        val sb = StringBuilder(256)
        sb.append('{')
        sb.append("\"envelope\":")
        encEnv(m.envelope, sb)
        sb.append(",\"payload\":")
        encPayload(m.payload, sb)
        sb.append('}')
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_ENVELOPE_BYTES) {
            throw ProtocolCodecException("encoded envelope exceeds $MAX_ENVELOPE_BYTES bytes: ${bytes.size}")
        }
        return bytes
    }

    fun decode(bytes: ByteArray): TypedMessage {
        if (bytes.size > MAX_ENVELOPE_BYTES) {
            throw ProtocolCodecException("input exceeds $MAX_ENVELOPE_BYTES bytes")
        }
        val root: JSONObject = try { JSONObject(bytes.toString(Charsets.UTF_8)) }
        catch (e: Exception) { throw ProtocolCodecException("Malformed JSON", e) }
        val knownRoot = setOf("envelope", "payload", "_meta")
        for (k in root.keys()) {
            if (k !in knownRoot) throw ProtocolCodecException("unknown root field: $k")
        }
        if (!root.has("envelope") || !root.has("payload")) {
            throw ProtocolCodecException("missing envelope or payload")
        }
        val env = decEnv(root.getJSONObject("envelope"))
        val payload = decPayload(env.msgType, root.getJSONObject("payload"))
        return TypedMessage(env, payload)
    }

    /** Decode then re-encode. For canonical fixtures, output equals input bytes. */
    fun roundTrip(bytes: ByteArray): ByteArray = encode(decode(bytes))

    // ------------------------------------------------------------------
    // Envelope
    // ------------------------------------------------------------------

    private fun validateEnvelope(e: Envelope) {
        if (e.v != 1) throw ProtocolCodecException("v must be 1, got ${e.v}")
        validateId(e.sessionId, "sessionId")
        validateId(e.messageId, "messageId")
        if (e.sentAtMs < 0L || e.sentAtMs > 4_102_444_800_000L) {
            throw ProtocolCodecException("sentAtMs out of range")
        }
    }

    private fun validateId(s: String, field: String) {
        if (!ID_RE.matches(s)) throw ProtocolCodecException("$field must match ${ID_RE.pattern}, got $s")
    }

    private fun encEnv(e: Envelope, sb: StringBuilder) {
        // Order: v, msgType, sessionId, messageId, sentAtMs, transport, payloadEncoding
        sb.append('{')
        sb.append("\"v\":").append(e.v).append(',')
        sb.append("\"msgType\":"); jsonString(e.msgType.name, sb); sb.append(',')
        sb.append("\"sessionId\":"); jsonString(e.sessionId, sb); sb.append(',')
        sb.append("\"messageId\":"); jsonString(e.messageId, sb); sb.append(',')
        sb.append("\"sentAtMs\":").append(e.sentAtMs).append(',')
        sb.append("\"transport\":"); jsonString(e.transport.name, sb); sb.append(',')
        sb.append("\"payloadEncoding\":"); jsonString(e.payloadEncoding.name, sb)
        sb.append('}')
    }

    private fun decEnv(o: JSONObject): Envelope {
        val known = setOf("v", "msgType", "sessionId", "messageId", "sentAtMs", "transport", "payloadEncoding")
        for (k in o.keys()) if (k !in known) throw ProtocolCodecException("unknown envelope field: $k")
        for (k in known) if (!o.has(k)) throw ProtocolCodecException("missing envelope field: $k")
        val v = o.getInt("v")
        if (v != 1) throw ProtocolCodecException("unsupported protocol version: $v")
        val mt = try { MsgType.valueOf(o.getString("msgType")) }
                 catch (_: Exception) { throw ProtocolCodecException("unknown msgType: ${o.opt("msgType")}") }
        val sid = o.getString("sessionId"); validateId(sid, "sessionId")
        val mid = o.getString("messageId"); validateId(mid, "messageId")
        val sentAt = o.getLong("sentAtMs")
        if (sentAt < 0L || sentAt > 4_102_444_800_000L) throw ProtocolCodecException("sentAtMs out of range")
        val tr = try { TransportKind.valueOf(o.getString("transport")) }
                 catch (_: Exception) { throw ProtocolCodecException("unknown transport: ${o.opt("transport")}") }
        val pe = try { PayloadEncoding.valueOf(o.getString("payloadEncoding")) }
                 catch (_: Exception) { throw ProtocolCodecException("unknown payloadEncoding: ${o.opt("payloadEncoding")}") }
        return Envelope(v, mt, sid, mid, sentAt, tr, pe)
    }

    // ------------------------------------------------------------------
    // Payload — manual emission in canonical key order
    // ------------------------------------------------------------------

    private fun encPayload(p: Payload, sb: StringBuilder) {
        when (p) {
            is Payload.Hello -> {
                sb.append('{')
                sb.append("\"protocolVersions\":"); intArr(p.protocolVersions, sb); sb.append(',')
                sb.append("\"transports\":"); strArr(p.transports.map { it.name }, sb); sb.append(',')
                sb.append("\"aeadSuites\":"); strArr(p.aeadSuites, sb); sb.append(',')
                sb.append("\"kdfSuites\":"); strArr(p.kdfSuites, sb); sb.append(',')
                sb.append("\"curves\":"); strArr(p.curves, sb); sb.append(',')
                sb.append("\"features\":"); strArr(p.features, sb); sb.append(',')
                sb.append("\"nonce\":"); jsonString(p.nonce, sb)
                sb.append('}')
            }
            is Payload.HelloAck -> {
                sb.append('{')
                sb.append("\"selectedProtocolVersion\":").append(p.selectedProtocolVersion).append(',')
                sb.append("\"selectedTransport\":"); jsonString(p.selectedTransport.name, sb); sb.append(',')
                sb.append("\"selectedAead\":"); jsonString(p.selectedAead, sb); sb.append(',')
                sb.append("\"selectedKdf\":"); jsonString(p.selectedKdf, sb); sb.append(',')
                sb.append("\"selectedCurve\":"); jsonString(p.selectedCurve, sb); sb.append(',')
                sb.append("\"featuresAccepted\":"); strArr(p.featuresAccepted, sb)
                sb.append('}')
            }
            is Payload.InterestRequest -> {
                sb.append('{')
                sb.append("\"previewProfile\":")
                // Order: alias, tags, intent
                sb.append('{')
                sb.append("\"alias\":"); jsonString(p.previewProfile["alias"]?.toString() ?: "", sb); sb.append(',')
                sb.append("\"tags\":")
                @Suppress("UNCHECKED_CAST")
                val tags = (p.previewProfile["tags"] as? List<String>) ?: emptyList()
                strArr(tags, sb); sb.append(',')
                sb.append("\"intent\":"); jsonString(p.previewProfile["intent"]?.toString() ?: "", sb)
                sb.append('}'); sb.append(',')
                sb.append("\"interestToken\":"); jsonString(p.interestToken, sb)
                sb.append('}')
            }
            is Payload.InterestResponse -> {
                sb.append('{')
                sb.append("\"accepted\":").append(if (p.accepted) "true" else "false")
                if (p.reason != null) {
                    sb.append(','); sb.append("\"reason\":"); jsonString(p.reason, sb)
                }
                sb.append('}')
            }
            is Payload.MatchEstablished -> {
                sb.append('{').append("\"matchId\":"); jsonString(p.matchId, sb); sb.append('}')
            }
            is Payload.ChatText -> {
                sb.append('{')
                sb.append("\"ciphertextB64\":"); jsonString(p.ciphertextB64, sb); sb.append(',')
                sb.append("\"aadHashHex\":"); jsonString(p.aadHashHex, sb); sb.append(',')
                sb.append("\"ratchetIndex\":").append(p.ratchetIndex)
                sb.append('}')
            }
            is Payload.ChatMediaChunk -> {
                sb.append('{')
                sb.append("\"transferId\":"); jsonString(p.transferId, sb); sb.append(',')
                sb.append("\"chunkIndex\":").append(p.chunkIndex).append(',')
                sb.append("\"isLast\":").append(if (p.isLast) "true" else "false").append(',')
                sb.append("\"ciphertextB64\":"); jsonString(p.ciphertextB64, sb); sb.append(',')
                sb.append("\"sha256Hex\":"); jsonString(p.sha256Hex, sb)
                sb.append('}')
            }
            is Payload.ChatMediaAck -> {
                sb.append('{')
                sb.append("\"transferId\":"); jsonString(p.transferId, sb); sb.append(',')
                sb.append("\"highestContiguousChunk\":").append(p.highestContiguousChunk)
                sb.append('}')
            }
            is Payload.Heartbeat -> {
                sb.append('{').append("\"seq\":").append(p.seq).append('}')
            }
            is Payload.Error -> {
                sb.append('{')
                sb.append("\"code\":"); jsonString(p.code, sb); sb.append(',')
                sb.append("\"retryable\":").append(if (p.retryable) "true" else "false")
                if (p.message != null) {
                    sb.append(','); sb.append("\"message\":"); jsonString(p.message, sb)
                }
                sb.append('}')
            }
            is Payload.TransportMigrate -> {
                sb.append('{').append("\"proposed\":")
                strArr(p.proposed.map { it.name }, sb)
                sb.append('}')
            }
            is Payload.Goodbye -> {
                sb.append('{').append("\"reason\":"); jsonString(p.reason, sb); sb.append('}')
            }
        }
    }

    // ------------------------------------------------------------------
    // Decode payload
    // ------------------------------------------------------------------

    private fun decPayload(t: MsgType, o: JSONObject): Payload {
        return when (t) {
            MsgType.HELLO -> {
                requireKeys(o, setOf("protocolVersions","transports","aeadSuites","kdfSuites","curves","features","nonce"), required = setOf("protocolVersions","transports","aeadSuites","kdfSuites","curves","features","nonce"), where = "HELLO")
                Payload.Hello(
                    intList(o.getJSONArray("protocolVersions")),
                    strList(o.getJSONArray("transports")).map { TransportKind.valueOf(it) },
                    strList(o.getJSONArray("aeadSuites")),
                    strList(o.getJSONArray("kdfSuites")),
                    strList(o.getJSONArray("curves")),
                    strList(o.getJSONArray("features")),
                    o.getString("nonce")
                )
            }
            MsgType.HELLO_ACK -> {
                requireKeys(o, setOf("selectedProtocolVersion","selectedTransport","selectedAead","selectedKdf","selectedCurve","featuresAccepted"), required = setOf("selectedProtocolVersion","selectedTransport","selectedAead","selectedKdf","selectedCurve","featuresAccepted"), where = "HELLO_ACK")
                Payload.HelloAck(
                    o.getInt("selectedProtocolVersion"),
                    TransportKind.valueOf(o.getString("selectedTransport")),
                    o.getString("selectedAead"),
                    o.getString("selectedKdf"),
                    o.getString("selectedCurve"),
                    strList(o.getJSONArray("featuresAccepted"))
                )
            }
            MsgType.INTEREST_REQUEST -> {
                requireKeys(o, setOf("previewProfile","interestToken"), required = setOf("previewProfile","interestToken"), where = "INTEREST_REQUEST")
                val pp = o.getJSONObject("previewProfile")
                requireKeys(pp, setOf("alias","tags","intent"), required = setOf("alias","tags","intent"), where = "previewProfile")
                val map = mapOf(
                    "alias" to pp.getString("alias"),
                    "tags" to strList(pp.getJSONArray("tags")),
                    "intent" to pp.getString("intent"),
                )
                Payload.InterestRequest(map, o.getString("interestToken"))
            }
            MsgType.INTEREST_RESPONSE -> {
                requireKeys(o, setOf("accepted","reason"), required = setOf("accepted"), where = "INTEREST_RESPONSE")
                Payload.InterestResponse(o.getBoolean("accepted"), if (o.has("reason")) o.getString("reason") else null)
            }
            MsgType.MATCH_ESTABLISHED -> {
                requireKeys(o, setOf("matchId"), required = setOf("matchId"), where = "MATCH_ESTABLISHED")
                Payload.MatchEstablished(o.getString("matchId"))
            }
            MsgType.CHAT_TEXT -> {
                requireKeys(o, setOf("ciphertextB64","aadHashHex","ratchetIndex"), required = setOf("ciphertextB64","aadHashHex","ratchetIndex"), where = "CHAT_TEXT")
                Payload.ChatText(o.getString("ciphertextB64"), o.getString("aadHashHex"), o.getLong("ratchetIndex"))
            }
            MsgType.CHAT_MEDIA_CHUNK -> {
                requireKeys(o, setOf("transferId","chunkIndex","isLast","ciphertextB64","sha256Hex"), required = setOf("transferId","chunkIndex","isLast","ciphertextB64","sha256Hex"), where = "CHAT_MEDIA_CHUNK")
                Payload.ChatMediaChunk(o.getString("transferId"), o.getInt("chunkIndex"), o.getBoolean("isLast"), o.getString("ciphertextB64"), o.getString("sha256Hex"))
            }
            MsgType.CHAT_MEDIA_ACK -> {
                requireKeys(o, setOf("transferId","highestContiguousChunk"), required = setOf("transferId","highestContiguousChunk"), where = "CHAT_MEDIA_ACK")
                Payload.ChatMediaAck(o.getString("transferId"), o.getInt("highestContiguousChunk"))
            }
            MsgType.HEARTBEAT -> {
                requireKeys(o, setOf("seq"), required = setOf("seq"), where = "HEARTBEAT")
                Payload.Heartbeat(o.getLong("seq"))
            }
            MsgType.ERROR -> {
                requireKeys(o, setOf("code","retryable","message"), required = setOf("code","retryable"), where = "ERROR")
                Payload.Error(o.getString("code"), o.getBoolean("retryable"), if (o.has("message")) o.getString("message") else null)
            }
            MsgType.TRANSPORT_MIGRATE -> {
                requireKeys(o, setOf("proposed"), required = setOf("proposed"), where = "TRANSPORT_MIGRATE")
                Payload.TransportMigrate(strList(o.getJSONArray("proposed")).map { TransportKind.valueOf(it) })
            }
            MsgType.GOODBYE -> {
                requireKeys(o, setOf("reason"), required = setOf("reason"), where = "GOODBYE")
                Payload.Goodbye(o.getString("reason"))
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun requireKeys(o: JSONObject, known: Set<String>, required: Set<String>, where: String) {
        for (k in o.keys()) if (k !in known) throw ProtocolCodecException("unknown field in $where: $k")
        for (k in required) if (!o.has(k)) throw ProtocolCodecException("missing field in $where: $k")
    }

    private fun strList(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }
    private fun intList(a: JSONArray): List<Int> = (0 until a.length()).map { a.getInt(it) }

    private fun strArr(a: List<String>, sb: StringBuilder) {
        sb.append('[')
        for ((i, s) in a.withIndex()) {
            if (i > 0) sb.append(',')
            jsonString(s, sb)
        }
        sb.append(']')
    }

    private fun intArr(a: List<Int>, sb: StringBuilder) {
        sb.append('[')
        for ((i, n) in a.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append(n)
        }
        sb.append(']')
    }

    /**
     * JSON string escaping matching Python json.dumps(ensure_ascii=True).
     * Emits `\uXXXX` for any codepoint >= 0x7F and for control chars.
     */
    private fun jsonString(s: String, sb: StringBuilder) {
        sb.append('"')
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val code = ch.code
            when {
                code == 0x22 -> sb.append("\\\"")
                code == 0x5C -> sb.append("\\\\")
                code == 0x08 -> sb.append("\\b")
                code == 0x0C -> sb.append("\\f")
                code == 0x0A -> sb.append("\\n")
                code == 0x0D -> sb.append("\\r")
                code == 0x09 -> sb.append("\\t")
                code < 0x20 -> sb.append("\\u").append(String.format("%04x", code))
                code < 0x7F -> sb.append(ch)
                else -> {
                    // Detect surrogate pairs to emit \uXXXX\uXXXX matching Python.
                    if (Character.isHighSurrogate(ch) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) {
                        sb.append("\\u").append(String.format("%04x", code))
                        sb.append("\\u").append(String.format("%04x", s[i + 1].code))
                        i++
                    } else {
                        sb.append("\\u").append(String.format("%04x", code))
                    }
                }
            }
            i++
        }
        sb.append('"')
    }
}
