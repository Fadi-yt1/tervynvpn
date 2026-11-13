package com.tervyn.vpn.net

import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.testutil.MockProxy
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the full HTTPS-through-proxy verification path against a local TLS server: the tester must
 * accept a trusted certificate for the right host and reject wrong-host and untrusted certificates.
 */
class ProxyTesterTest {
    private val ca = HeldCertificate.Builder().certificateAuthority(0).build()
    private val good = HeldCertificate.Builder().addSubjectAlternativeName("trace.test").signedBy(ca).build()
    private val wrongHost = HeldCertificate.Builder().addSubjectAlternativeName("other.test").signedBy(ca).build()
    private val clientTrust = HandshakeCertificates.Builder().addTrustedCertificate(ca.certificate).build()
    private lateinit var server: MockWebServer
    private lateinit var proxy: MockProxy

    private fun start(cert: HeldCertificate) {
        server = MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
        repeat(3) { server.enqueue(MockResponse.Builder().body("fl=1\nh=www.cloudflare.com\nip=203.0.113.77\nloc=DE\ntls=TLSv1.3\n").build()) }
        server.start()
        proxy = MockProxy(true) { server.port }
    }

    @After fun tearDown() { runCatching { server.close() }; runCatching { proxy.close() } }

    private fun tester(trust: javax.net.ssl.SSLSocketFactory) =
        ProxyTester(HttpsOverProxy(trust), "https://trace.test/cdn-cgi/trace", 5000)

    @Test fun validCertificatePasses() = runBlocking {
        start(good)
        val r = tester(clientTrust.sslSocketFactory()).test(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", proxy.port))
        assertTrue(r.error, r.ok); assertEquals("203.0.113.77", r.exitIp); assertEquals("DE", r.countryCode); assertTrue(r.latencyMs >= 0)
        assertEquals("trace.test:443", proxy.targets.single())
    }

    @Test fun hostnameMismatchRejected() = runBlocking {
        start(wrongHost)
        val r = tester(clientTrust.sslSocketFactory()).test(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", proxy.port))
        assertFalse(r.ok); assertEquals("TLS verification failed", r.error)
    }

    @Test fun untrustedCertificateRejected() = runBlocking {
        start(good)
        val systemOnly = HandshakeCertificates.Builder().addPlatformTrustedCertificates().build()
        val r = tester(systemOnly.sslSocketFactory()).test(ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", proxy.port))
        assertFalse(r.ok)
    }

    @Test fun deadProxyFails() = runBlocking {
        start(good)
        val port = java.net.ServerSocket(0).use { it.localPort }
        val r = tester(clientTrust.sslSocketFactory()).test(ProxyEndpoint(ProxyProtocol.HTTP, "127.0.0.1", port))
        assertFalse(r.ok)
    }

    @Test fun concurrentTesting() = runBlocking {
        start(good)
        val eps = listOf(proxy.port, java.net.ServerSocket(0).use { it.localPort }).map { ProxyEndpoint(ProxyProtocol.SOCKS5, "127.0.0.1", it) }
        val results = tester(clientTrust.sslSocketFactory()).testAll(eps, concurrency = 64)
        assertEquals(listOf(true, false), results.map { it.second.ok })
    }

    @Test fun traceParsing() {
        val t = ProxyTester.parseTrace("ip=1.2.3.4\nloc=US\nwarp=off\n")
        assertEquals("1.2.3.4", t["ip"]); assertEquals("US", t["loc"])
    }
}
