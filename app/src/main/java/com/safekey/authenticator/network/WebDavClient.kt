package com.safekey.authenticator.network

import com.safekey.authenticator.data.WebDavServerConfig
import com.safekey.authenticator.security.AppLog
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import javax.net.ssl.SSLException

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
 * Minimal WebDAV client for the backup feature.
 *
 * Uses only the JDK HttpURLConnection stack (zero dependencies, no OkHttp):
 * the Android platform already ships a solid HTTP client underneath it, and
 * keeping the dependency tree empty matters for an authenticator app.
 *
 * PROPFIND / MKCOL are not in HttpURLConnection's whitelist, so the request
 * method is set via the well-known reflection fallback (the `method` field is
 * part of the public-in-practice HttpURLConnection contract and is the same
 * technique used by every Android WebDAV library).
 *
 * Security notes:
 *  - HTTPS uses standard certificate validation. Self-signed NAS certificates
 *    are rejected on purpose — a password vault app must never install a
 *    TrustAllManager. Use HTTP on a trusted LAN, or a certificate the device
 *    trusts.
 *  - Server responses are size-capped (list 1 MiB, download 64 MiB) so a
 *    misbehaving server cannot exhaust memory.
 *  - Credentials are never logged.
 */
object WebDavClient {

    const val BACKUP_DIR = "osmium"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_LIST_BYTES = 1_048_576            // 1 MiB
    private const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024 // 64 MiB

    private val PROPFIND_BODY = (
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<d:propfind xmlns:d=\"DAV:\">" +
            "<d:prop><d:getcontentlength/><d:getlastmodified/><d:displayname/></d:prop>" +
            "</d:propfind>"
        ).toByteArray(Charsets.UTF_8)

    // ------------------------------------------------------------------ API

    /** PROPFIND depth 0 on the base URL. Throws [WebDavException] with a
     *  human-readable reason on any failure. */
    fun testConnection(config: WebDavServerConfig) {
        execute("PROPFIND", url(base(config)), config, requestBody = PROPFIND_BODY,
            extraHeaders = mapOf("Depth" to "0"), allowStatus = 200..299)
    }

    /** List password-encrypted backup files under <base>/osmium/, newest first. */
    fun listBackups(config: WebDavServerConfig): List<WebDavFile> {
        val dirUrl = url(dir(config))
        val body = execute("PROPFIND", dirUrl, config, requestBody = PROPFIND_BODY,
            extraHeaders = mapOf("Depth" to "1"), allowStatus = 200..299)
        if (body.size > MAX_LIST_BYTES) {
            throw WebDavException("Server response too large")
        }
        return parseMultistatus(body)
            .filter { it.name.endsWith(".json", ignoreCase = true) }
            .sortedByDescending { it.name } // timestamped names sort chronologically
    }

    /** Upload an encrypted backup file. Creates the directory on first use. */
    fun upload(config: WebDavServerConfig, fileName: String, bytes: ByteArray) {
        // MKCOL the directory; 405/409/301 mean "already exists", which is fine.
        try {
            execute("MKCOL", url(dir(config)), config, requestBody = ByteArray(0),
                allowStatus = 200..499)
        } catch (e: WebDavException) {
            AppLog.d("webdav: MKCOL reported: ${e.message}")
        }
        execute("PUT", url("${dir(config)}/$fileName"), config, requestBody = bytes,
            allowStatus = 200..299)
    }

    /** Download one backup file. Returns the raw encrypted envelope bytes. */
    fun download(config: WebDavServerConfig, href: String): ByteArray {
        val absolute = absoluteHref(base(config), href)
        val bytes = execute("GET", url(absolute), config, allowStatus = 200..299)
        if (bytes.size > MAX_DOWNLOAD_BYTES) {
            throw WebDavException("Server response too large")
        }
        return bytes
    }

    /** Delete one backup file from the server (204/200 expected). */
    fun delete(config: WebDavServerConfig, href: String) {
        val absolute = absoluteHref(base(config), href)
        execute("DELETE", url(absolute), config, allowStatus = 200..299)
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
        return parsed
    }

    private fun absoluteHref(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val base = URL(baseUrl)
        val root = "${base.protocol}://${base.authority}"
        return if (href.startsWith("/")) root + href
        else "$baseUrl/$href"
    }

    /** Run a request and return the response body (empty on errors that still
     *  fall inside [allowStatus]). Non-allowed statuses and I/O failures throw
     *  [WebDavException] with a friendly message. */
    private fun execute(
        method: String,
        url: URL,
        config: WebDavServerConfig,
        requestBody: ByteArray? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        allowStatus: IntRange
    ): ByteArray {
        AppLog.d("webdav: $method ${url.host}:${url.port}${url.path}")
        val conn = try {
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setMethod(this, method)
                if (config.username.isNotBlank()) {
                    val token = Base64.getEncoder()
                        .encodeToString("${config.username}:${config.password}".toByteArray(Charsets.UTF_8))
                    setRequestProperty("Authorization", "Basic $token")
                }
                extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                if (requestBody != null) {
                    doOutput = true
                    setFixedLengthStreamingMode(requestBody.size)
                    setRequestProperty("Content-Type", "application/xml")
                }
            }
        } catch (e: IOException) {
            throw WebDavException("Invalid server address", e)
        }

        try {
            if (requestBody != null) {
                conn.outputStream.use { it.write(requestBody) }
            }
            val status = conn.responseCode
            if (status !in allowStatus) {
                throw WebDavException(httpError(status))
            }
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            return if (stream == null) ByteArray(0) else stream.use { it.readBytes() }
        } catch (e: WebDavException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw WebDavException("The server timed out", e)
        } catch (e: UnknownHostException) {
            throw WebDavException("Cannot resolve the server address", e)
        } catch (e: ConnectException) {
            throw WebDavException("The server is unreachable", e)
        } catch (e: SSLException) {
            throw WebDavException("TLS error — the server certificate is not trusted", e)
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
        } finally {
            conn.disconnect()
        }
    }

    private fun httpError(status: Int): String = when (status) {
        401 -> "Wrong username or password (401)"
        403 -> "Access denied by the server (403)"
        404 -> "Not found on the server (404)"
        507 -> "Not enough space on the server (507)"
        else -> "Server error (HTTP $status)"
    }

    /** PROPFIND/MKCOL bypass the request-method whitelist via reflection. */
    private fun setMethod(conn: HttpURLConnection, method: String) {
        try {
            conn.requestMethod = method
        } catch (e: ProtocolException) {
            try {
                val field = HttpURLConnection::class.java.getDeclaredField("method")
                field.isAccessible = true
                field.set(conn, method)
            } catch (reflective: Exception) {
                throw WebDavException("Unsupported HTTP method", reflective)
            }
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
