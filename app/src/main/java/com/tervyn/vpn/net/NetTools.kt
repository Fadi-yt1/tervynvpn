package com.tervyn.vpn.net

import com.tervyn.vpn.domain.ProxyEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.security.SecureRandom

/** Public IP information. Fields are null when the lookup service did not provide them. */
data class IpInfo(
    val ip: String,
    val countryCode: String?,
    val city: String? = null,
    val region: String? = null,
    val isp: String? = null,
    val viaVpn: Boolean,
)

class IpChecker(private val http: HttpsOverProxy = HttpsOverProxy()) {
    /**
     * @param proxy the active VPN upstream; when null the device's direct connection is checked (and the
     *        result is labelled as *not* protected by the UI).
     */
    suspend fun check(proxy: ProxyEndpoint?, traceUrl: String, lookupUrl: String): IpInfo = runInterruptible(Dispatchers.IO) {
        val r = http.request(traceUrl, proxy, timeoutMs = 10_000, maxBody = 16 * 1024)
        if (r.code != 200) throw IOException("IP check failed (HTTP ${r.code})")
        val t = ProxyTester.parseTrace(r.text)
        val ip = t["ip"] ?: throw IOException("IP check returned no address")
        var info = IpInfo(ip, t["loc"]?.takeIf { it.length == 2 && it != "XX" }, viaVpn = proxy != null)
        if (lookupUrl.startsWith("https://")) {
            runCatching {
                val lr = http.request(lookupUrl.replace("{ip}", ip), proxy, timeoutMs = 8_000, maxBody = 64 * 1024,
                    extraHeaders = mapOf("Accept" to "application/json"))
                if (lr.code == 200) info = mergeLookup(info, lr.text)
            }
        }
        info
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Understands ipinfo.io, ipwho.is and ip-api style responses. */
        fun mergeLookup(base: IpInfo, body: String): IpInfo {
            val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return base
            fun s(obj: JsonObject?, vararg k: String) = k.firstNotNullOfOrNull { (obj?.get(it) as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.isNotBlank() } }
            val conn = o["connection"] as? JsonObject
            val ip = s(o, "ip", "query")
            if (ip != null && ip != base.ip) return base // lookup answered for a different address: ignore it
            return base.copy(
                city = s(o, "city") ?: base.city,
                region = s(o, "region", "regionName") ?: base.region,
                isp = s(o, "org", "isp") ?: s(conn, "isp", "org") ?: base.isp,
                countryCode = base.countryCode ?: s(o, "country_code", "countryCode", "country")?.takeIf { it.length == 2 },
            )
        }
    }
}

/** Speed test against Cloudflare's speed endpoints, through the active proxy when connected. */
class SpeedTest(private val http: HttpsOverProxy = HttpsOverProxy()) {
    data class Result(
        val latencyMs: Int?,
        val jitterMs: Int?,
        val lossPct: Int?,
        val downloadBps: Double?,
        val uploadBps: Double?,
        val viaVpn: Boolean,
        val error: String? = null,
    )

    private class Stop : IOException("stop")

    suspend fun latency(proxy: ProxyEndpoint?, samples: Int = 5): Triple<Int?, Int?, Int> {
        val times = mutableListOf<Int>()
        var failed = 0
        repeat(samples) {
            runCatching {
                runInterruptible(Dispatchers.IO) { http.request("https://speed.cloudflare.com/__down?bytes=0", proxy, timeoutMs = 6000, maxBody = 1024) }
            }.onSuccess { times += it.firstByteMs.toInt() }.onFailure { failed++ }
        }
        if (times.isEmpty()) return Triple(null, null, 100)
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = if (times.size > 1) times.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toInt() else 0
        return Triple(median, jitter, failed * 100 / samples)
    }

    /** Downloads for up to [maxMs], reporting live throughput (bits/s). */
    suspend fun download(proxy: ProxyEndpoint?, maxMs: Long = 10_000, onLive: (Double) -> Unit = {}): Double = runInterruptible(Dispatchers.IO) {
        val start = System.nanoTime()
        var bytes = 0L
        try {
            http.request("https://speed.cloudflare.com/__down?bytes=50000000", proxy, timeoutMs = 15_000, discardBody = true) { total ->
                bytes = total
                val el = (System.nanoTime() - start) / 1e9
                if (el > 0.2) onLive(total * 8 / el)
                if (el * 1000 > maxMs) throw Stop()
            }
        } catch (_: Stop) {
        }
        val el = (System.nanoTime() - start) / 1e9
        if (bytes == 0L) throw IOException("No data received")
        bytes * 8 / el
    }

    suspend fun upload(proxy: ProxyEndpoint?, size: Int = 8_000_000): Double = runInterruptible(Dispatchers.IO) {
        val body = ByteArray(size).also { SecureRandom().nextBytes(it) }
        val r = http.request("https://speed.cloudflare.com/__up", proxy, method = "POST", body = body,
            contentType = "application/octet-stream", timeoutMs = 30_000, maxBody = 64 * 1024)
        if (r.code !in 200..299) throw IOException("Upload failed (HTTP ${r.code})")
        // Time from first byte sent to response, minus connection setup.
        val sec = (r.firstByteMs - r.connectMs - r.tlsMs).coerceAtLeast(1) / 1000.0
        size * 8 / sec
    }
}
