package com.tervyn.vpn.vpn

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import androidx.core.app.ServiceCompat
import com.tervyn.vpn.MainActivity
import com.tervyn.vpn.TervynApp
import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.core.Links
import com.tervyn.vpn.core.Ranking
import com.tervyn.vpn.core.ServerManager
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.database.HistoryEntity
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.domain.TestResult
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.net.DnsResponder
import com.tervyn.vpn.net.FakeDns
import com.tervyn.vpn.net.ProxyDialer
import com.tervyn.vpn.services.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The Android VPN service. Owns the TUN interface, the [TunEngine], the active upstream proxy and the
 * background health / failover / auto-switch loops. All state is published through [VpnController].
 */
class TervynVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.tervyn.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.tervyn.vpn.DISCONNECT"
        const val ACTION_SWITCH = "com.tervyn.vpn.SWITCH"
        const val ACTION_RELOAD = "com.tervyn.vpn.RELOAD"
        const val ACTION_RELOAD_DOMAINS = "com.tervyn.vpn.RELOAD_DOMAINS"
        const val EXTRA_SERVER = "server"
        private const val TAG = "TervynVpn"
        private const val TUN_ADDR = "198.18.0.1"
        private const val TUN_V6 = "fdfe:7e7:7e7::1"
        private const val DNS_ADDR = "198.18.0.2"
        private const val MTU = 1500
    }

    private val c by lazy { (application as TervynApp).container }
    private val controller get() = c.controller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = Mutex()

    private var connectJob: Job? = null
    private var monitorJobs = mutableListOf<Job>()
    private var tun: ParcelFileDescriptor? = null
    private var engine: TunEngine? = null
    @Volatile private var upstream: ProxyEndpoint? = null
    @Volatile private var active: Server? = null
    @Volatile private var settings = AppSettings()
    @Volatile private var matcher = Domains.Matcher(emptyList())
    private val fakeDns = FakeDns()
    private lateinit var dns: DnsResponder
    private var historyId: Long? = null
    private var lastSwitchAt = 0L
    private var fasterStreak = 0
    private var healthFailures = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null
    private var foreground = false
    private var dataAlertSent = false

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        dns = DnsResponder(fakeDns, { name -> settings.mode == VpnMode.ALL_APPS || matcher.matches(name) }) { q ->
            if (settings.mode == VpnMode.ALL_APPS) upstream?.let { c.doh.viaProxy(it, q) } else c.doh.direct(q)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> { scope.launch { shutdown(null) }; return START_NOT_STICKY }
            ACTION_SWITCH -> { scope.launch { manualSwitch(intent.getStringExtra(EXTRA_SERVER)) }; return START_STICKY }
            ACTION_RELOAD -> { scope.launch { lifecycle.withLock { if (engine != null) rebuildTun() } }; return START_STICKY }
            ACTION_RELOAD_DOMAINS -> { scope.launch { reloadDomains() }; return START_STICKY }
            else -> startConnect() // ACTION_CONNECT, or started by the system for always-on VPN (SERVICE_INTERFACE)
        }
        return START_STICKY
    }

    // ---- connect ---------------------------------------------------------------------------

    private fun goForeground(text: String) {
        val n = Notifications.status(this, controller.status.value.copy(message = text), settings.showSpeedInNotification)
        ServiceCompat.startForeground(
            this, Notifications.STATUS_ID, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        foreground = true
    }

    private fun startConnect() {
        goForeground("Starting…")
        if (connectJob?.isActive == true || engine != null) return
        connectJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    lifecycle.withLock { connectOnce() }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = (e as? ServerManager.NoServerException)?.message ?: e.message ?: "Unknown error"
                    Log.w(TAG, "connect failed: ${e.javaClass.simpleName}")
                    // An always-on VPN keeps retrying instead of giving up (the system keeps traffic blocked meanwhile).
                    if (Build.VERSION.SDK_INT >= 29 && isAlwaysOn && attempt++ < 1000) {
                        controller.set { it.copy(phase = Phase.RECONNECTING, message = "Retrying in 30 s: $msg", error = msg) }
                        delay(30_000)
                        continue
                    }
                    fail(msg)
                    return@launch
                }
            }
        }
    }

    private suspend fun connectOnce() {
        settings = c.settings.current()
        reloadDomainsLocked()
        controller.set { VpnStatus(phase = Phase.TESTING, mode = settings.mode, message = "Finding the best server…") }
        val progressJob = scope.launch {
            c.serverManager.progress.collect { p ->
                controller.set { s -> if (s.phase == Phase.FETCHING || s.phase == Phase.TESTING) s.copy(tested = p.tested, testTotal = p.total, working = p.working, message = p.phase) else s }
            }
        }
        try {
            val (server, _) = c.serverManager.selectForConnect(settings) { phase, _ ->
                controller.set { it.copy(phase = if (phase == "FETCHING") Phase.FETCHING else Phase.TESTING) }
            }
            controller.set { it.copy(phase = Phase.CONNECTING, server = server, message = "Connecting to ${server.name}…") }
            val (srv, ep, verify) = activateWithFallback(server)
            upstream = ep
            controller.activeEndpoint = ep
            active = srv
            establishTunAndEngine()
            val now = System.currentTimeMillis()
            controller.set {
                it.copy(phase = Phase.CONNECTED, server = srv, exitIp = verify.exitIp, countryCode = verify.countryCode ?: srv.countryCode,
                    connectedSince = now, pingMs = verify.latencyMs, message = null, error = null, lastHealthCheck = now,
                    alwaysOn = if (Build.VERSION.SDK_INT >= 29) isAlwaysOn else null,
                    lockdown = if (Build.VERSION.SDK_INT >= 29) isLockdownEnabled else null)
            }
            c.servers.markUsed(srv.id)
            c.settings.update { it.copy(lastConnectedAt = now) }
            historyId = c.history.start(HistoryEntity(startedAt = now, endedAt = null, serverName = srv.name, countryCode = verify.countryCode,
                protocol = srv.protocolLabel, mode = settings.mode.name, status = "CONNECTED"))
            c.history.log("EVENT", "Connected", "${srv.name} · ${verify.exitIp ?: ""}")
            Notifications.event(this, settings, "Connected", "Tervyn VPN is protecting your connection via ${srv.name}", Notifications.Kind.CONNECT)
            registerNetworkCallback()
            startMonitors()
        } finally {
            progressJob.cancel()
        }
    }

    /**
     * Starts the upstream for [server] (Xray for share links) and verifies it end-to-end. With auto-switch on,
     * falls back to the next best verified servers.
     */
    private suspend fun activateWithFallback(server: Server): Triple<Server, ProxyEndpoint, TestResult> {
        val tried = mutableListOf<String>()
        var candidate: Server? = server
        var lastError = "verification failed"
        while (candidate != null && tried.size < 4) {
            tried += candidate.id
            val r = runCatching { activate(candidate) }.getOrElse { lastError = it.message ?: lastError; null }
            if (r != null && r.second.ok) return Triple(candidate, r.first, r.second)
            r?.second?.error?.let { lastError = it }
            c.servers.recordTest(candidate.id, r?.second ?: TestResult(false, error = lastError))
            if (!settings.autoSwitch) break
            candidate = c.serverManager.backups(settings, null, 8).firstOrNull { it.id !in tried }
            val next = candidate
            if (next != null) controller.set { it.copy(phase = Phase.CONNECTING, server = next, message = "Trying ${next.name}…") }
        }
        throw ServerManager.NoServerException("Could not verify the server: $lastError")
    }

    /** Returns the endpoint to dial and a fresh verification result through it. */
    private suspend fun activate(server: Server): Pair<ProxyEndpoint, TestResult> {
        val ep = if (server.isXray) {
            if (!c.xray.available) throw IOException("Xray is not available for this device ABI")
            c.xray.startActive(server.id, Links.parse(server.xrayLink!!))
        } else server.endpoint!!
        val r = c.serverManager.tester(settings).test(ep)
        if (!r.ok && server.isXray) c.xray.stop(server.id)
        return ep to r
    }

    // ---- TUN -------------------------------------------------------------------------------

    private fun buildTun(): ParcelFileDescriptor {
        val b = Builder()
            .setSession("Tervyn VPN")
            .setMtu(MTU)
            .addAddress(TUN_ADDR, 15)
            .addDnsServer(DNS_ADDR)
            .addRoute("198.18.0.0", 15)
        if (settings.mode == VpnMode.ALL_APPS) {
            b.addRoute("0.0.0.0", 0)
            // Capture IPv6 too so it cannot leak around the tunnel; the engine drops it.
            b.addAddress(TUN_V6, 126)
            b.addRoute("::", 0)
        } else {
            matcherCidrs.forEach { (net, prefix) -> runCatching { b.addRoute(com.tervyn.vpn.vpn.Packets.ipToString(net), prefix) } }
        }
        // Tervyn's own traffic (proxy tests, list downloads, Xray) must never loop back into the tunnel.
        if (settings.splitMode == com.tervyn.vpn.data.SplitMode.ONLY_SELECTED && settings.includedApps.isNotEmpty()) {
            // Allow-list mode: only the chosen apps are routed; Tervyn itself is implicitly outside the tunnel.
            settings.includedApps.filter { it != packageName }.forEach { pkg -> runCatching { b.addAllowedApplication(pkg) } }
        } else {
            b.addDisallowedApplication(packageName)
            settings.excludedApps.forEach { pkg -> runCatching { b.addDisallowedApplication(pkg) } }
        }
        b.setBlocking(true)
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        b.setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        currentNetwork?.let { b.setUnderlyingNetworks(arrayOf(it)) }
        return b.establish() ?: throw IOException("VPN permission was revoked")
    }

    @Volatile private var matcherCidrs: List<Pair<Int, Int>> = emptyList()

    private fun establishTunAndEngine() {
        val pfd = buildTun()
        val oldEngine = engine
        val oldTun = tun
        val e = TunEngine(
            TunInput(pfd), TunOutput(pfd.fileDescriptor),
            TunEngine.Config(routeAll = settings.mode == VpnMode.ALL_APPS, routedCidrs = matcherCidrs, mtu = MTU),
            fakeDns, dns,
        ) { host, port ->
            val ep = upstream ?: throw IOException("No upstream")
            ProxyDialer.connect(ep, host, port, 10_000, 10_000)
        }
        tun = pfd
        engine = e
        e.start()
        oldEngine?.stop()
        runCatching { oldTun?.close() }
    }

    private suspend fun rebuildTun() {
        settings = c.settings.current()
        reloadDomainsLocked()
        controller.set { it.copy(phase = Phase.RECONNECTING, message = "Applying routing changes…", mode = settings.mode) }
        try {
            establishTunAndEngine()
            controller.set { it.copy(phase = Phase.CONNECTED, message = null, mode = settings.mode) }
        } catch (e: Exception) {
            fail(e.message ?: "Failed to rebuild the VPN interface")
        }
    }

    private suspend fun reloadDomains() = lifecycle.withLock { reloadDomainsLocked() }

    private suspend fun reloadDomainsLocked() {
        matcher = Domains.Matcher(c.domains.enabledDomains())
        val newCidrs = c.domains.enabledCidrs().mapNotNull { Domains.parseCidr(it) }
        val changed = newCidrs != matcherCidrs
        matcherCidrs = newCidrs
        if (changed && engine != null && settings.mode == VpnMode.ONLY_MY_SITES) rebuildTun()
    }

    /** Polls the TUN fd so the reader can notice shutdown (a blocked read() is not interrupted by close()). */
    private class TunInput(private val pfd: ParcelFileDescriptor) : InputStream() {
        private val fd = pfd.fileDescriptor
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val pfdArr = arrayOf(StructPollfd().apply { this.fd = this@TunInput.fd; events = OsConstants.POLLIN.toShort() })
            while (true) {
                if (!fd.valid()) return -1
                val n = try { Os.poll(pfdArr, 500) } catch (e: Exception) { if (e is android.system.ErrnoException && e.errno == OsConstants.EINTR) continue; return -1 }
                if (n == 0) continue
                val re = pfdArr[0].revents.toInt()
                if (re and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) return -1
                return try { Os.read(fd, b, off, len) } catch (e: android.system.ErrnoException) {
                    if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) 0 else -1
                }
            }
        }
    }

    private class TunOutput(private val fd: FileDescriptor) : OutputStream() {
        override fun write(b: Int) = throw UnsupportedOperationException()
        override fun write(b: ByteArray, off: Int, len: Int) {
            try { Os.write(fd, b, off, len) } catch (e: android.system.ErrnoException) { throw IOException(e) }
        }
    }

    // ---- monitors --------------------------------------------------------------------------

    private fun startMonitors() {
        dataAlertSent = false
        monitorJobs.forEach { it.cancel() }
        monitorJobs.clear()
        monitorJobs += scope.launch { statsLoop() }
        monitorJobs += scope.launch { healthLoop() }
        if (ServerOrigin.FREE_PUBLIC in settings.sourceMode.origins) monitorJobs += scope.launch { refreshLoop() }
        val builtin = settings.sourceMode.origins.filter { it == ServerOrigin.TERVYN_FAST || it == ServerOrigin.TERVYN_ALL }
        if (builtin.isNotEmpty()) monitorJobs += scope.launch { builtinRefreshLoop(builtin) }
    }

    private suspend fun statsLoop() {
        var lastDown = 0L; var lastUp = 0L; var lastT = System.currentTimeMillis(); var tick = 0
        while (true) {
            delay(1000)
            val e = engine ?: continue
            val now = System.currentTimeMillis()
            val down = e.bytesDown.get(); val up = e.bytesUp.get()
            val dt = (now - lastT).coerceAtLeast(1)
            val dBps = (down - lastDown) * 1000 / dt; val uBps = (up - lastUp) * 1000 / dt
            lastDown = down; lastUp = up; lastT = now
            controller.set {
                it.copy(downBps = dBps, upBps = uBps, totalDown = down, totalUp = up, activeConnections = e.activeConnections,
                    udpBlocked = e.udpBlocked.get(), dnsQueries = e.dnsQueries.get())
            }
            if (++tick % 3 == 0 && foreground) Notifications.updateStatus(this, controller.status.value, settings.showSpeedInNotification)
            if (tick % 30 == 0) historyId?.let { c.history.updateTraffic(it, down, up) }
            val alertBytes = settings.dataAlertMb.toLong() * 1_048_576
            if (alertBytes > 0 && !dataAlertSent && down + up >= alertBytes) {
                dataAlertSent = true
                Notifications.event(this, settings, "Data usage", "This session has used ${com.tervyn.vpn.utils.Format.bytes(down + up)} through Tervyn VPN.", Notifications.Kind.DATA)
            }
            if (e.dialFailures.get() >= 4 && controller.status.value.phase == Phase.CONNECTED) {
                e.dialFailures.set(0)
                scope.launch { healthCheck(urgent = true) }
            }
        }
    }

    private suspend fun healthLoop() {
        while (true) {
            delay(settings.healthCheckSec * 1000L)
            healthCheck(urgent = false)
        }
    }

    private val healthLock = Mutex()

    private suspend fun healthCheck(urgent: Boolean): Unit = healthLock.withLock {
        val srv = active ?: return@withLock
        val ep = upstream ?: return@withLock
        if (controller.status.value.phase !in setOf(Phase.CONNECTED, Phase.RECONNECTING)) return@withLock
        // Restart a crashed Xray instance transparently.
        val r = if (srv.isXray && !c.xray.isRunning(srv.id)) runCatching { activate(srv) }.getOrNull()?.also { upstream = it.first; controller.activeEndpoint = it.first }?.second
            ?: TestResult(false, error = "Xray restart failed")
        else c.serverManager.tester(settings).test(ep)
        c.servers.recordTest(srv.id, r)
        val now = System.currentTimeMillis()
        if (r.ok) {
            healthFailures = 0
            active = c.servers.byId(srv.id) ?: srv
            controller.set { it.copy(phase = Phase.CONNECTED, pingMs = r.latencyMs, exitIp = r.exitIp ?: it.exitIp, lastHealthCheck = now, message = null) }
        } else {
            healthFailures++
            Log.w(TAG, "health check failed ($healthFailures)")
        }
        val dead = !r.ok && (healthFailures >= 2 || urgent)
        // Keep backups verified (only a handful, to save battery and data).
        val backups = c.serverManager.backups(settings, srv.id, 5)
        if (backups.isNotEmpty()) c.serverManager.testMany(backups, settings.copy(testConcurrency = 5), "Checking backups", overallTimeoutMs = 30_000)
        val ranked = c.serverManager.backups(settings, srv.id, 5)
        val current = c.servers.byId(srv.id) ?: srv
        val decision = Ranking.decide(
            autoSwitch = settings.autoSwitch, active = current, activeAlive = !dead, candidates = ranked,
            thresholdPct = settings.switchThresholdPct, consecutiveFasterChecks = fasterStreak, lastSwitchAt = lastSwitchAt, now = now,
        )
        val best = ranked.firstOrNull()
        fasterStreak = if (best?.latencyMs != null && current.latencyMs != null &&
            Ranking.isClearlyFaster(best.latencyMs, current.latencyMs, settings.switchThresholdPct)) fasterStreak + 1 else 0
        when {
            decision.switchTo != null -> switchTo(decision.switchTo, decision.reason)
            dead -> {
                // Manual server (auto-switch off): never move; keep retrying the same server.
                controller.set { it.copy(phase = Phase.RECONNECTING, message = if (settings.autoSwitch) "No backup available, retrying…" else "Server not responding, retrying…") }
                if (healthFailures == 2) {
                    Notifications.event(this, settings, "Connection problem", "${srv.name} is not responding. Retrying…", Notifications.Kind.FAILURE)
                    c.history.log("EVENT", "Server unavailable", srv.name)
                }
                scope.launch { delay(15_000); healthCheck(urgent = false) }
            }
        }
    }

    private suspend fun refreshLoop() {
        while (true) {
            delay(settings.listRefreshMin * 60_000L)
            runCatching {
                c.serverManager.refreshFreeLists(settings)
                // Test a limited batch of new candidates in the background with low concurrency.
                val untested = c.serverManager.pool(settings).filter { it.lastChecked == 0L }.shuffled().take(150)
                c.serverManager.testMany(untested, settings.copy(testConcurrency = 16), "Refreshing backups", overallTimeoutMs = 120_000)
            }
        }
    }

    /** Keeps the built-in Tervyn lists current (they advertise an hourly update interval) and re-tests new entries. */
    private suspend fun builtinRefreshLoop(origins: List<ServerOrigin>) {
        while (true) {
            delay(15 * 60_000L)
            for (o in origins) {
                if (!c.serverManager.builtinStale(o)) continue
                runCatching {
                    c.serverManager.refreshBuiltin(o)
                    val untested = c.serverManager.pool(settings).filter { it.origin == o && it.lastChecked == 0L }.sortedBy { it.addedAt }.take(100)
                    c.serverManager.testMany(untested, settings.copy(testConcurrency = 24), "Checking new servers", overallTimeoutMs = 120_000)
                }
            }
        }
    }

    private suspend fun switchTo(target: Server, reason: String) {
        val old = active
        controller.set { it.copy(phase = Phase.SWITCHING, message = "Switching to ${target.name}…") }
        val r = runCatching { activate(target) }.getOrNull()
        if (r == null || !r.second.ok) {
            c.servers.recordTest(target.id, r?.second ?: TestResult(false, error = "activation failed"))
            controller.set { it.copy(phase = if (healthFailures >= 2) Phase.RECONNECTING else Phase.CONNECTED, message = null) }
            return
        }
        upstream = r.first
        controller.activeEndpoint = r.first
        active = target
        lastSwitchAt = System.currentTimeMillis()
        healthFailures = 0; fasterStreak = 0
        controller.set { it.copy(phase = Phase.CONNECTED, server = target, exitIp = r.second.exitIp, countryCode = r.second.countryCode ?: target.countryCode, pingMs = r.second.latencyMs, message = null) }
        c.servers.markUsed(target.id)
        val e = engine
        historyId?.let { c.history.finish(it, e?.bytesDown?.get() ?: 0, e?.bytesUp?.get() ?: 0, "SWITCHED", reason) }
        historyId = c.history.start(HistoryEntity(startedAt = System.currentTimeMillis(), endedAt = null, serverName = target.name,
            countryCode = r.second.countryCode, protocol = target.protocolLabel, mode = settings.mode.name, status = "CONNECTED"))
        c.history.log("EVENT", "Switched server", "${old?.name ?: "?"} → ${target.name}: $reason")
        Notifications.event(this, settings, "Switched server", "Now using ${target.name}. $reason", Notifications.Kind.SWITCH)
        // Let existing connections on the old Xray instance drain before stopping it.
        if (old?.isXray == true && old.id != target.id) scope.launch { delay(30_000); if (active?.id != old.id) c.xray.stop(old.id) }
    }

    private suspend fun manualSwitch(serverId: String?) = lifecycle.withLock {
        settings = c.settings.current()
        if (engine == null) return@withLock
        if (serverId != null) {
            val s = c.servers.byId(serverId) ?: return@withLock
            switchTo(s, "Selected manually")
        } else {
            // "Fastest available" (optionally within the preferred country).
            val current = active
            val currentFits = current != null && (settings.preferredCountry == null || current.countryCode == settings.preferredCountry)
            val best = c.serverManager.backups(settings, current?.id, 3).firstOrNull()
            when {
                best != null && (!currentFits || (current?.latencyMs ?: Int.MAX_VALUE) > (best.latencyMs ?: Int.MAX_VALUE)) ->
                    switchTo(best, "Fastest available")
                best == null && !currentFits -> {
                    controller.set { it.copy(phase = Phase.SWITCHING, message = "Finding the fastest server…") }
                    val pick = runCatching { c.serverManager.selectForConnect(settings) { _, _ -> } }.getOrNull()
                    if (pick != null) switchTo(pick.first, "Fastest available")
                    else controller.set { it.copy(phase = Phase.CONNECTED, message = "No server found for that choice") }
                }
            }
        }
    }

    // ---- network changes -------------------------------------------------------------------

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previous = currentNetwork
                currentNetwork = network
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                if (previous != null && previous != network && settings.reconnectOnNetworkChange) {
                    scope.launch {
                        controller.set { it.copy(phase = Phase.RECONNECTING, message = "Network changed, reconnecting…") }
                        delay(1500)
                        healthCheck(urgent = true)
                    }
                }
            }

            override fun onLost(network: Network) {
                if (network == currentNetwork) {
                    controller.set { it.copy(phase = Phase.RECONNECTING, message = "Waiting for network…") }
                }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = Unit
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    // ---- shutdown --------------------------------------------------------------------------

    private suspend fun fail(message: String) {
        controller.set { VpnStatus(phase = Phase.FAILED, error = message, message = message, mode = settings.mode) }
        Notifications.event(this, settings, "Connection failed", message, Notifications.Kind.FAILURE)
        runCatching { c.history.log("EVENT", "Connection failed", message) }
        teardown("FAILED", message)
        stopSelfCompletely()
    }

    private suspend fun shutdown(reason: String?) {
        connectJob?.cancel()
        lifecycle.withLock {
            val wasConnected = engine != null
            teardown("ENDED", reason)
            controller.set { VpnStatus(phase = Phase.DISCONNECTED, mode = settings.mode, message = reason) }
            if (wasConnected) {
                Notifications.event(this, settings, "Disconnected", reason ?: "Tervyn VPN is off", Notifications.Kind.DISCONNECT)
                c.history.log("EVENT", "Disconnected", reason ?: "By user")
            }
        }
        stopSelfCompletely()
    }

    private suspend fun teardown(status: String, detail: String?) {
        monitorJobs.forEach { it.cancel() }
        monitorJobs.clear()
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        networkCallback = null
        val e = engine
        engine = null
        e?.stop()
        runCatching { tun?.close() }
        tun = null
        historyId?.let { runCatching { c.history.finish(it, e?.bytesDown?.get() ?: 0, e?.bytesUp?.get() ?: 0, status, detail) } }
        historyId = null
        upstream = null
        controller.activeEndpoint = null
        active = null
        c.xray.stopAllExcept(null)
        fakeDns.clear()
    }

    private fun stopSelfCompletely() {
        if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    override fun onRevoke() {
        scope.launch { shutdown("Another VPN was started or permission was revoked") }
    }

    override fun onDestroy() {
        if (engine != null) {
            engine?.stop(); runCatching { tun?.close() }; c.xray.stopAllExcept(null)
            controller.set { VpnStatus(phase = Phase.DISCONNECTED) }
        }
        scope.cancel()
        super.onDestroy()
    }
}
