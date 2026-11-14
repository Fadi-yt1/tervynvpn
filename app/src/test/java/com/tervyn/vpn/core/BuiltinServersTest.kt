package com.tervyn.vpn.core

import com.tervyn.vpn.xray.XrayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** The shipped server table must stay well-formed: one country per slot, parseable, encrypted, and accepted by Xray. */
class BuiltinServersTest {
    private val bin = File(System.getProperty("xray.host.binary") ?: "../xray/host/xray")

    @Test fun twentyDistinctCountries() {
        assertEquals(20, BuiltinServers.all.size)
        assertEquals(20, BuiltinServers.countryCodes.toSet().size)
        BuiltinServers.countryCodes.forEach { cc ->
            assertTrue("bad code $cc", cc.length == 2 && cc.all { it.isUpperCase() })
            assertTrue("unknown country $cc", com.tervyn.vpn.utils.Format.countryName(cc) != cc)
        }
    }

    @Test fun everyLinkParsesAndIsEncrypted() {
        BuiltinServers.all.forEach { b ->
            val o = Links.parse(b.link)
            assertTrue("${b.countryCode}: ${o.protocol} must use TLS or REALITY", o.security == "tls" || o.security == "reality")
            assertTrue("${b.countryCode}: links are stored without provider names", !b.link.contains('#'))
            assertTrue("${b.countryCode}: insecure TLS must not be requested", !o.insecureRequested)
        }
    }

    @Test fun xrayAcceptsTheWholeTable() {
        assumeTrue("host xray binary not available", bin.canExecute())
        val outbounds = BuiltinServers.all.map { Links.parse(it.link) }
        val work = kotlin.io.path.createTempDirectory("builtin").toFile()
        try {
            val f = File(work, "all.json")
            f.writeText(XrayConfig.buildMulti(outbounds.mapIndexed { i, o -> o to XrayConfig.LocalSocks(21000 + i, "u", "p") }))
            val p = ProcessBuilder(bin.absolutePath, "run", "-test", "-c", f.absolutePath).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            assertTrue(p.waitFor(60, TimeUnit.SECONDS))
            assertEquals("xray rejected the built-in table:\n$out", 0, p.exitValue())
        } finally {
            work.deleteRecursively()
        }
    }

    @Test fun updaterRanksEncryptedLinksByCountry() {
        val body = """
            #profile-title: test
            vless://11111111-1111-1111-1111-111111111111@1.2.3.4:443?security=reality&sni=a.com&pbk=abc&fp=chrome&type=tcp#0001 | 🇩🇪 Germany | [40ms]
            vless://11111111-1111-1111-1111-111111111112@1.2.3.5:443?security=tls&sni=b.com&type=ws&path=/x#0002 | 🇩🇪 Germany | [41ms]
            vless://11111111-1111-1111-1111-111111111113@1.2.3.6:443?security=none&type=tcp#0003 | 🇫🇷 France
            trojan://pw@1.2.3.7:443?security=tls&sni=c.com#0004 | 🇫🇷 France
        """.trimIndent()
        val ranked = BuiltinUpdater.rank(body, null)
        assertEquals(listOf("DE", "FR"), ranked.keys.toList())
        assertEquals(2, ranked.getValue("DE").size)
        assertEquals("reality", ranked.getValue("DE")[0].second.security) // REALITY/TCP preferred over TLS/WS
        assertEquals(1, ranked.getValue("FR").size) // the unencrypted VLESS is not a candidate
    }
}
