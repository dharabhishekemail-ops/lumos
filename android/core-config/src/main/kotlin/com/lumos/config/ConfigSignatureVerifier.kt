package com.lumos.config
sealed class ConfigVerifyResult { object Valid: ConfigVerifyResult(); data class InvalidSignature(val keyId:String?): ConfigVerifyResult(); data class Malformed(val reason:String): ConfigVerifyResult(); data class UnsupportedKey(val keyType:String): ConfigVerifyResult() }
data class SignedConfigEnvelope(val keyId:String, val algorithm:String, val payloadCanonicalJson:ByteArray, val signature:ByteArray)
interface Ed25519Verifier { fun verify(publicKey:ByteArray, message:ByteArray, signature:ByteArray): Boolean }
interface TrustAnchorProvider { fun publicKeyForKeyId(keyId:String): ByteArray?; fun allKeyIds(): Set<String> }
class ConfigSignatureVerifier(private val trustAnchorProvider:TrustAnchorProvider, private val ed25519Verifier:Ed25519Verifier){
 fun verify(envelope:SignedConfigEnvelope): ConfigVerifyResult {
  if (!envelope.algorithm.equals("ED25519", true)) return ConfigVerifyResult.UnsupportedKey(envelope.algorithm)
  val publicKey = trustAnchorProvider.publicKeyForKeyId(envelope.keyId) ?: return ConfigVerifyResult.InvalidSignature(envelope.keyId)
  return if (ed25519Verifier.verify(publicKey, envelope.payloadCanonicalJson, envelope.signature)) ConfigVerifyResult.Valid else ConfigVerifyResult.InvalidSignature(envelope.keyId)
 }
}
