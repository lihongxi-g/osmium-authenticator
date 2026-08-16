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
import java.util.Base64

/**
 * End-to-end tests of the WebDAV client against a real (in-process) HTTP
 * server that mimics the parts of RFC 4918 a NAS WebDAV server answers.
 *
 * The mock server is hand-rolled on a plain ServerSocket: AGP compiles unit
 * tests against android.jar, which does not contain com.sun.net.httpserver,
 * so the JDK HttpServer cannot be referenced from test sources.
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
    fun `invalid address without scheme throws friendly error`() {
        val bad = config().copy(baseUrl = "192.168.1.5:5005")
        val e = assertThrows(WebDavException::class.java) { WebDavClient.listBackups(bad) }
        assertTrue(e.message!!.contains("scheme", ignoreCase = true))
    }
}

// -------------------------------------------- minimal HTTP/1.1 mock server

/** Hand-rolled mock WebDAV server: enough HTTP/1.1 to answer PROPFIND, PUT,
 *  GET and MKCOL with Basic auth, plus a realistic 207 multistatus body
 *  (status element AFTER prop, one 404 propstat per entry — the shape real
 *  wsgidav servers produce). */
private class MockDavServer(private val user: String, private val pass: String) : Closeable {

    private val serverSocket = ServerSocket(0)
    val port: Int get() = serverSocket.localPort
    private val stored = mutableMapOf<String, ByteArray>()
    @Volatile private var running = true

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
                // per-connection failures are fine in a mock
            }
        }
    }

    private fun handle(socket: Socket) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 3) return
        val method = parts[0]
        val path = parts[1]

        var contentLength = 0
        var authOk = false
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
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
            "PROPFIND" -> writeBytes(output, 207, multistatus().toByteArray(Charsets.UTF_8))
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
        val reason = when (code) {
            200 -> "OK"
            201 -> "Created"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> ""
        }
        write(output, "HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
    }

    private fun writeBytes(output: OutputStream, code: Int, bytes: ByteArray) {
        val reason = when (code) {
            200 -> "OK"
            207 -> "Multi-Status"
            else -> ""
        }
        write(output, "HTTP/1.1 $code $reason\r\nContent-Length: ${bytes.size}\r\n" +
            "Content-Type: application/xml; charset=utf-8\r\nConnection: close\r\n\r\n")
        output.write(bytes)
        output.flush()
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
