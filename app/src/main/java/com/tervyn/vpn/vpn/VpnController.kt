package com.tervyn.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.tervyn.vpn.data.SettingsRepository
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.VpnMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase(val label: String, val busy: Boolean) {
    DISCONNECTED("Disconnected", false),
    FETCHING("Updating server list…", true),
    TESTING("Testing servers…", true),
    CONNECTING("Connecting…", true),
    CONNECTED("Connected", false),
    SWITCHING("Switching server…", true),
    RECONNECTING("Reconnecting…", true),
    FAILED("Connection failed", false),
    /** No server is connected; the tunnel only carries DNS, answered over DNS-over-HTTPS (Secure DNS). */
    DNS_ONLY("Secure DNS active", false),
}

data class VpnStatus(
    val phase: Phase = Phase.DISCONNECTED,
    val message: String? = null,
    val server: Server? = null,
    val exitIp: String? = null,
    val countryCode: String? = null,
    val connectedSince: Long = 0,
    val downBps: Long = 0,
    val upBps: Long = 0,
    val totalDown: Long = 0,
    val totalUp: Long = 0,
    val pingMs: Int? = null,
    val activeConnections: Int = 0,
    val udpBlocked: Long = 0,
    val dnsQueries: Long = 0,
    val mode: VpnMode = VpnMode.ALL_APPS,
    val tested: Int = 0,
    val testTotal: Int = 0,
    val working: Int = 0,
    val lastHealthCheck: Long = 0,
    /** Reported by the service on Android 10+: always-on VPN and "Block connections without VPN". */
    val alwaysOn: Boolean? = null,
    val lockdown: Boolean? = null,
    val error: String? = null,
) {
    /** A server connection is up or being established (Secure DNS alone does not count). */
    val isActive get() = phase != Phase.DISCONNECTED && phase != Phase.FAILED && phase != Phase.DNS_ONLY
    val isConnected get() = phase == Phase.CONNECTED
    val isDnsOnly get() = phase == Phase.DNS_ONLY
    /** The VPN interface exists (server tunnel or DNS-only tunnel). */
    val tunnelUp get() = isActive || isDnsOnly
}

/** Single source of truth for the VPN state, shared by UI, tile and service. */
class VpnController(private val app: Context, private val settings: SettingsRepository) {
    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    /** Active upstream endpoint (the local Xray SOCKS endpoint for share-link servers). Used for "Check my IP" etc. */
    @Volatile var activeEndpoint: ProxyEndpoint? = null
        internal set

    internal fun set(transform: (VpnStatus) -> VpnStatus) = _status.update(transform)

    /** Non-null when the system VPN consent dialog must be shown first (needed for both VPN and Secure DNS). */
    fun needsPermission(): Intent? = VpnService.prepare(app)

    /** Connects to a server. Works from the idle state and from Secure-DNS-only mode. */
    fun connect() {
        if (_status.value.isActive) return
        _status.update { it.copy(phase = Phase.CONNECTING, error = null, message = "Starting…") }
        ContextCompat.startForegroundService(app, Intent(app, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_CONNECT))
    }

    /** Disconnects the server. If Secure DNS is enabled the service stays up in DNS-only mode. */
    fun disconnect() {
        if (!_status.value.tunnelUp) { _status.update { it.copy(phase = Phase.DISCONNECTED, error = null) }; return }
        app.startService(Intent(app, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_DISCONNECT))
    }

    fun toggle() = if (_status.value.isActive) disconnect() else connect()

    /** Starts the DNS-only tunnel (no server). Only meaningful when no server connection is active. */
    fun startSecureDns() {
        if (_status.value.tunnelUp) return
        _status.update { it.copy(phase = Phase.DNS_ONLY, message = "Starting Secure DNS…", error = null) }
        ContextCompat.startForegroundService(app, Intent(app, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_DNS_ONLY))
    }

    /** Stops the DNS-only tunnel. A server connection is not affected. */
    fun stopSecureDns() {
        if (_status.value.isDnsOnly) app.startService(Intent(app, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_STOP_DNS))
    }

    /**
     * Turns the Secure DNS feature on or off. It runs only while no server is connected: enabling it while
     * connected takes effect after the next disconnect; disabling it while in DNS-only mode stops the tunnel.
     */
    suspend fun setSecureDns(enabled: Boolean) {
        settings.update { it.copy(secureDns = enabled) }
        val s = _status.value
        when {
            enabled && !s.tunnelUp && needsPermission() == null -> startSecureDns()
            !enabled && s.isDnsOnly -> stopSecureDns()
        }
    }

    /** Selecting a specific server disables auto-switch; null ("Fastest available") enables it. */
    suspend fun selectServer(serverId: String?) {
        settings.update { it.copy(selectedServerId = serverId, autoSwitch = serverId == null) }
        if (_status.value.isActive) send(TervynVpnService.ACTION_SWITCH, serverId)
    }

    /** Routing (mode, excluded apps, preset IP ranges) changed: rebuild the TUN when connected. */
    fun reloadRouting() { if (_status.value.isActive) send(TervynVpnService.ACTION_RELOAD, null) }

    /** Website list changed: update the live DNS policy without reconnecting. */
    fun reloadDomains() { if (_status.value.isActive) send(TervynVpnService.ACTION_RELOAD_DOMAINS, null) }

    private fun send(action: String, serverId: String?) {
        app.startService(Intent(app, TervynVpnService::class.java).setAction(action).putExtra(TervynVpnService.EXTRA_SERVER, serverId))
    }
}
