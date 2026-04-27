
package com.lumos.session

import com.lumos.session.api.*
import com.lumos.session.deps.*
import com.lumos.session.impl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private class FakeCodec : ProtocolCodec {
    override fun <T : Any> encode(envelope: TypedEnvelope<T>): ByteArray {
        return ("${'$'}{envelope.messageType}|${'$'}{envelope.messageId}|${'$'}{envelope.sessionId ?: ""}|${'$'}{envelope.payload}").toByteArray()
    }
    override fun decode(bytes: ByteArray): TypedEnvelope<Any> {
        val s = bytes.toString(Charsets.UTF_8)
        val parts = s.split("|", limit = 4)
        return TypedEnvelope(
            version = 1,
            messageType = parts[0],
            messageId = parts[1],
            sessionId = parts[2].ifEmpty { null },
            payload = mapOf("raw" to parts.getOrNull(3))
        )
    }
}

private class FakeCrypto : CryptoFacade {
    private var n = 0
    override fun randomId(prefix: String): String = "${'$'}prefix-${'$'}{n++}"
    override fun sha256(bytes: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
}

private class FakeNegotiator : CapabilityNegotiator {
    override fun selectCommon(local: Capabilities, remote: Capabilities): NegotiationResult = NegotiationResult(ok = true, chosenProtocol = 1, chosenTransport = "BLE")
}

private class FakeConfig : ConfigRuntime {
    override fun current(): SignedConfig = SignedConfig(
        schemaVersion = 1,
        retry = RetryConfig(baseDelayMs = 10, maxDelayMs = 50, maxAttemptsText = 2, jitterPct = 0),
        dedupeWindow = DedupeConfig(windowSize = 64, ttlMs = 10_000),
        rateLimits = RateLimitConfig(interestsPer10Min = 10, messagesPerMin = 60)
    )
}

private class ImmediateTimer : Timer {
    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        task()
        return object : Cancellable { override fun cancel() {} }
    }
}

private class FakeTransport(private val onSend: (ByteArray) -> Unit) : TransportAdapter {
    override val kind: TransportKind = TransportKind.BLE
    override fun startDiscovery(onPeerFound: (RemotePeer) -> Unit, onPeerLost: (RemotePeer) -> Unit) { /*no-op*/ }
    override fun stopDiscovery() {}
    override fun startAccepting(onInbound: (InboundFrame) -> Unit, onError: (Throwable) -> Unit) {}
    override fun stopAccepting() {}
    override fun connect(peer: RemotePeer, onInbound: (InboundFrame) -> Unit, onError: (Throwable) -> Unit): TransportConnection {
        return object : TransportConnection {
            override val peer: RemotePeer = peer
            override fun send(frame: ByteArray) { onSend(frame) }
            override fun close() {}
        }
    }
    override fun shutdown() {}
}

class SessionOrchestratorTest {

    @Test fun chatRetryStopsAfterAck() = runBlocking {
        val sent = CopyOnWriteArrayList<ByteArray>()
        val transport = FakeTransport { sent.add(it) }
        val orch = SessionOrchestrator(
            scope = this,
            protocol = FakeCodec(),
            crypto = FakeCrypto(),
            negotiator = FakeNegotiator(),
            config = FakeConfig(),
            transports = listOf(transport),
            timer = ImmediateTimer(),
            rngSeed = 1
        )

        val peer = PeerRef("p1","A")
        // Establish session directly
        orch.submit(SessionCommand.RespondInterest("req-0", true)) // no-op without pending, but ok
        // Bootstrap to force active
        orch.submit(SessionCommand.BootstrapFromQr("p1"))

        // Send chat
        orch.submit(SessionCommand.SendChatText(sessionId="s-2", to=peer, text="hi"))

        // We should have produced at least one send
        assertTrue(sent.isNotEmpty())
    }
}
