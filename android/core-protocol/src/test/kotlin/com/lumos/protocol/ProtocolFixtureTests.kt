package com.lumos.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolFixtureTests {

    private fun decodeJson(s: String): TypedMessage =
        ProtocolCodec.decode(s.toByteArray(Charsets.UTF_8))

    @Test fun decodeGoldenHello() {
        val s = """{"envelope":{"v":1,"msgType":"HELLO","sessionId":"s1","messageId":"m1","sentAtMs":1730000000000,"transport":"BLE","payloadEncoding":"JSON"},"payload":{"protocolVersions":[1],"transports":["BLE","WIFI"],"aeadSuites":["CHACHA20_POLY1305"],"kdfSuites":["HKDF_SHA256"],"curves":["X25519"],"features":["CHAT"],"nonce":"n1"}}"""
        val m = decodeJson(s)
        assertEquals(MsgType.HELLO, m.envelope.msgType)
    }

    @Test fun decodeGoldenInterestReq() {
        val s = """{"envelope":{"v":1,"msgType":"INTEREST_REQUEST","sessionId":"s1","messageId":"m2","sentAtMs":1730000000100,"transport":"WIFI","payloadEncoding":"JSON"},"payload":{"previewProfile":{"alias":"A","tags":["music"],"intent":"dating"},"interestToken":"tok123"}}"""
        val m = decodeJson(s)
        assertEquals(MsgType.INTEREST_REQUEST, m.envelope.msgType)
    }

    @Test fun decodeGoldenMigrate() {
        val s = """{"envelope":{"v":1,"msgType":"TRANSPORT_MIGRATE","sessionId":"s1","messageId":"m4","sentAtMs":1730000000300,"transport":"BLE","payloadEncoding":"JSON"},"payload":{"proposed":["WIFI","QR"]}}"""
        val m = decodeJson(s)
        assertEquals(MsgType.TRANSPORT_MIGRATE, m.envelope.msgType)
        val p = m.payload as Payload.TransportMigrate
        assertEquals(listOf(TransportKind.WIFI, TransportKind.QR), p.proposed)
    }
}
