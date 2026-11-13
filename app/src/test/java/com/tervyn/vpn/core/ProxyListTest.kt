package com.tervyn.vpn.core

import com.tervyn.vpn.domain.ProxyProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyListTest {
    @Test fun hostPort() {
        val e = ProxyList.parseLine("1.2.3.4:8080", ProxyProtocol.SOCKS5)!!
        assertEquals(ProxyProtocol.SOCKS5, e.protocol); assertEquals("1.2.3.4", e.host); assertEquals(8080, e.port); assertNull(e.username)
    }
    @Test fun hostPortUserPass() {
        val e = ProxyList.parseLine("proxy.example.com:3128:alice:s3cr3t")!!
        assertEquals("proxy.example.com", e.host); assertEquals(3128, e.port); assertEquals("alice", e.username); assertEquals("s3cr3t", e.password)
    }
    @Test fun userPassAtHostPort() {
        val e = ProxyList.parseLine("bob:p%40ss@10.0.0.1:1080", ProxyProtocol.SOCKS5)!!
        assertEquals("bob", e.username); assertEquals("p@ss", e.password); assertEquals("10.0.0.1", e.host)
    }
    @Test fun userPassHostPort() {
        val e = ProxyList.parseLine("carol:pw:gate.example.net:7777")!!
        assertEquals("gate.example.net", e.host); assertEquals(7777, e.port); assertEquals("carol", e.username); assertEquals("pw", e.password)
    }
    @Test fun urls() {
        assertEquals(ProxyProtocol.SOCKS5, ProxyList.parseLine("socks5://u:p@h.example:1")!!.protocol)
        assertEquals(ProxyProtocol.SOCKS5, ProxyList.parseLine("socks5h://h.example:1")!!.protocol)
        assertEquals(ProxyProtocol.HTTP, ProxyList.parseLine("https://h.example:443")!!.protocol)
        assertEquals("p:w", ProxyList.parseLine("http://u:p:w@h.example:8080")!!.password)
    }
    @Test fun trailingColumnsAndComments() {
        val r = ProxyList.parse("# header\n1.1.1.1:80 US elite\n\n2.2.2.2:81\tDE\n1.1.1.1:80\nnot a proxy\n999.1.1.1:80\n3.3.3.3:70000\n")
        assertEquals(2, r.proxies.size); assertEquals(1, r.duplicates); assertEquals(3, r.invalidLines)
    }
    @Test fun ipv6() {
        val e = ProxyList.parseLine("[2001:db8::1]:1080", ProxyProtocol.SOCKS5)!!
        assertEquals("2001:db8::1", e.host); assertEquals(1080, e.port)
    }
    @Test fun rejectsGarbage() {
        listOf("", "abc", "1.2.3:80", "host:0", "host:-1", "ftp://h:21", "a b:c").forEach { assertNull(it, ProxyList.parseLine(it)) }
    }
    @Test fun toStringHidesPassword() {
        val e = ProxyList.parseLine("u:topsecret@h.example:1")!!
        assert(!e.toString().contains("topsecret"))
    }
    @Test fun roundTripUrl() {
        val e = ProxyList.parseLine("socks5://us er:p@ss:w@h.example:9", ProxyProtocol.HTTP)
        val e2 = ProxyList.parseLine("socks5://user:p%40ss@h.example:9")!!
        assertEquals(e2, ProxyList.parseLine(ProxyList.toUrl(e2)))
        assertNull(e)
    }
}
