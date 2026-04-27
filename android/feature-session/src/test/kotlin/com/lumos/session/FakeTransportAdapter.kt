package com.lumos.session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
class FakeTransportAdapter(override val kind: AdapterKind): TransportAdapter {
 private val flow = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 128)
 val sentFrames = mutableListOf<Pair<PeerRef, ByteArray>>()
 var started = false
 override fun events(): Flow<TransportEvent> = flow
 override suspend fun start(){ started = true; flow.emit(TransportEvent.LinkStateChanged(kind,true)) }
 override suspend fun stop(){ started = false; flow.emit(TransportEvent.LinkStateChanged(kind,false)) }
 override suspend fun advertise(enabled:Boolean) {}
 override suspend fun discover(enabled:Boolean) {}
 override suspend fun send(peer:PeerRef, bytes:ByteArray, correlationId:String): Boolean { sentFrames += peer to bytes; flow.emit(TransportEvent.SendResult(correlationId,true,false)); return true }
 suspend fun emit(event:TransportEvent) = flow.emit(event)
}
