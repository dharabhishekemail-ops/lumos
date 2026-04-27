package com.lumos.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.lumos.protocol.TransportKind
import com.lumos.transport.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * BLE transport over GATT with framing + chunking.
 *
 * Notes (robustness):
 * - iOS pacing constraints exist; Android also needs write queue discipline.
 * - We serialize callbacks onto main looper to avoid re-entrancy into orchestrator.
 * - This adapter is split into Central (scanner/connector) and Peripheral (advertiser/gatt-server).
 * - For Phase-4 we keep a single role at a time based on StartMode.
 */
class BleGattTransportAdapter(
    private val ctx: Context,
    override val id: String = "ble",
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : TransportAdapter {

    override val kind: TransportKind = TransportKind.BLE
    private var listener: TransportListener? = null

    private val peers = ConcurrentHashMap.newKeySet<String>()

    private val btMgr: BluetoothManager = ctx.getSystemService(BluetoothManager::class.java)
    private val btAdapter: BluetoothAdapter = btMgr.adapter

    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null

    // Per connected peer state
    private data class PeerState(
        var gatt: BluetoothGatt? = null,
        var rxRemainder: ByteArray = ByteArray(0),
        var mtu: Int = 23,
        val writeQueue: ArrayDeque<ByteArray> = ArrayDeque(),
        var writing: Boolean = false,
    )

    private val peerState = ConcurrentHashMap<String, PeerState>()

    override fun setListener(listener: TransportListener?) { this.listener = listener }

    override fun peers(): Set<String> = peers.toSet()

    override fun start(mode: StartMode) {
        if (!btAdapter.isEnabled) {
            listener?.onAdapterError(object: AdapterError(AdapterError.Code.RadioDisabled, "Bluetooth disabled"){})
            return
        }
        when (mode) {
            StartMode.Advertise -> startPeripheral()
            StartMode.Discover -> startCentralScan()
            StartMode.Session -> {
                // For sessions we can choose based on capability: default to Discover+connect.
                startCentralScan()
            }
        }
    }

    override fun stop() {
        stopCentralScan()
        stopPeripheral()
        peerState.values.forEach { it.gatt?.close() }
        peerState.clear()
        peers.clear()
    }

    override fun send(peerId: String, bytes: ByteArray) {
        val st = peerState[peerId] ?: return
        val framed = FrameCodec.encode(bytes)
        // Chunk to (mtu - 3) for ATT payload, conservative; real value updates on MTU callback.
        val chunkSize = (st.mtu - 3).coerceAtLeast(20)
        var off = 0
        while (off < framed.size) {
            val n = min(chunkSize, framed.size - off)
            st.writeQueue.addLast(framed.copyOfRange(off, off + n))
            off += n
        }
        drainWriteQueue(peerId, st)
    }

    private fun deliverOnMain(block: () -> Unit) { handler.post(block) }

    // ---------------- Central role (scan + connect) ----------------

    @SuppressLint("MissingPermission")
    private fun startCentralScan() {
        scanner = btAdapter.bluetoothLeScanner
        val s = scanner ?: return

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        s.startScan(filters, settings, scanCb)
    }

    @SuppressLint("MissingPermission")
    private fun stopCentralScan() {
        scanner?.stopScan(scanCb)
        scanner = null
    }

    private val scanCb = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val peerId = dev.address // ephemeral rotation handled above protocol; here we use address as link-layer id
            if (peers.add(peerId)) {
                deliverOnMain { listener?.onPeerDiscovered(PeerInfo(peerId, result.rssi, "ble")) }
            }
            // Optional: auto-connect policy is handled by session/orchestrator, not by scanner.
        }

        override fun onScanFailed(errorCode: Int) {
            deliverOnMain {
                listener?.onAdapterError(object : AdapterError(AdapterError.Code.IoError, "Scan failed: ${'$'}errorCode"){})
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(peerId: String) {
        val dev = btAdapter.getRemoteDevice(peerId) ?: return
        val st = peerState.computeIfAbsent(peerId) { PeerState() }
        st.gatt?.close()
        st.gatt = dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCb = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val peerId = gatt.device.address
            val st = peerState.computeIfAbsent(peerId) { PeerState() }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                peers.add(peerId)
                st.gatt = gatt
                deliverOnMain { listener?.onConnected(peerId) }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                peers.remove(peerId)
                deliverOnMain { listener?.onDisconnected(peerId, DisconnectReason.Remote) }
                st.gatt?.close()
                st.gatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            peerState[gatt.device.address]?.mtu = mtu
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val svc = gatt.getService(BleUuids.SERVICE) ?: return
            val tx = svc.getCharacteristic(BleUuids.CHAR_TX) ?: return
            gatt.setCharacteristicNotification(tx, true)
            // CCCD enable
            tx.descriptors?.firstOrNull()?.let { d ->
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(d)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != BleUuids.CHAR_TX) return
            val peerId = gatt.device.address
            val st = peerState.computeIfAbsent(peerId) { PeerState() }
            val data = characteristic.value ?: return
            val merged = st.rxRemainder + data
            val (frames, rem) = FrameCodec.decodeMany(merged)
            st.rxRemainder = rem
            frames.forEach { payload ->
                deliverOnMain { listener?.onBytesReceived(peerId, payload) }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val peerId = gatt.device.address
            val st = peerState[peerId] ?: return
            st.writing = false
            drainWriteQueue(peerId, st)
        }
    }

    @SuppressLint("MissingPermission")
    private fun drainWriteQueue(peerId: String, st: PeerState) {
        if (st.writing) return
        val gatt = st.gatt ?: return
        val svc = gatt.getService(BleUuids.SERVICE) ?: return
        val rx = svc.getCharacteristic(BleUuids.CHAR_RX) ?: return
        val next = st.writeQueue.removeFirstOrNull() ?: return
        st.writing = true
        rx.value = next
        rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val ok = gatt.writeCharacteristic(rx)
        if (!ok) {
            st.writing = false
            listener?.onAdapterError(object: AdapterError(AdapterError.Code.IoError, "BLE write failed enqueue"){})
        }
    }

    // ---------------- Peripheral role (advertise + gatt server) ----------------

    @SuppressLint("MissingPermission")
    private fun startPeripheral() {
        advertiser = btAdapter.bluetoothLeAdvertiser
        val adv = advertiser ?: return
        gattServer = btMgr.openGattServer(ctx, gattServerCb)

        val service = BluetoothGattService(BleUuids.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val rx = BluetoothGattCharacteristic(
            BleUuids.CHAR_RX,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val tx = BluetoothGattCharacteristic(
            BleUuids.CHAR_TX,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        tx.addDescriptor(BluetoothGattDescriptor(
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        ))
        service.addCharacteristic(rx)
        service.addCharacteristic(tx)
        gattServer?.addService(service)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleUuids.SERVICE))
            .setIncludeDeviceName(false)
            .build()

        adv.startAdvertising(settings, data, advCb)
    }

    @SuppressLint("MissingPermission")
    private fun stopPeripheral() {
        advertiser?.stopAdvertising(advCb)
        advertiser = null
        gattServer?.close()
        gattServer = null
    }

    private val advCb = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            deliverOnMain {
                listener?.onAdapterError(object: AdapterError(AdapterError.Code.IoError, "Advertise failed: ${'$'}errorCode"){})
            }
        }
    }

    private val subscribed = ConcurrentHashMap<String, BluetoothDevice>()

    private val gattServerCb = object : BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val peerId = device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                peers.add(peerId)
                deliverOnMain { listener?.onConnected(peerId) }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                peers.remove(peerId)
                subscribed.remove(peerId)
                deliverOnMain { listener?.onDisconnected(peerId, DisconnectReason.Remote) }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val peerId = device.address
            val st = peerState.computeIfAbsent(peerId) { PeerState() }
            val merged = st.rxRemainder + value
            val (frames, rem) = FrameCodec.decodeMany(merged)
            st.rxRemainder = rem
            frames.forEach { payload ->
                deliverOnMain { listener?.onBytesReceived(peerId, payload) }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            // CCCD subscribe/unsubscribe
            subscribed[device.address] = device
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    /**
     * Peripheral -> Central notifications.
     * Called by orchestrator when this adapter is active as peripheral.
     */
    @SuppressLint("MissingPermission")
    fun notify(peerId: String, bytes: ByteArray) {
        val dev = subscribed[peerId] ?: return
        val svc = gattServer?.getService(BleUuids.SERVICE) ?: return
        val tx = svc.getCharacteristic(BleUuids.CHAR_TX) ?: return
        val framed = FrameCodec.encode(bytes)
        // Notifications also need chunking by MTU; here we conservatively use 20 bytes.
        val chunkSize = 20
        var off = 0
        while (off < framed.size) {
            val n = min(chunkSize, framed.size - off)
            tx.value = framed.copyOfRange(off, off + n)
            gattServer?.notifyCharacteristicChanged(dev, tx, false)
            off += n
        }
    }
}
