package com.tervyn.vpn.net

import com.tervyn.vpn.core.ProxyList
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

class ProxyException(message: String, val authFailure: Boolean = false) : IOException(message)

/**
 * Opens a TCP tunnel to `targetHost:targetPort` through an HTTP CONNECT or SOCKS5 proxy.
 *
 * Domain names are passed to the proxy unresolved (SOCKS5 ATYP=DOMAIN / CONNECT host:port), so DNS
 * resolution happens on the proxy side and never leaks to the local network.
 */
object ProxyDialer {

    /** Hook used by the VPN service to `protect()` sockets; defaults to a plain socket. */
    fun interface SocketFactory { fun create(): Socket }

    fun connect(
        proxy: ProxyEndpoint,
        targetHost: String,
        targetPort: Int,
        connectTimeoutMs: Int = 10_000,
        handshakeTimeoutMs: Int = 10_000,
        factory: SocketFactory = SocketFactory { Socket() },
    ): Socket {
        val socket = factory.create()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(proxy.host, proxy.port), connectTimeoutMs)
            socket.soTimeout = handshakeTimeoutMs
            when (proxy.protocol) {
                ProxyProtocol.SOCKS5 -> socks5Handshake(socket, proxy, targetHost, targetPort)
                ProxyProtocol.HTTP -> httpConnect(socket, proxy, targetHost, targetPort)
            }
            socket.soTimeout = 0
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw if (e is IOException) e else IOException(e.message, e)
        }
    }

    // ---- SOCKS5 (RFC 1928 / RFC 1929) -------------------------------------------------------

    fun socks5Handshake(socket: Socket, proxy: ProxyEndpoint, host: String, port: Int) {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()
        val methods = if (proxy.hasAuth) byteArrayOf(0x00, 0x02) else byteArrayOf(0x00)
        out.write(byteArrayOf(0x05, methods.size.toByte()) + methods)
        out.flush()
        val sel = readFully(inp, 2)
        if (sel[0].toInt() != 0x05) throw ProxyException("Not a SOCKS5 proxy")
        when (sel[1].toInt() and 0xFF) {
            0x00 -> Unit
            0x02 -> {
                if (!proxy.hasAuth) throw ProxyException("Proxy requires authentication", authFailure = true)
                val u = proxy.username!!.toByteArray(Charsets.UTF_8)
                val p = (proxy.password ?: "").toByteArray(Charsets.UTF_8)
                if (u.size > 255 || p.size > 255) throw ProxyException("Credentials too long")
                out.write(byteArrayOf(0x01, u.size.toByte()) + u + byteArrayOf(p.size.toByte()) + p)
                out.flush()
                val r = readFully(inp, 2)
                if (r[1].toInt() != 0x00) throw ProxyException("SOCKS5 authentication failed", authFailure = true)
            }
            0xFF -> throw ProxyException("SOCKS5 proxy rejected all auth methods", authFailure = proxy.hasAuth)
            else -> throw ProxyException("Unsupported SOCKS5 auth method")
        }
        out.write(buildSocks5Request(0x01, host, port))
        out.flush()
        val head = readFully(inp, 4)
        if (head[0].toInt() != 0x05) throw ProxyException("Bad SOCKS5 reply")
        val rep = head[1].toInt() and 0xFF
        if (rep != 0) throw ProxyException("SOCKS5 connect failed: ${socks5Error(rep)}")
        // Skip BND.ADDR / BND.PORT
        when (head[3].toInt()) {
            0x01 -> readFully(inp, 4 + 2)
            0x04 -> readFully(inp, 16 + 2)
            0x03 -> { val len = inp.read(); if (len < 0) throw EOFException(); readFully(inp, len + 2) }
            else -> throw ProxyException("Bad SOCKS5 address type")
        }
    }

    fun buildSocks5Request(cmd: Int, host: String, port: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        buf.write(byteArrayOf(0x05, cmd.toByte(), 0x00))
        when {
            ProxyList.isIpv4(host) -> { buf.write(0x01); buf.write(InetAddress.getByName(host).address) }
            ProxyList.isIpv6(host) -> { buf.write(0x04); buf.write(InetAddress.getByName(host).address) }
            else -> {
                val b = host.toByteArray(Charsets.US_ASCII)
                require(b.size in 1..255) { "Host name too long" }
                buf.write(0x03); buf.write(b.size); buf.write(b)
            }
        }
        buf.write((port shr 8) and 0xFF); buf.write(port and 0xFF)
        return buf.toByteArray()
    }

    fun socks5Error(code: Int) = when (code) {
        1 -> "general failure"; 2 -> "not allowed by ruleset"; 3 -> "network unreachable"
        4 -> "host unreachable"; 5 -> "connection refused"; 6 -> "TTL expired"
        7 -> "command not supported"; 8 -> "address type not supported"; else -> "error $code"
    }

    // ---- HTTP CONNECT (RFC 9110 §9.3.6) --------------------------------------------------------

    fun httpConnect(socket: Socket, proxy: ProxyEndpoint, host: String, port: Int) {
        val authority = if (host.contains(':')) "[$host]:$port" else "$host:$port"
        val req = StringBuilder()
            .append("CONNECT ").append(authority).append(" HTTP/1.1\r\n")
            .append("Host: ").append(authority).append("\r\n")
            .append("Proxy-Connection: keep-alive\r\n")
        if (proxy.hasAuth) {
            val token = Base64.getEncoder().encodeToString("${proxy.username}:${proxy.password ?: ""}".toByteArray(Charsets.UTF_8))
            req.append("Proxy-Authorization: Basic ").append(token).append("\r\n")
        }
        req.append("\r\n")
        socket.getOutputStream().apply { write(req.toString().toByteArray(Charsets.ISO_8859_1)); flush() }
        val head = readHeaderBlock(socket.getInputStream())
        val statusLine = head.substringBefore("\r\n")
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: throw ProxyException("Bad HTTP proxy reply")
        when {
            code in 200..299 -> Unit
            code == 407 -> throw ProxyException("HTTP proxy authentication required/failed", authFailure = true)
            else -> throw ProxyException("HTTP proxy CONNECT failed: $statusLine")
        }
    }

    /** Reads up to and including CRLFCRLF byte-by-byte so no tunnelled bytes are consumed. */
    fun readHeaderBlock(inp: InputStream, max: Int = 16 * 1024): String {
        val sb = StringBuilder()
        var state = 0
        while (sb.length < max) {
            val b = inp.read()
            if (b < 0) throw EOFException("Proxy closed connection")
            sb.append(b.toChar())
            state = when {
                b == '\r'.code && (state == 0 || state == 2) -> state + 1
                b == '\n'.code && (state == 1 || state == 3) -> state + 1
                else -> 0
            }
            if (state == 4) return sb.toString()
        }
        throw ProxyException("Proxy response headers too large")
    }

    fun readFully(inp: InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = inp.read(b, off, n - off)
            if (r < 0) throw EOFException("Proxy closed connection")
            off += r
        }
        return b
    }
}
