
package com.lumos.protocol
enum class MsgType { HELLO, HELLO_ACK, INTEREST_REQUEST, INTEREST_RESPONSE, MATCH_ESTABLISHED, CHAT_TEXT, CHAT_MEDIA_CHUNK, CHAT_MEDIA_ACK, HEARTBEAT, ERROR, TRANSPORT_MIGRATE, GOODBYE }
enum class TransportKind { BLE, WIFI, QR }
enum class PayloadEncoding { JSON }
data class Envelope(val v:Int,val msgType:MsgType,val sessionId:String,val messageId:String,val sentAtMs:Long,val transport:TransportKind,val payloadEncoding:PayloadEncoding=PayloadEncoding.JSON)
sealed interface Payload {
 data class Hello(val protocolVersions:List<Int>,val transports:List<TransportKind>,val aeadSuites:List<String>,val kdfSuites:List<String>,val curves:List<String>,val features:List<String>,val nonce:String):Payload
 data class HelloAck(val selectedProtocolVersion:Int,val selectedTransport:TransportKind,val selectedAead:String,val selectedKdf:String,val selectedCurve:String,val featuresAccepted:List<String>):Payload
 data class InterestRequest(val previewProfile:Map<String,Any?>,val interestToken:String):Payload
 data class InterestResponse(val accepted:Boolean,val reason:String?=null):Payload
 data class MatchEstablished(val matchId:String):Payload
 data class ChatText(val ciphertextB64:String,val aadHashHex:String,val ratchetIndex:Long):Payload
 data class ChatMediaChunk(val transferId:String,val chunkIndex:Int,val isLast:Boolean,val ciphertextB64:String,val sha256Hex:String):Payload
 data class ChatMediaAck(val transferId:String,val highestContiguousChunk:Int):Payload
 data class Heartbeat(val seq:Long):Payload
 data class Error(val code:String,val retryable:Boolean,val message:String?):Payload
 data class TransportMigrate(val proposed:List<TransportKind>):Payload
 data class Goodbye(val reason:String):Payload
}
data class TypedMessage(val envelope:Envelope,val payload:Payload)
