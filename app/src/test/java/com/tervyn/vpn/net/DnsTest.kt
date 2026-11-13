package com.tervyn.vpn.net

import com.tervyn.vpn.vpn.Packets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTest {
    @Test fun parseQueryAndAnswer() {
        val q = DnsMessage.buildQuery(0x1234, "WWW.Example.COM", DnsMessage.TYPE_A)
        val pq = DnsMessage.parseQuery(q)!!
        assertEquals("www.example.com", pq.name); assertEquals(DnsMessage.TYPE_A, pq.qtype); assertEquals(0x1234, pq.id)
        val ip = Packets.ipFromString("198.18.1.5")
        val resp = DnsMessage.answerA(q, pq, listOf(ip), 30)
        assertEquals(listOf(ip), DnsMessage.parseAnswersA(resp))
        assertEquals(0, DnsMessage.rcode(resp))
        assertEquals(0x1234, Packets.u16(resp, 0))
        assertTrue((Packets.u16(resp, 2) and 0x8000) != 0)
    }

    @Test fun rejectsMalformed() {
        assertNull(DnsMessage.parseQuery(ByteArray(5)))
        val q = DnsMessage.buildQuery(1, "a.example", 1); q[2] = (q[2].toInt() or 0x80).toByte() // response bit
        assertNull(DnsMessage.parseQuery(q))
        val bad = DnsMessage.buildQuery(1, "a.example", 1); bad[12] = 100 // label overflows
        assertNull(DnsMessage.parseQuery(bad))
    }

    @Test fun fakeDnsStableAndReverse() {
        val f = FakeDns()
        val a = f.ipFor("a.example"); val b = f.ipFor("b.example")
        assertNotEquals(a, b); assertEquals(a, f.ipFor("A.EXAMPLE."))
        assertEquals("a.example", f.domainFor(a)); assertTrue(f.isFake(a)); assertTrue(FakeDns.inRange(a))
        assertTrue(Packets.ipToString(a).startsWith("198.18.") || Packets.ipToString(a).startsWith("198.19."))
        assertTrue(!f.isFake(FakeDns.DNS_ADDRESS))
    }

    @Test fun fakeDnsEvictsLru() {
        val f = FakeDns(capacity = 3)
        val a = f.ipFor("a.ex"); f.ipFor("b.ex"); f.ipFor("c.ex")
        f.ipFor("a.ex") // touch a
        val d = f.ipFor("d.ex") // evicts b
        assertNull(f.domainFor(Packets.ipFromString("198.18.1.1")))
        assertEquals("a.ex", f.domainFor(a)); assertEquals("d.ex", f.domainFor(d))
    }

    @Test fun responderRoutesAndForwards() = runBlocking {
        val f = FakeDns()
        var forwarded = 0
        val r = DnsResponder(f, { it.endsWith("youtube.com") }) { q -> forwarded++; DnsMessage.answerA(q, DnsMessage.parseQuery(q)!!, listOf(Packets.ipFromString("93.184.216.34")), 60) }
        val a = r.respond(DnsMessage.buildQuery(7, "www.youtube.com", DnsMessage.TYPE_A))!!
        assertTrue(f.isFake(DnsMessage.parseAnswersA(a).single()))
        val aaaa = r.respond(DnsMessage.buildQuery(8, "www.youtube.com", DnsMessage.TYPE_AAAA))!!
        assertEquals(0, Packets.u16(aaaa, 6)); assertEquals(0, forwarded)
        val other = r.respond(DnsMessage.buildQuery(9, "example.org", DnsMessage.TYPE_A))!!
        assertEquals(Packets.ipFromString("93.184.216.34"), DnsMessage.parseAnswersA(other).single()); assertEquals(1, forwarded)
        val local = r.respond(DnsMessage.buildQuery(10, "printer.local", DnsMessage.TYPE_A))!!
        assertEquals(2, forwarded); assertTrue(local.isNotEmpty())
    }

    @Test fun responderServfailOnForwardError() = runBlocking {
        val r = DnsResponder(FakeDns(), { false }) { error("down") }
        val resp = r.respond(DnsMessage.buildQuery(3, "example.org", DnsMessage.TYPE_A))!!
        assertEquals(DnsMessage.RCODE_SERVFAIL, DnsMessage.rcode(resp))
    }
}
