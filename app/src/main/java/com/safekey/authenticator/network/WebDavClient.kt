package com.safekey.authenticator.network

import com.safekey.authenticator.data.WebDavServerConfig
import com.safekey.authenticator.security.AppLog
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A failure talking to the WebDAV server, with a user-presentable message. */
class WebDavException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A backup file discovered on the server. */
data class WebDavFile(
    val name: String,
    val href: String,
    val size: Long,
    val lastModified: Long
)

/**
 * Minimal WebDAV client for the backup feature: a self-contained HTTP/1.1
 * client over [Socket] / [SSLSocket] (zero dependencies, no OkHttp).
 *
 * Why this is no longer built on HttpURLConnection: PROPFIND and MKCOL are
 * outside the platform's request-method whitelist (GET, POST, HEAD, OPTIONS,
 * PUT, DELETE, TRACE, PATCH), so `setRequestMethod` throws ProtocolException.
 * The usual workaround — writing the protected `HttpURLConnection.method` field
 * by reflection — only takes effect for http:// URLs. For https:// the object
 * returned by `openConnection()` is a delegating wrapper
 * (`com.android.okhttp.internal.huc.HttpsURLConnectionImpl`) whose
 * get/setRequestMethod forward to a private delegate, so the reflected field is
 * never read; and because the request carries a body, the delegate then sends
 * POST instead of PROPFIND/MKCOL. Every https WebDAV server answered 501/405,
 * so the feature only ever worked against plain-http LAN servers. Speaking
 * HTTP ourselves removes the reflection and that whole class of failures.
 *
 * Security notes:
 *  - HTTPS uses the platform default socket factory (system trust anchors) and
 *    the platform hostname verifier. Self-signed NAS certificates are rejected
 *    on purpose — a password vault must never install a TrustAllManager. Use
 *    HTTP on a trusted LAN, or a certificate the device trusts.
 *  - Credentials are sent only to the configured host — never to a redirect
 *    that points somewhere else.
 *  - Responses are size-capped (list 1 MiB, download 64 MiB) and header lines
 *    are length-capped, so a misbehaving server cannot exhaust memory.
 *  - Credentials are never logged.
 */
object WebDavClient {

    const val BACKUP_DIR = "osmium"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_LIST_BYTES = 1_048_576L           // 1 MiB
    private const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024 // 64 MiB
    private const val MAX_REDIRECTS = 3
    private const val MAX_LINE_BYTES = 8 * 1024              // one status/header line
    private const val MAX_HEADER_BYTES = 64 * 1024           // all header lines

    private const val CONTENT_TYPE_XML = "application/xml"
    private const val CONTENT_TYPE_BINARY = "application/octet-stream"

    /** Followed manually so credentials never reach a third host. */
    private val REDIRECT_CODES = setOf(301, 302, 307, 308)

    private val PROPFIND_BODY = (
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<d:propfind xmlns:d=\"DAV:\">" +
            "<d:prop><d:getcontentlength/><d:getlastmodified/><d:displayname/></d:prop>" +
            "</d:propfind>"
        ).toByteArray(Charsets.UTF_8)

    private val HEX = "0123456789ABCDEF".toCharArray()

    /**
     * TLS knobs. The app always uses [DEFAULT] — the platform trust anchors.
     * It is injectable only so the unit tests can talk to a local server with a
     * test certificate; it is internal to this module and no production call
     * site passes anything else.
     *
     * Host name verification is not a separate step: the socket is created with
     * [SSLParameters.endpointIdentificationAlgorithm] = "HTTPS", which both the
     * JDK and Conscrypt (Android) enforce inside the handshake — Conscrypt's
     * TrustManagerImpl runs the platform host name check when that algorithm is
     * set. Doing it that way keeps production and the JVM tests on exactly the
     * same enforcement path; HttpsURLConnection's default HostnameVerifier, by
     * contrast, is a stub that rejects everything outside an HttpsURLConnection.
     */
    internal class Tls(val socketFactory: SSLSocketFactory) {
        companion object {
            val DEFAULT = Tls(HttpsURLConnection.getDefaultSSLSocketFactory())
        }
    }

    // ------------------------------------------------------------------ API

    /** PROPFIND depth 0 on the base URL. Throws [WebDavException] with a
     *  human-readable reason on any failure. */
    fun testConnection(config: WebDavServerConfig) = testConnection(config, Tls.DEFAULT)

    internal fun testConnection(config: WebDavServerConfig, tls: Tls) {
        execute("PROPFIND", url(base(config)), config, requestBody = PROPFIND_BODY,
            extraHeaders = mapOf("Depth" to "0"), allowStatus = 200..299, tls = tls)
    }

    /** List password-encrypted backup files under <base>/osmium/, newest first. */
    fun listBackups(config: WebDavServerConfig): List<WebDavFile> =
        listBackups(config, Tls.DEFAULT)

    internal fun listBackups(config: WebDavServerConfig, tls: Tls): List<WebDavFile> {
        val body = execute("PROPFIND", url(dir(config)), config, requestBody = PROPFIND_BODY,
            extraHeaders = mapOf("Depth" to "1"), allowStatus = 200..299,
            maxBytes = MAX_LIST_BYTES, tls = tls)
        return parseMultistatus(body)
            .filter { it.name.endsWith(".json", ignoreCase = true) }
            .sortedByDescending { it.name } // timestamped names sort chronologically
    }

    /** Upload an encrypted backup file. Creates the directory on first use. */
    fun upload(config: WebDavServerConfig, fileName: String, bytes: ByteArray) =
        upload(config, fileName, bytes, Tls.DEFAULT)

    internal fun upload(config: WebDavServerConfig, fileName: String, bytes: ByteArray, tls: Tls) {
        // MKCOL the directory. 405/409 mean "already exists", which is the
        // normal answer on every upload after the first one and is ignored
        // below. A 401/403 must NOT be swallowed here: the PUT that follows
        // would then report a confusing 409 instead of the real reason.
        try {
            execute("MKCOL", url(dir(config)), config, requestBody = ByteArray(0),
                allowStatus = 200..299, tls = tls)
        } catch (e: WebDavException) {
            AppLog.d("webdav: MKCOL reported: ${e.message}")
        }
        execute("PUT", url("${dir(config)}/$fileName"), config, requestBody = bytes,
            contentType = CONTENT_TYPE_BINARY, allowStatus = 200..299, tls = tls)
    }

    /** Download one backup file. Returns the raw encrypted envelope bytes. */
    fun download(config: WebDavServerConfig, href: String) = download(config, href, Tls.DEFAULT)

    internal fun download(config: WebDavServerConfig, href: String, tls: Tls): ByteArray {
        val absolute = absoluteHref(base(config), href)
        return execute("GET", url(absolute), config, allowStatus = 200..299,
            maxBytes = MAX_DOWNLOAD_BYTES, tls = tls)
    }

    /** Delete one backup file from the server (204/200 expected). */
    fun delete(config: WebDavServerConfig, href: String) = delete(config, href, Tls.DEFAULT)

    internal fun delete(config: WebDavServerConfig, href: String, tls: Tls) {
        val absolute = absoluteHref(base(config), href)
        execute("DELETE", url(absolute), config, allowStatus = 200..299, tls = tls)
    }

    // ------------------------------------------------------------ plumbing

    private fun base(config: WebDavServerConfig): String =
        config.baseUrl.trim().trimEnd('/')

    private fun dir(config: WebDavServerConfig): String = "${base(config)}/$BACKUP_DIR"

    private fun url(path: String): URL {
        val parsed = try {
            URL(path)
        } catch (e: java.net.MalformedURLException) {
            throw WebDavException(
                "Invalid server address — include the scheme, e.g. http://192.168.1.5:5005", e)
        }
        if (parsed.protocol != "http" && parsed.protocol != "https") {
            throw WebDavException("Invalid server address — only http:// and https:// are supported")
        }
        if (parsed.host.isNullOrEmpty()) {
            throw WebDavException("Invalid server address — no host name")
        }
        return parsed
    }

    private fun absoluteHref(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            // A server-supplied absolute URL used to be fetched as-is, with the
            // Basic auth header attached — a hostile or compromised server (or
            // anyone MITM-ing the plain-http LAN setup this client supports)
            // could name its own host in <href> and collect the NAS password.
            val absolute = url(href)
            if (!sameHost(absolute, baseUrl)) {
                throw WebDavException(
                    "The server returned a link to another host — refusing to send the backup credentials"
                )
            }
            return absolute.toExternalForm()
        }
        val base = URL(baseUrl)
        val root = "${base.protocol}://${base.authority}"
        return if (href.startsWith("/")) root + href
        else "$baseUrl/$href"
    }

    /** True when [url] points at the same host as the configured [baseUrl]. */
    private fun sameHost(url: URL, baseUrl: String): Boolean =
        try {
            url.host.equals(URL(baseUrl).host, ignoreCase = true)
        } catch (_: Exception) {
            false
        }

    /**
     * Run one request, following same-host redirects, and return the response
     * body. Non-allowed statuses and I/O failures throw [WebDavException] with
     * a friendly message.
     */
    private fun execute(
        method: String,
        url: URL,
        config: WebDavServerConfig,
        requestBody: ByteArray? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        contentType: String = CONTENT_TYPE_XML,
        allowStatus: IntRange,
        maxBytes: Long = MAX_DOWNLOAD_BYTES,
        tls: Tls
    ): ByteArray {
        var target = url
        var hops = 0
        while (true) {
            AppLog.d("webdav: $method ${target.host}:${target.port}${target.path}")
            // Credentials go out only towards the host the user configured.
            val trusted = sameHost(target, base(config))
            val response = try {
                roundTrip(method, target, config, trusted, requestBody, extraHeaders,
                    contentType, maxBytes, tls)
            } catch (e: WebDavException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw WebDavException("The server timed out", e)
            } catch (e: UnknownHostException) {
                throw WebDavException("Cannot resolve the server address", e)
            } catch (e: ConnectException) {
                throw WebDavException("The server is unreachable", e)
            } catch (e: SSLException) {
                throw WebDavException(
                    "TLS error — the server certificate is not trusted " +
                        "or does not match the server name", e)
            } catch (e: IOException) {
                val msg = e.message ?: ""
                // Android 16+ local-network protection returns EPERM/ECONNABORTED
                // when LAN access is blocked (Android 17 enforces it for apps
                // targeting 37+). Surface a hint instead of a raw socket error.
                if (msg.contains("EPERM") || msg.contains("ECONNABORTED")) {
                    throw WebDavException(
                        "Local network access was blocked by the system — " +
                            "check this app's local-network permission", e
                    )
                }
                throw WebDavException("Connection failed: $msg", e)
            }

            if (response.status in REDIRECT_CODES && hops < MAX_REDIRECTS) {
                val location = response.location
                if (location.isNullOrBlank()) {
                    throw WebDavException("The server redirected without a target")
                }
                val next = try {
                    URL(target, location)
                } catch (e: Exception) {
                    throw WebDavException("The server sent an unusable redirect", e)
                }
                if (next.protocol != "http" && next.protocol != "https") {
                    throw WebDavException("The server redirected to an unsupported address")
                }
                if (!sameHost(next, base(config))) {
                    throw WebDavException(
                        "The server redirected to another host — refusing to send the backup credentials"
                    )
                }
                hops++
                target = next
                continue
            }
            if (response.status !in allowStatus) {
                throw WebDavException(httpError(response.status))
            }
            return response.body
        }
    }

    private fun httpError(status: Int): String = when (status) {
        401 -> "Wrong username or password (401)"
        403 -> "Access denied by the server (403)"
        404 -> "Not found on the server (404)"
        405 -> "The server does not allow this operation (405)"
        501 -> "The server does not support this operation (501)"
        507 -> "Not enough space on the server (507)"
        else -> "Server error (HTTP $status)"
    }

    // ------------------------------------------------------------- transport

    private class Response(val status: Int, val location: String?, val body: ByteArray)

    private class Head(val status: Int, val headers: Map<String, String>)

    /** One request/response exchange on a fresh connection. */
    private fun roundTrip(
        method: String,
        target: URL,
        config: WebDavServerConfig,
        sendCredentials: Boolean,
        requestBody: ByteArray?,
        extraHeaders: Map<String, String>,
        contentType: String,
        maxBytes: Long,
        tls: Tls
    ): Response {
        connect(target, tls).use { socket ->
            val output = socket.getOutputStream()
            writeRequest(output, method, target, config, sendCredentials, requestBody,
                extraHeaders, contentType)
            output.flush()

            val input = socket.getInputStream()
            var head = readHead(input)
            // Skip 1xx informational responses (we never send Expect, but a
            // server is allowed to emit them).
            while (head.status in 100..199) head = readHead(input)

            // A redirect body is never used — the connection is closed anyway.
            val body = if (head.status in REDIRECT_CODES) ByteArray(0)
            else readBody(input, head.headers, maxBytes)
            return Response(head.status, head.headers["location"], body)
        }
    }

    /**
     * Opens the TCP connection and, for https, completes the TLS handshake
     * with real certificate *and* host name verification.
     */
    private fun connect(target: URL, tls: Tls): Socket {
        val host = target.host
        val port = port(target)
        val raw = Socket()
        try {
            raw.tcpNoDelay = true
            raw.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            raw.soTimeout = READ_TIMEOUT_MS
            if (target.protocol != "https") return raw
            // Handing the host name to the factory makes the TLS layer send SNI
            // and resume sessions; endpoint identification makes the handshake
            // fail if the certificate is not valid for this name.
            val ssl = tls.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            ssl.soTimeout = READ_TIMEOUT_MS
            val params = ssl.sslParameters
            params.endpointIdentificationAlgorithm = "HTTPS"
            ssl.sslParameters = params
            ssl.startHandshake()
            return ssl
        } catch (e: Throwable) {
            try {
                raw.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun port(target: URL): Int =
        if (target.port != -1) target.port
        else if (target.protocol == "https") 443 else 80

    private fun writeRequest(
        output: OutputStream,
        method: String,
        target: URL,
        config: WebDavServerConfig,
        sendCredentials: Boolean,
        requestBody: ByteArray?,
        extraHeaders: Map<String, String>,
        contentType: String
    ) {
        val head = StringBuilder(256)
        head.append(method).append(' ').append(requestTarget(target)).append(" HTTP/1.1\r\n")
        head.append("Host: ").append(hostHeader(target)).append("\r\n")
        // Some WebDAV front ends reject requests without a user agent.
        head.append("User-Agent: Osmium\r\n")
        head.append("Accept: */*\r\n")
        // One request per connection: no chunked request bodies, no keep-alive.
        head.append("Connection: close\r\n")
        for ((name, value) in extraHeaders) {
            head.append(checkedHeader(name)).append(": ").append(checkedHeader(value)).append("\r\n")
        }
        if (sendCredentials && config.username.isNotBlank()) {
            val token = Base64.getEncoder()
                .encodeToString("${config.username}:${config.password}".toByteArray(Charsets.UTF_8))
            head.append("Authorization: Basic ").append(token).append("\r\n")
        }
        if (requestBody != null) {
            head.append("Content-Length: ").append(requestBody.size).append("\r\n")
            if (requestBody.isNotEmpty()) {
                head.append("Content-Type: ").append(contentType).append("\r\n")
            }
        }
        head.append("\r\n")
        // Everything above is ASCII by construction (requestTarget and
        // checkedHeader see to that), so the byte mapping is unambiguous.
        output.write(head.toString().toByteArray(Charsets.US_ASCII))
        if (requestBody != null && requestBody.isNotEmpty()) output.write(requestBody)
    }

    /** A header name/value must not be able to smuggle in another header. */
    private fun checkedHeader(text: String): String {
        if (text.any { it == '\r' || it == '\n' || it.code == 0 }) {
            throw WebDavException("Invalid server address — control characters are not allowed")
        }
        return text
    }

    private fun hostHeader(target: URL): String {
        val host = checkedHeader(target.host)
        val bracketed = if (host.contains(':')) "[$host]" else host // IPv6 literal
        val defaultPort = if (target.protocol == "https") 443 else 80
        return if (port(target) == defaultPort) bracketed else "$bracketed:${port(target)}"
    }

    /**
     * Request-URI: the raw path (already percent-encoded by whoever built the
     * URL) plus its query. Anything that may not appear verbatim in a request
     * line — controls, space, non-ASCII — is percent-encoded here, so a host or
     * path coming from user input or a redirect can never inject a header or a
     * second request.
     */
    private fun requestTarget(target: URL): String {
        val path = target.path.orEmpty()
        val withSlash = when {
            path.isEmpty() -> "/"
            path.startsWith("/") -> path
            else -> "/$path"
        }
        val raw = withSlash + (target.query?.let { "?$it" } ?: "")
        val sb = StringBuilder(raw.length + 16)
        for (byte in raw.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xff
            // Keep the unreserved/sub-delims set and existing escapes; encode
            // everything else (controls, space, quotes, non-ASCII).
            val keep = c in 0x21..0x7e && c.toChar() !in "\"<>\\^`{|}"
            if (keep) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0f])
            }
        }
        return sb.toString()
    }

    private fun readHead(input: InputStream): Head {
        val statusLine = readAsciiLine(input, MAX_LINE_BYTES)
            ?: throw WebDavException("The server closed the connection without answering")
        val parts = statusLine.split(' ', limit = 3)
        if (parts.size < 2 || !parts[0].startsWith("HTTP/")) {
            throw WebDavException("The server sent an unreadable response")
        }
        val status = parts[1].trim().toIntOrNull()
            ?: throw WebDavException("The server sent an unreadable status code")

        val headers = HashMap<String, String>()
        var total = 0
        while (true) {
            val line = readAsciiLine(input, MAX_LINE_BYTES)
                ?: throw WebDavException("The server closed the connection mid-response")
            if (line.isEmpty()) break
            total += line.length
            if (total > MAX_HEADER_BYTES) throw WebDavException("The server sent oversized headers")
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            val value = line.substring(colon + 1).trim()
            if (name.isNotEmpty() && !headers.containsKey(name)) headers[name] = value
        }
        return Head(status, headers)
    }

    /** Reads one LF- (or CRLF-) terminated line as ISO-8859-1, with a hard cap. */
    private fun readAsciiLine(input: InputStream, max: Int): String? {
        val bytes = ByteArrayOutputStream(64)
        while (true) {
            val b = input.read()
            if (b < 0) {
                return if (bytes.size() == 0) null
                else String(bytes.toByteArray(), Charsets.ISO_8859_1)
            }
            if (b == '\n'.code) {
                return String(bytes.toByteArray(), Charsets.ISO_8859_1).trimEnd('\r')
            }
            if (bytes.size() >= max) throw WebDavException("The server sent an oversized header")
            bytes.write(b)
        }
    }

    /** Reads the response body, honouring Content-Length / chunked framing and
     *  counting the [cap] in bytes while streaming. */
    private fun readBody(input: InputStream, headers: Map<String, String>, cap: Long): ByteArray {
        val transferEncoding = headers["transfer-encoding"].orEmpty().lowercase(Locale.ROOT)
        if (transferEncoding.contains("chunked")) return readChunked(input, cap)
        val declared = headers["content-length"]?.toLongOrNull()
        if (declared != null && declared > cap) throw WebDavException("Server response too large")
        return readToEnd(input, cap, declared ?: -1L)
    }

    private fun readChunked(input: InputStream, cap: Long): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val line = readAsciiLine(input, MAX_LINE_BYTES)
                ?: throw WebDavException("The server closed the connection mid-response")
            val size = line.substringBefore(';').trim().toLongOrNull(16)
                ?: throw WebDavException("The server sent an unreadable response")
            if (size == 0L) {
                // Trailer section: header lines up to the empty line.
                while (true) {
                    val trailer = readAsciiLine(input, MAX_LINE_BYTES) ?: break
                    if (trailer.isEmpty()) break
                }
                return out.toByteArray()
            }
            if (out.size().toLong() + size > cap) throw WebDavException("Server response too large")
            copyExactly(input, out, size)
            readAsciiLine(input, MAX_LINE_BYTES) // CRLF that closes the chunk
        }
    }

    private fun readToEnd(input: InputStream, cap: Long, declared: Long): ByteArray {
        val initial = if (declared in 1..cap) declared.toInt() else 4096
        val out = ByteArrayOutputStream(initial)
        val buffer = ByteArray(16 * 1024)
        var remaining = if (declared >= 0) declared else Long.MAX_VALUE
        while (remaining > 0) {
            val want = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, want)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size().toLong() > cap) throw WebDavException("Server response too large")
            remaining -= read
        }
        return out.toByteArray()
    }

    private fun copyExactly(input: InputStream, out: ByteArrayOutputStream, count: Long) {
        val buffer = ByteArray(16 * 1024)
        var remaining = count
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw WebDavException("The server closed the connection mid-response")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    // ------------------------------------------------------------ parsing

    /** Minimal RFC 4918 multistatus parser (namespace-aware, prefix-agnostic).
     *  Props are buffered per propstat and committed only when that propstat
     *  carries a 200 status — note that in real responses <status> comes
     *  AFTER <prop>, so values cannot be read "in order". */
    private fun parseMultistatus(body: ByteArray): List<WebDavFile> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(body.inputStream(), "UTF-8")

        val files = mutableListOf<WebDavFile>()
        var href: String? = null
        var propstatOk = false
        var pendingSize = -1L
        var pendingModified = 0L
        var size = -1L
        var modified = 0L

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "href" -> if (href == null) href = parser.nextText()
                    "propstat" -> {
                        propstatOk = false
                        pendingSize = -1L
                        pendingModified = 0L
                    }
                    "status" -> propstatOk = parser.nextText().contains("200")
                    "getcontentlength" ->
                        pendingSize = parser.nextText().trim().toLongOrNull() ?: -1L
                    "getlastmodified" ->
                        pendingModified = parseHttpDate(parser.nextText().trim())
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "propstat" -> if (propstatOk) {
                        size = pendingSize
                        modified = pendingModified
                    }
                    "response" -> {
                        val h = href
                        if (h != null) {
                            val name = URLDecoder.decode(h.trimEnd('/').substringAfterLast('/'), "UTF-8")
                            if (name.isNotBlank() && name != BACKUP_DIR) {
                                files.add(WebDavFile(name, h, size, modified))
                            }
                        }
                        href = null
                        propstatOk = false
                        size = -1L
                        modified = 0L
                    }
                }
            }
            event = parser.next()
        }
        return files
    }

    private val httpDateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US)
    )

    private fun parseHttpDate(text: String): Long {
        for (format in httpDateFormats) {
            try {
                return format.parse(text)?.time ?: 0L
            } catch (_: Exception) {
                // try the next format
            }
        }
        return 0L
    }
}
