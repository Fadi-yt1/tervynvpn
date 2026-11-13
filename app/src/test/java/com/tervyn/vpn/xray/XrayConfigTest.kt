package com.tervyn.vpn.xray

import com.tervyn.vpn.core.Links
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

class XrayConfigTest {
    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"
    private val local = XrayConfig.LocalSocks(10808, "tuser", "s3cretpass")
    private val links = listOf(
        "vless://$uuid@203.0.113.5:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome&pbk=Z84J2IelR9ch3k8VtlVhhs5ycBUlXA7wHBWcBrjqnAw&sid=6ba85179e30d4fc2&type=tcp#reality",
        "vless://$uuid@cdn.example.com:443?security=tls&type=ws&host=cdn.example.com&path=%2Fws&sni=cdn.example.com&fp=chrome",
        "vless://$uuid@h.example.com:443?security=tls&type=grpc&serviceName=svc&mode=multi",
        "vless://$uuid@h.example.com:443?security=tls&type=xhttp&path=%2Fx&mode=auto",
        "vless://$uuid@h.example.com:443?security=tls&type=httpupgrade&path=%2Fu&host=h.example.com",
        "vmess://" + Base64.getEncoder().encodeToString("""{"v":"2","ps":"vm","add":"vm.example.com","port":"443","id":"$uuid","aid":"0","scy":"auto","net":"ws","type":"none","host":"vm.example.com","path":"/v","tls":"tls","sni":"vm.example.com"}""".toByteArray()),
        "trojan://password@tj.example.com:443?sni=tj.example.com&pcs=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855#tj",
        "ss://" + Base64.getUrlEncoder().withoutPadding().encodeToString("aes-256-gcm:secret".toByteArray()) + "@ss.example.com:8388#ss",
        "ss://2022-blake3-aes-128-gcm:AAECAwQFBgcICQoLDA0ODw%3D%3D@ss.example.com:443",
        "socks://" + Base64.getUrlEncoder().withoutPadding().encodeToString("u:p".toByteArray()) + "@s.example.com:1080",
        "http://u:p@h.example.com:8080",
    )

    @Test fun inboundIsLocalAndAuthenticated() {
        val cfg = Json.parseToJsonElement(XrayConfig.build(Links.parse(links[0]), local)).jsonObject
        val inbound = cfg["inbounds"]!!.jsonArray[0].jsonObject
        assertEquals("127.0.0.1", inbound["listen"]!!.jsonPrimitive.content)
        assertEquals(10808, inbound["port"]!!.jsonPrimitive.int)
        val settings = inbound["settings"]!!.jsonObject
        assertEquals("password", settings["auth"]!!.jsonPrimitive.content)
        assertEquals("s3cretpass", settings["accounts"]!!.jsonArray[0].jsonObject["pass"]!!.jsonPrimitive.content)
        val stream = cfg["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        assertEquals("reality", stream["security"]!!.jsonPrimitive.content)
        assertEquals("raw", stream["network"]!!.jsonPrimitive.content)
    }

    @Test fun neverInsecure() {
        links.forEach { assertFalse(XrayConfig.build(Links.parse(it), local).contains("allowInsecure")) }
    }

    /**
     * Validates every generated config with the real Xray-core v26.9.30 binary (`xray run -test`).
     * Runs when the host build of Xray exists (see scripts/build-xray.sh).
     */
    @Test fun configsAcceptedByXrayCore() {
        val bin = File(System.getProperty("xray.host.binary") ?: "../xray/host/xray")
        assumeTrue("host xray binary not available: ${bin.absolutePath}", bin.canExecute())
        val dir = kotlin.io.path.createTempDirectory("xraycfg").toFile()
        links.forEach { link ->
            val f = File(dir, "c.json").apply { writeText(XrayConfig.build(Links.parse(link), local)) }
            val p = ProcessBuilder(bin.absolutePath, "run", "-test", "-c", f.absolutePath).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(20, TimeUnit.SECONDS)
            assertEquals("xray rejected config for $link:\n$out", 0, p.exitValue())
        }
        dir.deleteRecursively()
    }
}
