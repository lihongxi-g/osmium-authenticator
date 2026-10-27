package com.safekey.authenticator.network

import com.safekey.authenticator.data.WebDavServerConfig
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOCAL-ONLY smoke test — deliberately NOT part of the committed suite: it
 * talks to a real cloud WebDAV server over the public internet.
 *
 * It proves the transport against the server the bug report came from:
 * a 401 means TLS handshake, host name verification, the Basic auth header and
 * PROPFIND itself all reached Nutstore. The old client answered 501 here
 * because the request went out as POST instead of PROPFIND.
 */
class WebDavRealServerSmokeTest {

    @Test
    fun nutstore_reaches_the_server_with_propfind() {
        val cfg = WebDavServerConfig(
            baseUrl = "https://dav.jianguoyun.com/dav",
            username = "osmium-smoke@example.invalid",
            password = "definitely-not-the-password"
        )
        val e = assertThrows(WebDavException::class.java) { WebDavClient.testConnection(cfg) }
        println("real server answered: ${e.message}")
        assertTrue(
            "expected a 401 (credentials rejected) but got: ${e.message}",
            e.message.orEmpty().contains("401")
        )
    }
}
