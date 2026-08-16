package com.safekey.authenticator.network

import com.safekey.authenticator.data.WebDavServerConfig
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Base64

/**
 * End-to-end tests of the WebDAV client against a real (in-process) HTTP
 * server that mimics the parts of RFC 4918 that a NAS WebDAV server answers.
 */
class WebDavClientTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val stored = mutableMapOf<String, ByteArray>()

    private val user = "alice"
    private val pass = "secret"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val method = exchange.requestMethod
        val authOk = checkAuth(exchange)
        if (!authOk) {
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
            return
        }
        when {
            method == "MKCOL" -> exchange.sendResponseHeaders(201, -1).also { exchange.close() }
            method == "PUT" -> {
                val bytes = exchange.requestBody.readBytes()
                stored[path] = bytes
                exchange.sendResponseHeaders(201, -1)
                exchange.close()
            }
            method == "GET" -> {
                val bytes = stored[path]
                if (bytes == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                exchange.close()
            }
            method == "PROPFIND" -> {
                val body = multistatus(
                    """<D:response>
                        <D:href>${baseUrl}/osmium/</D:href>
                        <D:propstat><D:prop><D:getcontentlength>0</D:getcontentlength></D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                    </D:response>""" +
                        stored.keys.sorted().joinToString("") { key ->
                            val size = stored[key]?.size ?: 0
                            """<D:response>
                                <D:href>${baseUrl}$key</D:href>
                                <D:propstat>
                                  <D:prop>
                                    <D:getcontentlength>$size</D:getcontentlength>
                                    <D:getlastmodified>Mon, 17 Aug 2026 09:00:00 GMT</D:getlastmodified>
                                  </D:prop>
                                  <D:status>HTTP/1.1 200 OK</D:status>
                                </D:propstat>
                                <D:propstat>
                                  <D:prop><D:displayname/></D:prop>
                                  <D:status>HTTP/1.1 404 Not Found</D:status>
                                </D:propstat>
                            </D:response>"""
                        }
                )
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/xml; charset=utf-8")
                exchange.sendResponseHeaders(207, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            else -> {
                exchange.sendResponseHeaders(405, -1)
                exchange.close()
            }
        }
    }

    private fun checkAuth(exchange: HttpExchange): Boolean {
        val header = exchange.requestHeaders.getFirst("Authorization") ?: return false
        val expected = "Basic " + Base64.getEncoder()
            .encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))
        return header == expected
    }

    private fun multistatus(responses: String): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<D:multistatus xmlns:D=\"DAV:\">$responses</D:multistatus>"

    private fun config(): WebDavServerConfig =
        WebDavServerConfig(baseUrl = baseUrl, username = user, password = pass)

    // ------------------------------------------------------------------ tests

    @Test
    fun `test connection succeeds with valid credentials`() {
        WebDavClient.testConnection(config())
    }

    @Test
    fun `test connection fails on wrong credentials`() {
        val bad = config().copy(username = "mallory")
        val e = assertThrows(WebDavException::class.java) { WebDavClient.testConnection(bad) }
        assertTrue(e.message!!.contains("username", ignoreCase = true))
    }

    @Test
    fun `test connection fails on unreachable server`() {
        val dead = config().copy(baseUrl = "http://127.0.0.1:1")
        val e = assertThrows(WebDavException::class.java) { WebDavClient.testConnection(dead) }
        assertTrue(e.message!!.contains("unreachable", ignoreCase = true) ||
            e.message!!.contains("refused", ignoreCase = true) ||
            e.message!!.contains("Connection failed", ignoreCase = true))
    }

    @Test
    fun `upload then download round-trips the bytes`() {
        val payload = "{\"encrypted\":true}".toByteArray(Charsets.UTF_8)
        WebDavClient.upload(config(), "osmium-backup-20260817-090000.json", payload)

        val files = WebDavClient.listBackups(config())
        assertEquals(1, files.size)
        assertEquals("osmium-backup-20260817-090000.json", files[0].name)
        assertEquals(payload.size.toLong(), files[0].size)

        val downloaded = WebDavClient.download(config(), files[0].href)
        assertArrayEquals(payload, downloaded)
    }

    @Test
    fun `list skips the collection and non-json entries and sorts newest first`() {
        WebDavClient.upload(config(), "osmium-backup-20260816-090000.json", byteArrayOf(1))
        WebDavClient.upload(config(), "osmium-backup-20260817-090000.json", byteArrayOf(1, 2))
        WebDavClient.upload(config(), "notes.txt", byteArrayOf(3))

        val files = WebDavClient.listBackups(config())
        assertEquals(2, files.size)
        assertEquals("osmium-backup-20260817-090000.json", files[0].name)
        assertEquals("osmium-backup-20260816-090000.json", files[1].name)
    }

    @Test
    fun `listing an empty server returns no files`() {
        val files = WebDavClient.listBackups(config())
        assertTrue(files.isEmpty())
    }

    @Test
    fun `invalid address without scheme throws friendly error`() {
        val bad = config().copy(baseUrl = "192.168.1.5:5005")
        val e = assertThrows(WebDavException::class.java) { WebDavClient.listBackups(bad) }
        assertTrue(e.message!!.contains("scheme", ignoreCase = true))
    }
}
