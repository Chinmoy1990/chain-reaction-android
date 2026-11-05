package com.chainreaction.app.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * A single Bluetooth Classic (RFCOMM) connection between two phones.
 * Messages are plain text lines. One phone [host]s, the other [connect]s.
 *
 * Every host/connect/stop starts a new "session"; background threads from an
 * older session notice the number changed and quietly give up.
 */
@SuppressLint("MissingPermission") // permissions are requested in the lobby before any call
object BtLink {
    private val SERVICE_UUID: UUID = UUID.fromString("5f2a8c1e-3b7d-4e9a-a6c4-1d8e0b2f7c93")
    private const val SERVICE_NAME = "ChainReaction"

    sealed interface Status {
        data object Idle : Status
        data object Hosting : Status
        data class Connecting(val name: String) : Status
        data class Connected(val name: String, val isHost: Boolean) : Status
        data class Lost(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Lines received from the other phone. */
    val incoming = Channel<String>(Channel.UNLIMITED)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session = 0
    private var server: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var outbox: Channel<String>? = null

    fun host(adapter: BluetoothAdapter) {
        val id = reset()
        _status.value = Status.Hosting
        scope.launch {
            try {
                val ss = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                synchronized(this@BtLink) {
                    if (id != session) { ss.close(); return@launch }
                    server = ss
                }
                val s = ss.accept()          // blocks until a friend joins (or reset() closes it)
                runCatching { ss.close() }
                attach(id, s, isHost = true)
            } catch (e: IOException) {
                fail(id, "Hosting stopped.")
            } catch (e: SecurityException) {
                fail(id, "Bluetooth permission missing.")
            }
        }
    }

    fun connect(adapter: BluetoothAdapter, device: BluetoothDevice) {
        val id = reset()
        _status.value = Status.Connecting(device.name ?: device.address)
        scope.launch {
            runCatching { adapter.cancelDiscovery() } // discovery slows connecting down a lot
            try {
                val s = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                synchronized(this@BtLink) {
                    if (id != session) { s.close(); return@launch }
                    socket = s
                }
                s.connect()
                attach(id, s, isHost = false)
            } catch (e: IOException) {
                fail(id, "Couldn't connect. Make sure the other phone pressed \"Host a game\".")
            } catch (e: SecurityException) {
                fail(id, "Bluetooth permission missing.")
            }
        }
    }

    fun send(line: String) {
        outbox?.trySend(line)
    }

    fun stop() {
        reset()
        _status.value = Status.Idle
    }

    private fun attach(id: Int, s: BluetoothSocket, isHost: Boolean) {
        val box = Channel<String>(Channel.UNLIMITED)
        synchronized(this) {
            if (id != session) { runCatching { s.close() }; return }
            socket = s
            outbox = box
        }
        val name = runCatching { s.remoteDevice.name }.getOrNull() ?: s.remoteDevice.address
        _status.value = Status.Connected(name, isHost)

        // Writer: a single coroutine so messages keep their order.
        scope.launch {
            try {
                val w = s.outputStream.bufferedWriter()
                for (line in box) {
                    w.write(line); w.write("\n"); w.flush()
                }
            } catch (e: IOException) {
                fail(id, "Connection lost.")
            }
        }
        // Reader
        scope.launch {
            try {
                val r = s.inputStream.bufferedReader()
                while (true) {
                    val line = r.readLine() ?: break
                    if (id == session) incoming.trySend(line)
                }
            } catch (_: IOException) {
            }
            fail(id, "Your friend disconnected.")
        }
    }

    private fun fail(id: Int, message: String) {
        synchronized(this) {
            if (id != session) return
            reset()
        }
        _status.value = Status.Lost(message)
    }

    @Synchronized
    private fun reset(): Int {
        session++
        outbox?.close(); outbox = null
        runCatching { server?.close() }; server = null
        runCatching { socket?.close() }; socket = null
        while (incoming.tryReceive().isSuccess) { /* drop stale messages */ }
        return session
    }
}
