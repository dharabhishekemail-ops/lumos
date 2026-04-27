package com.lumos.protocol

data class LocalCapabilities(val supportedProtocolVersions:List<Int>, val transportsPreference:List<TransportKind>, val aeadPreference:List<AeadSuite>, val kdfPreference:List<KdfSuite>, val curvesPreference:List<CurveSuite>, val features:Set<String>)
sealed class NegotiationResult {
  data class Success(val protocolVersion:Int,val transport:TransportKind,val aead:AeadSuite,val kdf:KdfSuite,val curve:CurveSuite,val mutualFeatures:Set<String>) : NegotiationResult()
  data class Failure(val code:Code, val detail:String) : NegotiationResult(){ enum class Code { NO_COMMON_PROTOCOL, NO_COMMON_TRANSPORT, NO_COMMON_AEAD, NO_COMMON_KDF, NO_COMMON_CURVE } }
}
class CapabilityNegotiator {
 fun negotiate(local:LocalCapabilities, remote:PeerCapabilities, remoteProtocolVersions:List<Int>):NegotiationResult {
  val version = local.supportedProtocolVersions.firstOrNull{it in remoteProtocolVersions} ?: return NegotiationResult.Failure(NegotiationResult.Failure.Code.NO_COMMON_PROTOCOL,"No common protocol version")
  val transport = local.transportsPreference.firstOrNull{it in remote.transports} ?: return NegotiationResult.Failure(NegotiationResult.Failure.Code.NO_COMMON_TRANSPORT,"No common transport")
  val aead = local.aeadPreference.firstOrNull{it in remote.aeadSuites} ?: return NegotiationResult.Failure(NegotiationResult.Failure.Code.NO_COMMON_AEAD,"No common AEAD")
  val kdf = local.kdfPreference.firstOrNull{it in remote.kdfSuites} ?: return NegotiationResult.Failure(NegotiationResult.Failure.Code.NO_COMMON_KDF,"No common KDF")
  val curve = local.curvesPreference.firstOrNull{it in remote.curves} ?: return NegotiationResult.Failure(NegotiationResult.Failure.Code.NO_COMMON_CURVE,"No common curve")
  return NegotiationResult.Success(version, transport, aead, kdf, curve, local.features.intersect(remote.features.toSet()))
 }
}
