package com.tervyn.vpn.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketsTest {
    private val a = Packets.ipFromString("10.0.0.2")
    private val b = Packets.ipFromString("198.18.1.1")

    @Test fun tcpBuildParseChecksum() {
        val payload = "hello".toByteArray()
        val p = Packets.buildTcp(a, b, 40000, 443, -5, 77, Packets.ACK or Packets.PSH, 1234,
            byteArrayOf(2, 4, 5, 0xB4.toByte(), 1, 3, 3, 7), payload, 0, payload.size)
        assertTrue(Packets.ipChecksumValid(p)); assertTrue(Packets.tcpChecksumValid(p))
        val tcp = Packets.Tcp.parse(Packets.Ipv4.parse(p, p.size)!!)!!
        assertEquals(40000, tcp.srcPort); assertEquals(443, tcp.dstPort); assertEquals(-5, tcp.seq); assertEquals(77, tcp.ack)
        assertEquals(1234, tcp.window); assertEquals(5, tcp.payloadLength); assertEquals(1460 to 7, tcp.options())
        assertEquals("hello", String(p, tcp.payloadOffset, tcp.payloadLength))
    }

    @Test fun udpAndIcmp() {
        val u = Packets.buildUdp(a, b, 5353, 53, byteArrayOf(1, 2, 3))
        val ip = Packets.Ipv4.parse(u, u.size)!!
        val udp = Packets.Udp.parse(ip)!!
        assertEquals(53, udp.dstPort); assertEquals(3, udp.payloadLength)
        val icmp = Packets.buildIcmpUnreachable(ip, 3, b)
        assertTrue(Packets.ipChecksumValid(icmp)); assertEquals(1, Packets.u8(icmp, 9)); assertEquals(3, Packets.u8(icmp, 20))
        assertEquals(0, Packets.fold(Packets.onesSum(icmp, 20, icmp.size - 20)))
    }

    @Test fun rejectsIpv6AndFragments() {
        val v6 = ByteArray(60).also { it[0] = 0x60 }
        assertNull(Packets.Ipv4.parse(v6, v6.size))
        val p = Packets.buildUdp(a, b, 1, 2, ByteArray(4)); Packets.put16(p, 6, 0x2000)
        assertNull(Packets.Ipv4.parse(p, p.size))
    }

    @Test fun seqArithmeticWraps() {
        assertTrue(Packets.seqLt(Int.MAX_VALUE, Int.MIN_VALUE)); assertTrue(Packets.seqGt(5, -10)); assertTrue(Packets.seqLt(-1, 1))
    }

    @Test fun byteRing() {
        val r = ByteRing(8)
        assertEquals(5, r.append("abcde".toByteArray(), 0, 5)); r.drop(3)
        assertEquals(6, r.append("fghijk".toByteArray(), 0, 6)); assertEquals(0, r.free)
        val out = ByteArray(8); r.peek(0, out, 0, 8); assertEquals("defghijk", String(out))
    }
}
