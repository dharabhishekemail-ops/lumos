package com.lumos.protocol
import kotlin.test.*
class CapabilityNegotiatorTest {
 @Test fun negotiatesByLocalPreference(){
  val local = LocalCapabilities(listOf(2,1), listOf(TransportKind.WIFI_LOCAL,TransportKind.BLE), listOf(AeadSuite.CHACHA20_POLY1305,AeadSuite.AES_256_GCM), listOf(KdfSuite.HKDF_SHA256), listOf(CurveSuite.X25519), setOf("CHAT_TEXT","CHAT_MEDIA_PHOTO","VOUCHER_QR"))
  val remote = PeerCapabilities(listOf(TransportKind.BLE,TransportKind.WIFI_LOCAL), listOf(AeadSuite.AES_256_GCM,AeadSuite.CHACHA20_POLY1305), listOf(KdfSuite.HKDF_SHA256), listOf(CurveSuite.X25519), listOf("CHAT_TEXT"))
  val result = CapabilityNegotiator().negotiate(local, remote, listOf(1))
  assertTrue(result is NegotiationResult.Success)
  result as NegotiationResult.Success
  assertEquals(1, result.protocolVersion)
  assertEquals(TransportKind.WIFI_LOCAL, result.transport)
  assertEquals(setOf("CHAT_TEXT"), result.mutualFeatures)
 }
}
