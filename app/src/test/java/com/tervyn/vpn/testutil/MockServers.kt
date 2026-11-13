package com.tervyn.vpn.testutil

import java.io.Closeable
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Echo server: writes back everything it receives; closes its side after the peer half-closes. */
class EchoServer : Closeable {
    val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    val port get() = server.localPort
    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    s.use {
                        val buf = ByteArray(8192)
                        val inp = it.getInputStream(); val out = it.getOutputStream()
                        while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                        it.shutdownOutput()
                    }
                }
            }
        }
    }
    override fun close() = server.close()
}

/** Bulk server: sends [size] bytes of a deterministic pattern, then closes. */
class BulkServer(private val size: Int) : Closeable {
    val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    val port get() = server.localPort
    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    s.use { val out = it.getOutputStream(); out.write(pattern(size)); out.flush() }
                }
            }
        }
    }
    override fun close() = server.close()
    companion object { fun pattern(n: Int) = ByteArray(n) { (it * 31 + 7).toByte() } }
}

/**
 * Minimal SOCKS5 / HTTP CONNECT proxy for tests. Records requested targets. Connects every request
 * to [redirectPort] on loopback (so tests can use fake hostnames).
 */
class MockProxy(
    val socks: Boolean,
    private val user: String? = null,
    private val pass: String? = null,
    private val redirectPort: () -> Int,
) : Closeable {
    val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    val port get() = server.localPort
    val targets = CopyOnWriteArrayList<String>()
    val accepted = AtomicInteger()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val c = runCatching { server.accept() }.getOrNull() ?: break
                accepted.incrementAndGet()
                thread(isDaemon = true) { runCatching { if (socks) handleSocks(c) else handleHttp(c) }; }
            }
        }
    }

    private fun relay(c: Socket, target: String) {
        targets += target
        val up = Socket()
        up.connect(InetSocketAddress("127.0.0.1", redirectPort()), 3000)
        thread(isDaemon = true) {
            runCatching { c.getInputStream().copyTo(up.getOutputStream()) }
            runCatching { up.shutdownOutput() }
        }
        runCatching { up.getInputStream().copyTo(c.getOutputStream()) }
        runCatching { c.shutdownOutput() }
        Thread.sleep(200)
        up.close(); c.close()
    }

    private fun handleSocks(c: Socket) {
        val inp = DataInputStream(c.getInputStream()); val out = c.getOutputStream()
        require(inp.read() == 5)
        val methods = ByteArray(inp.read()).also { inp.readFully(it) }
        if (user != null) {
            if (!methods.contains(2)) { out.write(byteArrayOf(5, 0xFF.toByte())); c.close(); return }
            out.write(byteArrayOf(5, 2))
            require(inp.read() == 1)
            val u = String(ByteArray(inp.read()).also { inp.readFully(it) })
            val p = String(ByteArray(inp.read()).also { inp.readFully(it) })
            if (u != user || p != pass) { out.write(byteArrayOf(1, 1)); c.close(); return }
            out.write(byteArrayOf(1, 0))
        } else out.write(byteArrayOf(5, 0))
        require(inp.read() == 5); val cmd = inp.read(); inp.read()
        val host = when (inp.read()) {
            1 -> ByteArray(4).also { inp.readFully(it) }.joinToString(".") { (it.toInt() and 255).toString() }
            3 -> String(ByteArray(inp.read()).also { inp.readFully(it) })
            else -> error("atyp")
        }
        val port = inp.readUnsignedShort()
        if (cmd != 1) { out.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0)); c.close(); return }
        out.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
        relay(c, "$host:$port")
    }

    private fun handleHttp(c: Socket) {
        val head = com.tervyn.vpn.net.ProxyDialer.readHeaderBlock(c.getInputStream())
        val lines = head.split("\r\n")
        val target = lines[0].split(' ')[1]
        if (user != null) {
            val expected = "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
            val auth = lines.firstOrNull { it.startsWith("Proxy-Authorization:", true) }?.substringAfter(':')?.trim()
            if (auth != expected) {
                c.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\nContent-Length: 0\r\n\r\n".toByteArray()); c.close(); return
            }
        }
        c.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
        relay(c, target)
    }

    override fun close() = server.close()
}
