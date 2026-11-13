package com.tervyn.vpn.core

import android.content.Context
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.data.SecureStore
import com.tervyn.vpn.data.ServerRepository
import com.tervyn.vpn.data.SettingsRepository
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.domain.TestResult
import com.tervyn.vpn.net.HttpsOverProxy
import com.tervyn.vpn.net.ProxyTester
import com.tervyn.vpn.xray.XrayManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Server discovery, testing, ranking and selection.
 *
 * Nothing is marked available until it passed an HTTPS test with certificate validation through the
 * proxy; countries come only from the verified exit IP (Cloudflare `loc`), never guessed.
 */
class ServerManager(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val servers: ServerRepository,
    private val http: OkHttpClient,
    private val xray: XrayManager,
    private val proxio: ProxioClient,
    private val secureStore: SecureStore,
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
    private val refreshLock = Mutex()
    @Volatile var lastListRefresh = 0L; private set

    fun tester(s: AppSettings) = ProxyTester(HttpsOverProxy(), s.traceUrl, s.testTimeoutMs)

    // ---- discovery -------------------------------------------------------------------------

    /** Downloads all enabled free proxy lists, de-duplicates and stores new candidates. Returns candidate count. */
    suspend fun refreshFreeLists(s: AppSettings, perSourceCap: Int = 250): Int = refreshLock.withLock {
        _progress.value = Progress("Fetching proxy lists", running = true)
        val urls = s.enabledSources
        val fetched = AtomicInteger()
        val lists = coroutineScope {
            urls.map { url ->
                async(Dispatchers.IO) {
                    val proto = ProxySources.DEFAULT.firstOrNull { it.url == url }?.protocol ?: ProxySources.guessProtocol(url)
                    val body = runCatching {
                        http.newCall(Request.Builder().url(url).header("User-Agent", "TervynVPN/1.0").build()).execute().use { r ->
                            if (r.isSuccessful) r.body.string().take(3_000_000) else null
                        }
                    }.getOrNull()
                    _progress.value = _progress.value.copy(tested = fetched.incrementAndGet(), total = urls.size, message = "Lists downloaded")
                    body?.let { ProxyList.parse(it, proto).proxies.shuffled().take(perSourceCap) } ?: emptyList()
                }
            }.awaitAll()
        }
        val unique = LinkedHashMap<String, ProxyEndpoint>()
        lists.flatten().forEach { unique.putIfAbsent(it.key, it) }
        servers.pruneFree()
        val existing = servers.byOrigins(setOf(ServerOrigin.FREE_PUBLIC)).associateBy { it.id }
        val fresh = unique.values.map { servers.endpointServer(it, ServerOrigin.FREE_PUBLIC) }.filter { it.id !in existing }
        servers.save(fresh)
        lastListRefresh = System.currentTimeMillis()
        val sourcesOk = lists.count { it.isNotEmpty() }
        _progress.value = Progress("Lists updated", running = false, message = "$sourcesOk/${urls.size} sources, ${unique.size} proxies")
        unique.size
    }

    /** Loads servers from Proxio.io when a key is configured. */
    suspend fun refreshProxio(s: AppSettings): Int {
        val key = secureStore.proxioKey() ?: return 0
        val protos = when (s.proxioProtocol) {
            "http" -> listOf(ProxyProtocol.HTTP)
            "both" -> listOf(ProxyProtocol.SOCKS5, ProxyProtocol.HTTP)
            else -> listOf(ProxyProtocol.SOCKS5)
        }
        val eps = protos.flatMap { proxio.fetch(key, it, if (protos.size == 2) maxOf(1, s.proxioLimit / 2) else s.proxioLimit) }
        servers.deleteOrigin(ServerOrigin.PROXIO)
        servers.save(eps.map { servers.endpointServer(it, ServerOrigin.PROXIO, "Proxio ${it.host}") })
        return eps.size
    }

    // ---- built-in Tervyn server lists ---------------------------------------------------------

    data class BuiltinStatus(val usable: Int = 0, val skipped: Map<String, Int> = emptyMap(), val updatedAt: Long = 0, val error: String? = null) {
        val skippedTotal get() = skipped.values.sum()
    }

    private val _builtin = MutableStateFlow(mapOf<ServerOrigin, BuiltinStatus>())
    val builtinStatus: StateFlow<Map<ServerOrigin, BuiltinStatus>> = _builtin.asStateFlow()
    private val builtinPrefs = context.getSharedPreferences("tervyn_builtin", Context.MODE_PRIVATE)

    fun builtinUpdatedAt(origin: ServerOrigin) = builtinPrefs.getLong("updated_${origin.name}", 0L)

    /** Downloads one of the built-in subscriptions and stores its servers (provider order is preserved). */
    suspend fun refreshBuiltin(origin: ServerOrigin): BuiltinStatus = withContext(Dispatchers.IO) {
        val url = BuiltinServers.urlFor(origin)
        _progress.value = Progress("Downloading ${BuiltinServers.labelFor(origin)}", running = true)
        try {
            val body = http.newCall(Request.Builder().url(url).header("User-Agent", "TervynVPN/1.0").build()).execute().use { r ->
                if (!r.isSuccessful) throw java.io.IOException("Server list returned HTTP ${r.code}")
                r.body.string().also { if (it.length > 8_000_000) throw java.io.IOException("Server list too large") }
            }
            val sub = Links.parseSubscription(body)
            if (sub.links.isEmpty()) throw java.io.IOException("The server list contained no usable servers")
            val base = System.currentTimeMillis()
            val fresh = sub.links.mapIndexed { i, (link, o) -> servers.linkServer(link, o, origin, addedAt = base + i) }
            servers.replaceOrigin(origin, fresh)
            builtinPrefs.edit().putLong("updated_${origin.name}", System.currentTimeMillis())
                .putInt("interval_${origin.name}", sub.updateIntervalHours ?: 1).apply()
            BuiltinStatus(sub.links.size, sub.skipped, System.currentTimeMillis())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            BuiltinStatus(error = e.message ?: "Download failed", updatedAt = builtinUpdatedAt(origin))
        }.also { st ->
            _builtin.value = _builtin.value + (origin to st)
            _progress.value = Progress(if (st.error == null) "Server list updated" else "Server list update failed", running = false, message = st.error)
        }
    }

    /** True when the built-in list has never been downloaded or is older than its advertised update interval. */
    fun builtinStale(origin: ServerOrigin): Boolean {
        val hours = builtinPrefs.getInt("interval_${origin.name}", 1).coerceIn(1, 48)
        return System.currentTimeMillis() - builtinUpdatedAt(origin) > hours * 3_600_000L
    }

    // ---- testing ---------------------------------------------------------------------------

    /** Tests a single server (starting a temporary Xray instance for share-link servers). */
    suspend fun testServer(server: Server, s: AppSettings? = null): TestResult {
        val settings = s ?: settingsRepo.current()
        val t = tester(settings)
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

    /**
     * Tests [list] with bounded concurrency. When [stopAfterGood] is set, returns early once that many
     * working servers were found and either all finished or [graceMs] passed since the first success —
     * so we pick the *fastest* of several, not just the first responder.
     */
    suspend fun testMany(
        list: List<Server>,
        s: AppSettings,
        phase: String = "Testing servers",
        stopAfterGood: Int? = null,
        graceMs: Long = 4_000,
        overallTimeoutMs: Long = 120_000,
    ): List<Pair<Server, TestResult>> {
        if (list.isEmpty()) return emptyList()
        val results = java.util.Collections.synchronizedList(mutableListOf<Pair<Server, TestResult>>())
        val tested = AtomicInteger()
        val good = AtomicInteger()
        val firstGoodAt = java.util.concurrent.atomic.AtomicLong(0)
        _progress.value = Progress(phase, 0, list.size, 0, running = true)
        val sem = Semaphore(s.testConcurrency.coerceIn(1, 128))
        val t = tester(s)

        // Share-link servers are tested through one batched Xray process (one local SOCKS5 port per server).
        val xrayServers = if (xray.available) list.filter { it.isXray } else emptyList()
        val parsed = xrayServers.mapNotNull { srv -> runCatching { srv to Links.parse(srv.xrayLink!!) }.getOrNull() }

        suspend fun runAll(xrayEndpoints: Map<String, ProxyEndpoint?>) = coroutineScope {
            val jobs = list.map { srv ->
                launch {
                    sem.withPermit {
                        if (stopAfterGood != null && good.get() >= stopAfterGood &&
                            firstGoodAt.get() > 0 && System.currentTimeMillis() - firstGoodAt.get() > graceMs) return@withPermit
                        val r = if (srv.id in xrayEndpoints) {
                            val ep = xrayEndpoints[srv.id]
                            (if (ep == null) TestResult(false, error = "Xray could not start this server") else t.test(ep))
                                .also { servers.recordTest(srv.id, it) }
                        } else testServer(srv, s)
                        results += srv to r
                        if (r.ok) { if (good.incrementAndGet() == 1) firstGoodAt.set(System.currentTimeMillis()) }
                        _progress.value = Progress(phase, tested.incrementAndGet(), list.size, good.get(), running = true)
                    }
                }
            }
            if (stopAfterGood != null) {
                // Watchdog: once enough good servers were found and the grace period elapsed, cancel the rest.
                launch {
                    while (jobs.any { it.isActive }) {
                        kotlinx.coroutines.delay(250)
                        if (good.get() >= stopAfterGood && System.currentTimeMillis() - firstGoodAt.get() > graceMs) {
                            jobs.forEach { it.cancel() }
                        }
                    }
                }
            }
        }

        withTimeoutOrNull(overallTimeoutMs) {
            if (parsed.isEmpty()) runAll(emptyMap())
            else xray.withBatch(parsed.map { it.second }) { eps ->
                val map = HashMap<String, ProxyEndpoint?>()
                parsed.forEachIndexed { k, (srv, _) -> map[srv.id] = eps[k] }
                // Servers whose link could not be parsed are reported as failures via the null endpoint.
                xrayServers.filter { it.id !in map }.forEach { map[it.id] = null }
                runAll(map)
            }
        }
        _progress.value = _progress.value.copy(running = false)
        return results.toList()
    }

    // ---- selection -------------------------------------------------------------------------

    suspend fun pool(s: AppSettings): List<Server> = servers.byOrigins(s.sourceMode.origins)

    class NoServerException(message: String) : Exception(message)

    /**
     * Chooses (and verifies) the server to connect to.
     * Manual selection (auto-switch off) is honoured strictly; otherwise the fastest verified server of the
     * configured source (and preferred country, if any) is used, refreshing lists only when needed.
     */
    suspend fun selectForConnect(s: AppSettings, onPhase: (String, Progress?) -> Unit): Pair<Server, TestResult> = withContext(Dispatchers.IO) {
        val manualId = s.selectedServerId
        if (!s.autoSwitch && manualId != null) {
            val srv = servers.byId(manualId) ?: throw NoServerException("The selected server no longer exists")
            onPhase("TESTING", null)
            val r = testServer(srv, s)
            if (!r.ok) throw NoServerException("${srv.name} is not responding (${r.error ?: "test failed"})")
            return@withContext servers.byId(srv.id)!! to r
        }
        fun fits(x: Server) = s.preferredCountry == null || x.countryCode == s.preferredCountry
        // 1) Re-verify recently good servers (fast path).
        val now = System.currentTimeMillis()
        var pool = pool(s)
        val recent = Ranking.rank(pool.filter { now - it.lastChecked < 45 * 60_000L }, s.preferredCountry).take(12)
        if (recent.isNotEmpty()) {
            onPhase("TESTING", null)
            val res = testMany(recent, s, "Checking recent servers", stopAfterGood = 3, graceMs = 2_000, overallTimeoutMs = 25_000)
            best(res, ::fits)?.let { return@withContext it }
        }
        // 2) Refresh sources that need it.
        val origins = s.sourceMode.origins
        for (o in listOf(ServerOrigin.TERVYN_FAST, ServerOrigin.TERVYN_ALL).filter { it in origins }) {
            if (pool.none { it.origin == o } || builtinStale(o)) {
                onPhase("FETCHING", null)
                val st = refreshBuiltin(o)
                if (st.error != null && pool.none { it.origin == o }) throw NoServerException("Could not download the Tervyn server list: ${st.error}")
            }
        }
        if (ServerOrigin.FREE_PUBLIC in origins) {
            val untested = pool.count { it.origin == ServerOrigin.FREE_PUBLIC && it.lastChecked == 0L }
            if (untested < 100 || now - lastListRefresh > s.listRefreshMin * 60_000L) {
                onPhase("FETCHING", null)
                runCatching { refreshFreeLists(s) }
            }
        }
        if (ServerOrigin.PROXIO in origins && pool.none { it.origin == ServerOrigin.PROXIO }) {
            onPhase("FETCHING", null)
            runCatching { refreshProxio(s) }.onFailure { if (origins.size == 1) throw NoServerException(it.message ?: "Proxio failed") }
        }
        pool = pool(s)
        if (pool.isEmpty()) throw NoServerException(
            when (s.sourceMode) {
                com.tervyn.vpn.domain.SourceMode.FREE -> "Could not download any proxy lists. Check your internet connection."
                com.tervyn.vpn.domain.SourceMode.PROXIO -> "No Proxio.io servers. Add your API key in Server Source."
                else -> "No servers configured for \"${s.sourceMode.label}\". Add servers in Server Source."
            }
        )
        // 3) Test candidates: untested and stale first-class citizens, then everything else.
        val candidates = pool.filter { it.failCount < 3 }
            .sortedWith(compareBy<Server> { if (it.alive) 0 else if (it.lastChecked == 0L) 1 else 2 }.thenBy { it.latencyMs ?: Int.MAX_VALUE }
                .thenBy { if (it.origin == ServerOrigin.TERVYN_FAST) 0 else 1 }.thenBy { it.addedAt })
            .let { l -> if (origins.any { it == ServerOrigin.TERVYN_FAST || it == ServerOrigin.TERVYN_ALL }) l else l.take(40) + l.drop(40).shuffled() }
            .take(if (ServerOrigin.TERVYN_ALL in origins) 400 else 1500)
        onPhase("TESTING", null)
        val res = testMany(candidates, s, "Testing servers", stopAfterGood = 6, graceMs = 4_000, overallTimeoutMs = 150_000)
        best(res, ::fits) ?: throw NoServerException(
                if (s.preferredCountry != null && res.any { it.second.ok }) "No working server in ${s.preferredCountry}. Choose another country."
                else "No working server found (${res.size} tested). Try again or change the server source."
            )
    }

    private suspend fun best(res: List<Pair<Server, TestResult>>, fits: (Server) -> Boolean): Pair<Server, TestResult>? {
        val ok = res.filter { it.second.ok }.mapNotNull { (srv, r) -> servers.byId(srv.id)?.let { it to r } }.filter { fits(it.first) }
        return ok.minByOrNull { Ranking.score(it.first) }
    }

    /** Top verified backups (excluding [activeId]) used by failover / auto-switch. */
    suspend fun backups(s: AppSettings, activeId: String?, n: Int = 5): List<Server> =
        Ranking.rank(pool(s).filter { it.id != activeId }, s.preferredCountry).take(n)

    /** Countries that have at least one verified server in the current pool. */
    suspend fun countries(s: AppSettings): Map<String, List<Server>> =
        pool(s).filter { it.alive && it.countryCode != null }.groupBy { it.countryCode!! }

    fun xrayAvailable() = xray.available

    @Suppress("unused")
    private val appContext = context.applicationContext
}


/** The Tervyn server lists built into the app (standard base64/plain subscription format). */
object BuiltinServers {
    const val FASTEST_URL = "https://vless.svinakraft.workers.dev/fastest.txt"
    const val ALL_URL = "https://vless.svinakraft.workers.dev/podpiska.txt"

    fun urlFor(origin: ServerOrigin) = if (origin == ServerOrigin.TERVYN_ALL) ALL_URL else FASTEST_URL
    fun labelFor(origin: ServerOrigin) = if (origin == ServerOrigin.TERVYN_ALL) "all Tervyn servers" else "fastest Tervyn servers"
}
