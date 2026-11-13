package com.somnaflow.app.bio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class BleDevice(val device: BluetoothDevice, val name: String, val kind: String, val rssi: Int)

/**
 * Bluetooth Low Energy wearables.
 *  - Any strap/watch exposing the standard Heart Rate Service (0x180D) -> heart rate + RR intervals for HRV.
 *  - Muse-compatible EEG headbands (service 0xFE8D): 4 dry electrodes, 256 Hz, 12-bit packed samples.
 *  - Generic EEG over Nordic UART: text lines of comma-separated microvolt values, one line per sample, 250 Hz.
 */
@SuppressLint("MissingPermission")
object Ble {
    private fun u(s: String) = UUID.fromString(s)
    private val HR_SERVICE = u("0000180d-0000-1000-8000-00805f9b34fb"); private val HR_CHAR = u("00002a37-0000-1000-8000-00805f9b34fb")
    private val CCCD = u("00002902-0000-1000-8000-00805f9b34fb")
    private val MUSE_SERVICE = u("0000fe8d-0000-1000-8000-00805f9b34fb"); private val MUSE_CONTROL = u("273e0001-4c4d-454d-96be-f03bac821358")
    private val MUSE_EEG = listOf("273e0003", "273e0004", "273e0005", "273e0006").map { u("$it-4c4d-454d-96be-f03bac821358") }
    private val NUS_SERVICE = u("6e400001-b5a3-f393-e0a9-e50e24dcca9e"); private val NUS_TX = u("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    var scanning by mutableStateOf(false); private set
    var found by mutableStateOf(listOf<BleDevice>()); private set
    var status by mutableStateOf(""); private set
    private val handler = Handler(Looper.getMainLooper())
    private val gatts = ArrayList<BluetoothGatt>()

    private fun adapter(ctx: Context) = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    fun available(ctx: Context) = adapter(ctx)?.isEnabled == true

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val name = try { r.device.name ?: r.scanRecord?.deviceName } catch (_: Throwable) { null } ?: return
            val uuids = r.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            val kind = when {
                HR_SERVICE in uuids -> "Heart rate"
                MUSE_SERVICE in uuids || name.startsWith("Muse", true) -> "EEG headband"
                NUS_SERVICE in uuids -> "EEG stream"
                else -> "Other"
            }
            val list = found.filter { it.device.address != r.device.address } + BleDevice(r.device, name, kind, r.rssi)
            found = list.sortedWith(compareBy<BleDevice> { it.kind == "Other" }.thenByDescending { it.rssi })
        }
        override fun onScanFailed(errorCode: Int) { scanning = false; status = "Scan failed ($errorCode)" }
    }

    fun scan(ctx: Context) {
        val sc = adapter(ctx)?.bluetoothLeScanner
        if (sc == null) { status = "Turn Bluetooth on to scan"; return }
        try {
            found = emptyList(); sc.startScan(scanCb); scanning = true; status = "Scanning…"
            handler.postDelayed({ stopScan(ctx) }, 15_000)
        } catch (t: Throwable) { status = "Scan not permitted" }
    }
    fun stopScan(ctx: Context) {
        try { adapter(ctx)?.bluetoothLeScanner?.stopScan(scanCb) } catch (_: Throwable) {}
        if (scanning) { scanning = false; status = if (found.isEmpty()) "No devices found" else "" }
    }

    fun connect(ctx: Context, d: BleDevice) {
        stopScan(ctx); status = "Connecting to ${d.name}…"
        try { gatts.add(d.device.connectGatt(ctx.applicationContext, false, Session(d.name), BluetoothDevice.TRANSPORT_LE)) }
        catch (_: Throwable) { status = "Could not connect" }
    }

    fun disconnectAll() {
        gatts.forEach { try { it.disconnect(); it.close() } catch (_: Throwable) {} }
        gatts.clear(); Bio.hrSource = ""; if (!Bio.demoRunning) Bio.clearEeg(); status = ""
    }

    private class Session(val name: String) : BluetoothGattCallback() {
        private val queue = ArrayDeque<() -> Unit>()
        private val text = StringBuilder()
        private fun next() { queue.removeFirstOrNull()?.invoke() }

        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                status = "$name disconnected"
                if (Bio.hrSource == name) Bio.hrSource = ""
                if (Bio.eegSource == name) Bio.clearEeg()
                try { g.close() } catch (_: Throwable) {}
            }
        }

        @Suppress("DEPRECATION")
        private fun subscribe(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            queue.addLast {
                g.setCharacteristicNotification(c, true)
                val d = c.getDescriptor(CCCD)
                if (d == null) next() else { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; if (!g.writeDescriptor(d)) next() }
            }
        }
        @Suppress("DEPRECATION")
        private fun command(g: BluetoothGatt, c: BluetoothGattCharacteristic, cmd: String) {
            queue.addLast {
                val b = "X$cmd\n".toByteArray(); b[0] = (b.size - 1).toByte()
                c.value = b; c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                g.writeCharacteristic(c)
                handler.postDelayed({ next() }, 150)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            val hr = g.getService(HR_SERVICE)?.getCharacteristic(HR_CHAR)
            val muse = g.getService(MUSE_SERVICE)
            val nus = g.getService(NUS_SERVICE)?.getCharacteristic(NUS_TX)
            when {
                hr != null -> { subscribe(g, hr); Bio.resetHeart(); Bio.hrSource = name; status = "$name connected · heart rate" }
                muse != null -> {
                    MUSE_EEG.forEach { id -> muse.getCharacteristic(id)?.let { subscribe(g, it) } }
                    muse.getCharacteristic(MUSE_CONTROL)?.let { c -> command(g, c, "p21"); command(g, c, "s"); command(g, c, "d") }
                    Bio.stopDemo(); Bio.eegSource = name; status = "$name connected · EEG 256 Hz"
                }
                nus != null -> { subscribe(g, nus); Bio.stopDemo(); Bio.eegSource = name; status = "$name connected · EEG stream" }
                else -> { status = "$name has no supported heart-rate or EEG service"; g.disconnect() }
            }
            next()
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) = next()

        @Deprecated("Deprecated in Java") @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            when (c.uuid) {
                HR_CHAR -> parseHeartRate(v)
                NUS_TX -> parseLines(v)
                else -> { val ch = MUSE_EEG.indexOf(c.uuid); if (ch >= 0) parseMuse(ch, v) }
            }
        }

        private fun parseLines(v: ByteArray) {
            text.append(String(v))
            while (true) {
                val nl = text.indexOf("\n"); if (nl < 0) break
                val line = text.substring(0, nl).trim(); text.delete(0, nl + 1)
                line.split(',', ';', '\t').take(8).forEachIndexed { ch, s -> s.trim().toFloatOrNull()?.let { Bio.onEeg(ch, it, 250) } }
            }
            if (text.length > 512) text.setLength(0)
        }
    }

    /** Heart Rate Measurement (0x2A37): flags, HR (8/16 bit), optional energy, then RR intervals in 1/1024 s. */
    fun parseHeartRate(v: ByteArray): List<Float> {
        if (v.isEmpty()) return emptyList()
        val flags = v[0].toInt()
        var i = if (flags and 1 != 0) 3 else 2
        if (flags and 0x08 != 0) i += 2
        val out = ArrayList<Float>()
        if (flags and 0x10 != 0) while (i + 1 < v.size) {
            val rr = ((v[i].toInt() and 0xff) or ((v[i + 1].toInt() and 0xff) shl 8)) * 1000f / 1024f
            out.add(rr); Bio.onRr(rr); i += 2
        }
        return out
    }

    /** Muse EEG packet: 2-byte sequence number, then twelve 12-bit samples; 0.48828125 µV per count around 0x800. */
    fun parseMuse(ch: Int, v: ByteArray): FloatArray {
        val out = FloatArray(12); var n = 0
        var i = 2
        while (i + 2 < v.size && n < 12) {
            val b0 = v[i].toInt() and 0xff; val b1 = v[i + 1].toInt() and 0xff; val b2 = v[i + 2].toInt() and 0xff
            out[n++] = 0.48828125f * (((b0 shl 4) or (b1 shr 4)) - 0x800)
            out[n++] = 0.48828125f * ((((b1 and 0xf) shl 8) or b2) - 0x800)
            i += 3
        }
        for (k in 0 until n) Bio.onEeg(ch, out[k], 256)
        return out
    }
}
