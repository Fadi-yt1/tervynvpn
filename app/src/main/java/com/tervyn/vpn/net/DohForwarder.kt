package com.tervyn.vpn.net

import com.tervyn.vpn.domain.ProxyEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** DNS-over-HTTPS (RFC 8484) forwarding, either directly or tunnelled through the active proxy. */
class DohForwarder(
    private val urlProvider: () -> String,
    private val directClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build(),
    private val tunnelled: HttpsOverProxy = HttpsOverProxy(),
) {
    companion object {
        private val DNS_MESSAGE = "application/dns-message".toMediaType()
        val PRESETS = linkedMapOf(
            "Cloudflare (1.1.1.1)" to "https://1.1.1.1/dns-query",
            "Google (8.8.8.8)" to "https://8.8.8.8/dns-query",
            "Quad9 (9.9.9.9)" to "https://9.9.9.9/dns-query",
            "AdGuard DNS" to "https://dns.adguard-dns.com/dns-query",
            "OpenDNS" to "https://doh.opendns.com/dns-query",
        )
    }

    suspend fun direct(query: ByteArray): ByteArray? = runInterruptible(Dispatchers.IO) {
        val req = Request.Builder().url(urlProvider())
            .header("Accept", "application/dns-message")
            .post(query.toRequestBody(DNS_MESSAGE)).build()
        directClient.newCall(req).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
    }

    suspend fun viaProxy(proxy: ProxyEndpoint, query: ByteArray): ByteArray? = runInterruptible(Dispatchers.IO) {
        val r = tunnelled.request(
            urlProvider(), proxy, method = "POST", body = query, contentType = "application/dns-message",
            timeoutMs = 6_000, maxBody = 64 * 1024, extraHeaders = mapOf("Accept" to "application/dns-message"),
        )
        if (r.code == 200) r.body else null
    }
}
