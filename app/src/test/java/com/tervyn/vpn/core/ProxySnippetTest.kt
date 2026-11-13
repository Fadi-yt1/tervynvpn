package com.tervyn.vpn.core

import com.tervyn.vpn.domain.ProxyProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxySnippetTest {
    @Test fun curlProxyFlag() {
        val r = ProxySnippet.parse("""curl -x "http://user-abc:pw123@p.webshare.io:80/" https://ipv4.webshare.io/""")
        assertEquals(1, r.proxies.size)
        val e = r.proxies[0]
        assertEquals("p.webshare.io", e.host); assertEquals(80, e.port); assertEquals("user-abc", e.username); assertEquals("pw123", e.password)
    }
    @Test fun curlProxyUser() {
        val r = ProxySnippet.parse("curl --proxy brd.superproxy.io:33335 --proxy-user brd-customer-hl_1-zone-res:secret -k \"https://geo.brdtest.com/welcome.txt\"")
        val e = r.proxies.single()
        assertEquals("brd.superproxy.io", e.host); assertEquals(33335, e.port); assertEquals("brd-customer-hl_1-zone-res", e.username); assertEquals("secret", e.password)
    }
    @Test fun curlSocks5() {
        val e = ProxySnippet.parse("curl --socks5 gate.example.com:7000 -U 'u:p' https://ip.me").proxies.single()
        assertEquals(ProxyProtocol.SOCKS5, e.protocol); assertEquals("u", e.username)
    }
    @Test fun curlMultiline() {
        val e = ProxySnippet.parse("curl 'https://ip.oxylabs.io/location' \\\n  -U 'customer-USER:PASS' \\\n  -x 'pr.oxylabs.io:7777'").proxies.single()
        assertEquals("pr.oxylabs.io", e.host); assertEquals(7777, e.port); assertEquals("customer-USER", e.username); assertEquals("PASS", e.password)
    }
    @Test fun pythonDictFString() {
        val code = """
            import requests
            username = "customer-abc"
            password = "pa55"
            proxy = "pr.oxylabs.io:7777"
            proxies = {
              "http": f"http://{username}:{password}@{proxy}",
              "https": f"http://{username}:{password}@{proxy}",
            }
            response = requests.get("https://ip.oxylabs.io/location", proxies=proxies)
        """.trimIndent()
        val e = ProxySnippet.parse(code).proxies.single()
        assertEquals("pr.oxylabs.io", e.host); assertEquals("customer-abc", e.username); assertEquals("pa55", e.password)
    }
    @Test fun pythonConcatenation() {
        val code = "host = 'geo.iproyal.com'\nport = 12321\nuser = 'u1'\npw = 'p1'\nproxies = {'https': 'http://' + user + ':' + pw + '@' + host + ':' + str(port)}"
        val e = ProxySnippet.parse(code).proxies.single()
        assertEquals("geo.iproyal.com", e.host); assertEquals(12321, e.port); assertEquals("u1", e.username)
    }
    @Test fun axiosObject() {
        val code = """
            const axios = require('axios');
            axios.get('https://ipv4.webshare.io/', {
              proxy: {
                protocol: 'http',
                host: 'p.webshare.io',
                port: 80,
                auth: { username: 'abc-1', password: 'xyz' }
              }
            })
        """.trimIndent()
        val e = ProxySnippet.parse(code).proxies.single()
        assertEquals("p.webshare.io", e.host); assertEquals(80, e.port); assertEquals("abc-1", e.username); assertEquals("xyz", e.password)
    }
    @Test fun nodeAgentsTemplateLiteral() {
        val code = """
            const { SocksProxyAgent } = require('socks-proxy-agent');
            const user = 'nu'; const pass = 'np';
            const agent = new SocksProxyAgent(`socks5://${'$'}{user}:${'$'}{pass}@gw.example.org:1080`);
        """.trimIndent().replace("; const", ";\nconst")
        val e = ProxySnippet.parse(code).proxies.single()
        assertEquals(ProxyProtocol.SOCKS5, e.protocol); assertEquals("nu", e.username); assertEquals("gw.example.org", e.host)
    }
    @Test fun httpsProxyAgent() {
        val e = ProxySnippet.parse("const agent = new HttpsProxyAgent('http://a:b@1.2.3.4:3128');").proxies.single()
        assertEquals("1.2.3.4", e.host); assertEquals(3128, e.port)
    }
    @Test fun ignoresTargetUrls() {
        assertEquals(0, ProxySnippet.parse("requests.get('https://example.com/ip')").proxies.size)
    }
}
