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
    FETCHING("Fetching servers…", true),
    TESTING("Testing servers…", true),
    CONNECTING("Connecting…", true),
    CONNECTED("Connected", false),
    SWITCHING("Switching server…", true),
    RECONNECTING("Reconnecting…", true),
    FAILED("Connection failed", false),
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
    val error: String? = null,
) {
    val isActive get() = phase != Phase.DISCONNECTED && phase != Phase.FAILED
    val isConnected get() = phase == Phase.CONNECTED
}

/** Single source of truth for the VPN state, shared by UI, tile and service. */
class VpnController(private val app: Context, private val settings: SettingsRepository) {
    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    /** Active upstream endpoint (the local Xray SOCKS endpoint for share-link servers). Used for "Check my IP" etc. */
    @Volatile var activeEndpoint: ProxyEndpoint? = null
        internal set

    internal fun set(transform: (VpnStatus) -> VpnStatus) = _status.update(transform)

    fun needsPermission(): Intent? = VpnService.prepare(app)

    fun connect() {
        if (_status.value.isActive) return
        _status.update { it.copy(phase = Phase.CONNECTING, error = null, message = "Starting…") }
        ContextCompat.startForegroundService(app, Intent(app, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_CONNECT))
    }

    fun disconnect() {
        if (!_status.value.isActive) { _status.update { it.copy(phase = Phase.DISCONNECTED, error = null) }; return }
        app.startService(Intent(app, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_DISCONNECT))
    }

    fun toggle() = if (_status.value.isActive) disconnect() else connect()

    /** Selecting a specific server disables auto-switch; null ("Fastest available") enables it. */
    suspend fun selectServer(serverId: String?) {
        settings.update { it.copy(selectedServerId = serverId, autoSwitch = serverId == null) }
        if (_status.value.isActive) send(TervynVpnService.ACTION_SWITCH, serverId)
    }

    suspend fun selectCountry(country: String?) {
        settings.update { it.copy(preferredCountry = country, selectedServerId = null, autoSwitch = true) }
        if (_status.value.isActive) send(TervynVpnService.ACTION_SWITCH, null)
    }

    /** Routing (mode, excluded apps, preset IP ranges) changed: rebuild the TUN when connected. */
    fun reloadRouting() { if (_status.value.isActive) send(TervynVpnService.ACTION_RELOAD, null) }

    /** Website list changed: update the live DNS policy without reconnecting. */
    fun reloadDomains() { if (_status.value.isActive) send(TervynVpnService.ACTION_RELOAD_DOMAINS, null) }

    private fun send(action: String, serverId: String?) {
        app.startService(Intent(app, TervynVpnService::class.java).setAction(action).putExtra(TervynVpnService.EXTRA_SERVER, serverId))
    }
}
