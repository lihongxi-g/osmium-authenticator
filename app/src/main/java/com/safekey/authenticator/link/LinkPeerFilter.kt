package com.safekey.authenticator.link

/** Stable filtering rule kept separate from Android NSD callbacks for regression tests. */
object LinkPeerFilter {
    fun shouldShow(serviceName: String, ownServiceName: String?): Boolean =
        serviceName.isNotBlank() && serviceName != ownServiceName
}
