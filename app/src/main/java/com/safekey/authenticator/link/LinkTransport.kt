package com.safekey.authenticator.link

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean

/** LAN transport. Only LinkAccountView is serialized; raw secrets never enter this class. */
class LinkTransport(
    private val scope: CoroutineScope,
    private val deviceName: String,
    private val accountSnapshot: () -> List<LinkAccountView>
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val keyPair = LinkCrypto.generateKeyPair()
    private val _state = MutableStateFlow<LinkConnectionState>(LinkConnectionState.Idle)
    val state: StateFlow<LinkConnectionState> = _state
    private var server: ServerSocket? = null
    private val closed = AtomicBoolean(false)
    private var pendingSocket: Socket? = null
    private var pendingOutput: BufferedWriter? = null
    private var pendingRequest: LinkEnvelope? = null
    private var pendingPeerKey: PublicKey? = null

    fun startServer(port: Int = 0): Int {
        stop()
        closed.set(false)
        val socket = ServerSocket(port)
        server = socket
        scope.launch(Dispatchers.IO) {
            while (!closed.get()) {
                val peer = runCatching { socket.accept() }.getOrNull() ?: break
                scope.launch(Dispatchers.IO) { receiveRequest(peer) }
            }
        }
        return socket.localPort
    }

    private fun receiveRequest(socket: Socket) {
        val input = socket.getInputStream().bufferedReader()
        val output = socket.getOutputStream().bufferedWriter()
        try {
            val code = LinkCrypto.randomPairingCode()
            output.send(LinkEnvelope("HELLO", deviceName, LinkCrypto.encodePublicKey(keyPair.public), code))
            val request = input.readLine()?.let { json.decodeFromString(LinkEnvelope.serializer(), it) } ?: return
            if (request.type != "PAIR" || request.code != code) {
                output.send(LinkEnvelope("REJECTED"))
                return
            }
            pendingSocket = socket
            pendingOutput = output
            pendingRequest = request
            pendingPeerKey = LinkCrypto.decodePublicKey(request.publicKey)
            _state.value = LinkConnectionState.Incoming(request.deviceName, request.publicKey, code)
            return
        } catch (_: Exception) {
            if (!closed.get()) _state.value = LinkConnectionState.Idle
        } finally {
            if (pendingSocket === socket) {
                // Keep this accepted socket alive; the UI-owned accept/reject path closes it.
                return
            }
            runCatching { socket.close() }
        }
    }

    fun connect(host: String, port: Int, code: String, onResult: (Result<List<LinkAccountView>>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                Socket(host, port).use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val output = socket.getOutputStream().bufferedWriter()
                    val hello = json.decodeFromString(LinkEnvelope.serializer(), input.readLine())
                    require(hello.type == "HELLO") { "Invalid Link hello" }
                    val peerKey = LinkCrypto.decodePublicKey(hello.publicKey)
                    val sessionKey = LinkCrypto.deriveKey(keyPair.private, peerKey, "osmium-link-v1".toByteArray())
                    output.send(LinkEnvelope("PAIR", publicKey = LinkCrypto.encodePublicKey(keyPair.public), code = code, deviceName = deviceName))
                    val accepted = json.decodeFromString(LinkEnvelope.serializer(), input.readLine())
                    require(accepted.type == "ACCEPT") { "Pairing rejected" }
                    val data = json.decodeFromString(LinkEnvelope.serializer(), input.readLine())
                    require(data.type == "DATA") { "Missing Link account data" }
                    val plain = LinkCrypto.decrypt(sessionKey, data.payload, "accounts".toByteArray()).toString(Charsets.UTF_8)
                    json.decodeFromString(ListSerializerHolder.serializer, plain)
                }
            }.onSuccess { onResult(Result.success(it)) }.onFailure { onResult(Result.failure(it)) }
        }
    }

    fun acceptIncoming() {
        val socket = pendingSocket ?: return
        val output = pendingOutput ?: return
        val request = pendingRequest ?: return
        val peerKey = pendingPeerKey ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val sessionKey = LinkCrypto.deriveKey(keyPair.private, peerKey, "osmium-link-v1".toByteArray())
                output.send(LinkEnvelope("ACCEPT", deviceName, LinkCrypto.encodePublicKey(keyPair.public)))
                val payload = json.encodeToString(ListSerializerHolder.serializer, accountSnapshot())
                output.send(LinkEnvelope("DATA", payload = LinkCrypto.encrypt(sessionKey, payload.toByteArray(), "accounts".toByteArray())))
                _state.value = LinkConnectionState.Connected(request.deviceName, LinkCrypto.fingerprint(peerKey), false, accountSnapshot())
            }.onFailure { _state.value = LinkConnectionState.Error(it.message ?: "Link failed") }
            clearPending()
        }
    }

    fun rejectIncoming() {
        pendingOutput?.let { runCatching { it.send(LinkEnvelope("REJECTED")) } }
        clearPending()
        _state.value = LinkConnectionState.Idle
    }

    fun trustCurrent() {
        (_state.value as? LinkConnectionState.Connected)?.let { _state.value = it.copy(trusted = true) }
    }

    fun publishConnected(deviceName: String, accounts: List<LinkAccountView>, trusted: Boolean = false) {
        _state.value = LinkConnectionState.Connected(deviceName, "", trusted, accounts)
    }

    fun publishError(message: String) { _state.value = LinkConnectionState.Error(message) }

    fun stop() {
        closed.set(true)
        clearPending()
        runCatching { server?.close() }
        server = null
        _state.value = LinkConnectionState.Idle
    }

    private fun clearPending() {
        runCatching { pendingSocket?.close() }
        pendingSocket = null
        pendingOutput = null
        pendingRequest = null
        pendingPeerKey = null
    }

    private fun BufferedWriter.send(envelope: LinkEnvelope) {
        write(json.encodeToString(LinkEnvelope.serializer(), envelope))
        newLine()
        flush()
    }

    private object ListSerializerHolder {
        val serializer = kotlinx.serialization.builtins.ListSerializer(LinkAccountView.serializer())
    }
}
