package com.lumos.app

import android.app.Application
import com.lumos.core.config.ConfigRuntime
import com.lumos.core.config.RemoteConfigFetcher
import com.lumos.core.config.LastKnownGoodStore
import com.lumos.core.session.SessionOrchestrator
import com.lumos.core.transport.mux.TransportMux
import com.lumos.core.transport.ble.BleTransportAdapter
import com.lumos.core.transport.wifi.WifiTransportAdapter
import com.lumos.core.transport.qr.QrBootstrapCodec

/**
 * Phase 9: Application-level wiring and lifecycle surfaces.
 *
 * Responsibilities:
 * - Initialize ConfigRuntime with LastKnownGoodStore
 * - Initialize transports and TransportMux
 * - Initialize SessionOrchestrator (service layer)
 * - Provide singletons to UI layer via DI (Hilt/Koin) in the full project
 */
class LumosApp : Application() {

    lateinit var configRuntime: ConfigRuntime
        private set

    lateinit var transportMux: TransportMux
        private set

    lateinit var orchestrator: SessionOrchestrator
        private set

    override fun onCreate() {
        super.onCreate()

        val lkg = LastKnownGoodStore(this)
        val fetcher = RemoteConfigFetcher(this)

        configRuntime = ConfigRuntime(
            context = this,
            lastKnownGoodStore = lkg,
            remoteFetcher = fetcher
        )

        val ble = BleTransportAdapter(this)
        val wifi = WifiTransportAdapter(this)
        val qr = QrBootstrapCodec()

        transportMux = TransportMux(
            adapters = listOf(ble, wifi),
            qrBootstrap = qr
        )

        orchestrator = SessionOrchestrator(
            transport = transportMux,
            config = configRuntime
        )
    }
}
