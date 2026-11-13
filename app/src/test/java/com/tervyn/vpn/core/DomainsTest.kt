package com.tervyn.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainsTest {
    @Test fun normalize() {
        assertEquals("example.com", Domains.normalize("https://www.Example.com/path?q=1#x"))
        assertEquals("m.youtube.com", Domains.normalize("https://m.youtube.com/watch?v=abc"))
        assertEquals("example.co.uk", Domains.normalize("*.example.co.uk"))
        assertEquals("xn--bcher-kva.example", Domains.normalize("bücher.example"))
        assertEquals("news.ycombinator.com", Domains.normalize("Check this out https://news.ycombinator.com/item?id=1"))
        assertEquals("example.com", Domains.normalize("example.com:8443"))
    }
    @Test fun rejects() {
        listOf("", "localhost", "1.2.3.4", "javascript:alert(1)", "file:///etc/passwd", "ftp://x.example", "-bad-.com", "a..b", "exa mple", "http://[::1]/")
            .forEach { assertNull(it, Domains.normalize(it)) }
    }
    @Test fun matcher() {
        val m = Domains.Matcher(listOf("youtube.com", "googlevideo.com"))
        assertTrue(m.matches("youtube.com")); assertTrue(m.matches("www.youtube.com")); assertTrue(m.matches("rr1---sn-abc.googlevideo.com"))
        assertFalse(m.matches("notyoutube.com")); assertFalse(m.matches("youtube.com.evil.example")); assertFalse(m.matches("com"))
    }
    @Test fun cidr() {
        val n = Domains.parseCidr("149.154.160.0/20")!!
        assertTrue(Domains.cidrContains(n, com.tervyn.vpn.vpn.Packets.ipFromString("149.154.167.51")))
        assertFalse(Domains.cidrContains(n, com.tervyn.vpn.vpn.Packets.ipFromString("149.154.176.1")))
    }
    @Test fun localNames() {
        assertTrue(Domains.isLocalName("printer.local")); assertTrue(Domains.isLocalName("4.3.2.1.in-addr.arpa")); assertTrue(Domains.isLocalName("router"))
        assertFalse(Domains.isLocalName("example.com"))
    }
}
