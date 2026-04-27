package com.lumos.session
import kotlinx.coroutines.flow.Flow

enum class AdapterKind { BLE, WIFI_LOCAL, QR }
data class WireFrame(val bytes:ByteArray, val transport:AdapterKind, val receivedAtMs:Long)
data class PeerRef(val ephemeralId:String, val transport:AdapterKind)
sealed class TransportEvent {
 data class PeerDiscovered(val peer:PeerRef): TransportEvent()
 data class PeerLost(val peer:PeerRef): TransportEvent()
 data class FrameReceived(val frame:WireFrame): TransportEvent()
 data class LinkStateChanged(val transport:AdapterKind, val available:Boolean): TransportEvent()
 data class SendResult(val correlationId:String, val success:Boolean, val retryable:Boolean): TransportEvent()
}
interface TransportAdapter { val kind:AdapterKind; fun events(): Flow<TransportEvent>; suspend fun start(); suspend fun stop(); suspend fun advertise(enabled:Boolean); suspend fun discover(enabled:Boolean); suspend fun send(peer:PeerRef, bytes:ByteArray, correlationId:String): Boolean }
