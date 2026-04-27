
package com.lumos.session.impl

import com.lumos.session.api.*
import com.lumos.session.deps.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * SessionOrchestrator:
 * - consumes SessionCommand from feature layer
 * - uses TransportAdapters to discover/connect
 * - uses ProtocolCodec to encode/decode typed envelopes
 * - uses ConfigRuntime for retry/dedupe/rate-limits
 *
 * Designed to be deterministic with injected Timer + Random in RetryPolicy.
 */
class SessionOrchestrator(
    private val scope: CoroutineScope,
    private val protocol: ProtocolCodec,
    private val crypto: CryptoFacade,
    private val negotiator: CapabilityNegotiator,
    private val config: ConfigRuntime,
    private val transports: List<TransportAdapter>,
    private val timer: Timer,
    rngSeed: Int = 0
) {
    private val commands = Channel<SessionCommand>(Channel.BUFFERED)
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val retry = RetryPolicy(config, kotlin.random.Random(rngSeed))
    private val dedupe = DedupeWindow(config)

    private val nearby = ConcurrentHashMap<String, PeerRef>()
    private val pendingInterests = ConcurrentHashMap<String, PeerRef>() // requestId -> peer
    private val activeSessions = ConcurrentHashMap<String, SessionCtx>() // sessionId -> ctx

    init {
        scope.launch { loop() }
    }

    fun submit(cmd: SessionCommand) { commands.trySend(cmd) }

    private suspend fun loop() {
        for (cmd in commands) {
            when (cmd) {
                is SessionCommand.StartDiscovering -> startDiscovering(cmd.hint)
                is SessionCommand.StopDiscovering -> stopDiscovering()
                is SessionCommand.SendInterest -> sendInterest(cmd.to)
                is SessionCommand.RespondInterest -> respondInterest(cmd.requestId, cmd.accept)
                is SessionCommand.SendChatText -> sendChatText(cmd.sessionId, cmd.to, cmd.text)
                is SessionCommand.EndSession -> endSession(cmd.sessionId)
                is SessionCommand.BootstrapFromQr -> bootstrapFromQr(cmd.token)
            }
        }
    }

    private fun startDiscovering(hint: TransportHint?) {
        _state.value = SessionState.Discovering(hint)
        _events.tryEmit(SessionEvent.Toast("Discovering nearby users…"))

        // Prefer BLE + WIFI by default, or based on hint
        val order = hint?.preferred ?: listOf(TransportKind.BLE, TransportKind.WIFI, TransportKind.QR)
        order.forEach { kind ->
            transports.find { it.kind == kind }?.startDiscovery(
                onPeerFound = { rp ->
                    val pr = PeerRef(rp.peerId, rp.alias)
                    nearby[rp.peerId] = pr
                    _events.tryEmit(SessionEvent.NearbyUpdated(nearby.values.toList()))
                },
                onPeerLost = { rp ->
                    nearby.remove(rp.peerId)
                    _events.tryEmit(SessionEvent.NearbyUpdated(nearby.values.toList()))
                }
            )
        }
    }

    private fun stopDiscovering() {
        transports.forEach { it.stopDiscovery() }
        nearby.clear()
        _events.tryEmit(SessionEvent.NearbyUpdated(emptyList()))
        _state.value = SessionState.Idle
    }

    private fun sendInterest(to: PeerRef) {
        val reqId = crypto.randomId("ir")
        val env = TypedEnvelope(
            version = 1,
            messageType = "INTEREST_REQUEST",
            messageId = reqId,
            sessionId = null,
            payload = mapOf(
                "requestId" to reqId,
                "fromPeerId" to "local",
                "toPeerId" to to.peerId,
                "preview" to mapOf("alias" to "local")
            )
        )
        val bytes = protocol.encode(env)
        // choose best transport that currently sees the peer
        val remote = RemotePeer(to.peerId, to.displayAlias, TransportKind.BLE) // hint; adapter decides
        val adapter = transports.firstOrNull { it.kind == TransportKind.BLE } ?: transports.first()
        val conn = adapter.connect(remote, ::onInboundFrame, ::onTransportError)
        conn.send(bytes)
        conn.close()
        pendingInterests[reqId] = to
        _events.tryEmit(SessionEvent.Toast("Interest sent"))
    }

    private fun respondInterest(requestId: String, accept: Boolean) {
        val from = pendingInterests[requestId] ?: return
        val respId = crypto.randomId("irs")
        val env = TypedEnvelope(
            version = 1,
            messageType = "INTEREST_RESPONSE",
            messageId = respId,
            sessionId = null,
            payload = mapOf(
                "requestId" to requestId,
                "accept" to accept
            )
        )
        val bytes = protocol.encode(env)
        val adapter = transports.firstOrNull { it.kind == TransportKind.BLE } ?: transports.first()
        val remote = RemotePeer(from.peerId, from.displayAlias, TransportKind.BLE)
        val conn = adapter.connect(remote, ::onInboundFrame, ::onTransportError)
        conn.send(bytes)
        conn.close()
        if (accept) {
            establishSession(from)
        } else {
            _events.tryEmit(SessionEvent.Toast("Rejected"))
        }
    }

    private fun establishSession(peer: PeerRef) {
        val sid = crypto.randomId("s")
        _state.value = SessionState.Connecting(peer, TransportKind.BLE)

        val ctx = SessionCtx(peer, sid, TransportKind.BLE)
        activeSessions[sid] = ctx

        // Send MATCH_ESTABLISHED to open chat
        val env = TypedEnvelope(
            version = 1,
            messageType = "MATCH_ESTABLISHED",
            messageId = crypto.randomId("m"),
            sessionId = sid,
            payload = mapOf("sessionId" to sid, "peerId" to peer.peerId)
        )
        val bytes = protocol.encode(env)

        val adapter = transports.firstOrNull { it.kind == TransportKind.BLE } ?: transports.first()
        val remote = RemotePeer(peer.peerId, peer.displayAlias, TransportKind.BLE)
        val conn = adapter.connect(remote, ::onInboundFrame, ::onTransportError)
        adapter.startAccepting(::onInboundFrame, ::onTransportError)
        conn.send(bytes)

        _state.value = SessionState.Active(peer, adapter.kind, sid)
        _events.tryEmit(SessionEvent.MatchEstablished(peer, sid))
    }

    private fun sendChatText(sessionId: String, to: PeerRef, text: String) {
        val ctx = activeSessions[sessionId] ?: return
        val msgId = crypto.randomId("t")
        val msg = ChatMessage(
            messageId = msgId, sessionId = sessionId,
            fromPeerId = "local", toPeerId = to.peerId,
            timestampMs = System.currentTimeMillis(),
            text = text,
            status = DeliveryStatus.QUEUED
        )
        _events.tryEmit(SessionEvent.ChatMessageUpdated(msg.copy(status = DeliveryStatus.SENT)))

        ctx.sendText(msgId, text) { updated ->
            _events.tryEmit(SessionEvent.ChatMessageUpdated(updated))
        }
    }

    private fun endSession(sessionId: String) {
        activeSessions.remove(sessionId)?.close()
        _state.value = SessionState.Terminated
        _events.tryEmit(SessionEvent.Toast("Conversation ended"))
    }

    private fun bootstrapFromQr(token: String) {
        // Token => peerId + transport hint in real implementation.
        _events.tryEmit(SessionEvent.Toast("QR bootstrap received"))
        // Hook to establish session based on token. For now, treat as peerId.
        establishSession(PeerRef(peerId = token, displayAlias = null))
    }

    private fun onInboundFrame(frame: InboundFrame) {
        // Dedupe on transport frame hash (simple). In production use envelope messageId + sessionId.
        val key = frame.peer.peerId + ":" + crypto.sha256(frame.bytes).joinToString("") { "%02x".format(it) }
        if (dedupe.seenBefore(key)) return

        val env = protocol.decode(frame.bytes)
        when (env.messageType) {
            "INTEREST_REQUEST" -> {
                val requestId = (env.payload as Map<*, *>)["requestId"] as? String ?: return
                val peer = PeerRef(frame.peer.peerId, frame.peer.alias)
                pendingInterests[requestId] = peer
                _events.tryEmit(SessionEvent.InterestReceived(peer, requestId))
            }
            "INTEREST_RESPONSE" -> {
                // If accepted, establish session
                val accept = (env.payload as Map<*, *>)["accept"] as? Boolean ?: false
                val requestId = (env.payload as Map<*, *>)["requestId"] as? String ?: return
                val peer = pendingInterests[requestId]
                if (accept && peer != null) establishSession(peer)
            }
            "MATCH_ESTABLISHED" -> {
                val sid = env.sessionId ?: return
                val peer = PeerRef(frame.peer.peerId, frame.peer.alias)
                activeSessions.putIfAbsent(sid, SessionCtx(peer, sid, TransportKind.BLE))
                _state.value = SessionState.Active(peer, TransportKind.BLE, sid)
                _events.tryEmit(SessionEvent.MatchEstablished(peer, sid))
            }
            "CHAT_TEXT" -> {
                val payload = env.payload as Map<*, *>
                val text = payload["text"] as? String ?: return
                val msgId = env.messageId
                val sid = env.sessionId ?: return
                val peer = frame.peer.peerId
                val chat = ChatMessage(msgId, sid, peer, "local", System.currentTimeMillis(), text, DeliveryStatus.DELIVERED)
                _events.tryEmit(SessionEvent.ChatMessageUpdated(chat))
                // Send ACK
                val ack = TypedEnvelope(
                    version = 1,
                    messageType = "CHAT_ACK",
                    messageId = crypto.randomId("ack"),
                    sessionId = sid,
                    payload = mapOf("ackedMessageId" to msgId)
                )
                val bytes = protocol.encode(ack)
                val adapter = transports.firstOrNull { it.kind == TransportKind.BLE } ?: transports.first()
                val conn = adapter.connect(frame.peer, ::onInboundFrame, ::onTransportError)
                conn.send(bytes)
                conn.close()
            }
            "CHAT_ACK" -> {
                val sid = env.sessionId ?: return
                val payload = env.payload as Map<*, *>
                val acked = payload["ackedMessageId"] as? String ?: return
                activeSessions[sid]?.onAck(acked)
            }
        }
    }

    private fun onTransportError(t: Throwable) {
        _state.value = SessionState.Failed(FailureReason.TRANSPORT_ERROR)
        _events.tryEmit(SessionEvent.Toast("Transport error: ${'$'}{t.message ?: "unknown"}"))
    }

    private inner class SessionCtx(
        val peer: PeerRef,
        val sessionId: String,
        var transport: TransportKind
    ) {
        private val pending = ConcurrentHashMap<String, PendingText>()

        fun sendText(messageId: String, text: String, onUpdate: (ChatMessage) -> Unit) {
            val p = PendingText(messageId, text, attempt = 0, onUpdate = onUpdate)
            pending[messageId] = p
            sendAttempt(p)
        }

        fun onAck(ackedMessageId: String) {
            val p = pending.remove(ackedMessageId) ?: return
            p.onUpdate(ChatMessage(
                messageId = ackedMessageId,
                sessionId = sessionId,
                fromPeerId = "local",
                toPeerId = peer.peerId,
                timestampMs = System.currentTimeMillis(),
                text = p.text,
                status = DeliveryStatus.DELIVERED
            ))
        }

        private fun sendAttempt(p: PendingText) {
            val adapter = transports.firstOrNull { it.kind == transport } ?: transports.first()
            val remote = RemotePeer(peer.peerId, peer.displayAlias, transport)
            val conn = adapter.connect(remote, ::onInboundFrame, ::onTransportError)

            val env = TypedEnvelope(
                version = 1,
                messageType = "CHAT_TEXT",
                messageId = p.messageId,
                sessionId = sessionId,
                payload = mapOf("text" to p.text)
            )
            conn.send(protocol.encode(env))
            conn.close()

            val maxAttempts = config.current().retry.maxAttemptsText
            if (p.attempt >= maxAttempts) {
                pending.remove(p.messageId)
                p.onUpdate(ChatMessage(p.messageId, sessionId, "local", peer.peerId, System.currentTimeMillis(), p.text, DeliveryStatus.FAILED))
                return
            }
            val delay = retry.nextDelayMs(p.attempt)
            p.onUpdate(ChatMessage(p.messageId, sessionId, "local", peer.peerId, System.currentTimeMillis(), p.text, DeliveryStatus.RETRYING))
            timer.schedule(delay) {
                val next = p.copy(attempt = p.attempt + 1)
                pending[p.messageId] = next
                sendAttempt(next)
            }
        }

        fun close() { pending.clear() }

        private data class PendingText(
            val messageId: String,
            val text: String,
            val attempt: Int,
            val onUpdate: (ChatMessage) -> Unit
        )
    }
}
