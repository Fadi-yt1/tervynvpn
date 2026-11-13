package com.tervyn.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.AnimatedGlobe
import com.tervyn.vpn.ui.components.AppTile
import com.tervyn.vpn.ui.components.BrandHeader
import com.tervyn.vpn.ui.components.ConnectionStatus
import com.tervyn.vpn.ui.components.CountryFlag
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.ModeToggle
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SectionHeader
import com.tervyn.vpn.ui.components.SettingsRow
import com.tervyn.vpn.ui.components.StatTile
import com.tervyn.vpn.ui.components.ToggleSetting
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.components.VPNPowerButton
import com.tervyn.vpn.ui.components.phaseColor
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import com.tervyn.vpn.vpn.Phase
import kotlinx.coroutines.delay

val presetEmoji = mapOf(
    "youtube" to "▶️", "instagram" to "📸", "facebook" to "📘", "x" to "✖️", "discord" to "🎮", "telegram" to "✈️",
    "chatgpt" to "🤖", "claude" to "✳️", "netflix" to "🎬", "whatsapp" to "💬", "spotify" to "🎵", "reddit" to "👽",
    "tiktok" to "🎶", "google" to "🔎",
)

@Composable
fun rememberNow(active: Boolean): Long {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active) { now.longValue = System.currentTimeMillis(); delay(1000) } }
    return now.longValue
}

@Composable
fun HomeScreen(vm: MainViewModel, connect: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()
    val domains by vm.domains.collectAsState()
    val servers by vm.servers.collectAsState()
    val s = settings ?: return
    val now = rememberNow(status.isConnected)
    val selected = s.selectedServerId?.let { id -> servers.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxSize().background(c.backgroundGradient).statusBarsPadding().verticalScroll(rememberScrollState())) {
        BrandHeader {
            IconButton(onClick = { go(Routes.SETTINGS) }, modifier = Modifier.clip(CircleShape).background(c.card)) {
                Icon(Icons.Rounded.Settings, "Settings", tint = c.text)
            }
        }
        Box(Modifier.fillMaxWidth().height(290.dp), contentAlignment = Alignment.Center) {
            AnimatedGlobe(Modifier.fillMaxWidth().height(290.dp), active = status.isConnected)
            VPNPowerButton(status, onClick = { if (status.isActive) vm.disconnect() else connect() })
        }
        ConnectionStatus(status, Modifier.fillMaxWidth().padding(horizontal = Space.gutter))
        Column(Modifier.padding(horizontal = Space.gutter)) {
            if (status.isConnected) {
                Spacer(Modifier.height(Space.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StatTile("Download", Format.speed(status.downBps), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
                    StatTile("Upload", Format.speed(status.upBps), Icons.Rounded.ArrowUpward, Modifier.weight(1f), c.purple)
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StatTile("Duration", Format.duration(now - status.connectedSince), Icons.Rounded.Timer, Modifier.weight(1f), c.green)
                    StatTile("Ping", status.pingMs?.let { "$it ms" } ?: "—", Icons.Rounded.Speed, Modifier.weight(1f), c.amber)
                    StatTile("IP", status.exitIp ?: "—", Icons.Rounded.Public, Modifier.weight(1.3f))
                }
            }
            if (status.phase == Phase.FAILED && status.error != null) {
                Spacer(Modifier.height(Space.m))
                NotificationCard("Could not connect", status.error!!, Tone.ERROR) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        NeonButton("Retry", connect)
                        NeonButton("Diagnose", { go(Routes.DIAGNOSTICS) }, secondary = true)
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            ModeToggle(s.mode, { vm.setMode(it) })
            if (s.mode == VpnMode.ONLY_MY_SITES) {
                Spacer(Modifier.height(Space.s))
                Text("${domains.count { it.enabled }} website rules active · Manage in My Sites", color = c.text2, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(Space.m))
            // Location card
            GlassCard(onClick = { go(Routes.SERVERS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.autoSwitch && s.preferredCountry == null) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(c.blue.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Language, null, tint = c.cyan, modifier = Modifier.size(32.dp))
                        }
                    } else CountryFlag(selected?.countryCode ?: s.preferredCountry, 52.dp)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        val title = when {
                            selected != null && !s.autoSwitch -> selected.name
                            s.preferredCountry != null -> "Fastest in ${Format.countryName(s.preferredCountry)}"
                            else -> "Fastest Available"
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bolt, null, tint = c.amber, modifier = Modifier.size(16.dp))
                            Text(
                                if (status.isConnected && status.server != null) "Now: ${status.server!!.name}" else if (s.autoSwitch) "Auto-selects the best server" else "Manual server · auto-switch off",
                                style = MaterialTheme.typography.bodySmall, color = c.text2,
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.text2)
                }
            }
            SectionHeader("Quick Access", Icons.Rounded.Bolt) {
                Text("See All", color = c.cyan, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(CircleShape).clickable { go(Routes.SITES) }.padding(4.dp))
            }
            val enabledPresets = domains.mapNotNull { it.presetId }.toSet()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Domains.presets.take(6).forEach { p ->
                    AppTile(p.label, presetEmoji[p.id] ?: "🌐", p.id in enabledPresets, { vm.togglePreset(p, p.id !in enabledPresets) }, Modifier.weight(1f))
                }
            }
            Text("Tap an app to add or remove it from Only My Sites.", color = c.text3, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(Space.m))
            GlassCard {
                ToggleSetting(Icons.Rounded.Speed, "Auto-Switch to Fastest Server", "Switch to a faster server when available", s.autoSwitch, { vm.setAutoSwitch(it) }, tint = c.green)
                SettingsRow(Icons.Rounded.VerifiedUser, "Check My IP", "View your current IP and country", { go(Routes.IP) }, tint = c.purple)
                SettingsRow(Icons.Rounded.Dns, "Choose Server", if (status.isConnected) "Connected via ${status.server?.protocolLabel}" else "Countries, favourites, recent", { go(Routes.SERVERS) })
                SettingsRow(Icons.Rounded.Speed, "Speed Test", "Measure your connection", { go(Routes.SPEED) }, tint = c.amber)
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

private data class Step(val phase: Phase, val title: String)

@Composable
fun ConnectingScreen(vm: MainViewModel, onBack: () -> Unit, onConnected: () -> Unit) {
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    LaunchedEffect(status.phase) {
        if (status.phase == Phase.CONNECTED) onConnected()
        if (status.phase == Phase.DISCONNECTED) onBack()
    }
    val steps = listOf(Step(Phase.FETCHING, "Fetching servers"), Step(Phase.TESTING, "Testing servers over verified HTTPS"), Step(Phase.CONNECTING, "Establishing the secure tunnel"), Step(Phase.CONNECTED, "Verifying exit IP"))
    val order = listOf(Phase.FETCHING, Phase.TESTING, Phase.CONNECTING, Phase.CONNECTED)
    val idx = order.indexOf(status.phase).let { if (it < 0) 0 else it }
    ScreenScaffold("Connecting", "Finding the fastest working server", onBack) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            AnimatedGlobe(Modifier.fillMaxSize(), active = true)
            VPNPowerButton(status, onClick = { vm.disconnect() }, size = 190.dp)
        }
        ConnectionStatus(status, Modifier.fillMaxWidth())
        if (status.phase == Phase.TESTING && status.testTotal > 0) {
            Spacer(Modifier.height(Space.m))
            LinearProgressIndicator(progress = { status.tested.toFloat() / status.testTotal }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = c.cyan, trackColor = c.bg2)
        }
        Spacer(Modifier.height(Space.l))
        GlassCard {
            steps.forEachIndexed { i, st ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        status.phase == Phase.FAILED -> Icon(Icons.Rounded.Close, null, tint = c.red)
                        i < idx -> Icon(Icons.Rounded.CheckCircle, null, tint = c.green)
                        i == idx -> CircularProgressIndicator(Modifier.size(22.dp), color = c.cyan, strokeWidth = 2.dp)
                        else -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = c.text3)
                    }
                    Spacer(Modifier.width(Space.m))
                    Text(st.title, color = if (i <= idx) c.text else c.text3, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (status.phase == Phase.FAILED) {
            Spacer(Modifier.height(Space.m))
            NotificationCard("Connection failed", status.error ?: "Unknown error", Tone.ERROR)
        }
        Spacer(Modifier.height(Space.l))
        NeonButton(if (status.phase == Phase.FAILED) "Back" else "Cancel", { if (status.isActive) vm.disconnect() else onBack() }, Modifier.fillMaxWidth(), secondary = true)
    }
}

@Composable
fun ConnectedScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    val now = rememberNow(status.isConnected)
    LaunchedEffect(status.phase) { if (!status.isActive) onBack() }
    ScreenScaffold("VPN Connected", status.server?.name, onBack) {
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            AnimatedGlobe(Modifier.fillMaxSize(), active = true)
            VPNPowerButton(status, onClick = { vm.disconnect() }, size = 180.dp)
        }
        ConnectionStatus(status, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.l))
        GlassCard(highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountryFlag(status.countryCode, 52.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(Format.countryName(status.countryCode), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("Exit IP ${status.exitIp ?: "—"} · ${status.server?.protocolLabel ?: ""}", style = MaterialTheme.typography.bodySmall, color = c.text2)
                }
                Text(status.phase.label, color = phaseColor(status.phase), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Download", Format.speed(status.downBps), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
            StatTile("Upload", Format.speed(status.upBps), Icons.Rounded.ArrowUpward, Modifier.weight(1f), c.purple)
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Duration", Format.duration(now - status.connectedSince), Icons.Rounded.Timer, Modifier.weight(1f), c.green)
            StatTile("Ping", status.pingMs?.let { "$it ms" } ?: "—", Icons.Rounded.Speed, Modifier.weight(1f), c.amber)
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Received", Format.bytes(status.totalDown), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
            StatTile("Sent", Format.bytes(status.totalUp), Icons.Rounded.ArrowUpward, Modifier.weight(1f), c.purple)
        }
        Spacer(Modifier.height(Space.m))
        GlassCard {
            SettingsRow(Icons.Rounded.VerifiedUser, "Check My IP", null, { go(Routes.IP) })
            SettingsRow(Icons.Rounded.Speed, "Speed Test", null, { go(Routes.SPEED) })
            SettingsRow(Icons.Rounded.Dns, "Connection Statistics", null, { go(Routes.STATS) })
        }
        Spacer(Modifier.height(Space.m))
        NeonButton("Disconnect", { vm.disconnect() }, Modifier.fillMaxWidth(), danger = true)
    }
}
