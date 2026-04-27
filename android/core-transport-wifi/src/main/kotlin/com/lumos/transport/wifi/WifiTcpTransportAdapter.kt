package com.lumos.transport.wifi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.lumos.protocol.TransportKind
import com.lumos.transport.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Wi-Fi local transport using NSD (Bonjour-like) discovery + TCP sockets.
 *
 * This is the venue-friendly fallback when BLE is noisy.
 * - Advertise: start ServerSocket + register service via NSD
 * - Discover: discover NSD services + connect via TCP
 *
 * Framing uses FrameCodec.
 */
class WifiTcpTransportAdapter(
    private val ctx: Context,
    override val id: String = "wifi_tcp",
    private val serviceType: String = "_lumos._tcp.",
    private val serviceName: String = "Lumos",
    private val handler: Handler = Handler(Looper.getMainLooper())
) : TransportAdapter {

    override val kind: TransportKind = TransportKind.WIFI
    private var listener: TransportListener? = null
    private val peers = ConcurrentHashMap.newKeySet<String>()
    private val nsd: NsdManager = ctx.getSystemService(NsdManager::class.java)

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private data class Conn(val sock: Socket, var rem: ByteArray = ByteArray(0))
    private val conns = ConcurrentHashMap<String, Conn>()

    override fun setListener(listener: TransportListener?) { this.listener = listener }
    override fun peers(): Set<String> = peers.toSet()

    override fun start(mode: StartMode) {
        when (mode) {
            StartMode.Advertise -> startAdvertise()
            StartMode.Discover -> startDiscover()
            StartMode.Session -> { startDiscover() }
        }
    }

    override fun stop() {
        stopDiscover()
        stopAdvertise()
        conns.values.forEach { try { it.sock.close() } catch (_:Exception) {} }
        conns.clear()
        peers.clear()
    }

    override fun send(peerId: String, bytes: ByteArray) {
        val c = conns[peerId] ?: return
        val framed = FrameCodec.encode(bytes)
        if (framed.isEmpty()) {
            handler.post { listener?.onAdapterError(object:AdapterError(AdapterError.Code.Internal, "Frame too large"){}) }
            return
        }
        try {
            val out = BufferedOutputStream(c.sock.getOutputStream())
            out.write(framed)
            out.flush()
        } catch (e: Exception) {
            handler.post { listener?.onDisconnected(peerId, DisconnectReason.Unknown) }
        }
    }

    private fun onMain(block:()->Unit){ handler.post(block) }

    private fun startAdvertise() {
        try {
            val sock = ServerSocket(0) // random port
            serverSocket = sock
            val port = sock.localPort
            val info = NsdServiceInfo().apply {
                serviceName = this@WifiTcpTransportAdapter.serviceName
                serviceType = this@WifiTcpTransportAdapter.serviceType
                setPort(port)
            }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, object: NsdManager.RegistrationListener {
                override fun onServiceRegistered(p0: NsdServiceInfo) {}
                override fun onRegistrationFailed(p0: NsdServiceInfo, p1: Int) { onMain { listener?.onAdapterError(object:AdapterError(AdapterError.Code.IoError, "NSD register failed ${'$'}p1"){}) } }
                override fun onServiceUnregistered(p0: NsdServiceInfo) {}
                override fun onUnregistrationFailed(p0: NsdServiceInfo, p1: Int) {}
            })

            acceptThread = thread(name="lumos-wifi-accept") {
                while (!Thread.currentThread().isInterrupted) {
                    val s = serverSocket?.accept() ?: break
                    val peerId = "${'$'}{s.inetAddress.hostAddress}:${'$'}{s.port}"
                    conns[peerId] = Conn(s)
                    peers.add(peerId)
                    onMain { listener?.onConnected(peerId) }
                    startReader(peerId, s)
                }
            }
        } catch (e: Exception) {
            onMain { listener?.onAdapterError(object:AdapterError(AdapterError.Code.IoError, "WiFi advertise error", e){}) }
        }
    }

    private fun stopAdvertise() {
        try { serverSocket?.close() } catch (_:Exception){}
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        // nsd unregister: in real app keep registration handle; omitted for brevity in Phase-4
    }

    private fun startDiscover() {
        val dl = object: NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType != serviceType) return
                nsd.resolveService(service, object: NsdManager.ResolveListener {
                    override fun onResolveFailed(p0: NsdServiceInfo, p1: Int) {}
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host: InetAddress = resolved.host
                        val port: Int = resolved.port
                        val peerId = "${'$'}{host.hostAddress}:${'$'}port"
                        if (peers.add(peerId)) onMain { listener?.onPeerDiscovered(PeerInfo(peerId, null, "wifi")) }
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { nsd.stopServiceDiscovery(this); onMain { listener?.onAdapterError(object:AdapterError(AdapterError.Code.IoError,"NSD start failed ${'$'}errorCode"){}) } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { nsd.stopServiceDiscovery(this) }
        }
        discoveryListener = dl
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, dl)
    }

    private fun stopDiscover() {
        discoveryListener?.let { try { nsd.stopServiceDiscovery(it) } catch (_:Exception){} }
        discoveryListener = null
    }

    /** Explicit connect called by orchestrator after discovery. */
    fun connect(peerHostPort: String) {
        try {
            val parts = peerHostPort.split(":")
            val host = parts[0]; val port = parts[1].toInt()
            val s = Socket(host, port)
            val peerId = peerHostPort
            conns[peerId] = Conn(s)
            peers.add(peerId)
            onMain { listener?.onConnected(peerId) }
            startReader(peerId, s)
        } catch (e: Exception) {
            onMain { listener?.onAdapterError(object:AdapterError(AdapterError.Code.IoError,"WiFi connect failed",e){}) }
        }
    }

    private fun startReader(peerId: String, s: Socket) {
        thread(name="lumos-wifi-read-${'$'}peerId") {
            val inp = BufferedInputStream(s.getInputStream())
            val buf = ByteArray(4096)
            while (true) {
                val n = try { inp.read(buf) } catch (_:Exception) { -1 }
                if (n <= 0) break
                val c = conns[peerId] ?: continue
                val merged = c.rem + buf.copyOfRange(0,n)
                val (frames, rem) = FrameCodec.decodeMany(merged)
                c.rem = rem
                frames.forEach { payload -> onMain { listener?.onBytesReceived(peerId, payload) } }
            }
            onMain { listener?.onDisconnected(peerId, DisconnectReason.Remote) }
            try { s.close() } catch (_:Exception){}
        }
    }
}
