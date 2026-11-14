package com.tervyn.vpn.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tervyn.vpn.TervynApp
import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.core.LinkParseException
import com.tervyn.vpn.core.Links
import com.tervyn.vpn.core.ProxyList
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.data.SplitMode
import com.tervyn.vpn.data.SubscriptionRepository
import com.tervyn.vpn.database.DomainEntity
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.domain.TestResult
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.net.DnsMessage
import com.tervyn.vpn.net.HttpsOverProxy
import com.tervyn.vpn.net.IpChecker
import com.tervyn.vpn.net.IpInfo
import com.tervyn.vpn.net.ProxyTester
import com.tervyn.vpn.net.SpeedTest
import com.tervyn.vpn.security.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetAddress

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val c = (app as TervynApp).container
    private val ctx: Context get() = getApplication()

    val status = c.controller.status
    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val servers: StateFlow<List<Server>> = c.servers.servers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val domains: StateFlow<List<DomainEntity>> = c.domains.domains.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val subscriptions = c.subscriptions.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = c.history.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val speedTests = c.history.speedTests().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progress = c.serverManager.progress
    val pinHash = c.settings.pinHash.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    fun toast(msg: String) { _messages.tryEmit(msg) }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.serverManager.ensureBuiltin() }
            // First launch: measure the built-in servers once so the list shows real status, not "not tested".
            val builtin = runCatching { c.servers.byOrigins(setOf(ServerOrigin.BUILTIN)) }.getOrDefault(emptyList())
            if (builtin.isNotEmpty() && builtin.all { it.lastChecked == 0L } && c.settings.current().onboardingDone) refreshServers()
        }
    }

    // ---- connection --------------------------------------------------------------------------

    fun connect() = c.controller.connect()
    fun disconnect() = c.controller.disconnect()

    fun selectServer(id: String?) = viewModelScope.launch {
        c.controller.selectServer(id)
        toast(if (id == null) "Fastest available: Tervyn picks and keeps the best server" else "Server selected")
    }

    /** Persists the selection first, then connects, so the service reads the new choice. */
    fun selectAndConnect(id: String, connect: () -> Unit) = viewModelScope.launch {
        c.controller.selectServer(id)
        connect()
    }

    /**
     * "Fastest available" on/off. Turning it off needs a concrete server: the connected one, else the best verified
     * one; without any, the choice is left on and the user is pointed to the Servers tab.
     */
    fun setAutoSwitch(on: Boolean) = viewModelScope.launch {
        if (on) { c.controller.selectServer(null); return@launch }
        val pick = status.value.server?.id ?: c.serverManager.backups(null, 1).firstOrNull()?.id
        if (pick == null) toast("Choose a server in the Servers tab first") else c.controller.selectServer(pick)
    }

    fun setMode(mode: VpnMode) = viewModelScope.launch {
        c.settings.update { it.copy(mode = mode) }
        c.controller.reloadRouting()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(transform) }

    // ---- secure dns --------------------------------------------------------------------------

    /** Called after the VPN consent has been granted (the DNS-only tunnel needs it too). */
    fun setSecureDns(on: Boolean) = viewModelScope.launch { c.controller.setSecureDns(on) }

    fun setDohUrl(url: String) = viewModelScope.launch {
        c.settings.update { it.copy(dohUrl = url) }
        c.dohUrlCache = url
    }

    // ---- servers -----------------------------------------------------------------------------

    private var refreshJob: Job? = null
    val refreshing: StateFlow<Boolean> get() = _refreshing
    private val _refreshing = MutableStateFlow(false)

    /** Re-tests every server and repairs built-in slots that are down. */
    fun refreshServers() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            _refreshing.value = true
            try {
                val (ok, tested, repaired) = c.serverManager.refreshAll()
                toast("$ok of $tested servers working" + if (repaired > 0) " · $repaired replaced" else "")
            } catch (e: Exception) {
                toast(e.message ?: "Refresh failed")
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun testServer(server: Server, onDone: (TestResult) -> Unit = {}) = viewModelScope.launch {
        val r = c.serverManager.testServer(server)
        onDone(r)
        toast(if (r.ok) "${server.name}: ${r.latencyMs} ms · exit ${r.exitIp}" else "${server.name}: ${r.error}")
    }

    fun toggleFavorite(s: Server) = viewModelScope.launch { c.servers.setFavorite(s.id, !s.favorite) }
    fun deleteServer(s: Server) = viewModelScope.launch {
        if (s.isBuiltin) { toast("Built-in servers cannot be removed"); return@launch }
        c.servers.delete(s.id)
        val cur = c.settings.current()
        if (cur.selectedServerId == s.id) c.controller.selectServer(null)
        toast("Server removed")
    }

    // ---- imports -----------------------------------------------------------------------------

    data class ImportResult(val added: Int, val working: Int, val invalid: Int, val message: String)

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val bytes = s.readNBytes(5_000_001)
            if (bytes.size > 5_000_000) throw IllegalArgumentException("File is larger than 5 MB")
            String(bytes, Charsets.UTF_8)
        } ?: throw IllegalArgumentException("Cannot read file")
    }

    suspend fun addCustomProxy(ep: ProxyEndpoint, name: String): Pair<Server, TestResult> = withContext(Dispatchers.IO) {
        val s = c.servers.endpointServer(ep, ServerOrigin.CUSTOM, name.ifBlank { null })
        c.servers.save(s)
        s to c.serverManager.testServer(s)
    }

    /** "Auto" proxy type: tries SOCKS5 first, then HTTP CONNECT, and keeps whichever passes the HTTPS test. */
    suspend fun addCustomProxyAuto(host: String, port: Int, user: String?, pass: String?, name: String): Pair<Server, TestResult> = withContext(Dispatchers.IO) {
        val s5 = addCustomProxy(ProxyEndpoint(ProxyProtocol.SOCKS5, host, port, user, pass), name)
        if (s5.second.ok) return@withContext s5
        val http = addCustomProxy(ProxyEndpoint(ProxyProtocol.HTTP, host, port, user, pass), name)
        if (http.second.ok) { c.servers.delete(s5.first.id); http } else { c.servers.delete(http.first.id); s5 }
    }

    /** Imports one or many share links (vless/vmess/trojan/ss/hysteria2/socks/http) and tests them. */
    suspend fun importLinks(text: String): ImportResult = withContext(Dispatchers.IO) {
        val lines = Links.decodeSubscription(text).lineSequence().map { it.trim() }.filter { it.contains("://") && !it.startsWith("#") }.toList()
        val servers = mutableListOf<Server>()
        var errors = 0
        var insecure = 0
        var firstError: String? = null
        for (l in lines) {
            val scheme = l.substringBefore("://").lowercase()
            if (scheme in setOf("socks", "socks5", "socks5h", "http", "https")) {
                // Plain proxy URLs are stored as direct endpoints (no Xray needed).
                val ep = ProxyList.parseUrl(l)
                if (ep != null) servers += c.servers.endpointServer(ep, ServerOrigin.CUSTOM) else { errors++; firstError = firstError ?: "Invalid proxy URL" }
                continue
            }
            try {
                val o = Links.parse(l)
                if (o.insecureRequested) insecure++
                servers += c.servers.linkServer(l, o, ServerOrigin.CUSTOM)
            } catch (e: Exception) {
                errors++; firstError = firstError ?: e.message
            }
        }
        if (servers.isEmpty()) return@withContext ImportResult(0, 0, errors, firstError ?: "No valid server link found")
        c.servers.save(servers)
        val res = c.serverManager.testMany(servers, "Testing imported servers", overallTimeoutMs = 120_000, concurrency = 8)
        val working = res.count { it.second.ok }
        ImportResult(servers.size, working, errors, "${servers.size} imported, $working working" +
            (if (errors > 0) " · $errors invalid" else "") +
            (if (insecure > 0) " · $insecure asked for insecure TLS (ignored: certificates are always verified)" else "") +
            (if (!c.xray.available) " · Xray is unavailable on this device" else ""))
    }

    suspend fun importLinksFromFile(uri: Uri): ImportResult = importLinks(readText(uri))

    suspend fun addSubscription(name: String, url: String): ImportResult = withContext(Dispatchers.IO) {
        val id = c.subscriptions.add(name.ifBlank { Uri.parse(url).host ?: "Subscription" }, url.trim())
        refreshSubscription(c.subscriptions.all().first { it.id == id })
    }

    suspend fun refreshSubscription(sub: SubscriptionRepository.Subscription): ImportResult = withContext(Dispatchers.IO) {
        try {
            val (ok, errors) = c.subscriptionFetcher.fetch(sub.url)
            c.servers.deleteSubscription(sub.id)
            val servers = ok.map { (link, o) -> c.servers.linkServer(link, o, ServerOrigin.SUBSCRIPTION, sub.id) }
            c.servers.save(servers)
            c.subscriptions.markUpdated(sub, servers.size, null)
            val res = c.serverManager.testMany(servers.take(60), "Testing subscription", overallTimeoutMs = 150_000, concurrency = 8)
            ImportResult(servers.size, res.count { it.second.ok }, errors, "${servers.size} servers updated, ${res.count { it.second.ok }} working")
        } catch (e: Exception) {
            c.subscriptions.markUpdated(sub, sub.count, e.message ?: "failed")
            ImportResult(0, 0, 0, e.message ?: "Subscription update failed")
        }
    }

    fun deleteSubscription(id: Long) = viewModelScope.launch { c.servers.deleteSubscription(id); c.subscriptions.delete(id) }

    fun linkError(text: String): String? = try { Links.parse(text.trim()); null } catch (e: LinkParseException) { e.message } catch (e: Exception) { "Invalid link" }

    // ---- websites ----------------------------------------------------------------------------

    fun addDomain(input: String): String? {
        val d = Domains.normalize(input) ?: return null
        viewModelScope.launch { c.domains.add(d); c.controller.reloadDomains() }
        return d
    }

    fun togglePreset(p: Domains.Preset, enabled: Boolean) = viewModelScope.launch {
        if (enabled) c.domains.addPreset(p) else c.domains.removePreset(p.id)
        if (p.cidrs.isNotEmpty()) c.controller.reloadRouting() else c.controller.reloadDomains()
    }

    fun setDomainEnabled(d: String, on: Boolean) = viewModelScope.launch { c.domains.setEnabled(d, on); c.controller.reloadDomains() }
    fun removeDomain(d: String) = viewModelScope.launch { c.domains.remove(d); c.controller.reloadDomains() }

    fun setExcludedApps(pkgs: Set<String>) = viewModelScope.launch { c.settings.update { it.copy(excludedApps = pkgs) }; c.controller.reloadRouting() }
    fun setIncludedApps(pkgs: Set<String>) = viewModelScope.launch { c.settings.update { it.copy(includedApps = pkgs) }; c.controller.reloadRouting() }
    fun setSplitMode(m: SplitMode) = viewModelScope.launch { c.settings.update { it.copy(splitMode = m) }; c.controller.reloadRouting() }

    // ---- tools -------------------------------------------------------------------------------

    private val _ip = MutableStateFlow<Pair<IpInfo?, String?>>(null to null)
    val ip: StateFlow<Pair<IpInfo?, String?>> = _ip.asStateFlow()
    private val _ipLoading = MutableStateFlow(false)
    val ipLoading = _ipLoading.asStateFlow()

    fun checkIp() = viewModelScope.launch {
        _ipLoading.value = true
        val ep = if (status.value.isConnected) c.controller.activeEndpoint else null
        _ip.value = runCatching { IpChecker(c.httpsOverProxy).check(ep, ProxyTester.DEFAULT_TRACE_URL, IpChecker.DEFAULT_LOOKUP) }
            .fold({ it to null }, { null to (it.message ?: "IP check failed") })
        _ipLoading.value = false
    }

    data class SpeedState(
        val running: Boolean = false, val stage: String = "", val latency: Int? = null, val jitter: Int? = null, val loss: Int? = null,
        val downMbps: Double? = null, val upMbps: Double? = null, val liveMbps: Double? = null, val viaVpn: Boolean = false, val error: String? = null,
    )

    private val _speed = MutableStateFlow(SpeedState())
    val speed = _speed.asStateFlow()

    fun runSpeedTest() {
        if (_speed.value.running) return
        viewModelScope.launch {
            val ep = if (status.value.isConnected) c.controller.activeEndpoint else null
            val st = SpeedTest(c.httpsOverProxy)
            _speed.value = SpeedState(running = true, stage = "Measuring latency…", viaVpn = ep != null)
            try {
                val (lat, jit, loss) = st.latency(ep)
                _speed.value = _speed.value.copy(latency = lat, jitter = jit, loss = loss, stage = "Download…")
                if (lat == null) throw IllegalStateException("Speed test server unreachable")
                val down = st.download(ep) { live -> _speed.value = _speed.value.copy(liveMbps = live / 1e6) } / 1e6
                _speed.value = _speed.value.copy(downMbps = down, liveMbps = null, stage = "Upload…")
                val up = runCatching { st.upload(ep) / 1e6 }.getOrNull()
                _speed.value = _speed.value.copy(upMbps = up, running = false, stage = "Done")
                c.history.log("SPEED_TEST", if (ep != null) "Through VPN (${status.value.server?.name})" else "Direct connection",
                    "↓ ${"%.1f".format(down)} Mbps · ↑ ${up?.let { "%.1f".format(it) } ?: "n/a"} Mbps · $lat ms", lat, down, up)
            } catch (e: Exception) {
                _speed.value = _speed.value.copy(running = false, error = e.message ?: "Speed test failed", stage = "Failed", liveMbps = null)
            }
        }
    }

    data class Check(val name: String, val ok: Boolean?, val detail: String)

    private val _diag = MutableStateFlow<List<Check>>(emptyList())
    val diagnostics = _diag.asStateFlow()
    private val _diagRunning = MutableStateFlow(false)
    val diagRunning = _diagRunning.asStateFlow()

    fun runDiagnostics() = viewModelScope.launch(Dispatchers.IO) {
        _diagRunning.value = true
        val out = mutableListOf<Check>()
        fun add(ch: Check) { out += ch; _diag.value = out.toList() }
        val s = c.settings.current()
        val http = HttpsOverProxy()
        add(runCatching { http.request(ProxyTester.DEFAULT_TRACE_URL, null, timeoutMs = 8000, maxBody = 8192) }.fold(
            { Check("Internet (HTTPS, direct)", it.code == 200, "Response in ${it.totalMs} ms, certificate verified") },
            { Check("Internet (HTTPS, direct)", false, it.message ?: "failed") }))
        add(runCatching { runInterruptible { InetAddress.getAllByName("www.cloudflare.com") } }.fold(
            { Check("DNS resolution (system)", true, "${it.size} address(es)") }, { Check("DNS resolution (system)", false, it.message ?: "failed") }))
        add(runCatching { c.doh.direct(DnsMessage.buildQuery(7, "example.com", DnsMessage.TYPE_A)) }.fold(
            { Check("Encrypted DNS (DoH)", it != null && DnsMessage.parseAnswersA(it).isNotEmpty(), "Resolver: ${s.dohUrl}") },
            { Check("Encrypted DNS (DoH)", false, it.message ?: "failed") }))
        add(Check("VPN permission", c.controller.needsPermission() == null, if (c.controller.needsPermission() == null) "Granted" else "Not granted yet"))
        val st = status.value
        add(Check("VPN tunnel", if (st.isConnected) true else null, "${st.phase.label}${st.server?.let { " · ${it.name}" } ?: ""}"))
        add(Check("Secure DNS", if (st.isDnsOnly) true else null, when {
            st.isDnsOnly -> "DNS-only tunnel active · ${st.dnsQueries} queries answered over DoH"
            st.isConnected -> "Not needed while connected: the server resolves names"
            s.secureDns -> "Enabled, waiting to start"
            else -> "Off"
        }))
        val ep = c.controller.activeEndpoint
        if (st.isConnected && ep != null) {
            val r = c.serverManager.tester().test(ep)
            add(Check("Server reachability", r.ok, if (r.ok) "Exit ${r.exitIp} (${r.countryCode ?: "?"}), ${r.latencyMs} ms" else r.error ?: "failed"))
            add(Check("DNS leak protection", true, if (s.mode == VpnMode.ALL_APPS) "All DNS answered locally; names resolved by the server" else "Selected sites resolved via the server; others via ${s.dohUrl}"))
            add(Check("UDP / QUIC", null, "${st.udpBlocked} UDP packets rejected (the tunnel carries TCP only; apps fall back to TCP)"))
        }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = cm.getLinkProperties(cm.activeNetwork)
            val pdns = lp?.privateDnsServerName
            add(Check("Android Private DNS", if (pdns != null && (s.mode == VpnMode.ONLY_MY_SITES || s.secureDns)) false else null,
                when {
                    pdns != null -> "Set to \"$pdns\": it bypasses Tervyn's DNS (Only my sites and Secure DNS). Set Private DNS to Automatic or Off."
                    lp?.isPrivateDnsActive == true -> "Automatic (opportunistic) — compatible"
                    else -> "Off — compatible"
                }))
        }
        val pm = ctx.getSystemService(PowerManager::class.java)
        add(Check("Battery optimisation", if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) true else null,
            if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) "Unrestricted" else "Optimised (the system may stop the VPN in deep sleep)"))
        add(Check("Xray core", c.xray.available, c.xray.version() ?: "Binary not found for this device"))
        c.history.log("DIAGNOSTICS", "Network diagnostics", "${out.count { it.ok == true }} passed, ${out.count { it.ok == false }} failed")
        _diagRunning.value = false
    }

    // ---- privacy / data ----------------------------------------------------------------------

    fun clearHistory() = viewModelScope.launch { c.history.clear(); toast("History cleared") }

    fun resetSettings() = viewModelScope.launch { c.settings.reset(); c.settings.setPinHash(null); toast("Settings reset") }

    fun setPin(pin: String?) = viewModelScope.launch {
        if (pin == null) { c.settings.setPinHash(null); c.settings.update { it.copy(appLock = false, biometric = false) } }
        else { c.settings.setPinHash(PinHasher.hash(pin)); c.settings.update { it.copy(appLock = true) } }
    }

    fun verifyPin(pin: String): Boolean = pinHash.value?.let { PinHasher.verify(pin, it) } ?: false

    @Serializable
    data class ExportBundle(
        val format: String = "tervyn-config",
        val version: Int = 2,
        val settings: AppSettings,
        val websites: List<String>,
        val presets: List<String>,
        val servers: List<String> = emptyList(),
        val subscriptions: List<String> = emptyList(),
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportConfig(uri: Uri, includeServers: Boolean) = withContext(Dispatchers.IO) {
        val doms = c.domains.all()
        val own = if (includeServers) c.servers.byOrigins(setOf(ServerOrigin.CUSTOM)) else emptyList()
        val bundle = ExportBundle(
            settings = c.settings.current().copy(selectedServerId = null),
            websites = doms.filter { it.presetId == null }.map { it.domain },
            presets = doms.mapNotNull { it.presetId }.distinct(),
            servers = own.mapNotNull { it.xrayLink ?: it.endpoint?.let(ProxyList::toUrl) },
            subscriptions = if (includeServers) c.subscriptions.all().map { it.url } else emptyList(),
        )
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.encodeToString(ExportBundle.serializer(), bundle).toByteArray()) }
        toast(if (includeServers) "Exported (contains server credentials — keep the file private)" else "Exported")
    }

    suspend fun importConfig(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val b = json.decodeFromString(ExportBundle.serializer(), readText(uri))
            require(b.format == "tervyn-config") { "Not a Tervyn configuration file" }
            c.settings.update { cur -> b.settings.copy(onboardingDone = cur.onboardingDone, appLock = cur.appLock, biometric = cur.biometric, selectedServerId = cur.selectedServerId) }
            b.websites.forEach { c.domains.add(it) }
            Domains.presets.filter { it.id in b.presets }.forEach { c.domains.addPreset(it) }
            val links = b.servers.filter { Links.isShareLink(it) }
            val plain = b.servers.filterNot { Links.isShareLink(it) }.mapNotNull { ProxyList.parseLine(it) }
            c.servers.save(plain.map { c.servers.endpointServer(it, ServerOrigin.CUSTOM) })
            c.servers.save(links.mapNotNull { l -> runCatching { c.servers.linkServer(l, Links.parse(l), ServerOrigin.CUSTOM) }.getOrNull() })
            b.subscriptions.forEach { c.subscriptions.add(Uri.parse(it).host ?: "Subscription", it) }
            toast("Configuration imported")
        } catch (e: Exception) {
            toast("Import failed: ${e.message}")
        }
    }
}
