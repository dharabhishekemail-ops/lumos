
package com.lumos.crypto
import java.util.Base64
interface Ed25519Verifier { fun verify(publicKeyBytes:ByteArray, message:ByteArray, signature:ByteArray):Boolean }
interface TrustAnchorProvider { fun publicKeyFor(keyId:String):ByteArray? }
class ConfigSignatureVerifier(private val trust:TrustAnchorProvider, private val verifier:Ed25519Verifier){
 fun verifyCanonicalPayload(canonicalPayloadJson:String, signatureB64:String, keyId:String):Boolean{
  val pk = trust.publicKeyFor(keyId) ?: return false
  val sig = try { Base64.getDecoder().decode(signatureB64) } catch (_:Exception){ return false }
  return verifier.verify(pk, canonicalPayloadJson.toByteArray(Charsets.UTF_8), sig)
 }
}
class AndroidTinkEd25519Verifier:Ed25519Verifier{
 override fun verify(publicKeyBytes:ByteArray, message:ByteArray, signature:ByteArray):Boolean = throw UnsupportedOperationException("Bind provider in app module")
}
