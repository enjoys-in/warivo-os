@file:Suppress("DEPRECATION", "MissingPermission")

package com.warivo.os.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One row in the picker. [connected] is A2DP audio, not just a live ACL link. */
data class BtDevice(
    val name: String,
    val address: String,
    val bonded: Boolean,
    val bonding: Boolean,
    val connected: Boolean,
    val audio: Boolean,
)

/**
 * The in-app Bluetooth picker's engine: paired + nearby devices, pair, and best-effort
 * connect the audio output — so a rider can add a speaker without the system Settings app,
 * which a locked-down head unit does not expose.
 *
 * targetSdk 29 keeps the legacy Bluetooth model: discovery needs the location permission
 * the app already holds for BLE, and BLUETOOTH_ADMIN covers pairing — no new prompts.
 *
 * There is no public API to *connect* an A2DP sink; a freshly bonded audio device almost
 * always auto-connects, and [select] additionally tries the hidden connect() as a best
 * effort. The picker's real job is pairing.
 */
@SuppressLint("MissingPermission")
class BluetoothController(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var a2dp: BluetoothA2dp? = null

    private val _devices = MutableStateFlow<List<BtDevice>>(emptyList())
    val devices: StateFlow<List<BtDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true

    // Devices seen during the current scan, kept until the next scan clears them.
    private val discovered = linkedMapOf<String, BluetoothDevice>()
    private var registered = false

    private val a2dpListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP) {
                a2dp = proxy as BluetoothA2dp
                rebuild()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) a2dp = null
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                    if (!d.name.isNullOrBlank()) {
                        discovered[d.address] = d
                        rebuild()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _scanning.value = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> rebuild()
            }
        }
    }

    /** Called while the picker is on screen. Discovery is battery-heavy, so it is not kept on. */
    fun start() {
        val a = adapter ?: return
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }
            context.registerReceiver(receiver, filter)
            registered = true
        }
        if (a2dp == null) a.getProfileProxy(context, a2dpListener, BluetoothProfile.A2DP)
        rebuild()
        scan()
    }

    fun stop() {
        runCatching { adapter?.cancelDiscovery() }
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
        _scanning.value = false
    }

    /** Restart discovery, clearing the previous scan's finds. */
    fun scan() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        discovered.clear()
        runCatching { if (a.isDiscovering) a.cancelDiscovery() }
        runCatching { a.startDiscovery() }
        rebuild()
    }

    fun enable() {
        runCatching { adapter?.enable() }
    }

    /** Pair an unpaired device; for an already-paired one, best-effort connect its audio. */
    fun select(address: String) {
        val a = adapter ?: return
        runCatching { a.cancelDiscovery() }
        val device = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: return
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            runCatching { device.createBond() }
        } else {
            connectAudio(device)
        }
        rebuild()
    }

    private fun connectAudio(device: BluetoothDevice) {
        val proxy = a2dp ?: return
        // No public "connect" for an A2DP sink; try the hidden one and shrug if it is
        // blocked — a bonded audio device generally auto-connects anyway.
        runCatching {
            BluetoothA2dp::class.java.getMethod("connect", BluetoothDevice::class.java)
                .invoke(proxy, device)
        }.onFailure { Log.i(TAG, "A2DP connect unavailable: ${it.message}") }
    }

    private fun isConnected(device: BluetoothDevice): Boolean {
        val proxy = a2dp ?: return false
        return runCatching { proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED }
            .getOrDefault(false)
    }

    private fun isAudio(device: BluetoothDevice): Boolean =
        runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull() ==
            BluetoothClass.Device.Major.AUDIO_VIDEO

    private fun rebuild() {
        val a = adapter ?: return
        val byAddr = linkedMapOf<String, BtDevice>()
        runCatching { a.bondedDevices ?: emptySet() }.getOrDefault(emptySet()).forEach { d ->
            byAddr[d.address] = BtDevice(
                name = d.name ?: d.address,
                address = d.address,
                bonded = true,
                bonding = d.bondState == BluetoothDevice.BOND_BONDING,
                connected = isConnected(d),
                audio = isAudio(d),
            )
        }
        discovered.values.forEach { d ->
            if (byAddr.containsKey(d.address)) return@forEach
            byAddr[d.address] = BtDevice(
                name = d.name ?: d.address,
                address = d.address,
                bonded = false,
                bonding = d.bondState == BluetoothDevice.BOND_BONDING,
                connected = false,
                audio = isAudio(d),
            )
        }
        // Connected first, then other paired, then discovered; audio devices ahead of the rest.
        _devices.value = byAddr.values.sortedWith(
            compareByDescending<BtDevice> { it.connected }
                .thenByDescending { it.bonded }
                .thenByDescending { it.audio }
                .thenBy { it.name.lowercase() }
        )
    }

    private companion object {
        const val TAG = "WarivoBt"
    }
}
