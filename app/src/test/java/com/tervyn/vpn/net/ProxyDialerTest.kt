package com.tervyn.vpn.net

import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.testutil.EchoServer
import com.tervyn.vpn.testutil.MockProxy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProxyDialerTest {
    private val echo = EchoServer()
    private val closeables = mutableListOf<AutoCloseable>(echo)
    @After fun tearDown() = closeables.forEach { it.close() }

    private fun roundTrip(ep: ProxyEndpoint, host: String) {
        ProxyDialer.connect(ep, host, 443).use { s ->
            s.getOutputStream().write("ping".toByteArray())
            val b = ByteArray(4); var off = 0
            while (off < 4) off += s.getInputStream().read(b, off, 4 - off)
            assertArrayEquals("ping".toByteArray(), b)
        }
    }

    @Test fun socks5NoAuthDomainTarget() {
        val p = MockProxy(true) { echo.port }.also { closeables += it }
        roundTrip(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", p.port), "www.example.com")
        assertEquals("www.example.com:443", p.targets.single()) // domain sent unresolved: no local DNS leak
    }

    @Test fun socks5Auth() {
        val p = MockProxy(true, "user", "pass") { echo.port }.also { closeables += it }
        roundTrip(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", p.port, "user", "pass"), "1.2.3.4")
        assertEquals("1.2.3.4:443", p.targets.single())
    }

    @Test fun socks5BadAuth() {
        val p = MockProxy(true, "user", "pass") { echo.port }.also { closeables += it }
        try { ProxyDialer.connect(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", p.port, "user", "nope"), "h.example", 80); fail() }
        catch (e: ProxyException) { assertTrue(e.authFailure) }
        try { ProxyDialer.connect(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", p.port), "h.example", 80); fail() }
        catch (e: ProxyException) { assertTrue(e.message!!.contains("auth")) }
    }

    @Test fun httpConnectAuth() {
        val p = MockProxy(false, "u", "p:w") { echo.port }.also { closeables += it }
        roundTrip(ProxyEndpoint(ProxyProtocol.HTTP, "127.0.0.1", p.port, "u", "p:w"), "api.example.org")
        assertEquals("api.example.org:443", p.targets.single())
    }

    @Test fun httpConnectBadAuth() {
        val p = MockProxy(false, "u", "p") { echo.port }.also { closeables += it }
        try { ProxyDialer.connect(ProxyEndpoint(ProxyProtocol.HTTP, "127.0.0.1", p.port, "u", "x"), "h.example", 443); fail() }
        catch (e: ProxyException) { assertTrue(e.authFailure) }
    }

    @Test fun socks5RequestEncoding() {
        assertArrayEquals(byteArrayOf(5, 1, 0, 1, 10, 0, 0, 1, 0x01, 0xBB.toByte()), ProxyDialer.buildSocks5Request(1, "10.0.0.1", 443))
        val d = ProxyDialer.buildSocks5Request(1, "a.io", 80)
        assertEquals(3, d[3].toInt()); assertEquals(4, d[4].toInt())
    }

    @Test fun refusedProxyFailsFast() {
        val start = System.currentTimeMillis()
        val port = java.net.ServerSocket(0).use { it.localPort }
        assertTrue(runCatching { ProxyDialer.connect(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", port), "h.example", 1, 2000) }.isFailure)
        assertTrue(System.currentTimeMillis() - start < 3000)
    }
}
