package com.itantra.app.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

class BluetoothClassicTransport(private val context: Context) : Transport {
    companion object {
        val APP_UUID: UUID = UUID.fromString("9c7e6a52-1d9c-4b8a-aed1-49a6cce7a173")
        private const val SERVICE_NAME = "iTantraTextV1"
        private const val MAX_FRAME = MessagePayload.MAX_PAYLOAD_BYTES
        private const val PING_INTERVAL_MS = 8_000L
        private const val LINK_TIMEOUT_MS = 25_000L
    }

    private val adapter: BluetoothAdapter? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    } else {
        @Suppress("DEPRECATION")
        BluetoothAdapter.getDefaultAdapter()
    }
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _incoming = MutableSharedFlow<MessagePayload>(extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val incoming: Flow<MessagePayload> = _incoming.asSharedFlow()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _state.asStateFlow()
    private val writeMutex = Mutex()
    private val seq = AtomicLong(1)
    private val outbox = LinkedHashMap<String, MessagePayload>()
    private val outboxMutex = Mutex()
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var lastInboundMs = 0L
    @Volatile private var closedByUser = false

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        requireBluetoothPermission()
        val a = adapter ?: throw IllegalStateException("Bluetooth is not supported")
        if (!a.isEnabled) throw IllegalStateException("Bluetooth is off")
        closedByUser = false
        _state.value = ConnectionState.Listening
        try {
            serverSocket = a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
            while (coroutineContext.isActive && !closedByUser) {
                try {
                    val accepted = serverSocket?.accept() ?: break
                    installSocket(accepted)
                    readLoop(accepted)
                } catch (e: IOException) {
                    if (!closedByUser) _state.value = ConnectionState.Error("Host socket failed: ${e.message}")
                    break
                }
            }
        } finally {
            runCatching { serverSocket?.close() }
            serverSocket = null
            if (!closedByUser) _state.value = ConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectTo(address: String) = withContext(Dispatchers.IO) {
        requireBluetoothPermission()
        val a = adapter ?: throw IllegalStateException("Bluetooth is not supported")
        if (!a.isEnabled) throw IllegalStateException("Bluetooth is off")
        closedByUser = false
        _state.value = ConnectionState.Connecting
        var backoff = 1_000L
        while (!closedByUser && coroutineContext.isActive) {
            try {
                val device = a.getRemoteDevice(address)
                a.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(APP_UUID)
                s.connect()
                installSocket(s)
                backoff = 1_000L
                readLoop(s)
                if (closedByUser) break
                _state.value = ConnectionState.Disconnected
            } catch (e: SecurityException) {
                _state.value = ConnectionState.Error("Bluetooth permission denied: ${e.message}")
                throw e
            } catch (e: Exception) {
                _state.value = ConnectionState.Error("Connection failed: ${e.message ?: e.javaClass.simpleName}")
            }
            if (!closedByUser) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun installSocket(s: BluetoothSocket) {
        socket?.let { runCatching { it.close() } }
        socket = s
        lastInboundMs = System.currentTimeMillis()
        val name = try { s.remoteDevice.name ?: s.remoteDevice.address } catch (_: Exception) { "Bluetooth peer" }
        _state.value = ConnectionState.Connected(name)
        scope.launch { pingLoop(s) }
        scope.launch { flushOutbox() }
    }

    private suspend fun readLoop(s: BluetoothSocket) = withContext(Dispatchers.IO) {
        try {
            val input = DataInputStream(s.inputStream)
            while (!closedByUser && s.isConnected) {
                val length = try { input.readInt() } catch (_: EOFException) { break }
                if (length !in 1..MAX_FRAME) throw IOException("Invalid frame length: $length")
                val bytes = ByteArray(length)
                input.readFully(bytes)
                lastInboundMs = System.currentTimeMillis()
                val payload = PayloadSerializer.decode(bytes)
                if (payload.type == MessageType.ACK) {
                    outboxMutex.lock()
                    try { outbox.remove(payload.ackForMessageId) } finally { outboxMutex.unlock() }
                    _incoming.emit(payload) // Expose ACK to the RTT/timeline layer as well.
                } else if (payload.type != MessageType.PING) {
                    _incoming.emit(payload)
                    if (payload.type == MessageType.SPEECH || payload.type == MessageType.ALERT) {
                        sendRaw(MessagePayload(type = MessageType.ACK,
                            messageId = UUID.randomUUID().toString(), seq = seq.getAndIncrement(),
                            senderId = localSenderId(), sentAtEpochMs = System.currentTimeMillis(),
                            ackForMessageId = payload.messageId))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!closedByUser) _state.value = ConnectionState.Error("Link lost: ${e.message}")
        } finally {
            if (socket === s) socket = null
            runCatching { s.close() }
            if (!closedByUser) _state.value = ConnectionState.Disconnected
        }
    }

    override suspend fun send(payload: MessagePayload) {
        payload.validate()
        if (payload.type == MessageType.SPEECH || payload.type == MessageType.ALERT) {
            outboxMutex.lock()
            try { outbox[payload.messageId] = payload } finally { outboxMutex.unlock() }
        }
        flushOutbox()
    }

    private suspend fun flushOutbox() {
        val pending = outboxMutex.run {
            lock()
            try { outbox.values.toList() } finally { unlock() }
        }
        for (payload in pending) {
            try { sendRaw(payload) } catch (_: Exception) { break }
        }
    }

    private suspend fun sendRaw(payload: MessagePayload) {
        val current = socket ?: throw IOException("Not connected; message retained in outbox")
        val bytes = PayloadSerializer.encode(payload)
        writeMutex.lock()
        try {
            val output = DataOutputStream(current.outputStream)
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        } catch (e: Exception) {
            runCatching { current.close() }
            throw e
        } finally {
            writeMutex.unlock()
        }
    }

    private suspend fun pingLoop(expected: BluetoothSocket) {
        while (!closedByUser && socket === expected) {
            delay(PING_INTERVAL_MS)
            if (System.currentTimeMillis() - lastInboundMs > LINK_TIMEOUT_MS) {
                _state.value = ConnectionState.Error("Peer heartbeat timed out")
                runCatching { expected.close() }
                break
            }
            runCatching {
                sendRaw(MessagePayload(type = MessageType.PING, messageId = UUID.randomUUID().toString(),
                    seq = seq.getAndIncrement(), senderId = localSenderId(),
                    sentAtEpochMs = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun disconnect() {
        closedByUser = true
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        socket = null
        serverSocket = null
        _state.value = ConnectionState.Disconnected
    }

    private fun localSenderId(): String =
        android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.take(64) ?: "android"

    private fun requireBluetoothPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT
            else Manifest.permission.BLUETOOTH
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Permission required: $permission")
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Permission required: ${Manifest.permission.BLUETOOTH_SCAN}")
    }
}
