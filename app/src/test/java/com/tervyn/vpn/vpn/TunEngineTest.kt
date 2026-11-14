package com.tervyn.vpn.vpn

import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.net.DnsMessage
import com.tervyn.vpn.net.DnsResponder
import com.tervyn.vpn.net.FakeDns
import com.tervyn.vpn.net.ProxyDialer
import com.tervyn.vpn.testutil.BulkServer
import com.tervyn.vpn.testutil.EchoServer
import com.tervyn.vpn.testutil.MockProxy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Drives the real [TunEngine] + [TcpConn] stack with raw IPv4 packets, as the Android TUN would,
 * against a real (local) SOCKS5/HTTP proxy and target servers.
 */
class TunEngineTest {
    private val appIp = Packets.ipFromString("198.18.0.1")
    private val cleanup = mutableListOf<AutoCloseable>()
    private lateinit var engine: TunEngine
    private val toEngine = LinkedBlockingQueue<ByteArray>()
    private val fromEngine = LinkedBlockingQueue<ByteArray>()
    private val fakeDns = FakeDns()

    @After fun tearDown() { engine.stop(); toEngine.put(ByteArray(0)); cleanup.forEach { runCatching { it.close() } } }

    private fun startEngine(proxy: ProxyEndpoint?, routeAll: Boolean = true, routed: (String) -> Boolean = { true }, forward: suspend (ByteArray) -> ByteArray? = { null }) {
        val input = object : InputStream() {
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val p = toEngine.take(); if (p.isEmpty()) return -1
                System.arraycopy(p, 0, b, off, p.size); return p.size
            }
        }
        val output = object : OutputStream() {
            override fun write(b: Int) = throw UnsupportedOperationException()
            override fun write(b: ByteArray, off: Int, len: Int) { fromEngine.put(b.copyOfRange(off, off + len)) }
        }
        val dns = DnsResponder(fakeDns, routed, forward)
        engine = TunEngine(input, output, TunEngine.Config(routeAll = routeAll), fakeDns, dns,
            TunEngine.Upstream { h, p -> if (proxy == null) error("no proxy") else ProxyDialer.connect(proxy, h, p, 3000, 3000) })
        engine.start()
    }

    private fun send(p: ByteArray) = toEngine.put(p)
    private fun next(timeoutMs: Long = 5000): ByteArray = fromEngine.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: error("timeout waiting for packet")

    private fun resolve(name: String): Int {
        send(Packets.buildUdp(appIp, FakeDns.DNS_ADDRESS, 5555, 53, DnsMessage.buildQuery(42, name, DnsMessage.TYPE_A)))
        val p = next()
        val udp = Packets.Udp.parse(Packets.Ipv4.parse(p, p.size)!!)!!
        assertEquals(5555, udp.dstPort)
        return DnsMessage.parseAnswersA(udp.payload()).single()
    }

    /** A tiny client-side TCP used to talk to the engine. */
    inner class Client(val dst: Int, val dstPort: Int, val srcPort: Int = 40000 + (Math.random() * 20000).toInt(), var window: Int = 65535, val ws: Int? = 6) {
        var seq = 1000
        var ack = 0
        val received = ByteArrayOutputStream()
        var finReceived = false
        var maxInflightObserved = 0

        fun pkt(flags: Int, payload: ByteArray? = null, opts: ByteArray? = null, seqOverride: Int? = null) =
            Packets.buildTcp(appIp, dst, srcPort, dstPort, seqOverride ?: seq, ack, flags, if (flags and Packets.SYN != 0) window else (window ushr (ws ?: 0)), opts, payload, 0, payload?.size ?: 0)

        fun connect(): Packets.Tcp {
            val opts = if (ws != null) byteArrayOf(2, 4, 5, 0xB4.toByte(), 1, 3, 3, ws.toByte()) else byteArrayOf(2, 4, 5, 0xB4.toByte())
            send(pkt(Packets.SYN, opts = opts))
            val sa = parse(next())
            assertTrue("expected SYN-ACK", sa.has(Packets.SYN) && sa.has(Packets.ACK))
            assertEquals(seq + 1, sa.ack)
            seq += 1; ack = sa.seq + 1
            send(pkt(Packets.ACK))
            return sa
        }

        fun write(data: ByteArray) { send(pkt(Packets.ACK or Packets.PSH, data)); seq += data.size }

        /** Reads segments, ACKing as we go, until [n] bytes arrived or FIN. */
        fun readAtLeast(n: Int, timeoutMs: Long = 10_000, ackEvery: Int = 1) {
            val deadline = System.currentTimeMillis() + timeoutMs
            var unacked = 0
            while (received.size() < n && !finReceived) {
                val p = fromEngine.poll(maxOf(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS) ?: error("timeout: got ${received.size()} of $n")
                val t = parse(p)
                assertTrue(Packets.tcpChecksumValid(p))
                if (t.dstPort != srcPort) continue
                if (t.payloadLength > 0) {
                    if (t.seq == ack) {
                        received.write(p, t.payloadOffset, t.payloadLength); ack += t.payloadLength
                    }
                    unacked++
                    maxInflightObserved = maxOf(maxInflightObserved, received.size())
                }
                if (t.has(Packets.FIN) && t.seq + t.payloadLength == ack) { ack += 1; finReceived = true }
                if (unacked >= ackEvery || finReceived || t.has(Packets.FIN)) { send(pkt(Packets.ACK)); unacked = 0 }
            }
            send(pkt(Packets.ACK))
        }

        fun close() { send(pkt(Packets.FIN or Packets.ACK)); seq += 1 }
    }

    private fun parse(p: ByteArray) = Packets.Tcp.parse(Packets.Ipv4.parse(p, p.size)!!)!!

    @Test fun echoThroughSocks5WithAuthUsingFakeDns() {
        val echo = EchoServer().also { cleanup += it }
        val proxy = MockProxy(true, "u", "p") { echo.port }.also { cleanup += it }
        startEngine(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", proxy.port, "u", "p"))
        val ip = resolve("chat.example.com")
        assertTrue(fakeDns.isFake(ip))
        val c = Client(ip, 443)
        c.connect()
        val msg = "GET / HTTP/1.1\r\nHost: chat.example.com\r\n\r\n".toByteArray()
        c.write(msg)
        c.readAtLeast(msg.size)
        assertArrayEquals(msg, c.received.toByteArray())
        assertEquals("chat.example.com:443", proxy.targets.single()) // the proxy got the domain, not an IP
        c.close()
        c.readAtLeast(Int.MAX_VALUE, 5000) // until FIN from engine
        assertTrue(c.finReceived)
        Thread.sleep(300)
        assertEquals(0, engine.activeConnections)
        assertTrue(engine.bytesUp.get() >= msg.size && engine.bytesDown.get() >= msg.size)
    }

    @Test fun bulkDownloadRespectsSmallWindowNoScaling() {
        val size = 3 * 1024 * 1024 + 123
        val bulk = BulkServer(size).also { cleanup += it }
        val proxy = MockProxy(false) { bulk.port }.also { cleanup += it }
        startEngine(ProxyEndpoint(ProxyProtocol.HTTP, "127.0.0.1", proxy.port))
        val c = Client(Packets.ipFromString("93.184.216.34"), 80, window = 16 * 1024, ws = null)
        c.connect()
        c.readAtLeast(size, 60_000, ackEvery = 2)
        assertArrayEquals(BulkServer.pattern(size), c.received.toByteArray())
        assertEquals("93.184.216.34:80", proxy.targets.single())
    }

    @Test fun retransmitsUnackedData() {
        val echo = EchoServer().also { cleanup += it }
        val proxy = MockProxy(true) { echo.port }.also { cleanup += it }
        startEngine(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", proxy.port))
        val c = Client(Packets.ipFromString("1.1.1.1"), 443)
        c.connect()
        c.write("abc".toByteArray())
        // Engine ACKs our data, then sends the echo. Drop the first copy of the echo (do not ACK).
        var first: Packets.Tcp? = null
        while (first == null) { val t = parse(next()); if (t.payloadLength > 0) first = t }
        val again = generateSequence { parse(next(4000)) }.first { it.payloadLength > 0 }
        assertEquals(first.seq, again.seq) // retransmission after RTO
        c.ack = again.seq + again.payloadLength
        send(c.pkt(Packets.ACK))
    }

    @Test fun dialFailureResetsConnection() {
        startEngine(null)
        val c = Client(Packets.ipFromString("8.8.4.4"), 443)
        send(c.pkt(Packets.SYN, opts = byteArrayOf(2, 4, 5, 0xB4.toByte())))
        val t = parse(next())
        assertTrue(t.has(Packets.RST)); assertEquals(c.seq + 1, t.ack)
    }

    @Test fun onlyMySitesRejectsUnroutedIpsAndRoutesNames() {
        val echo = EchoServer().also { cleanup += it }
        val proxy = MockProxy(true) { echo.port }.also { cleanup += it }
        startEngine(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", proxy.port), routeAll = false, routed = { it.endsWith("youtube.com") })
        val c = Client(Packets.ipFromString("8.8.8.8"), 443)
        send(c.pkt(Packets.SYN))
        assertTrue(parse(next()).has(Packets.RST))
        val ip = resolve("www.youtube.com")
        Client(ip, 443).connect()
        assertEquals("www.youtube.com:443", proxy.targets.single())
    }

    @Test fun udpIsRejectedWithIcmp() {
        startEngine(null)
        send(Packets.buildUdp(appIp, Packets.ipFromString("142.250.1.1"), 50000, 443, ByteArray(20)))
        val p = next()
        assertEquals(Packets.PROTO_ICMP, Packets.u8(p, 9))
        assertEquals(1L, engine.udpBlocked.get())
    }

    @Test fun ipv6IsDropped() {
        startEngine(null)
        val v6 = ByteArray(60).also { it[0] = 0x60 }
        send(v6)
        assertEquals(null, fromEngine.poll(500, TimeUnit.MILLISECONDS))
        assertNotNull(engine)
    }

    @Test fun manyConcurrentConnections() {
        val echo = EchoServer().also { cleanup += it }
        val proxy = MockProxy(true) { echo.port }.also { cleanup += it }
        startEngine(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", proxy.port))
        // Open 20 connections sequentially and keep them open.
        val clients = (0 until 20).map { i -> Client(fakeDns.ipFor("h$i.example"), 443, srcPort = 30000 + i).also { it.connect() } }
        assertEquals(20, engine.activeConnections)
        clients.forEach { it.write("x".toByteArray()) }
        Thread.sleep(500)
        assertEquals(20, proxy.targets.size)
    }

    // ---- Secure DNS (DNS-only tunnel) --------------------------------------------------------

    /** DNS-only mode: no names are routed (no synthetic addresses); every query is forwarded and the real answer returned. */
    @Test fun dnsOnlyForwardsQueriesAndReturnsRealAnswers() {
        val realIp = Packets.ipFromString("93.184.216.34")
        var forwarded = 0
        startEngine(null, routeAll = false, routed = { false }) { q ->
            forwarded++
            val parsed = DnsMessage.parseQuery(q)!!
            DnsMessage.answerA(q, parsed, listOf(realIp), 300)
        }
        val ip = resolve("example.com")
        assertEquals(realIp, ip)
        assertEquals(1, forwarded)
        assertTrue("no synthetic address must be handed out in DNS-only mode", !fakeDns.isFake(ip))
        assertEquals(0, fakeDns.size())
        assertEquals(1, engine.dnsQueries.get())
    }

    /** DNS-only mode: a TCP connection attempt is refused immediately (nothing is relayed, nothing is leaked). */
    @Test fun dnsOnlyRefusesTcp() {
        startEngine(null, routeAll = false, routed = { false })
        val client = Client(Packets.ipFromString("93.184.216.34"), 443)
        send(client.pkt(Packets.SYN, opts = byteArrayOf(2, 4, 5, 0xB4.toByte())))
        val rst = parse(next())
        assertTrue(rst.has(Packets.RST))
        assertEquals(0, engine.activeConnections)
        assertEquals(0L, engine.connectionsOpened.get())
    }

    /** DNS-only mode: when the resolver is unreachable the app gets SERVFAIL, never a made-up answer. */
    @Test fun dnsOnlyReportsResolverFailure() {
        startEngine(null, routeAll = false, routed = { false }) { null }
        send(Packets.buildUdp(appIp, FakeDns.DNS_ADDRESS, 5556, 53, DnsMessage.buildQuery(7, "example.org", DnsMessage.TYPE_A)))
        val p = next()
        val udp = Packets.Udp.parse(Packets.Ipv4.parse(p, p.size)!!)!!
        assertEquals(DnsMessage.RCODE_SERVFAIL, DnsMessage.rcode(udp.payload()))
        assertTrue(DnsMessage.parseAnswersA(udp.payload()).isEmpty())
    }
}
