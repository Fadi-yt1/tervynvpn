package com.tervyn.vpn.net

import com.tervyn.vpn.domain.ProxyEndpoint
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A deliberately small HTTP/1.1 client that runs over a [ProxyDialer] tunnel (or directly).
 *
 * TLS uses the platform trust store and enables RFC 2818 hostname verification via
 * `endpointIdentificationAlgorithm = "HTTPS"`; invalid, expired or mismatched certificates fail the
 * request. There is intentionally no way to disable verification.
 */
class HttpsOverProxy(
    private val sslFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
    private val socketFactory: ProxyDialer.SocketFactory = ProxyDialer.SocketFactory { Socket() },
) {
    data class Response(
        val code: Int,
        val headers: Map<String, String>,
        val body: ByteArray,
        val connectMs: Long,
        val tlsMs: Long,
        val firstByteMs: Long,
        val totalMs: Long,
    ) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    /**
     * @param proxy null for a direct connection.
     * @param onProgress called with received body bytes (used by speed tests).
     */
    fun request(
        url: String,
        proxy: ProxyEndpoint?,
        method: String = "GET",
        body: ByteArray? = null,
        contentType: String? = null,
        timeoutMs: Int = 10_000,
        maxBody: Int = 1 shl 20,
        discardBody: Boolean = false,
        extraHeaders: Map<String, String> = emptyMap(),
        onProgress: ((Long) -> Unit)? = null,
    ): Response {
        val uri = URI(url)
        val https = uri.scheme.equals("https", true)
        val host = uri.host ?: throw IOException("Invalid URL")
        val port = if (uri.port > 0) uri.port else if (https) 443 else 80
        val start = System.nanoTime()
        val raw: Socket = if (proxy != null) {
            ProxyDialer.connect(proxy, host, port, timeoutMs, timeoutMs, socketFactory)
        } else {
            socketFactory.create().apply { connect(InetSocketAddress(host, port), timeoutMs) }
        }
        val connectMs = (System.nanoTime() - start) / 1_000_000
        var sock: Socket = raw
        try {
            raw.soTimeout = timeoutMs
            var tlsMs = 0L
            if (https) {
                val ssl = sslFactory.createSocket(raw, host, port, true) as SSLSocket
                val params = ssl.sslParameters
                params.endpointIdentificationAlgorithm = "HTTPS"
                if (!com.tervyn.vpn.core.ProxyList.isIpv4(host) && !host.contains(':')) params.serverNames = listOf(SNIHostName(host))
                ssl.sslParameters = params
                ssl.soTimeout = timeoutMs
                ssl.startHandshake()
                sock = ssl
                tlsMs = (System.nanoTime() - start) / 1_000_000 - connectMs
            }
            val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
            val hostHeader = if ((https && port == 443) || (!https && port == 80)) host else "$host:$port"
            val req = StringBuilder()
                .append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(hostHeader).append("\r\n")
                .append("User-Agent: TervynVPN/1.0\r\n")
                .append("Accept: */*\r\n")
                .append("Connection: close\r\n")
            extraHeaders.forEach { (k, v) -> req.append(k).append(": ").append(v).append("\r\n") }
            if (body != null) {
                req.append("Content-Length: ").append(body.size).append("\r\n")
                if (contentType != null) req.append("Content-Type: ").append(contentType).append("\r\n")
            }
            req.append("\r\n")
            val out = sock.getOutputStream()
            out.write(req.toString().toByteArray(Charsets.ISO_8859_1))
            if (body != null) {
                var off = 0
                while (off < body.size) {
                    val n = minOf(64 * 1024, body.size - off)
                    out.write(body, off, n); off += n
                }
            }
            out.flush()
            val inp = BufferedInputStream(sock.getInputStream(), 64 * 1024)
            val head = ProxyDialer.readHeaderBlock(inp)
            val firstByteMs = (System.nanoTime() - start) / 1_000_000
            val lines = head.trimEnd().split("\r\n")
            val code = lines.first().split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("Bad HTTP response")
            val headers = lines.drop(1).filter { it.contains(':') }
                .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            val bodyBytes = readBody(inp, headers, maxBody, discardBody, onProgress)
            return Response(code, headers, bodyBytes, connectMs, tlsMs, firstByteMs, (System.nanoTime() - start) / 1_000_000)
        } finally {
            runCatching { sock.close() }
            runCatching { raw.close() }
        }
    }

    private fun readBody(inp: InputStream, headers: Map<String, String>, max: Int, discard: Boolean, progress: ((Long) -> Unit)?): ByteArray {
        val out = ByteArrayOutputStream()
        var total = 0L
        val buf = ByteArray(32 * 1024)
        fun sink(b: ByteArray, n: Int) {
            total += n
            progress?.invoke(total)
            if (!discard) {
                if (out.size() + n > max) throw IOException("Response too large")
                out.write(b, 0, n)
            }
        }
        if (headers["transfer-encoding"]?.contains("chunked", true) == true) {
            while (true) {
                val sizeLine = readLine(inp)
                val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: throw IOException("Bad chunk")
                if (size == 0) { readLine(inp); break }
                var left = size
                while (left > 0) {
                    val n = inp.read(buf, 0, minOf(buf.size, left))
                    if (n < 0) throw IOException("Truncated chunk")
                    sink(buf, n); left -= n
                }
                readLine(inp)
            }
        } else {
            val len = headers["content-length"]?.toLongOrNull()
            var left = len ?: Long.MAX_VALUE
            while (left > 0) {
                val n = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) { if (len != null) throw IOException("Truncated body"); break }
                sink(buf, n); left -= n
            }
        }
        return out.toByteArray()
    }

    private fun readLine(inp: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = inp.read()
            if (b < 0) throw IOException("EOF")
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) throw IOException("Line too long")
        }
    }
}
