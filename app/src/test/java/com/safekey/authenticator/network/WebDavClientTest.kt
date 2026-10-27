package com.safekey.authenticator.network

import com.safekey.authenticator.data.WebDavServerConfig
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * End-to-end tests of the WebDAV client against a real (in-process) HTTP/HTTPS
 * server that mimics the parts of RFC 4918 a NAS or cloud WebDAV server
 * answers.
 *
 * The mock server is hand-rolled on a plain ServerSocket: AGP compiles unit
 * tests against android.jar, which does not contain com.sun.net.httpserver,
 * so the JDK HttpServer cannot be referenced from test sources.
 *
 * The TLS tests are the regression guard for the bug that made every https
 * server fail: the request method has to arrive on the wire as PROPFIND /
 * MKCOL, and it must not fall back to POST. [MockDavServer] answers POST with
 * 501 exactly like Nutstore does, so a regression fails loudly.
 */
class WebDavClientTest {

    private lateinit var server: MockDavServer
    private lateinit var baseUrl: String

    private val user = "alice"
    private val pass = "secret"

    @Before
    fun setUp() {
        server = MockDavServer(user, pass)
        baseUrl = "http://127.0.0.1:${server.port}"
    }

    @After
    fun tearDown() {
        server.close()
    }

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
        assertTrue(
            e.message!!.contains("unreachable", ignoreCase = true) ||
                e.message!!.contains("Connection failed", ignoreCase = true)
        )
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
    fun `upload preserves non-ascii bytes`() {
        val payload = byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte())
        WebDavClient.upload(config(), "osmium-backup-20260817-100000.json", payload)
        val files = WebDavClient.listBackups(config())
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
    fun `delete removes the backup from the server`() {
        WebDavClient.upload(config(), "osmium-backup-20260816-090000.json", byteArrayOf(1))
        WebDavClient.upload(config(), "osmium-backup-20260817-090000.json", byteArrayOf(2))

        val files = WebDavClient.listBackups(config())
        assertEquals(2, files.size)

        WebDavClient.delete(config(), files[0].href)

        val remaining = WebDavClient.listBackups(config())
        assertEquals(1, remaining.size)
        assertEquals("osmium-backup-20260816-090000.json", remaining[0].name)
    }

    @Test
    fun `deleting a non-existent backup does not throw for 204 servers`() {
        // servers answer 204 for DELETE of a missing file too; treat as success
        WebDavClient.delete(config(), "/osmium/ghost.json")
    }

    @Test
    fun `invalid address without scheme throws friendly error`() {
        val bad = config().copy(baseUrl = "192.168.1.5:5005")
        val e = assertThrows(WebDavException::class.java) { WebDavClient.listBackups(bad) }
        assertTrue(e.message!!.contains("scheme", ignoreCase = true))
    }

    // ------------------------------------------- request method (regression)

    @Test
    fun `webdav methods reach the server unchanged`() {
        WebDavClient.upload(config(), "osmium-backup-20260817-090000.json", byteArrayOf(1, 2))
        WebDavClient.listBackups(config())
        WebDavClient.testConnection(config())
        WebDavClient.delete(config(), "/osmium/osmium-backup-20260817-090000.json")

        // MKCOL/PROPFIND are not in HttpURLConnection's method whitelist; the
        // old client silently sent POST over https. Assert the wire methods.
        assertEquals(
            listOf("MKCOL", "PUT", "PROPFIND", "PROPFIND", "DELETE"),
            server.methods.toList()
        )
    }

    @Test
    fun `file names with spaces are percent-encoded in the request line`() {
        WebDavClient.upload(config(), "a backup file.json", byteArrayOf(1))
        assertTrue(server.paths.any { it == "/osmium/a%20backup%20file.json" })
        // The server path never contains a raw space, i.e. no request-line split.
        assertTrue(server.paths.none { it.contains(' ') })
    }

    @Test
    fun `a header cannot be injected through the server address`() {
        val bad = config().copy(baseUrl = "$baseUrl/dav\r\nX-Injected: yes")
        try {
            WebDavClient.testConnection(bad)
        } catch (_: WebDavException) {
            // either the URL is refused outright or it is percent-encoded
        }
        assertTrue(server.headerLines.none { it.startsWith("X-Injected", ignoreCase = true) })
    }

    // ---------------------------------------------------------------- framing

    @Test
    fun `chunked responses are decoded`() {
        val chunked = MockDavServer(user, pass, chunked = true)
        try {
            val cfg = WebDavServerConfig("http://127.0.0.1:${chunked.port}", user, pass)
            WebDavClient.upload(cfg, "osmium-backup-20260817-090000.json", byteArrayOf(1, 2, 3))
            val files = WebDavClient.listBackups(cfg)
            assertEquals(1, files.size)
            assertEquals("osmium-backup-20260817-090000.json", files[0].name)
        } finally {
            chunked.close()
        }
    }

    @Test
    fun `same-host redirects are followed`() {
        val redirecting = MockDavServer(user, pass, redirectTarget = "/osmium/", skipPath = "/osmium/")
        try {
            val cfg = WebDavServerConfig("http://127.0.0.1:${redirecting.port}", user, pass)
            val files = WebDavClient.listBackups(cfg)
            assertTrue(files.isEmpty())
            assertEquals(listOf("PROPFIND", "PROPFIND"), redirecting.methods.toList())
            assertEquals(listOf("/osmium", "/osmium/"), redirecting.paths.toList())
        } finally {
            redirecting.close()
        }
    }

    @Test
    fun `redirects to another host are refused`() {
        val hostile = MockDavServer(user, pass, redirectTarget = "http://evil.example/collect")
        try {
            val cfg = WebDavServerConfig("http://127.0.0.1:${hostile.port}", user, pass)
            val e = assertThrows(WebDavException::class.java) { WebDavClient.listBackups(cfg) }
            assertTrue(e.message!!.contains("another host", ignoreCase = true))
        } finally {
            hostile.close()
        }
    }

    // -------------------------------------------------------------------- TLS

    @Test
    fun `https server receives PROPFIND, not POST`() {
        val tlsServer = MockDavServer(user, pass, sslContext = serverContext(CERT, KEY))
        try {
            val cfg = WebDavServerConfig("https://localhost:${tlsServer.port}", user, pass)
            val tls = clientTlsTrusting(CERT)
            WebDavClient.testConnection(cfg, tls)
            WebDavClient.upload(cfg, "osmium-backup-20260817-090000.json", byteArrayOf(9), tls)
            val files = WebDavClient.listBackups(cfg, tls)
            assertEquals(1, files.size)

            assertEquals(
                listOf("PROPFIND", "MKCOL", "PUT", "PROPFIND"),
                tlsServer.methods.toList()
            )
            // POST is what a silent method downgrade would produce; the mock
            // answers it with 501, so this also guards the failure mode.
            assertTrue(tlsServer.methods.none { it == "POST" })
        } finally {
            tlsServer.close()
        }
    }

    @Test
    fun `an untrusted certificate is refused`() {
        val tlsServer = MockDavServer(user, pass, sslContext = serverContext(CERT, KEY))
        try {
            val cfg = WebDavServerConfig("https://localhost:${tlsServer.port}", user, pass)
            // Production TLS: platform trust anchors only.
            val e = assertThrows(WebDavException::class.java) { WebDavClient.testConnection(cfg) }
            assertTrue(e.message!!.contains("TLS", ignoreCase = true))
        } finally {
            tlsServer.close()
        }
    }

    @Test
    fun `a certificate for another host name is refused`() {
        val tlsServer = MockDavServer(user, pass, sslContext = serverContext(WRONG_CERT, WRONG_KEY))
        try {
            val cfg = WebDavServerConfig("https://localhost:${tlsServer.port}", user, pass)
            // Trusting the certificate is not enough: the name has to match.
            val e = assertThrows(WebDavException::class.java) {
                WebDavClient.testConnection(cfg, clientTlsTrusting(WRONG_CERT))
            }
            assertTrue(e.message!!.contains("does not match", ignoreCase = true))
        } finally {
            tlsServer.close()
        }
    }

    // ------------------------------------------------------------------ keys

    private companion object {
        const val CERT = "/tls/test-cert.pem"
        const val KEY = "/tls/test-key.pem"
        const val WRONG_CERT = "/tls/wrong-name-cert.pem"
        const val WRONG_KEY = "/tls/wrong-name-key.pem"
    }
}

// ------------------------------------------------------------ TLS test keys

private const val KEY_PASS = "test-only"

private fun readResource(path: String): ByteArray =
    WebDavClientTest::class.java.getResourceAsStream(path)?.use { it.readBytes() }
        ?: throw IllegalStateException("missing test resource $path")

private fun pemBody(pem: ByteArray, label: String): ByteArray {
    val text = String(pem, Charsets.US_ASCII)
    val body = text.substringAfter("-----BEGIN $label-----").substringBefore("-----END $label-----")
    return Base64.getMimeDecoder().decode(body)
}

private fun readCertificate(path: String): X509Certificate =
    CertificateFactory.getInstance("X.509")
        .generateCertificate(readResource(path).inputStream()) as X509Certificate

/** Server side: a SSLContext holding the given test key pair. */
private fun serverContext(certPath: String, keyPath: String): SSLContext {
    val cert = readCertificate(certPath)
    val key = KeyFactory.getInstance("RSA")
        .generatePrivate(PKCS8EncodedKeySpec(pemBody(readResource(keyPath), "PRIVATE KEY")))
    val store = KeyStore.getInstance("PKCS12").apply {
        load(null, null)
        setKeyEntry("test", key, KEY_PASS.toCharArray(), arrayOf(cert))
    }
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(store, KEY_PASS.toCharArray())
    return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
}

/**
 * Client side: trusts exactly one certificate, but still uses the platform
 * host name verifier — so the TLS tests exercise both checks the client
 * performs in production.
 */
private fun clientTlsTrusting(certPath: String): WebDavClient.Tls {
    val store = KeyStore.getInstance("PKCS12").apply {
        load(null, null)
        setCertificateEntry("test", readCertificate(certPath))
    }
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(store)
    val context = SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }
    return WebDavClient.Tls(context.socketFactory, HttpsURLConnection.getDefaultHostnameVerifier())
}

// -------------------------------------------- minimal HTTP/1.1 mock server

/**
 * Hand-rolled mock WebDAV server: enough HTTP/1.1 to answer PROPFIND, PUT,
 * GET, MKCOL and DELETE with Basic auth, plus a realistic 207 multistatus body
 * (status element AFTER prop, one 404 propstat per entry — the shape real
 * wsgidav servers produce).
 *
 * @param sslContext when set, the server speaks TLS (test certificate).
 * @param chunked answer bodies with Transfer-Encoding: chunked instead of
 *        Content-Length.
 * @param redirectTarget answer 301 with this Location unless the request path
 *        already equals [skipPath].
 */
private class MockDavServer(
    private val user: String,
    private val pass: String,
    sslContext: SSLContext? = null,
    private val chunked: Boolean = false,
    private val redirectTarget: String? = null,
    private val skipPath: String? = null
) : Closeable {

    private val serverSocket: ServerSocket =
        sslContext?.serverSocketFactory?.createServerSocket(0) ?: ServerSocket(0)

    val port: Int get() = serverSocket.localPort
    private val stored = mutableMapOf<String, ByteArray>()
    @Volatile private var running = true

    /** Everything the client sent, for assertions. */
    val methods: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val paths: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val headerLines: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    private val thread = Thread { serve() }.apply { isDaemon = true; start() }

    private fun serve() {
        while (running) {
            val socket = try {
                serverSocket.accept()
            } catch (e: SocketException) {
                break
            }
            try {
                socket.use { handle(it) }
            } catch (_: Exception) {
                // per-connection failures (e.g. a rejected TLS handshake) are
                // exactly what some tests expect
            }
        }
    }

    private fun handle(socket: Socket) {
        if (socket is javax.net.ssl.SSLSocket) socket.startHandshake()
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 3) return
        val method = parts[0]
        val path = parts[1]
        methods.add(method)
        paths.add(path)

        var contentLength = 0
        var authOk = false
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            headerLines.add(line)
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
            if (line.startsWith("Authorization:", ignoreCase = true)) {
                val expected = "Basic " + Base64.getEncoder()
                    .encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))
                authOk = line.substringAfter(":").trim() == expected
            }
        }
        val body = if (contentLength > 0) {
            val buf = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(buf, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            buf
        } else ByteArray(0)

        if (!authOk) {
            writeStatus(output, 401)
            return
        }
        if (redirectTarget != null && path != skipPath) {
            writeRedirect(output, redirectTarget)
            return
        }
        when (method) {
            "MKCOL" -> writeStatus(output, 201)
            "PUT" -> {
                stored[path] = body
                writeStatus(output, 201)
            }
            "GET" -> {
                val bytes = stored[path]
                if (bytes == null) writeStatus(output, 404)
                else writeBytes(output, 200, bytes)
            }
            "DELETE" -> {
                stored.remove(path)
                writeStatus(output, 204)
            }
            "PROPFIND" -> writeBytes(output, 207, multistatus().toByteArray(Charsets.UTF_8))
            // Nutstore answers POST on a collection with 501; a client that
            // silently downgrades PROPFIND to POST fails here.
            "POST" -> writeStatus(output, 501)
            else -> writeStatus(output, 405)
        }
    }

    private fun multistatus(): String {
        val responses = StringBuilder()
        responses.append(
            "<D:response><D:href>/osmium/</D:href>" +
                "<D:propstat><D:prop><D:getcontentlength/></D:prop>" +
                "<D:status>HTTP/1.1 404 Not Found</D:status></D:propstat>" +
                "<D:propstat><D:prop><D:getlastmodified>Sun, 16 Aug 2026 09:26:13 GMT" +
                "</D:getlastmodified><D:displayname>osmium</D:displayname></D:prop>" +
                "<D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"
        )
        stored.keys.sorted().forEach { key ->
            val size = stored[key]?.size ?: 0
            responses.append(
                "<D:response><D:href>$key</D:href>" +
                    "<D:propstat><D:prop>" +
                    "<D:getcontentlength>$size</D:getcontentlength>" +
                    "<D:getlastmodified>Mon, 17 Aug 2026 09:00:00 GMT</D:getlastmodified>" +
                    "</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"
            )
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<D:multistatus xmlns:D=\"DAV:\">$responses</D:multistatus>"
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == 10) break   // \n
            if (b == 13) continue // \r
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun writeStatus(output: OutputStream, code: Int) {
        write(output, "HTTP/1.1 $code ${reason(code)}\r\nContent-Length: 0\r\n" +
            "Connection: close\r\n\r\n")
    }

    private fun writeRedirect(output: OutputStream, location: String) {
        write(output, "HTTP/1.1 301 Moved Permanently\r\nLocation: $location\r\n" +
            "Content-Length: 0\r\nConnection: close\r\n\r\n")
    }

    private fun writeBytes(output: OutputStream, code: Int, bytes: ByteArray) {
        if (chunked) {
            write(output, "HTTP/1.1 $code ${reason(code)}\r\n" +
                "Content-Type: application/xml; charset=utf-8\r\n" +
                "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n")
            var offset = 0
            val step = 37 // deliberately awkward, to exercise chunk framing
            while (offset < bytes.size) {
                val n = minOf(step, bytes.size - offset)
                write(output, n.toString(16) + "\r\n")
                output.write(bytes, offset, n)
                write(output, "\r\n")
                offset += n
            }
            write(output, "0\r\n\r\n")
        } else {
            write(output, "HTTP/1.1 $code ${reason(code)}\r\nContent-Length: ${bytes.size}\r\n" +
                "Content-Type: application/xml; charset=utf-8\r\nConnection: close\r\n\r\n")
            output.write(bytes)
        }
        output.flush()
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        207 -> "Multi-Status"
        301 -> "Moved Permanently"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        501 -> "Not Implemented"
        else -> ""
    }

    private fun write(output: OutputStream, text: String) {
        output.write(text.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    override fun close() {
        running = false
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
    }
}
