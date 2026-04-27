
package com.lumos.session.deps

import com.lumos.session.api.TransportKind

/** Minimal transport adapter contract expected from Phase 4. */
interface TransportAdapter {
    val kind: TransportKind
    fun startDiscovery(onPeerFound: (RemotePeer) -> Unit, onPeerLost: (RemotePeer) -> Unit)
    fun stopDiscovery()

    fun startAccepting(onInbound: (InboundFrame) -> Unit, onError: (Throwable) -> Unit)
    fun stopAccepting()

    fun connect(peer: RemotePeer, onInbound: (InboundFrame) -> Unit, onError: (Throwable) -> Unit): TransportConnection
    fun shutdown()
}

data class RemotePeer(
    val peerId: String,
    val alias: String?,
    val transportHint: TransportKind
)

interface TransportConnection {
    val peer: RemotePeer
    fun send(frame: ByteArray)
    fun close()
}

data class InboundFrame(
    val peer: RemotePeer,
    val bytes: ByteArray
)
