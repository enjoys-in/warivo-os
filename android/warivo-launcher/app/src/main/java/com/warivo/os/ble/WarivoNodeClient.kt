@file:Suppress("DEPRECATION", "MissingPermission")

package com.warivo.os.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.warivo.os.model.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

/**
 * BLE client for the Warivo Node (see firmware/warivo-node/warivo-node.ino).
 *
 * Service fff0:
 *   fff1  telemetry  READ + NOTIFY  -> scooter state JSON
 *   fff2  gps        WRITE          <- this phone's GPS fix
 *   fff3  config     WRITE          <- proximity-beep settings
 *
 * Two details that are easy to get wrong and are handled here:
 *
 *  1. **MTU.** A telemetry frame is ~140 bytes but the default BLE MTU of 23 caps a
 *     notification at 20 bytes, so every frame would arrive truncated and unparseable.
 *     We request a 247-byte MTU and only then discover services.
 *  2. **One GATT operation at a time.** Android silently drops a write issued while
 *     another is outstanding, so writes and descriptor writes go through a queue.
 */
class WarivoNodeClient(private val context: Context) {

    enum class State { IDLE, BLUETOOTH_OFF, NO_PERMISSION, SCANNING, CONNECTING, CONNECTED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    private val _deviceAddress = MutableStateFlow<String?>(null)
    val deviceAddress: StateFlow<String?> = _deviceAddress.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val manager get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter

    private var gatt: BluetoothGatt? = null
    private var telemetryChar: BluetoothGattCharacteristic? = null
    private var gpsChar: BluetoothGattCharacteristic? = null
    private var configChar: BluetoothGattCharacteristic? = null

    private var scanning = false
    private var wantConnection = false
    private var retryDelayMs = 1_000L

    // ---- GATT operation queue -------------------------------------------------

    private val ops = ArrayDeque<() -> Boolean>()
    private var opInFlight = false

    private fun enqueue(op: () -> Boolean) {
        synchronized(ops) {
            // GPS fixes arrive 1/sec; never let a stalled link build an unbounded backlog.
            if (ops.size >= MAX_QUEUED_OPS) ops.pollFirst()
            ops.addLast(op)
        }
        drain()
    }

    private fun drain() {
        while (true) {
            var next: (() -> Boolean)? = null
            synchronized(ops) {
                if (opInFlight) return
                next = ops.pollFirst() ?: return
                opInFlight = true
            }
            val op = next ?: return
            // If the op could not even be issued, fall through and try the next one.
            if (op()) return
            synchronized(ops) { opInFlight = false }
        }
    }

    private fun opComplete() {
        synchronized(ops) { opInFlight = false }
        drain()
    }

    private fun clearQueue() {
        synchronized(ops) {
            ops.clear()
            opInFlight = false
        }
    }

    // ---- lifecycle ------------------------------------------------------------

    /** Idempotent: safe to call on every resume. */
    fun start() {
        wantConnection = true
        if (!hasScanPermission()) {
            _state.value = State.NO_PERMISSION
            return
        }
        val a = adapter
        if (a == null || !a.isEnabled) {
            _state.value = State.BLUETOOTH_OFF
            handler.postDelayed(::retry, 3_000)
            return
        }
        if (gatt != null || scanning) return
        startScan()
    }

    fun stop() {
        wantConnection = false
        handler.removeCallbacksAndMessages(null)
        stopScan()
        closeGatt()
        _state.value = State.IDLE
    }

    /** Drop the link and immediately look for the node again. */
    fun reconnect() {
        stopScan()
        closeGatt()
        retryDelayMs = 1_000
        start()
    }

    private fun retry() {
        if (wantConnection) start()
    }

    private fun scheduleRetry() {
        if (!wantConnection) return
        handler.postDelayed(::retry, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
    }

    private fun hasScanPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // ---- scanning -------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val uuids = result.scanRecord?.serviceUuids
            val matchesService = uuids?.any { it.uuid == SERVICE_UUID } == true
            val matchesName = result.scanRecord?.deviceName == DEVICE_NAME ||
                result.device?.name == DEVICE_NAME
            if (!matchesService && !matchesName) return

            Log.i(TAG, "found node ${result.device.address} rssi=${result.rssi}")
            stopScan()
            connect(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            scanning = false
            scheduleRetry()
        }
    }

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run { scheduleRetry(); return }
        // Two filters are OR-ed by the stack: match the advertised service UUID, or the
        // name in the scan response (NimBLE may put the name only in the response).
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build(),
            ScanFilter.Builder().setDeviceName(DEVICE_NAME).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        _state.value = State.SCANNING
        scanner.startScan(filters, settings, scanCallback)

        // Give up on this sweep after a while so we re-advertise our intent and back off.
        handler.postDelayed({
            if (scanning) {
                stopScan()
                scheduleRetry()
            }
        }, SCAN_WINDOW_MS)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    // ---- connection -----------------------------------------------------------

    private fun connect(result: ScanResult) {
        _state.value = State.CONNECTING
        _deviceAddress.value = result.device.address
        clearQueue()
        gatt = result.device.connectGatt(context, false, gattCallback)
    }

    private fun closeGatt() {
        clearQueue()
        telemetryChar = null
        gpsChar = null
        configChar = null
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "connected, requesting MTU")
                    // MTU first: service discovery is cheap to redo, a truncated
                    // notification stream is not recoverable.
                    if (!g.requestMtu(TARGET_MTU)) g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "disconnected (status=$status)")
                    _telemetry.value = null
                    closeGatt()
                    if (wantConnection) {
                        _state.value = State.SCANNING
                        scheduleRetry()
                    } else {
                        _state.value = State.IDLE
                    }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "mtu=$mtu status=$status")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(SERVICE_UUID)
            if (service == null) {
                Log.w(TAG, "service fff0 missing; dropping link")
                g.disconnect()
                return
            }
            telemetryChar = service.getCharacteristic(CHAR_TELEMETRY)
            gpsChar = service.getCharacteristic(CHAR_GPS)
            configChar = service.getCharacteristic(CHAR_CONFIG)

            val tc = telemetryChar
            if (tc == null) {
                Log.w(TAG, "characteristic fff1 missing; dropping link")
                g.disconnect()
                return
            }

            retryDelayMs = 1_000
            _state.value = State.CONNECTED

            // Subscribe: local flag plus a CCCD write, queued like any other operation.
            g.setCharacteristicNotification(tc, true)
            val cccd = tc.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                enqueue {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            opComplete()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            opComplete()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (c.uuid != CHAR_TELEMETRY) return
            val raw = c.value ?: return
            Telemetry.parse(String(raw, Charsets.UTF_8))?.let { _telemetry.value = it }
        }
    }

    // ---- writes ---------------------------------------------------------------

    /** Phone -> node: our latest GPS fix, on fff2. */
    fun writeGps(lat: Double, lon: Double, speedKmh: Float, epochSeconds: Long) {
        // Locale.US matters: a comma decimal separator would emit invalid JSON.
        val json = String.format(
            Locale.US,
            """{"lat":%.6f,"lon":%.6f,"spd":%.1f,"ts":%d}""",
            lat, lon, speedKmh, epochSeconds,
        )
        write(gpsChar, json)
    }

    /** Phone -> node: proximity-beep settings, on fff3. */
    fun writeConfig(beepEnabled: Boolean, beepCm: Int) {
        write(configChar, """{"beep":${if (beepEnabled) 1 else 0},"beep_cm":$beepCm}""")
    }

    private fun write(c: BluetoothGattCharacteristic?, json: String) {
        val g = gatt ?: return
        val ch = c ?: return
        enqueue {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ch.value = json.toByteArray(Charsets.UTF_8)
            g.writeCharacteristic(ch)
        }
    }

    companion object {
        private const val TAG = "WarivoNode"
        const val DEVICE_NAME = "Warivo-Node"

        private const val TARGET_MTU = 247
        private const val MAX_RETRY_MS = 15_000L
        private const val SCAN_WINDOW_MS = 12_000L
        private const val MAX_QUEUED_OPS = 8

        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val CHAR_TELEMETRY: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val CHAR_GPS: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val CHAR_CONFIG: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
