package com.tervyn.vpn.core

import com.tervyn.vpn.domain.ProxyProtocol
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxioTest {
    @Test fun parsesTextAndJsonShapes() {
        assertEquals(2, ProxioClient.parse("1.1.1.1:1080\n2.2.2.2:1080:u:p\n", ProxyProtocol.SOCKS5).size)
        val json = """{"data":[{"ip":"3.3.3.3","port":"8080","username":"a","password":"b","protocol":"http"},{"host":"h.example","port":1080}]}"""
        val r = ProxioClient.parse(json, ProxyProtocol.SOCKS5)
        assertEquals(2, r.size); assertEquals(ProxyProtocol.HTTP, r[0].protocol); assertEquals("a", r[0].username); assertEquals(ProxyProtocol.SOCKS5, r[1].protocol)
        assertEquals(1, ProxioClient.parse("""["socks5://u:p@4.4.4.4:1"]""", ProxyProtocol.HTTP).size)
    }

    @Test fun sendsKeyHeaderAndQuery() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().body("5.5.5.5:1080").build())
            server.start()
            val client = ProxioClient(OkHttpClient(), server.url("/api/list").toString())
            val r = client.fetch("KEY123", ProxyProtocol.SOCKS5, 25)
            assertEquals(1, r.size)
            val req = server.takeRequest()
            assertEquals("KEY123", req.headers["X-Key"])
            assertTrue(req.url.toString().contains("type=socks5")); assertTrue(req.url.toString().contains("limit=25"))
        }
    }

    @Test fun badKeyIsReported() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(401).build())
            server.start()
            val e = runCatching { ProxioClient(OkHttpClient(), server.url("/api/list").toString()).fetch("bad", ProxyProtocol.HTTP, 5) }.exceptionOrNull()
            assertTrue(e?.message?.contains("API key") == true)
        }
    }
}
