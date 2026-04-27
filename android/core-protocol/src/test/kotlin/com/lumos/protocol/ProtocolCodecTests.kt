package com.lumos.protocol
import org.junit.Test
import org.junit.Assert.*
class ProtocolCodecTests{@Test fun roundTrip(){val m=TypedMessage(Envelope(1,MsgType.HELLO,"s","m",1,TransportKind.BLE),Payload.Hello(listOf(1),listOf(TransportKind.BLE),listOf("CHACHA20_POLY1305"),listOf("HKDF_SHA256"),listOf("X25519"),listOf("CHAT"),"n")); val d=ProtocolCodec.decode(ProtocolCodec.encode(m)); assertEquals(MsgType.HELLO,d.envelope.msgType)} @Test fun bad(){try{ProtocolCodec.decode(byteArrayOf(1,2)); fail()}catch(_:Exception){}} }