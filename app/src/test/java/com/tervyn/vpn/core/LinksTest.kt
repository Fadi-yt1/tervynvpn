package com.tervyn.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LinksTest {
    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    @Test fun vlessReality() {
        val o = Links.parse("vless://$uuid@203.0.113.5:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome&pbk=Z84J2IelR9ch3k8VtlVhhs5ycBUlXA7wHBWcBrjqnAw&sid=6ba85179e30d4fc2&type=tcp#My%20Server")
        assertEquals(XrayProtocol.VLESS, o.protocol); assertEquals("reality", o.security); assertEquals("xtls-rprx-vision", o.flow)
        assertEquals("www.microsoft.com", o.sni); assertEquals("6ba85179e30d4fc2", o.shortId); assertEquals("My Server", o.name)
        assertEquals("VLESS · REALITY · Vision", o.displayProtocol)
    }
    @Test fun vlessWsTls() {
        val o = Links.parse("vless://$uuid@cdn.example.com:443?security=tls&type=ws&host=cdn.example.com&path=%2Fws%3Fed%3D2048&sni=cdn.example.com")
        assertEquals("ws", o.network); assertEquals("/ws?ed=2048", o.path)
    }
    @Test fun vlessGrpcXhttpHttpupgrade() {
        assertEquals("grpc", Links.parse("vless://$uuid@h.example:443?security=tls&type=grpc&serviceName=svc&mode=multi").network)
        assertEquals("xhttp", Links.parse("vless://$uuid@h.example:443?security=tls&type=xhttp&path=%2Fx&mode=packet-up").network)
        assertEquals("httpupgrade", Links.parse("vless://$uuid@h.example:443?security=tls&type=httpupgrade&path=%2Fu").network)
    }
    @Test fun vmess() {
        val json = """{"v":"2","ps":"vm","add":"vm.example.com","port":"443","id":"$uuid","aid":"0","scy":"auto","net":"ws","type":"none","host":"vm.example.com","path":"/v","tls":"tls","sni":"vm.example.com"}"""
        val o = Links.parse("vmess://" + Base64.getEncoder().encodeToString(json.toByteArray()))
        assertEquals(XrayProtocol.VMESS, o.protocol); assertEquals("ws", o.network); assertEquals("tls", o.security); assertEquals("vm", o.name)
        val again = Links.parse(Links.toLink(o)); assertEquals(o.copy(alpn = again.alpn), again)
    }
    @Test fun trojan() {
        val o = Links.parse("trojan://pa%24%24@tj.example.com:443?sni=tj.example.com#TJ")
        assertEquals("pa$$", o.password); assertEquals("tls", o.security)
    }
    @Test fun shadowsocksSip002AndLegacy() {
        val ui = Base64.getUrlEncoder().withoutPadding().encodeToString("aes-256-gcm:secret".toByteArray())
        val a = Links.parse("ss://$ui@ss.example.com:8388#SS")
        assertEquals("aes-256-gcm", a.method); assertEquals("secret", a.password); assertEquals(8388, a.port)
        val legacy = Base64.getEncoder().encodeToString("chacha20-ietf-poly1305:pw@1.2.3.4:8389".toByteArray())
        val b = Links.parse("ss://$legacy#L")
        assertEquals("chacha20-ietf-poly1305", b.method); assertEquals("1.2.3.4", b.address)
        val c = Links.parse("ss://2022-blake3-aes-128-gcm:AAECAwQFBgcICQoLDA0ODw%3D%3D@h.example:443")
        assertEquals("2022-blake3-aes-128-gcm", c.method); assertTrue(c.password.endsWith("="))
        // 32-byte key for a 16-byte cipher is rejected (Xray would refuse it too)
        assertTrue(runCatching { Links.parse("ss://2022-blake3-aes-128-gcm:YctPZ6U7xPPcU%2Bgp3u%2B0tx%2FtRizJN9K8y%2BuKlW2qjlI%3D@h.example:443") }.isFailure)
    }
    @Test fun insecureIsFlaggedNotHonoured() {
        val o = Links.parse("trojan://p@h.example:443?allowInsecure=1")
        assertTrue(o.insecureRequested)
        assertTrue(!com.tervyn.vpn.xray.XrayConfig.build(o, com.tervyn.vpn.xray.XrayConfig.LocalSocks(1, "u", "p")).contains("allowInsecure"))
    }
    @Test fun plaintextVlessRejectedExceptPrivate() {
        assertTrue(runCatching { Links.parse("vless://$uuid@h.example.com:80?type=ws") }.isFailure)
        assertEquals("192.168.1.10", Links.parse("vless://$uuid@192.168.1.10:80?type=ws").address)
    }
    @Test fun rejectsInvalid() {
        listOf("vless://@h:1", "vless://$uuid@h.example:443?security=reality", "vmess://notbase64!!", "ss://bad", "foo://x", "trojan://@h.example:1")
            .forEach { s -> assertTrue(s, runCatching { Links.parse(s) }.isFailure) }
    }
    @Test fun subscriptionBase64() {
        val body = Base64.getEncoder().encodeToString("vless://$uuid@a.example:443?security=tls\ntrojan://p@b.example:443\ngarbage://x\n".toByteArray())
        val (ok, errors) = Links.parseMany(body)
        assertEquals(2, ok.size); assertEquals(1, errors)
    }
    @Test fun roundTripVless() {
        val link = "vless://$uuid@203.0.113.5:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=a.example&fp=chrome&pbk=KEY&sid=ab&type=tcp#N"
        val o = Links.parse(link); assertEquals(o, Links.parse(Links.toLink(o)))
    }
}
