package com.safekey.authenticator.network

import com.safekey.authenticator.data.WebDavServerConfig
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in smoke test against a real cloud WebDAV server over the public
 * internet. Skipped unless OSMIUM_WEBDAV_SMOKE=1, so ordinary builds and CI
 * never depend on somebody else's server:
 *
 *   OSMIUM_WEBDAV_SMOKE=1 ./gradlew :app:testDebugUnitTest \
 *       --tests "com.safekey.authenticator.network.WebDavRealServerSmokeTest"
 *
 * A 401 here means the TLS handshake, the host name check, the Basic auth
 * header and PROPFIND itself all reached Nutstore. The old HttpURLConnection
 * client got 501 from this exact URL, because the request went out as POST
 * instead of PROPFIND.
 */
class WebDavRealServerSmokeTest {

    @Test
    fun nutstore_reaches_the_server_with_propfind() {
        assumeTrue(System.getenv("OSMIUM_WEBDAV_SMOKE") == "1")
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
