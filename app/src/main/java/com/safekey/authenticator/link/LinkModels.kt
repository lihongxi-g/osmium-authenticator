package com.safekey.authenticator.link

import kotlinx.serialization.Serializable

@Serializable
data class LinkAccountView(
    val id: String,
    val issuer: String,
    val label: String,
    val code: String,
    val remainingSeconds: Int,
    val type: String,
    val tags: List<String> = emptyList()
)

@Serializable
data class LinkEnvelope(
    val type: String,
    val deviceName: String = "",
    val publicKey: String = "",
    val code: String = "",
    val payload: String = "",
    val requestId: String = ""
)

sealed interface LinkConnectionState {
    data object Idle : LinkConnectionState
    data class Incoming(val deviceName: String, val publicKey: String, val code: String) : LinkConnectionState
    data class AwaitingCode(val deviceName: String, val publicKey: String, val requestId: String) : LinkConnectionState
    data class Connected(val deviceName: String, val fingerprint: String, val trusted: Boolean, val accounts: List<LinkAccountView>) : LinkConnectionState
    data class Error(val message: String) : LinkConnectionState
}
