package com.tervyn.vpn.core

import android.content.Context
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.data.ServerRepository
import com.tervyn.vpn.data.SettingsRepository
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.domain.TestResult
import com.tervyn.vpn.net.HttpsOverProxy
import com.tervyn.vpn.net.ProxyTester
import com.tervyn.vpn.utils.Format
import com.tervyn.vpn.xray.XrayManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Server pool, testing, ranking and selection.
 *
 * Nothing is marked available until it passed an HTTPS test with certificate validation through the
 * server; countries shown as *verified* come only from the exit IP (Cloudflare `loc`), never guessed.
 */
class ServerManager(
    context: Context,
    private val settingsRepo: SettingsRepository,
    private val servers: ServerRepository,
    private val http: OkHttpClient,
    private val xray: XrayManager,
) {
    data class Progress(
        val phase: String = "",
        val tested: Int = 0,
        val total: Int = 0,
        val working: Int = 0,
        val running: Boolean = false,
        val message: String? = null,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()
    private val prefs = context.getSharedPreferences("tervyn_servers", Context.MODE_PRIVATE)
    private val installLock = Mutex()
    private val replaceLock = Mutex()

    fun tester() = ProxyTester(HttpsOverProxy(), ProxyTester.DEFAULT_TRACE_URL, Tuning.TEST_TIMEOUT_MS)

    // ---- built-in servers ------------------------------------------------------------------

    /** Installs the shipped servers on first launch and refreshes them when the app's table changes. */
    suspend fun ensureBuiltin() = installLock.withLock {
        val version = BuiltinServers.VERSION
        if (prefs.getInt("builtin_version", -1) == version && servers.count(ServerOrigin.BUILTIN) == BuiltinServers.all.size) return@withLock
        val fresh = BuiltinServers.all.mapIndexedNotNull { i, b ->
            runCatching { servers.builtinServer(b.countryCode, b.link, Links.parse(b.link), i) }.getOrNull()
        }
        servers.installBuiltin(fresh)
        prefs.edit().putInt("builtin_version", version).apply()
    }

    /**
     * Free servers come and go. For every built-in slot that failed its last test, fetch the Tervyn lists and
     * try candidates of the same country until one passes verification; that link then takes over the slot.
     * Returns the number of slots repaired.
     */
    suspend fun replaceDeadBuiltins(force: Boolean = false): Int = replaceLock.withLock {
        val dead = servers.byOrigins(setOf(ServerOrigin.BUILTIN)).filter { force || (!it.alive && it.lastChecked > 0) }
        if (dead.isEmpty()) return@withLock 0
        _progress.value = Progress("Looking for replacement servers", running = true)
        val candidates = runCatching { BuiltinUpdater.fetchCandidates(http) }.getOrElse {
            _progress.value = Progress("Server list unavailable", running = false, message = it.message); return@withLock 0
        }
        var repaired = 0
        for (slot in dead) {
            val cc = slot.declaredCountry ?: continue
            val options = candidates[cc].orEmpty().filter { it.first.substringBefore('#') != slot.xrayLink?.substringBefore('#') }.take(6)
            if (options.isEmpty()) continue
            val probes = options.map { (link, o) -> servers.linkServer(link, o, ServerOrigin.BUILTIN) }
            // Probe without touching the database: results are evaluated here, not recorded per candidate.
            val res = probe(probes, "Testing ${Format.countryName(cc)} servers")
            val winner = res.filter { it.second.ok }.minByOrNull { it.second.latencyMs } ?: continue
            val link = options.first { (l, _) -> ServerRepository.idForLink(l) == winner.first.id }
            servers.replaceBuiltinLink(slot.id, link.first, link.second)
            servers.recordTest(slot.id, winner.second)
            repaired++
        }
        _progress.value = Progress(if (repaired > 0) "Replaced $repaired server${if (repaired == 1) "" else "s"}" else "No replacement found", running = false)
        repaired
    }

    // ---- testing ---------------------------------------------------------------------------

    /** Tests a single server (starting a temporary Xray instance for share-link servers) and records the result. */
    suspend fun testServer(server: Server): TestResult {
        val t = tester()
        val result = try {
            if (server.isXray) {
                if (!xray.available) TestResult(false, error = "Xray is not available on this device")
                else xray.withTemporary(Links.parse(server.xrayLink!!)) { ep -> t.test(ep) }
            } else t.test(server.endpoint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TestResult(false, error = e.message ?: e.javaClass.simpleName)
        }
        servers.recordTest(server.id, result)
        return result
    }

    /** Like [testMany] but never writes results to the database (used for replacement candidates). */
    private suspend fun probe(list: List<Server>, phase: String): List<Pair<Server, TestResult>> =
        testMany(list, phase, overallTimeoutMs = 60_000, record = false)

    /**
     * Tests [list] with bounded concurrency. When [stopAfterGood] is set, returns early once that many
     * working servers were found and [graceMs] passed since the first success — so we pick the *fastest*
     * of several, not just the first responder.
     */
    suspend fun testMany(
        list: List<Server>,
        phase: String = "Testing servers",
        stopAfterGood: Int? = null,
        graceMs: Long = 4_000,
        overallTimeoutMs: Long = 120_000,
        concurrency: Int = Tuning.TEST_CONCURRENCY,
        record: Boolean = true,
    ): List<Pair<Server, TestResult>> {
        if (list.isEmpty()) return emptyList()
        val results = java.util.Collections.synchronizedList(mutableListOf<Pair<Server, TestResult>>())
        val tested = AtomicInteger()
        val good = AtomicInteger()
        val firstGoodAt = AtomicLong(0)
        _progress.value = Progress(phase, 0, list.size, 0, running = true)
        val sem = Semaphore(concurrency.coerceIn(1, 128))
        val t = tester()

        // Share-link servers are tested through one batched Xray process (one local SOCKS5 port per server).
        val xrayServers = if (xray.available) list.filter { it.isXray } else emptyList()
        val parsed = xrayServers.mapNotNull { srv -> runCatching { srv to Links.parse(srv.xrayLink!!) }.getOrNull() }

        suspend fun one(srv: Server, ep: ProxyEndpoint?, viaBatch: Boolean): TestResult = when {
            viaBatch -> if (ep == null) TestResult(false, error = "Xray could not start this server") else t.test(ep)
            srv.isXray -> if (!xray.available) TestResult(false, error = "Xray is not available on this device")
                else runCatching { xray.withTemporary(Links.parse(srv.xrayLink!!)) { e -> t.test(e) } }.getOrElse { TestResult(false, error = it.message ?: "failed") }
            else -> t.test(srv.endpoint)
        }

        suspend fun runAll(xrayEndpoints: Map<String, ProxyEndpoint?>) = coroutineScope {
            val jobs = list.map { srv ->
                launch {
                    sem.withPermit {
                        if (stopAfterGood != null && good.get() >= stopAfterGood &&
                            firstGoodAt.get() > 0 && System.currentTimeMillis() - firstGoodAt.get() > graceMs) return@withPermit
                        val r = one(srv, xrayEndpoints[srv.id], srv.id in xrayEndpoints)
                        if (record) servers.recordTest(srv.id, r)
                        results += srv to r
                        if (r.ok && good.incrementAndGet() == 1) firstGoodAt.set(System.currentTimeMillis())
                        _progress.value = Progress(phase, tested.incrementAndGet(), list.size, good.get(), running = true)
                    }
                }
            }
            if (stopAfterGood != null) {
                launch {
                    while (jobs.any { it.isActive }) {
                        kotlinx.coroutines.delay(250)
                        if (good.get() >= stopAfterGood && System.currentTimeMillis() - firstGoodAt.get() > graceMs) jobs.forEach { it.cancel() }
                    }
                }
            }
        }

        withTimeoutOrNull(overallTimeoutMs) {
            if (parsed.isEmpty()) runAll(emptyMap())
            else xray.withBatch(parsed.map { it.second }) { eps ->
                val map = HashMap<String, ProxyEndpoint?>()
                parsed.forEachIndexed { k, (srv, _) -> map[srv.id] = eps[k] }
                xrayServers.filter { it.id !in map }.forEach { map[it.id] = null } // unparsable link: reported as failure
                runAll(map)
            }
        }
        _progress.value = _progress.value.copy(running = false)
        return results.toList()
    }

    /** Re-tests every server, then repairs built-in slots that are still down. Returns (working, tested, repaired). */
    suspend fun refreshAll(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        ensureBuiltin()
        val res = testMany(pool(), "Testing servers", overallTimeoutMs = 180_000)
        val repaired = replaceDeadBuiltins()
        Triple(res.count { it.second.ok } + repaired, res.size, repaired)
    }

    // ---- selection -------------------------------------------------------------------------

    /** Every server the user can connect to, built-in slots first. */
    suspend fun pool(): List<Server> = servers.all().sortedWith(compareBy<Server> { if (it.isBuiltin) 0 else 1 }.thenBy { it.addedAt })

    class NoServerException(message: String) : Exception(message)

    /**
     * Chooses (and verifies) the server to connect to. A manual choice (auto-switch off) is honoured strictly;
     * otherwise the fastest verified server is used, re-checking recent results first and testing the whole
     * pool only when needed.
     */
    suspend fun selectForConnect(s: AppSettings, onPhase: (String) -> Unit): Pair<Server, TestResult> = withContext(Dispatchers.IO) {
        ensureBuiltin()
        val manualId = s.selectedServerId
        if (!s.autoSwitch && manualId != null) {
            val srv = servers.byId(manualId) ?: throw NoServerException("The selected server no longer exists")
            onPhase("TESTING")
            val r = testServer(srv)
            if (!r.ok) throw NoServerException("${srv.name} is not responding (${r.error ?: "test failed"})")
            return@withContext servers.byId(srv.id)!! to r
        }
        val now = System.currentTimeMillis()
        val pool = pool()
        if (pool.isEmpty()) throw NoServerException("No servers available. Add a server or restore the built-in list.")
        // 1) Re-verify recently good servers (fast path).
        val recent = Ranking.rank(pool.filter { now - it.lastChecked < Tuning.RECENT_RESULT_MS }).take(8)
        if (recent.isNotEmpty()) {
            onPhase("TESTING")
            best(testMany(recent, "Checking recent servers", stopAfterGood = 3, graceMs = 2_000, overallTimeoutMs = 25_000))?.let { return@withContext it }
        }
        // 2) Test the pool: untested first, then previously working, then the rest.
        onPhase("TESTING")
        val candidates = pool.sortedWith(compareBy<Server> { if (it.lastChecked == 0L) 0 else if (it.alive) 1 else 2 }.thenBy { it.latencyMs ?: Int.MAX_VALUE })
        best(testMany(candidates, "Testing servers", stopAfterGood = 4, graceMs = 4_000, overallTimeoutMs = 120_000))?.let { return@withContext it }
        // 3) Nothing works: try to repair built-in slots from the Tervyn lists, then use a repaired one.
        onPhase("FETCHING")
        if (replaceDeadBuiltins() > 0) {
            Ranking.rank(pool()).firstOrNull()?.let { fixed ->
                onPhase("TESTING")
                val r = testServer(fixed)
                if (r.ok) return@withContext servers.byId(fixed.id)!! to r
            }
        }
        throw NoServerException("No working server found (${candidates.size} tested). Check your internet connection and try again.")
    }

    private suspend fun best(res: List<Pair<Server, TestResult>>): Pair<Server, TestResult>? {
        val ok = res.filter { it.second.ok }.mapNotNull { (srv, r) -> servers.byId(srv.id)?.let { it to r } }
        return ok.minByOrNull { Ranking.score(it.first) }
    }

    /** Top verified backups (excluding [activeId]) used by failover / auto-switch. */
    suspend fun backups(activeId: String?, n: Int = 5): List<Server> = Ranking.rank(pool().filter { it.id != activeId }).take(n)

    fun xrayAvailable() = xray.available
}

/**
 * Finds replacement links for built-in slots in the Tervyn lists. Only TLS/REALITY VLESS, Trojan and VMess
 * links with a declared country are considered; the fastest list is preferred over the full one.
 */
object BuiltinUpdater {
    const val FASTEST_URL = "https://vless.svinakraft.workers.dev/fastest.txt"
    const val ALL_URL = "https://vless.svinakraft.workers.dev/podpiska.txt"

    private fun eligible(o: XrayOutbound) =
        o.protocol in setOf(XrayProtocol.VLESS, XrayProtocol.TROJAN, XrayProtocol.VMESS) && o.security in setOf("tls", "reality")

    /** Candidate links per country code, best first. */
    fun rank(fastest: String?, all: String?): Map<String, List<Pair<String, XrayOutbound>>> {
        val out = LinkedHashMap<String, MutableList<Pair<String, XrayOutbound>>>()
        val seen = HashSet<String>()
        fun add(body: String?, tier: Int) {
            if (body == null) return
            val links = Links.parseSubscription(body).links
                .filter { eligible(it.second) }
                .sortedWith(compareBy<Pair<String, XrayOutbound>> { if (it.second.security == "reality") 0 else 1 }
                    .thenBy { if (it.second.network in setOf("tcp", "raw", "grpc", "xhttp")) 0 else 1 })
            for ((link, o) in links) {
                val cc = Format.countryCodeFromFlag(o.name) ?: continue
                val key = link.substringBefore('#')
                if (!seen.add(key)) continue
                out.getOrPut(cc) { mutableListOf() }.add(link to o)
            }
            if (tier == 0) out.values.forEach { l -> l.forEach { seen.add(it.first.substringBefore('#')) } }
        }
        add(fastest, 0)
        add(all, 1)
        return out
    }

    suspend fun fetchCandidates(http: OkHttpClient): Map<String, List<Pair<String, XrayOutbound>>> = withContext(Dispatchers.IO) {
        fun get(url: String): String? = runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "TervynVPN/1.0").build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body.string().also { if (it.length > 8_000_000) throw IOException("list too large") }
            }
        }.getOrNull()
        val fastest = get(FASTEST_URL)
        val all = get(ALL_URL)
        if (fastest == null && all == null) throw IOException("Could not download the Tervyn server lists")
        rank(fastest, all)
    }
}
