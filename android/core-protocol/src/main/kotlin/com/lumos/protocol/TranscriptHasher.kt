
package com.lumos.protocol
import java.security.MessageDigest
import org.json.JSONObject
object TranscriptHasher {
 fun sha256Hex(hello:Payload.Hello, ack:Payload.HelloAck):String{
  val h = JSONObject(mapOf("aeadSuites" to hello.aeadSuites,"curves" to hello.curves,"features" to hello.features,"kdfSuites" to hello.kdfSuites,"protocolVersions" to hello.protocolVersions,"transports" to hello.transports.map{it.name})).toString()
  val a = JSONObject(mapOf("featuresAccepted" to ack.featuresAccepted,"selectedAead" to ack.selectedAead,"selectedCurve" to ack.selectedCurve,"selectedKdf" to ack.selectedKdf,"selectedProtocolVersion" to ack.selectedProtocolVersion,"selectedTransport" to ack.selectedTransport.name)).toString()
  return MessageDigest.getInstance("SHA-256").digest((h+"|"+a).toByteArray()).joinToString(""){"%02x".format(it)}
 }
}
