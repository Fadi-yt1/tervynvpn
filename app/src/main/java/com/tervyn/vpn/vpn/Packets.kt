package com.tervyn.vpn.vpn

/**
 * IPv4 / TCP / UDP / ICMP parsing and construction for the user-space network stack.
 * Addresses are IPv4 addresses packed into an [Int] (network byte order, i.e. 1.2.3.4 = 0x01020304).
 */
object Packets {
    const val PROTO_ICMP = 1
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    const val FIN = 0x01
    const val SYN = 0x02
    const val RST = 0x04
    const val PSH = 0x08
    const val ACK = 0x10

    fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF
    fun u16(b: ByteArray, o: Int) = (u8(b, o) shl 8) or u8(b, o + 1)
    fun i32(b: ByteArray, o: Int) = (u8(b, o) shl 24) or (u8(b, o + 1) shl 16) or (u8(b, o + 2) shl 8) or u8(b, o + 3)
    fun put16(b: ByteArray, o: Int, v: Int) { b[o] = (v ushr 8).toByte(); b[o + 1] = v.toByte() }
    fun put32(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 24).toByte(); b[o + 1] = (v ushr 16).toByte(); b[o + 2] = (v ushr 8).toByte(); b[o + 3] = v.toByte()
    }

    fun ipToString(ip: Int) = "${(ip ushr 24) and 255}.${(ip ushr 16) and 255}.${(ip ushr 8) and 255}.${ip and 255}"
    fun ipFromString(s: String): Int {
        val p = s.split('.').map { it.toInt() }
        require(p.size == 4 && p.all { it in 0..255 })
        return (p[0] shl 24) or (p[1] shl 16) or (p[2] shl 8) or p[3]
    }

    /** One's-complement sum used by IP/TCP/UDP checksums. */
    fun onesSum(b: ByteArray, off: Int, len: Int, initial: Long = 0): Long {
        var sum = initial
        var i = off
        val end = off + len
        while (i + 1 < end) { sum += u16(b, i).toLong(); i += 2 }
        if (i < end) sum += (u8(b, i) shl 8).toLong()
        return sum
    }

    fun fold(sum0: Long): Int {
        var sum = sum0
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.toInt().inv()) and 0xFFFF
    }

    fun pseudoHeaderSum(src: Int, dst: Int, proto: Int, len: Int): Long =
        ((src ushr 16) and 0xFFFF).toLong() + (src and 0xFFFF) + ((dst ushr 16) and 0xFFFF) + (dst and 0xFFFF) + proto + len

    // ---- parsing ---------------------------------------------------------------------------

    /** Lightweight view over an IPv4 packet. Returns null for anything that is not a sane, unfragmented IPv4 packet. */
    class Ipv4(val buf: ByteArray, val length: Int) {
        val headerLen = (u8(buf, 0) and 0x0F) * 4
        val totalLen = u16(buf, 2)
        val protocol = u8(buf, 9)
        val src = i32(buf, 12)
        val dst = i32(buf, 16)
        val payloadOffset get() = headerLen
        val payloadLength get() = totalLen - headerLen

        companion object {
            fun parse(buf: ByteArray, length: Int): Ipv4? {
                if (length < 20) return null
                if ((u8(buf, 0) ushr 4) != 4) return null
                val ihl = (u8(buf, 0) and 0x0F) * 4
                val total = u16(buf, 2)
                if (ihl < 20 || total < ihl || total > length) return null
                val flagsFrag = u16(buf, 6)
                if ((flagsFrag and 0x2000) != 0 || (flagsFrag and 0x1FFF) != 0) return null // fragments unsupported
                return Ipv4(buf, length)
            }
        }
    }

    class Tcp(val ip: Ipv4) {
        private val b = ip.buf
        private val o = ip.payloadOffset
        val srcPort = u16(b, o)
        val dstPort = u16(b, o + 2)
        val seq = i32(b, o + 4)
        val ack = i32(b, o + 8)
        val dataOffset = (u8(b, o + 12) ushr 4) * 4
        val flags = u8(b, o + 13)
        val window = u16(b, o + 14)
        val payloadOffset = o + dataOffset
        val payloadLength = ip.payloadLength - dataOffset
        fun has(f: Int) = (flags and f) != 0

        /** Parses MSS (kind 2) and window scale (kind 3) options. */
        fun options(): Pair<Int?, Int?> {
            var mss: Int? = null
            var ws: Int? = null
            var i = o + 20
            val end = o + dataOffset
            while (i < end) {
                when (val kind = u8(b, i)) {
                    0 -> break
                    1 -> i++
                    else -> {
                        if (i + 1 >= end) break
                        val len = u8(b, i + 1)
                        if (len < 2 || i + len > end) break
                        if (kind == 2 && len == 4) mss = u16(b, i + 2)
                        if (kind == 3 && len == 3) ws = minOf(u8(b, i + 2), 14)
                        i += len
                    }
                }
            }
            return mss to ws
        }

        companion object {
            fun parse(ip: Ipv4): Tcp? {
                if (ip.protocol != PROTO_TCP || ip.payloadLength < 20) return null
                val dataOff = (u8(ip.buf, ip.payloadOffset + 12) ushr 4) * 4
                if (dataOff < 20 || dataOff > ip.payloadLength) return null
                return Tcp(ip)
            }
        }
    }

    class Udp(val ip: Ipv4) {
        private val o = ip.payloadOffset
        val srcPort = u16(ip.buf, o)
        val dstPort = u16(ip.buf, o + 2)
        val length = u16(ip.buf, o + 4)
        val payloadOffset = o + 8
        val payloadLength = minOf(length, ip.payloadLength) - 8
        fun payload(): ByteArray = ip.buf.copyOfRange(payloadOffset, payloadOffset + payloadLength)

        companion object {
            fun parse(ip: Ipv4): Udp? {
                if (ip.protocol != PROTO_UDP || ip.payloadLength < 8) return null
                val u = Udp(ip)
                return if (u.length < 8) null else u
            }
        }
    }

    // ---- building --------------------------------------------------------------------------

    private val ipId = java.util.concurrent.atomic.AtomicInteger(0)

    private fun writeIpHeader(b: ByteArray, totalLen: Int, proto: Int, src: Int, dst: Int) {
        b[0] = 0x45; b[1] = 0
        put16(b, 2, totalLen)
        put16(b, 4, ipId.getAndIncrement() and 0xFFFF)
        put16(b, 6, 0x4000) // DF
        b[8] = 64; b[9] = proto.toByte()
        put16(b, 10, 0)
        put32(b, 12, src); put32(b, 16, dst)
        put16(b, 10, fold(onesSum(b, 0, 20)))
    }

    fun buildTcp(
        src: Int, dst: Int, srcPort: Int, dstPort: Int, seq: Int, ack: Int, flags: Int, window: Int,
        options: ByteArray? = null, payload: ByteArray? = null, payloadOff: Int = 0, payloadLen: Int = 0,
    ): ByteArray {
        val optLen = options?.size ?: 0
        require(optLen % 4 == 0)
        val tcpLen = 20 + optLen + payloadLen
        val total = 20 + tcpLen
        val b = ByteArray(total)
        writeIpHeader(b, total, PROTO_TCP, src, dst)
        val o = 20
        put16(b, o, srcPort); put16(b, o + 2, dstPort)
        put32(b, o + 4, seq); put32(b, o + 8, ack)
        b[o + 12] = (((20 + optLen) / 4) shl 4).toByte()
        b[o + 13] = flags.toByte()
        put16(b, o + 14, window.coerceIn(0, 0xFFFF))
        if (options != null) System.arraycopy(options, 0, b, o + 20, optLen)
        if (payloadLen > 0) System.arraycopy(payload!!, payloadOff, b, o + 20 + optLen, payloadLen)
        val sum = onesSum(b, o, tcpLen, pseudoHeaderSum(src, dst, PROTO_TCP, tcpLen))
        put16(b, o + 16, fold(sum))
        return b
    }

    fun buildUdp(src: Int, dst: Int, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val b = ByteArray(total)
        writeIpHeader(b, total, PROTO_UDP, src, dst)
        put16(b, 20, srcPort); put16(b, 22, dstPort); put16(b, 24, udpLen)
        System.arraycopy(payload, 0, b, 28, payload.size)
        var cs = fold(onesSum(b, 20, udpLen, pseudoHeaderSum(src, dst, PROTO_UDP, udpLen)))
        if (cs == 0) cs = 0xFFFF
        put16(b, 26, cs)
        return b
    }

    /** ICMP destination unreachable (type 3) quoting the original header + 8 bytes. */
    fun buildIcmpUnreachable(original: Ipv4, code: Int, routerIp: Int): ByteArray {
        val quote = minOf(original.headerLen + 8, original.totalLen)
        val icmpLen = 8 + quote
        val total = 20 + icmpLen
        val b = ByteArray(total)
        writeIpHeader(b, total, PROTO_ICMP, routerIp, original.src)
        b[20] = 3; b[21] = code.toByte()
        System.arraycopy(original.buf, 0, b, 28, quote)
        put16(b, 22, fold(onesSum(b, 20, icmpLen)))
        return b
    }

    /** Verifies the TCP checksum (used by tests). */
    fun tcpChecksumValid(pkt: ByteArray): Boolean {
        val ip = Ipv4.parse(pkt, pkt.size) ?: return false
        val sum = onesSum(pkt, ip.payloadOffset, ip.payloadLength, pseudoHeaderSum(ip.src, ip.dst, PROTO_TCP, ip.payloadLength))
        return fold(sum) == 0
    }

    fun ipChecksumValid(pkt: ByteArray): Boolean = fold(onesSum(pkt, 0, (u8(pkt, 0) and 0x0F) * 4)) == 0

    // Sequence-number arithmetic (RFC 1982 style, mod 2^32)
    fun seqLt(a: Int, b: Int) = (a - b) < 0
    fun seqLe(a: Int, b: Int) = (a - b) <= 0
    fun seqGt(a: Int, b: Int) = (a - b) > 0
    fun seqGe(a: Int, b: Int) = (a - b) >= 0
}
