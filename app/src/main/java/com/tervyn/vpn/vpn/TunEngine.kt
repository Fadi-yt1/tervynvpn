package com.tervyn.vpn.vpn

import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.net.DnsResponder
import com.tervyn.vpn.net.FakeDns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * User-space network engine behind the TUN interface.
 *
 *  - TCP: every flow is terminated locally by a [TcpConn] and re-originated through the active proxy.
 *    Connections to synthetic (fake-DNS) addresses are opened by *domain name*.
 *  - UDP/53: answered by the local [DnsResponder] (fake IPs for routed names; forwarded otherwise).
 *  - Other UDP (incl. QUIC): HTTP CONNECT and most SOCKS5 proxies cannot carry UDP, so it is rejected
 *    with ICMP port-unreachable; apps then fall back to TCP. Nothing is silently leaked.
 *  - IPv6: not forwarded. In All Apps mode IPv6 is routed into the TUN and dropped (leak prevention).
 */
class TunEngine(
    private val tunIn: InputStream,
    private val tunOut: OutputStream,
    private val config: Config,
    private val fakeDns: FakeDns,
    private val dns: DnsResponder,
    private val upstream: Upstream,
) : TcpHost {

    data class Config(
        /** All Apps mode: route every IPv4 TCP flow through the proxy. */
        val routeAll: Boolean,
        /** Extra IPv4 ranges routed in Only My Sites mode (e.g. Telegram's published CIDRs). */
        val routedCidrs: List<Pair<Int, Int>> = emptyList(),
        val tunIp: Int = FakeDns.TUN_ADDRESS,
        val dnsIp: Int = FakeDns.DNS_ADDRESS,
        val mtu: Int = 1500,
    )

    /** Opens proxied connections. Implemented by the service on top of ProxyDialer. */
    fun interface Upstream { fun dial(host: String, port: Int): Socket }

    data class FlowKey(val appIp: Int, val appPort: Int, val remoteIp: Int, val remotePort: Int)

    private val executor = Executors.newCachedThreadPool { r -> Thread(r, "tervyn-tcp").apply { isDaemon = true } }
    override val blockingDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    override val scope = CoroutineScope(SupervisorJob() + blockingDispatcher)

    private val conns = ConcurrentHashMap<FlowKey, TcpConn>()
    private val running = AtomicBoolean(false)
    private val writeLock = Any()

    val bytesUp = AtomicLong()
    val bytesDown = AtomicLong()
    val udpBlocked = AtomicLong()
    val dnsQueries = AtomicLong()
    val connectionsOpened = AtomicLong()
    val dialFailures = AtomicInteger()
    val activeConnections: Int get() = conns.size

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread({ readLoop() }, "tervyn-tun-reader").apply { isDaemon = true }.start()
        scope.launch {
            while (isActive && running.get()) {
                delay(250)
                val now = now()
                conns.values.forEach { it.onTick(now) }
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        conns.values.forEach { it.abort(true) }
        conns.clear()
        scope.cancel()
        executor.shutdownNow()
    }

    val isRunning get() = running.get()

    private fun readLoop() {
        val buf = ByteArray(maxOf(config.mtu, 1500) + 64)
        try {
            while (running.get()) {
                val n = tunIn.read(buf)
                if (n < 0) break
                if (n == 0) { Thread.sleep(5); continue }
                handlePacket(buf.copyOf(n), n)
            }
        } catch (_: IOException) {
        } catch (_: InterruptedException) {
        } finally {
            stop()
        }
    }

    /** Entry point for a packet read from the TUN (public for tests). */
    fun handlePacket(pkt: ByteArray, len: Int) {
        val ip = Packets.Ipv4.parse(pkt, len) ?: return // IPv6, fragments and garbage are dropped
        when (ip.protocol) {
            Packets.PROTO_TCP -> Packets.Tcp.parse(ip)?.let { handleTcp(it) }
            Packets.PROTO_UDP -> Packets.Udp.parse(ip)?.let { handleUdp(it) }
            else -> Unit // ICMP etc. are not forwarded
        }
    }

    private fun isRouted(ip: Int): Boolean =
        config.routeAll || config.routedCidrs.any { Domains.cidrContains(it, ip) }

    private fun handleTcp(tcp: Packets.Tcp) {
        val ip = tcp.ip
        val key = FlowKey(ip.src, tcp.srcPort, ip.dst, tcp.dstPort)
        val existing = conns[key]
        if (existing != null) { existing.onSegment(tcp); return }
        if (!tcp.has(Packets.SYN) || tcp.has(Packets.ACK)) {
            if (!tcp.has(Packets.RST)) sendRst(tcp)
            return
        }
        val target: String? = when {
            ip.dst == config.dnsIp || ip.dst == config.tunIp -> null // DNS-over-TCP/TLS to us: refuse
            fakeDns.isFake(ip.dst) -> fakeDns.domainFor(ip.dst)
            FakeDns.inRange(ip.dst) -> null
            isRouted(ip.dst) -> Packets.ipToString(ip.dst)
            else -> null
        }
        if (target == null) { sendRst(tcp); return }
        val (mss, ws) = tcp.options()
        val conn = TcpConn(this, ip.src, tcp.srcPort, ip.dst, tcp.dstPort, target, tcp.seq, tcp.window, mss, ws)
        if (conns.putIfAbsent(key, conn) == null) {
            connectionsOpened.incrementAndGet()
            conn.start()
        }
    }

    private fun sendRst(tcp: Packets.Tcp) {
        val ip = tcp.ip
        val pkt = if (tcp.has(Packets.ACK)) {
            Packets.buildTcp(ip.dst, ip.src, tcp.dstPort, tcp.srcPort, tcp.ack, 0, Packets.RST, 0)
        } else {
            val segLen = tcp.payloadLength + (if (tcp.has(Packets.SYN)) 1 else 0) + (if (tcp.has(Packets.FIN)) 1 else 0)
            Packets.buildTcp(ip.dst, ip.src, tcp.dstPort, tcp.srcPort, 0, tcp.seq + segLen, Packets.RST or Packets.ACK, 0)
        }
        writePacket(pkt)
    }

    private fun handleUdp(udp: Packets.Udp) {
        val ip = udp.ip
        val isDns = udp.dstPort == 53 && (ip.dst == config.dnsIp || config.routeAll)
        if (isDns) {
            val query = udp.payload()
            val src = ip.src; val dst = ip.dst; val sp = udp.srcPort; val dp = udp.dstPort
            dnsQueries.incrementAndGet()
            scope.launch {
                val resp = runCatching { dns.respond(query) }.getOrNull() ?: return@launch
                writePacket(Packets.buildUdp(dst, src, dp, sp, resp))
            }
            return
        }
        // UDP cannot be carried over HTTP CONNECT / SOCKS5 CONNECT tunnels: reject explicitly.
        udpBlocked.incrementAndGet()
        writePacket(Packets.buildIcmpUnreachable(ip, 3, config.tunIp))
    }

    // ---- TcpHost ---------------------------------------------------------------------------

    override fun writePacket(packet: ByteArray) {
        if (!running.get()) return
        try {
            synchronized(writeLock) { tunOut.write(packet) }
        } catch (_: IOException) {
        }
    }

    override fun dial(host: String, port: Int): Socket = try {
        upstream.dial(host, port).also { dialFailures.set(0) }
    } catch (e: Exception) {
        dialFailures.incrementAndGet()
        throw e
    }

    override fun onClosed(conn: TcpConn) {
        conns.remove(FlowKey(conn.appIp, conn.appPort, conn.remoteIp, conn.remotePort), conn)
    }

    override fun onUpload(bytes: Int) { bytesUp.addAndGet(bytes.toLong()) }
    override fun onDownload(bytes: Int) { bytesDown.addAndGet(bytes.toLong()) }
}
