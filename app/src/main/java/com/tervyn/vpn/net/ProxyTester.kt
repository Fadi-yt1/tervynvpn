package com.tervyn.vpn.net

import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.net.ssl.SSLException

/**
 * Validates proxies by fetching Cloudflare's trace endpoint *through* the proxy over HTTPS with full
 * certificate validation. A proxy is only considered working when the TLS handshake succeeds against
 * the genuine certificate and the trace returns the exit IP and its country (`loc`).
 */
class ProxyTester(
    private val http: HttpsOverProxy = HttpsOverProxy(),
    var traceUrl: String = DEFAULT_TRACE_URL,
    var timeoutMs: Int = 8_000,
) {
    companion object {
        const val DEFAULT_TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"

        /** Parses `key=value` lines of /cdn-cgi/trace. */
        fun parseTrace(body: String): Map<String, String> = body.lineSequence()
            .filter { it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }

    fun testBlocking(proxy: ProxyEndpoint?): TestResult = try {
        val r = http.request(traceUrl, proxy, timeoutMs = timeoutMs, maxBody = 16 * 1024)
        if (r.code != 200) TestResult(false, error = "HTTP ${r.code}")
        else {
            val t = parseTrace(r.text)
            val ip = t["ip"]
            val loc = t["loc"]?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) && it != "XX" && it != "T1" }
            if (ip.isNullOrEmpty()) TestResult(false, error = "Invalid trace response")
            else TestResult(true, r.totalMs.toInt(), ip, loc)
        }
    } catch (e: SSLException) {
        TestResult(false, error = "TLS verification failed")
    } catch (e: ProxyException) {
        TestResult(false, error = e.message)
    } catch (e: Exception) {
        TestResult(false, error = e.javaClass.simpleName + (e.message?.let { ": ${it.take(80)}" } ?: ""))
    }

    suspend fun test(proxy: ProxyEndpoint?): TestResult =
        withTimeoutOrNull(timeoutMs * 2L + 1000) { runInterruptible(Dispatchers.IO) { testBlocking(proxy) } }
            ?: TestResult(false, error = "Timeout")

    /**
     * Tests many proxies with bounded concurrency. [onResult] is invoked as each result arrives so
     * callers can stop early (by cancelling) once enough good servers were found.
     */
    suspend fun testAll(
        proxies: List<ProxyEndpoint>,
        concurrency: Int = 64,
        onResult: suspend (ProxyEndpoint, TestResult) -> Unit = { _, _ -> },
    ): List<Pair<ProxyEndpoint, TestResult>> = coroutineScope {
        val sem = Semaphore(concurrency.coerceIn(1, 128))
        proxies.map { p ->
            async {
                sem.withPermit {
                    val r = test(p)
                    onResult(p, r)
                    p to r
                }
            }
        }.awaitAll()
    }
}
