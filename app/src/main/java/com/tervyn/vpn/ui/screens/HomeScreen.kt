package com.tervyn.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.net.DohForwarder
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.Banner
import com.tervyn.vpn.ui.components.BrandMark
import com.tervyn.vpn.ui.components.Card
import com.tervyn.vpn.ui.components.ConnectButton
import com.tervyn.vpn.ui.components.FlagTile
import com.tervyn.vpn.ui.components.GhostButton
import com.tervyn.vpn.ui.components.IconBadge
import com.tervyn.vpn.ui.components.LatencyBadge
import com.tervyn.vpn.ui.components.Pill
import com.tervyn.vpn.ui.components.Screen
import com.tervyn.vpn.ui.components.SegmentedControl
import com.tervyn.vpn.ui.components.StatTile
import com.tervyn.vpn.ui.components.TervynSwitch
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.components.phaseColor
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import com.tervyn.vpn.vpn.Phase
import com.tervyn.vpn.vpn.VpnStatus

/**
 * Home: the connect control, the current server, the independent Secure DNS switch, live session stats
 * and the routing mode. Everything shown here is live state — nothing is simulated.
 */
@Composable
fun HomeScreen(vm: MainViewModel, connect: () -> Unit, enableSecureDns: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val servers by vm.servers.collectAsState()
    val selected = servers.firstOrNull { it.id == settings.selectedServerId }

    Screen(title = "", onBack = null, gutter = Space.gutter, scrollable = true) {
        Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(36.dp)
            Spacer(Modifier.width(Space.m))
            Text("Tervyn", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f))
            IconButton(onClick = { go(Routes.IP) }, modifier = Modifier.semantics { contentDescription = "Check my IP" }) {
                Icon(Icons.Rounded.Public, null, tint = c.text2)
            }
        }

        Spacer(Modifier.height(Space.xl))
        StatusHeadline(status)
        Spacer(Modifier.height(Space.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ConnectButton(status, onClick = { if (status.isActive) vm.disconnect() else connect() })
        }
        Spacer(Modifier.height(Space.l))

        val failure = status.error?.takeIf { status.phase == Phase.FAILED || status.phase == Phase.DNS_ONLY }
        if (failure != null) {
            Banner("Connection failed", failure, Tone.ERROR) { GhostButton("Try again", connect, color = c.danger) }
            Spacer(Modifier.height(Space.m))
        }
        if (status.lockdown == true) {
            Banner("Kill switch active", "Android blocks connections without the VPN (system setting).", Tone.INFO)
            Spacer(Modifier.height(Space.m))
        }

        if (status.isConnected) {
            SessionStats(status)
            Spacer(Modifier.height(Space.m))
        }

        ServerCard(status, settings, selected, onClick = { go(Routes.SERVERS) })
        Spacer(Modifier.height(Space.m))
        SecureDnsCard(status, settings, onToggle = { on -> if (on) enableSecureDns() else vm.setSecureDns(false) }, onClick = { go(Routes.DNS) })
        Spacer(Modifier.height(Space.xl))

        Text("Routing", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(Space.s))
        SegmentedControl(VpnMode.entries.map { it.label }, settings.mode.ordinal, { vm.setMode(VpnMode.entries[it]) })
        Row(Modifier.fillMaxWidth().padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (settings.mode == VpnMode.ALL_APPS) "Every app goes through the server." else "Only the websites you choose go through the server; everything else connects directly.",
                style = MaterialTheme.typography.bodySmall, color = c.text2, modifier = Modifier.weight(1f),
            )
            if (settings.mode == VpnMode.ONLY_MY_SITES) GhostButton("Manage sites", { go(Routes.SITES) })
        }
    }
}

@Composable
private fun StatusHeadline(status: VpnStatus) {
    val c = Tervyn.colors
    val title = when (status.phase) {
        Phase.DISCONNECTED -> "Not connected"
        Phase.DNS_ONLY -> "Secure DNS only"
        Phase.CONNECTED -> "Connected"
        Phase.FAILED -> "Connection failed"
        else -> status.phase.label.trimEnd('…')
    }
    val sub = when (status.phase) {
        Phase.DISCONNECTED -> "Tap the button to connect"
        Phase.DNS_ONLY -> "Lookups are encrypted. Tap to connect a server."
        Phase.CONNECTED -> status.server?.let { "${Format.countryName(status.countryCode ?: it.displayCountry)} · ${it.protocolLabel}" }
        Phase.TESTING -> if (status.testTotal > 0) "Tested ${status.tested}/${status.testTotal} · ${status.working} working" else status.message
        Phase.FAILED -> null
        else -> status.message
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = if (status.phase == Phase.DISCONNECTED) c.text else phaseColor(status.phase), textAlign = TextAlign.Center)
        if (!sub.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.text2, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SessionStats(status: VpnStatus) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status.connectedSince) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        StatTile("Download", Format.speed(status.downBps), Modifier.weight(1f))
        StatTile("Upload", Format.speed(status.upBps), Modifier.weight(1f))
    }
    Spacer(Modifier.height(Space.s))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        StatTile("Session", Format.duration(now - status.connectedSince), Modifier.weight(1f))
        StatTile("Exit IP", status.exitIp ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun ServerCard(status: VpnStatus, settings: AppSettings, selected: Server?, onClick: () -> Unit) {
    val c = Tervyn.colors
    val shown = if (status.isActive) status.server ?: selected else selected
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (shown != null) FlagTile(status.countryCode?.takeIf { status.isConnected } ?: shown.displayCountry) else IconBadge(Icons.Rounded.Bolt, c.accent2, 44.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(if (shown != null) shown.name else "Fastest available", style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    shown == null -> "Tervyn picks the fastest verified server"
                    status.isConnected -> "${shown.protocolLabel}${status.exitIp?.let { " · $it" } ?: ""}"
                    settings.autoSwitch -> "Fastest available · ${shown.protocolLabel}"
                    else -> shown.protocolLabel
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.s))
            if (status.isConnected) LatencyBadge(status.pingMs, alive = true, tested = true)
            else if (shown != null) LatencyBadge(shown.latencyMs, shown.alive, shown.lastChecked > 0)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.text3)
        }
    }
}

@Composable
private fun SecureDnsCard(status: VpnStatus, settings: AppSettings, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val c = Tervyn.colors
    val provider = DohForwarder.PRESETS.entries.firstOrNull { it.value == settings.dohUrl }?.key ?: "Custom resolver"
    val sub = when {
        status.isDnsOnly -> "Active · $provider · ${status.dnsQueries} lookups"
        status.isActive && settings.secureDns -> "Paused while connected — the server resolves names"
        settings.secureDns -> "Starting…"
        else -> "Encrypt DNS lookups when the VPN is off"
    }
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Dns, if (status.isDnsOnly) c.success else c.accent2, 44.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Secure DNS", style = MaterialTheme.typography.titleMedium, color = c.text)
                    if (status.isDnsOnly) { Spacer(Modifier.width(Space.s)); Pill("ON", c.success) }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.s))
            TervynSwitch(settings.secureDns, onToggle)
        }
    }
}

@Suppress("unused")
private val shieldIcon = Icons.Rounded.Shield
