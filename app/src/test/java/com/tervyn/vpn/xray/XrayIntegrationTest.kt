package com.tervyn.vpn.xray

import com.tervyn.vpn.core.Links
import com.tervyn.vpn.core.XrayOutbound
import com.tervyn.vpn.core.XrayProtocol
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.net.ProxyDialer
import com.tervyn.vpn.net.ProxyException
import com.tervyn.vpn.testutil.EchoServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.Base64

/**
 * End-to-end through real Xray-core v26.9.30 processes: an Xray *server* on loopback and Tervyn's
 * client-side [XrayProcess] exposing an authenticated local SOCKS5 endpoint used via [ProxyDialer].
 */
class XrayIntegrationTest {
    private val bin = File(System.getProperty("xray.host.binary") ?: "../xray/host/xray")
    private val uuid = "5783a3e7-e373-51cd-8642-c83782b807c5"
    private lateinit var work: File
    private val procs = mutableListOf<Process>()
    private val echo = EchoServer()

    @Before fun setUp() {
        assumeTrue("host xray binary not available", bin.canExecute())
        work = kotlin.io.path.createTempDirectory("xrayit").toFile()
    }

    @After fun tearDown() {
        procs.forEach { it.destroyForcibly() }
        echo.close()
        if (::work.isInitialized) work.deleteRecursively()
    }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun startServer(inbound: String): Int {
        val port = freePort()
        val cfg = File(work, "server-$port.json")
        cfg.writeText("""{"log":{"loglevel":"warning"},"inbounds":[${inbound.replace("PORT", port.toString())}],"outbounds":[{"protocol":"freedom","settings":{"finalRules":[{"action":"allow","ip":["127.0.0.0/8"]}]}}]}""")
        procs += ProcessBuilder(bin.absolutePath, "run", "-c", cfg.absolutePath).redirectErrorStream(true).redirectOutput(File(work, "server.log")).start()
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { java.net.Socket("127.0.0.1", port).close() }.isSuccess) return port
            Thread.sleep(100)
        }
        error("server did not start: " + File(work, "server.log").readText())
    }

    private fun roundTrip(ep: ProxyEndpoint) {
        ProxyDialer.connect(ep, "127.0.0.1", echo.port, 5000, 5000).use { s ->
            s.soTimeout = 10_000
            val msg = "hello through xray".toByteArray()
            s.getOutputStream().write(msg)
            val buf = ByteArray(msg.size); var off = 0
            while (off < msg.size) { val n = s.getInputStream().read(buf, off, msg.size - off); if (n < 0) break; off += n }
            assertArrayEquals(msg, buf)
        }
    }

    @Test fun vlessThroughLocalSocks() = runBlocking {
        val port = startServer("""{"listen":"127.0.0.1","port":PORT,"protocol":"vless","settings":{"clients":[{"id":"$uuid"}],"decryption":"none"}}""")
        val link = "vless://$uuid@127.0.0.1:$port?encryption=none&type=tcp#it"
        val p = XrayProcess.start(bin, work, Links.parse(link))
        try {
            assertEquals("127.0.0.1", p.endpoint.host)
            assertTrue(p.endpoint.hasAuth && p.endpoint.password!!.length >= 20)
            roundTrip(p.endpoint)
            // The local endpoint must refuse clients without the random password.
            val wrong = runCatching { ProxyDialer.connect(p.endpoint.copy(password = "nope"), "127.0.0.1", echo.port, 3000, 3000) }.exceptionOrNull()
            assertTrue(wrong is ProxyException && wrong.authFailure)
            assertTrue(work.listFiles()!!.none { it.name.startsWith("xray-") }) // config with credentials was deleted
        } finally { p.stop() }
    }

    @Test fun shadowsocksThroughLocalSocks() = runBlocking {
        val port = startServer("""{"listen":"127.0.0.1","port":PORT,"protocol":"shadowsocks","settings":{"method":"aes-256-gcm","password":"s3cret","network":"tcp"}}""")
        val ui = Base64.getUrlEncoder().withoutPadding().encodeToString("aes-256-gcm:s3cret".toByteArray())
        val p = XrayProcess.start(bin, work, Links.parse("ss://$ui@127.0.0.1:$port#ss"))
        try { roundTrip(p.endpoint) } finally { p.stop() }
    }

    @Test fun managerTemporaryInstanceIsStopped() = runBlocking {
        val port = startServer("""{"listen":"127.0.0.1","port":PORT,"protocol":"vless","settings":{"clients":[{"id":"$uuid"}],"decryption":"none"}}""")
        val mgr = XrayManager(bin, work)
        var ep: ProxyEndpoint? = null
        mgr.withTemporary(XrayOutbound(XrayProtocol.VLESS, "127.0.0.1", port, id = uuid)) { e -> ep = e; roundTrip(e) }
        Thread.sleep(300)
        assertTrue(runCatching { java.net.Socket("127.0.0.1", ep!!.port).close() }.isFailure)
    }

    /** Batch mode: many servers in one Xray process; a config Xray refuses is isolated by bisection. */
    @Test fun batchWithBisection() = runBlocking {
        val port = startServer("""{"listen":"127.0.0.1","port":PORT,"protocol":"vless","settings":{"clients":[{"id":"$uuid"}],"decryption":"none"}}""")
        val good = XrayOutbound(XrayProtocol.VLESS, "127.0.0.1", port, id = uuid)
        // Bypasses the link parser on purpose: a 32-byte key for a 16-byte SS-2022 cipher makes Xray refuse to start.
        val bad = XrayOutbound(XrayProtocol.SHADOWSOCKS, "127.0.0.1", 1, method = "2022-blake3-aes-128-gcm", password = "YctPZ6U7xPPcU+gp3u+0tx/tRizJN9K8y+uKlW2qjlI=")
        val list = listOf(good, good.copy(), bad, good.copy(), good.copy())
        XrayManager(bin, work).withBatch(list) { eps ->
            assertEquals(5, eps.size)
            assertEquals(null, eps[2])
            listOf(0, 1, 3, 4).forEach { i -> roundTrip(eps[i]!!) }
            assertEquals(4, eps.filterNotNull().map { it.port }.toSet().size)
        }
    }
}
