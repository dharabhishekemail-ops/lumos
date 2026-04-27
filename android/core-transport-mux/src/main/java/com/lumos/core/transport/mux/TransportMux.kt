package com.lumos.core.transport.mux

import com.lumos.core.session.transport.TransportAdapter
import com.lumos.core.session.transport.TransportEvent
import com.lumos.core.session.transport.TransportKind
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * TransportMux
 * - owns multiple adapters (BLE/WIFI/QR)
 * - selects active adapter (preference order)
 * - forwards inbound events to listeners
 * - supports seamless migration by switching active adapter
 */
class TransportMux(
    private val adapters: List<TransportAdapter>,
    private val preference: List<TransportKind>
) : TransportAdapter {

    private val listeners = CopyOnWriteArrayList<(TransportEvent) -> Unit>()
    private val active = AtomicReference<TransportAdapter?>(null)

    override val kind: TransportKind get() = active.get()?.kind ?: TransportKind.NONE

    override fun start() {
        adapters.forEach { a ->
            a.setListener { ev -> listeners.forEach { it(ev) } }
            a.start()
        }
        chooseActive()
    }

    override fun stop() {
        adapters.forEach { it.stop() }
        active.set(null)
    }

    override fun setListener(listener: (TransportEvent) -> Unit) {
        listeners.add(listener)
    }

    override fun send(frame: ByteArray): Boolean {
        val a = active.get() ?: return false
        return a.send(frame)
    }

    fun chooseActive(): TransportKind {
        val chosen = preference.firstOrNull { pref -> adapters.any { it.kind == pref && it.isAvailable() } }
        val next = adapters.firstOrNull { it.kind == chosen }
        active.set(next)
        return next?.kind ?: TransportKind.NONE
    }

    fun migrateTo(kind: TransportKind): Boolean {
        val next = adapters.firstOrNull { it.kind == kind && it.isAvailable() } ?: return false
        active.set(next)
        return true
    }
}
