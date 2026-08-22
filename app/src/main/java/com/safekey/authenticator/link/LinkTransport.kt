package com.safekey.authenticator.link

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Framed, LAN-only Link transport. Pairing is explicit; after pairing every
 * envelope is authenticated-encrypted. The transport never accepts a raw
 * secret/account export: the caller supplies only LinkAccountView values.
 */
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
    private var job: Job? = null
    private val closed = AtomicBoolean(false)
    private var pendingAcceptance: CompletableDeferred<Boolean>? = null

    fun currentState(): LinkConnectionState = _state.value

    fun startServer(port: Int = 0): Int {
        stop()
        closed.set(false)
        val socket = ServerSocket(port)
        server = socket
        job = scope.launch(Dispatchers.IO) {
            while (!closed.get()) {
                runCatching { socket.accept() }.getOrNull()?.let { peer ->
                    launch { serve(peer) }
                }
            }
        }
        return socket.localPort
    }

    private suspend fun serve(socket: Socket) {
        socket.use { s ->
            val input = s.getInputStream().bufferedReader()
            val output = s.getOutputStream().bufferedWriter()
            val code = LinkCrypto.randomPairingCode()
            val hello = LinkEnvelope("HELLO", deviceName, LinkCrypto.encodePublicKey(keyPair.public), code)
            output.write(json.encodeToString(LinkEnvelope.serializer(), hello)); output.newLine(); output.flush()
            val request = input.readLine()?.let { json.decodeFromString(LinkEnvelope.serializer(), it) } ?: return
            _state.value = LinkConnectionState.Incoming(request.deviceName, request.publicKey, code)
            val accepted = CompletableDeferred<Boolean>()
            pendingAcceptance = accepted
            if (withTimeoutOrNull(60_000) { accepted.await() } != true) return
            if (request.type != "PAIR" || request.code != code) {
                output.write(json.encodeToString(LinkEnvelope.serializer(), LinkEnvelope("REJECTED"))); output.newLine(); output.flush(); return
            }
            val peerKey = LinkCrypto.decodePublicKey(request.publicKey)
            val sessionKey = LinkCrypto.deriveKey(keyPair.private, peerKey, "osmium-link-v1".toByteArray())
            output.write(json.encodeToString(LinkEnvelope.serializer(), LinkEnvelope("ACCEPT", deviceName, LinkCrypto.encodePublicKey(keyPair.public)))); output.newLine(); output.flush()
            val payload = json.encodeToString(ListSerializerHolder.serializer, accountSnapshot())
            output.write(json.encodeToString(LinkEnvelope.serializer(), LinkEnvelope("DATA", payload = LinkCrypto.encrypt(sessionKey, payload.toByteArray(), "accounts".toByteArray())))); output.newLine(); output.flush()
            _state.value = LinkConnectionState.Connected(deviceName, LinkCrypto.fingerprint(peerKey), false, accountSnapshot())
            while (!closed.get() && input.readLine() != null) { /* keep session until either side closes */ }
        }
        if (!closed.get()) _state.value = LinkConnectionState.Idle
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
                    output.write(json.encodeToString(LinkEnvelope.serializer(), LinkEnvelope("PAIR", publicKey = LinkCrypto.encodePublicKey(keyPair.public), code = code))); output.newLine(); output.flush()
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

    fun acceptIncoming() { pendingAcceptance?.complete(true); pendingAcceptance = null }
    fun rejectIncoming() { pendingAcceptance?.complete(false); pendingAcceptance = null }
    fun trustCurrent() { (_state.value as? LinkConnectionState.Connected)?.let { _state.value = it.copy(trusted = true) } }

    fun publishConnected(deviceName: String, accounts: List<LinkAccountView>, trusted: Boolean = false) {
        _state.value = LinkConnectionState.Connected(deviceName, "", trusted, accounts)
    }

    fun publishError(message: String) { _state.value = LinkConnectionState.Error(message) }

    fun stop() { closed.set(true); pendingAcceptance?.complete(false); pendingAcceptance = null; runCatching { server?.close() }; server = null; job?.cancel(); job = null; _state.value = LinkConnectionState.Idle }
    private object ListSerializerHolder {
        val serializer = kotlinx.serialization.builtins.ListSerializer(LinkAccountView.serializer())
    }
}
