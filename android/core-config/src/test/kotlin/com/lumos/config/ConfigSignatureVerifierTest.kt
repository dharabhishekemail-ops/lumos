package com.lumos.config
import kotlin.test.*
class ConfigSignatureVerifierTest {
 @Test fun returnsValidWhenVerifierSucceeds(){
  val sut = ConfigSignatureVerifier(object: TrustAnchorProvider { override fun publicKeyForKeyId(keyId:String)= if (keyId=="k1") ByteArray(32) else null; override fun allKeyIds()= setOf("k1") }, object: Ed25519Verifier { override fun verify(publicKey:ByteArray, message:ByteArray, signature:ByteArray)= true })
  assertTrue(sut.verify(SignedConfigEnvelope("k1","ED25519","{}".encodeToByteArray(),ByteArray(64))) is ConfigVerifyResult.Valid)
 }
}
