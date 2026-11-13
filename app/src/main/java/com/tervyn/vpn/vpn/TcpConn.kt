package com.tervyn.vpn.vpn

import com.tervyn.vpn.vpn.Packets.ACK
import com.tervyn.vpn.vpn.Packets.FIN
import com.tervyn.vpn.vpn.Packets.PSH
import com.tervyn.vpn.vpn.Packets.RST
import com.tervyn.vpn.vpn.Packets.SYN
import com.tervyn.vpn.vpn.Packets.seqGt
import com.tervyn.vpn.vpn.Packets.seqLt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Callbacks the engine provides to each connection. */
interface TcpHost {
    val scope: CoroutineScope
    val blockingDispatcher: CoroutineDispatcher
    fun writePacket(packet: ByteArray)
    /** Opens the upstream connection (through the active proxy). Blocking. */
    fun dial(host: String, port: Int): Socket
    fun onClosed(conn: TcpConn)
    fun onUpload(bytes: Int)
    fun onDownload(bytes: Int)
    fun now(): Long = System.currentTimeMillis()
}

/** Fixed-capacity byte ring buffer. Not thread safe — guarded by the connection lock. */
class ByteRing(val capacity: Int) {
    private val data = ByteArray(capacity)
    private var head = 0
    var size = 0; private set
    val free get() = capacity - size

    fun append(src: ByteArray, off: Int, len: Int): Int {
        val n = minOf(len, free)
        var tail = (head + size) % capacity
        var copied = 0
        while (copied < n) {
            val chunk = minOf(n - copied, capacity - tail)
            System.arraycopy(src, off + copied, data, tail, chunk)
            copied += chunk; tail = (tail + chunk) % capacity
        }
        size += n
        return n
    }

    fun peek(offset: Int, dst: ByteArray, dstOff: Int, len: Int) {
        require(offset >= 0 && offset + len <= size)
        var pos = (head + offset) % capacity
        var copied = 0
        while (copied < len) {
            val chunk = minOf(len - copied, capacity - pos)
            System.arraycopy(data, pos, dst, dstOff + copied, chunk)
            copied += chunk; pos = (pos + chunk) % capacity
        }
    }

    fun drop(n: Int) {
        val k = minOf(n, size)
        head = (head + k) % capacity
        size -= k
        if (size == 0) head = 0
    }
}

/**
 * One TCP flow between an app (behind the TUN) and a remote target reached through the proxy.
 *
 * The connection plays the remote peer towards the app: it answers the handshake only after the
 * upstream (proxied) connection is established, so failures surface to the app as a normal RST.
 *
 *  - Receive path (app -> upstream): in-order delivery, a receive window derived from the bytes
 *    still queued for the upstream socket (flow control), duplicate ACKs for out-of-order segments.
 *  - Send path (upstream -> app): data is buffered until acknowledged, transmission respects the
 *    app's advertised (scaled) window and MSS, with go-back-N retransmission on RTO, fast retransmit
 *    on three duplicate ACKs and zero-window probing.
 *  - Close: half-close in both directions, RST on errors and idle timeout.
 */
class TcpConn(
    private val host: TcpHost,
    val appIp: Int,
    val appPort: Int,
    val remoteIp: Int,
    val remotePort: Int,
    val target: String,
    clientIsn: Int,
    clientWindow: Int,
    clientMss: Int?,
    clientWindowScale: Int?,
) {
    companion object {
        const val MAX_MSS = 1460
        const val SEND_BUF = 512 * 1024
        const val RCV_BUF = 1024 * 1024
        const val OUR_WINDOW_SCALE = 5            // 65535 << 5 ≈ 2 MB
        const val BASE_RTO = 1_000L
        const val MAX_RTO = 8_000L
        const val CONNECT_TIMEOUT = 30_000L
        const val IDLE_TIMEOUT = 10 * 60_000L
        const val HALF_CLOSED_TIMEOUT = 60_000L
    }

    enum class State { CONNECTING, SYN_RCVD, ESTABLISHED, CLOSED }

    private val lock = Object()
    @Volatile var state = State.CONNECTING; private set

    private val iss = Random.nextInt()
    private var sndUna = iss
    private var sndNxt = iss
    private var sndMax = iss
    private val sndShift = clientWindowScale ?: 0
    private var sndWnd: Long = clientWindow.toLong() // the SYN window is never scaled
    private val wsEnabled = clientWindowScale != null
    private val rcvShift = if (wsEnabled) OUR_WINDOW_SCALE else 0
    private var rcvNxt = clientIsn + 1
    val mss = minOf(clientMss ?: 536, MAX_MSS).coerceAtLeast(64)

    private val sendBuf = ByteRing(SEND_BUF)
    private val toUpstream = Channel<ByteArray>(Channel.UNLIMITED)
    private val queued = AtomicInteger(0)
    private var advertised = 0

    private var upstreamEof = false
    private var finSent = false
    private var finSeq = 0
    private var finAcked = false
    private var clientFin = false
    private var upstreamWriteDone = false

    private var rto = BASE_RTO
    private var lastProgress = 0L
    private var dupAcks = 0
    private val created = host.now()
    @Volatile var lastActivity = created; private set
    private var socket: Socket? = null

    val isClosed get() = state == State.CLOSED

    /** Starts dialing the upstream; the SYN-ACK is sent once it is connected. */
    fun start() {
        host.scope.launch(host.blockingDispatcher) {
            val s = try {
                host.dial(target, remotePort)
            } catch (e: Exception) {
                synchronized(lock) {
                    if (state == State.CONNECTING) {
                        host.writePacket(Packets.buildTcp(remoteIp, appIp, remotePort, appPort, 0, rcvNxt, RST or ACK, 0))
                        closeLocked()
                    }
                }
                return@launch
            }
            synchronized(lock) {
                if (state != State.CONNECTING) { runCatching { s.close() }; return@launch }
                socket = s
                state = State.SYN_RCVD
                sendSynAckLocked()
                sndNxt = iss + 1; sndMax = sndNxt
                lastProgress = host.now()
            }
            host.scope.launch(host.blockingDispatcher) { readUpstream(s) }
            host.scope.launch(host.blockingDispatcher) { writeUpstream(s) }
        }
    }

    private fun sendSynAckLocked() {
        val opts = if (wsEnabled) {
            byteArrayOf(2, 4, (MAX_MSS ushr 8).toByte(), MAX_MSS.toByte(), 1, 3, 3, OUR_WINDOW_SCALE.toByte())
        } else {
            byteArrayOf(2, 4, (MAX_MSS ushr 8).toByte(), MAX_MSS.toByte())
        }
        advertised = minOf(rcvWindow(), 0xFFFF)
        host.writePacket(Packets.buildTcp(remoteIp, appIp, remotePort, appPort, iss, rcvNxt, SYN or ACK, advertised, opts))
    }

    private fun rcvWindow() = maxOf(0, RCV_BUF - queued.get())

    private fun sendAckLocked(flags: Int = ACK) {
        val wnd = rcvWindow()
        val field = minOf(wnd ushr rcvShift, 0xFFFF)
        advertised = field shl rcvShift
        host.writePacket(Packets.buildTcp(remoteIp, appIp, remotePort, appPort, sndNxt, rcvNxt, flags, field))
    }

    /** Handles a segment from the app. Called on the TUN reader thread. */
    fun onSegment(tcp: Packets.Tcp) {
        synchronized(lock) {
            if (state == State.CLOSED) return
            lastActivity = host.now()
            if (tcp.has(RST)) { closeLocked(); return }
            if (tcp.has(SYN)) {
                // Retransmitted SYN: re-send our SYN-ACK if we already sent one.
                if (state == State.SYN_RCVD && tcp.seq + 1 == rcvNxt) sendSynAckLocked()
                return
            }
            if (state == State.CONNECTING || !tcp.has(ACK)) return
            if (state == State.SYN_RCVD) {
                if (tcp.ack != iss + 1) {
                    host.writePacket(Packets.buildTcp(remoteIp, appIp, remotePort, appPort, tcp.ack, 0, RST, 0))
                    return
                }
                state = State.ESTABLISHED
                sndUna = iss + 1
            }
            handleAckLocked(tcp.ack, tcp.window, tcp.payloadLength)
            if (tcp.payloadLength > 0) handleDataLocked(tcp)
            if (tcp.has(FIN) && !clientFin && tcp.seq + tcp.payloadLength == rcvNxt) {
                rcvNxt += 1
                clientFin = true
                toUpstream.close()
                sendAckLocked()
            }
            trySendLocked()
            checkClosedLocked()
        }
    }

    private fun handleAckLocked(ack: Int, rawWindow: Int, payloadLen: Int) {
        val newWnd = rawWindow.toLong() shl sndShift
        if (seqGt(ack, sndMax)) return // acknowledges data never sent
        if (seqGt(ack, sndUna)) {
            val dataAcked = minOf((ack - sndUna).toLong(), sendBuf.size.toLong()).toInt()
            sendBuf.drop(dataAcked)
            sndUna = ack
            if (seqLt(sndNxt, sndUna)) sndNxt = sndUna
            if (finSent && seqGt(ack, finSeq)) finAcked = true
            dupAcks = 0
            rto = BASE_RTO
            lastProgress = host.now()
            lock.notifyAll() // send buffer space available
        } else if (ack == sndUna && payloadLen == 0 && sndNxt != sndUna && newWnd == sndWnd) {
            if (++dupAcks == 3) retransmitLocked()
        }
        sndWnd = newWnd
    }

    private fun handleDataLocked(tcp: Packets.Tcp) {
        var seq = tcp.seq
        var off = tcp.payloadOffset
        var len = tcp.payloadLength
        if (seqLt(seq, rcvNxt)) {
            val skip = rcvNxt - seq
            if (skip >= len) { sendAckLocked(); return } // pure retransmission
            off += skip; len -= skip; seq = rcvNxt
        }
        if (seq != rcvNxt || clientFin) { sendAckLocked(); return } // out of order: duplicate ACK
        len = minOf(len, rcvWindow())
        if (len <= 0) { sendAckLocked(); return }
        val chunk = tcp.ip.buf.copyOfRange(off, off + len)
        queued.addAndGet(len)
        toUpstream.trySend(chunk)
        rcvNxt += len
        host.onUpload(len)
        sendAckLocked()
    }

    private fun inflight(): Int = sndNxt - sndUna

    /** Sends as much buffered upstream data as the app's window allows, then FIN if upstream finished. */
    private fun trySendLocked() {
        if (state != State.ESTABLISHED) return
        val wasIdle = inflight() == 0
        while (true) {
            val offset = sndNxt - sndUna
            val unsent = sendBuf.size - offset
            if (unsent <= 0) break
            val canSend = sndWnd - inflight()
            if (canSend <= 0) break
            val n = minOf(mss.toLong(), unsent.toLong(), canSend).toInt()
            transmitLocked(offset, n)
        }
        if (upstreamEof && !finSent && sndNxt - sndUna == sendBuf.size) {
            finSeq = sndNxt
            finSent = true
            host.writePacket(Packets.buildTcp(remoteIp, appIp, remotePort, appPort, sndNxt, rcvNxt, FIN or ACK, currentWindowField()))
            sndNxt += 1
        }
        if (seqGt(sndNxt, sndMax)) sndMax = sndNxt
        if (wasIdle && inflight() > 0) lastProgress = host.now()
    }

    private fun currentWindowField(): Int = minOf(rcvWindow() ushr rcvShift, 0xFFFF)

    private fun transmitLocked(offset: Int, n: Int) {
        val payload = ByteArray(n)
        sendBuf.peek(offset, payload, 0, n)
        host.writePacket(
            Packets.buildTcp(remoteIp, appIp, remotePort, appPort, sndUna + offset, rcvNxt, ACK or PSH, currentWindowField(), null, payload, 0, n)
        )
        sndNxt = sndUna + offset + n
        if (seqGt(sndNxt, sndMax)) sndMax = sndNxt
    }

    /** Go-back-N: resend from the oldest unacknowledged byte. */
    private fun retransmitLocked() {
        sndNxt = sndUna
        if (finSent && !finAcked) finSent = false
        dupAcks = 0
        // Retransmit at least one segment even if the window shrank, then continue normally.
        val unsent = sendBuf.size
        if (unsent > 0) transmitLocked(0, minOf(mss, unsent))
        trySendLocked()
        lastProgress = host.now()
    }

    /** Periodic timer: retransmission, zero-window probe, timeouts. */
    fun onTick(now: Long) {
        synchronized(lock) {
            if (state == State.CLOSED) return
            if (state != State.ESTABLISHED) {
                if (now - created > CONNECT_TIMEOUT) abortLocked(true)
                return
            }
            val unsent = sendBuf.size - inflight()
            if (inflight() > 0 && now - lastProgress > rto) {
                retransmitLocked()
                rto = minOf(rto * 2, MAX_RTO)
            } else if (inflight() == 0 && unsent > 0 && sndWnd == 0L && now - lastProgress > rto) {
                transmitLocked(0, 1) // zero-window probe
                lastProgress = now
                rto = minOf(rto * 2, MAX_RTO)
            }
            val limit = if (clientFin || finAcked) HALF_CLOSED_TIMEOUT else IDLE_TIMEOUT
            if (now - lastActivity > limit) abortLocked(true)
        }
    }

    private suspend fun readUpstream(s: Socket) {
        val buf = ByteArray(32 * 1024)
        try {
            val input = s.getInputStream()
            while (true) {
                var max: Int
                synchronized(lock) {
                    while (sendBuf.free == 0 && state != State.CLOSED) lock.wait(1000)
                    if (state == State.CLOSED) return
                    max = minOf(buf.size, sendBuf.free)
                }
                val n = input.read(buf, 0, max)
                if (n < 0) {
                    synchronized(lock) { upstreamEof = true; trySendLocked(); checkClosedLocked() }
                    return
                }
                host.onDownload(n)
                synchronized(lock) {
                    if (state == State.CLOSED) return
                    lastActivity = host.now()
                    sendBuf.append(buf, 0, n)
                    trySendLocked()
                }
            }
        } catch (e: IOException) {
            synchronized(lock) { abortLocked(true) }
        }
    }

    private suspend fun writeUpstream(s: Socket) {
        try {
            val out = s.getOutputStream()
            for (chunk in toUpstream) {
                out.write(chunk)
                queued.addAndGet(-chunk.size)
                synchronized(lock) {
                    if (state == State.ESTABLISHED) {
                        val wnd = rcvWindow()
                        // Window update when a previously small window opened up substantially.
                        if (advertised < RCV_BUF / 2 && wnd - advertised >= maxOf(2 * mss, RCV_BUF / 4)) sendAckLocked()
                    }
                }
            }
            runCatching { s.shutdownOutput() }
            synchronized(lock) { upstreamWriteDone = true; checkClosedLocked() }
        } catch (e: IOException) {
            synchronized(lock) { abortLocked(true) }
        }
    }

    private fun checkClosedLocked() {
        if (state != State.CLOSED && clientFin && finAcked && upstreamWriteDone) closeLocked()
    }

    fun abort(sendRst: Boolean) = synchronized(lock) { abortLocked(sendRst) }

    private fun abortLocked(sendRst: Boolean) {
        if (state == State.CLOSED) return
        if (sendRst) host.writePacket(Packets.buildTcp(remoteIp, appIp, remotePort, appPort, sndNxt, rcvNxt, RST or ACK, 0))
        closeLocked()
    }

    private fun closeLocked() {
        if (state == State.CLOSED) return
        state = State.CLOSED
        toUpstream.close()
        runCatching { socket?.close() }
        lock.notifyAll()
        host.onClosed(this)
    }
}
