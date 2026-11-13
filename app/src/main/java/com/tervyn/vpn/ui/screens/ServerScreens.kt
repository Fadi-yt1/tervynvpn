package com.tervyn.vpn.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.core.Links
import com.tervyn.vpn.core.ProxyList
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.CountryFlag
import com.tervyn.vpn.ui.components.EmptyState
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.LoadingIndicator
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.PingIndicator
import com.tervyn.vpn.ui.components.Pill
import com.tervyn.vpn.ui.components.ProxyInputField
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SectionHeader
import com.tervyn.vpn.ui.components.ServerCard
import com.tervyn.vpn.ui.components.SettingsRow
import com.tervyn.vpn.ui.components.ToggleSetting
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format

private enum class ServerFilter(val label: String) { ALL("All"), VERIFIED("Verified"), FAVORITES("Favorites"), RECENT("Recent") }

@Composable
fun ServersScreen(vm: MainViewModel, go: (String) -> Unit, onBack: (() -> Unit)?) {
    val c = Tervyn.colors
    val servers by vm.servers.collectAsState()
    val settings by vm.settings.collectAsState()
    val status by vm.status.collectAsState()
    val progress by vm.progress.collectAsState()
    val s = settings ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ServerFilter.VERIFIED) }
    val pool = servers.filter { it.origin in s.sourceMode.origins }

    ScreenScaffold("Servers", "Choose a country or use the fastest server", onBack, actions = {
        IconButton(onClick = { if (progress.running) vm.cancelRefresh() else vm.refreshServers() }) {
            Icon(Icons.Rounded.Refresh, if (progress.running) "Cancel refresh" else "Refresh", tint = c.cyan)
        }
    }) {
        if (progress.running) {
            GlassCard {
                Text("${progress.phase} · ${progress.tested}/${progress.total} · ${progress.working} working", color = c.text2, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { if (progress.total == 0) 0f else progress.tested.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape), color = c.cyan, trackColor = c.bg2)
            }
            Spacer(Modifier.height(Space.s))
        }
        // Fastest available
        GlassCard(highlight = s.autoSwitch && s.preferredCountry == null, onClick = { vm.selectCountry(null) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = c.amber, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Fastest Available", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("Automatically selects the best server · auto-switch on", style = MaterialTheme.typography.bodySmall, color = c.text2)
                }
                Pill("Recommended", c.green)
            }
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeonButton("Countries", { go(Routes.COUNTRIES) }, Modifier.weight(1f), icon = Icons.Rounded.Public, secondary = true)
            NeonButton("Add server", { go(Routes.PROTOCOLS) }, Modifier.weight(1f), icon = Icons.Rounded.Add, secondary = true)
        }
        Spacer(Modifier.height(Space.s))
        Text("Source: ${s.sourceMode.label} · ${pool.size} servers, ${pool.count { it.alive }} verified", color = c.text3, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { go(Routes.SOURCES) })
        Spacer(Modifier.height(Space.s))
        ProxyInputField(query, { query = it }, "Search servers, countries, protocols", placeholder = "e.g. Germany, VLESS, 1.2.3.4")
        Row(Modifier.padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            ServerFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.blue.copy(alpha = .3f), selectedLabelColor = c.cyan, labelColor = c.text2))
            }
        }
        val q = query.trim().lowercase()
        val shown = pool.filter { srv ->
            when (filter) {
                ServerFilter.ALL -> true
                ServerFilter.VERIFIED -> srv.alive
                ServerFilter.FAVORITES -> srv.favorite
                ServerFilter.RECENT -> srv.lastUsed > 0
            }
        }.filter { srv ->
            q.isEmpty() || srv.name.lowercase().contains(q) || srv.protocolLabel.lowercase().contains(q) ||
                Format.countryName(srv.countryCode).lowercase().contains(q) || (srv.countryCode?.lowercase() == q)
        }.let { l -> if (filter == ServerFilter.RECENT) l.sortedByDescending { it.lastUsed } else vm.ranked(l.filter { it.alive }) + l.filter { !it.alive } }
            .take(300)
        if (shown.isEmpty()) {
            if (progress.running) LoadingIndicator("Testing servers…")
            else EmptyState(Icons.Rounded.Search, "No servers here yet",
                if (filter == ServerFilter.VERIFIED) "Tap refresh to download and test servers, or connect — Tervyn finds servers automatically." else "Try another filter.") {
                NeonButton("Find servers", { vm.refreshServers() }, icon = Icons.Rounded.Refresh)
            }
        }
        shown.forEach { srv ->
            ServerCard(srv, selected = (!s.autoSwitch && s.selectedServerId == srv.id) || status.server?.id == srv.id,
                onClick = { go(Routes.server(srv.id)) }, onFavorite = { vm.toggleFavorite(srv) })
            Spacer(Modifier.height(Space.s))
        }
        Spacer(Modifier.height(Space.s))
        GlassCard {
            ToggleSetting(Icons.Rounded.VerifiedUser, "Auto-Switch to Fastest Server", "Tervyn will switch to a faster server when available", s.autoSwitch, { vm.setAutoSwitch(it) }, tint = c.green)
        }
    }
}

@Composable
fun ServerDetailsScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    val c = Tervyn.colors
    val servers by vm.servers.collectAsState()
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val srv = servers.firstOrNull { it.id == id }
    var testing by remember { mutableStateOf(false) }
    if (srv == null) { ScreenScaffold("Server", null, onBack) { EmptyState(Icons.Rounded.Search, "Server not found", "It may have been removed.") }; return }
    val outbound = srv.xrayLink?.let { runCatching { Links.parse(it) }.getOrNull() }
    ScreenScaffold(srv.name, srv.protocolLabel, onBack, actions = {
        IconButton(onClick = { vm.toggleFavorite(srv) }) { Icon(Icons.Rounded.Star, "Favorite", tint = if (srv.favorite) c.amber else c.text3) }
    }) {
        GlassCard(highlight = status.server?.id == srv.id) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountryFlag(srv.countryCode, 56.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(if (srv.countryCode != null) Format.countryName(srv.countryCode) else "Location not verified yet", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(if (srv.alive) "Online · verified ${Format.ago(srv.lastChecked)}" else if (srv.lastChecked > 0) "Offline at last check" else "Not tested yet",
                        color = if (srv.alive) c.green else c.text2, style = MaterialTheme.typography.bodySmall)
                }
                PingIndicator(if (srv.alive) srv.latencyMs else null)
            }
        }
        Spacer(Modifier.height(Space.m))
        GlassCard {
            Detail("Protocol", srv.protocolLabel)
            Detail("Address", srv.endpoint?.let { "${it.host}:${it.port}" } ?: outbound?.let { "${it.address}:${it.port}" } ?: "—")
            if (srv.endpoint?.hasAuth == true || outbound?.username?.isNotEmpty() == true) Detail("Authentication", "Username & password (encrypted on device)")
            outbound?.let {
                Detail("Transport", it.network.uppercase())
                Detail("Security", it.security.uppercase() + if (it.pinnedPeerCertSha256.isNotEmpty()) " · pinned certificate" else "")
                if (it.sni.isNotEmpty()) Detail("SNI", it.sni)
                if (it.flow.isNotEmpty()) Detail("Flow", it.flow)
            }
            Detail("Verified exit IP", srv.exitIp ?: "—")
            Detail("Latency (HTTPS test)", srv.latencyMs?.let { "$it ms" } ?: "—")
            Detail("Load", "Not reported by this server type")
            Detail("Source", srv.origin.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
            Detail("Failures in a row", srv.failCount.toString())
            Detail("Last used", Format.ago(srv.lastUsed))
        }
        if (srv.origin == ServerOrigin.FREE_PUBLIC) {
            Spacer(Modifier.height(Space.m))
            NotificationCard("Free public server", "Operated by an unknown third party. Avoid sensitive logins through it.", Tone.WARNING)
        }
        if (outbound?.insecureRequested == true) {
            Spacer(Modifier.height(Space.s))
            NotificationCard("Insecure TLS requested", "This link asked to skip certificate checks. Tervyn always verifies certificates; use pinning (pcs) if the server uses a self-signed certificate.", Tone.WARNING)
        }
        Spacer(Modifier.height(Space.l))
        NeonButton(if (!settings!!.autoSwitch && settings!!.selectedServerId == srv.id) "Selected" else "Use this server", {
            vm.selectServer(srv.id)
            onBack()
        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.CheckCircle)
        Text("Selecting a specific server turns auto-switch off.", color = c.text3, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeonButton(if (testing) "Testing…" else "Test now", { testing = true; vm.testServer(srv) { testing = false } }, Modifier.weight(1f), icon = Icons.Rounded.Timer, secondary = true, enabled = !testing)
            NeonButton("Copy", {
                val text = srv.xrayLink ?: srv.endpoint?.let(ProxyList::toUrl) ?: ""
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("server", text))
                vm.toast("Copied (contains credentials)")
            }, Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, secondary = true)
        }
        Spacer(Modifier.height(Space.s))
        NeonButton("Delete server", { vm.deleteServer(srv); onBack() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Delete, danger = true)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = c.text2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.3f))
    }
}

@Composable
fun CountryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val servers by vm.servers.collectAsState()
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    var query by rememberSaveable { mutableStateOf("") }
    // Only countries proven by a successful test (exit IP geolocated by Cloudflare) are listed.
    val byCountry = servers.filter { it.origin in s.sourceMode.origins && it.alive && it.countryCode != null }.groupBy { it.countryCode!! }
    ScreenScaffold("Country", "Only countries of verified servers are shown", onBack) {
        GlassCard(highlight = s.preferredCountry == null, onClick = { vm.selectCountry(null); onBack() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = c.amber); Spacer(Modifier.width(Space.m))
                Text("Any country (fastest)", color = c.text, style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(Space.s))
        ProxyInputField(query, { query = it }, "Search countries")
        Spacer(Modifier.height(Space.s))
        if (byCountry.isEmpty()) EmptyState(Icons.Rounded.Public, "No verified countries yet", "Connect or refresh the server list; countries appear once servers pass the HTTPS test.") {
            NeonButton("Refresh servers", { vm.refreshServers() })
        }
        byCountry.entries.sortedBy { e -> e.value.mapNotNull { it.latencyMs }.minOrNull() ?: Int.MAX_VALUE }
            .filter { query.isBlank() || Format.countryName(it.key).contains(query, true) || it.key.equals(query, true) }
            .forEach { (cc, list) ->
                GlassCard(highlight = s.preferredCountry == cc, onClick = { vm.selectCountry(cc); onBack() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountryFlag(cc)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(Format.countryName(cc), color = c.text, style = MaterialTheme.typography.titleSmall)
                            Text("${list.size} verified server${if (list.size == 1) "" else "s"}", color = c.text2, style = MaterialTheme.typography.bodySmall)
                        }
                        PingIndicator(list.mapNotNull { it.latencyMs }.minOrNull())
                    }
                }
                Spacer(Modifier.height(Space.s))
            }
    }
}

@Composable
fun AutoSwitchScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    ScreenScaffold("Auto-Switch", "How Tervyn picks and changes servers", onBack) {
        GlassCard {
            ToggleSetting(Icons.Rounded.Bolt, "Auto-switch to fastest server", if (s.autoSwitch) "On: uses the fastest verified server and keeps backups ready" else "Off: stays on your selected server", s.autoSwitch, { vm.setAutoSwitch(it) }, tint = c.green)
        }
        Spacer(Modifier.height(Space.m))
        GlassCard {
            Text("Switch when a server is at least ${s.switchThresholdPct}% faster", color = c.text, style = MaterialTheme.typography.titleSmall)
            Slider(s.switchThresholdPct.toFloat(), { v -> vm.updateSettings { it.copy(switchThresholdPct = v.toInt()) } }, valueRange = 20f..80f, steps = 11,
                colors = SliderDefaults.colors(thumbColor = c.cyan, activeTrackColor = c.blue))
            Text("A faster server must be confirmed on two consecutive checks and Tervyn waits at least 5 minutes between switches to avoid flapping.", color = c.text2, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(Space.m))
        GlassCard {
            Text("Health check every ${s.healthCheckSec / 60} min ${s.healthCheckSec % 60} s", color = c.text, style = MaterialTheme.typography.titleSmall)
            Slider(s.healthCheckSec.toFloat(), { v -> vm.updateSettings { it.copy(healthCheckSec = (v / 30).toInt() * 30) } }, valueRange = 60f..600f,
                colors = SliderDefaults.colors(thumbColor = c.cyan, activeTrackColor = c.blue))
            Text("Refresh free proxy lists every ${s.listRefreshMin} min", color = c.text, style = MaterialTheme.typography.titleSmall)
            Slider(s.listRefreshMin.toFloat(), { v -> vm.updateSettings { it.copy(listRefreshMin = v.toInt()) } }, valueRange = 10f..120f,
                colors = SliderDefaults.colors(thumbColor = c.cyan, activeTrackColor = c.blue))
            Text("Checks run only while connected.", color = c.text2, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(Space.m))
        GlassCard {
            Text("Test up to ${s.testConcurrency} servers at once", color = c.text, style = MaterialTheme.typography.titleSmall)
            Slider(s.testConcurrency.toFloat(), { v -> vm.updateSettings { it.copy(testConcurrency = v.toInt()) } }, valueRange = 8f..128f,
                colors = SliderDefaults.colors(thumbColor = c.cyan, activeTrackColor = c.blue))
            Text("Test timeout ${s.testTimeoutMs / 1000} s", color = c.text, style = MaterialTheme.typography.titleSmall)
            Slider(s.testTimeoutMs.toFloat(), { v -> vm.updateSettings { it.copy(testTimeoutMs = (v / 1000).toInt() * 1000) } }, valueRange = 3000f..20000f,
                colors = SliderDefaults.colors(thumbColor = c.cyan, activeTrackColor = c.blue))
        }
        Spacer(Modifier.height(Space.m))
        NotificationCard("Rules", "• Selecting a specific server turns auto-switch off.\n• Selecting Fastest Available turns it on.\n• With auto-switch off Tervyn never moves you to another server; it retries your server instead.", Tone.INFO)
    }
}

@Composable
fun CheckIpScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val ip by vm.ip.collectAsState()
    val loading by vm.ipLoading.collectAsState()
    val status by vm.status.collectAsState()
    LaunchedEffect(status.isConnected) { vm.checkIp() }
    ScreenScaffold("Check My IP", "Your public address as websites see it", onBack, actions = {
        IconButton(onClick = { vm.checkIp() }) { Icon(Icons.Rounded.Refresh, "Refresh", tint = c.cyan) }
    }) {
        val (info, err) = ip
        when {
            loading -> LoadingIndicator("Checking…")
            err != null -> NotificationCard("Could not check IP", err, Tone.ERROR)
            info != null -> {
                GlassCard(highlight = info.viaVpn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountryFlag(info.countryCode, 64.dp)
                        Spacer(Modifier.width(Space.m))
                        Column {
                            Text(info.ip, style = MaterialTheme.typography.headlineSmall, color = c.text)
                            Text(Format.countryName(info.countryCode), color = c.text2)
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                    Pill(if (info.viaVpn) "Protected by Tervyn VPN" else "Not protected — this is your real IP", if (info.viaVpn) c.green else c.amber)
                }
                Spacer(Modifier.height(Space.m))
                GlassCard {
                    Detail("City", info.city ?: "Not available")
                    Detail("Region", info.region ?: "Not available")
                    Detail("ISP / network", info.isp ?: "Not available")
                    Detail("Connection", if (info.viaVpn) "VPN · ${status.server?.name ?: ""}" else "Direct")
                    if (info.viaVpn) Detail("Protocol", status.server?.protocolLabel ?: "—")
                }
                Spacer(Modifier.height(Space.s))
                Text("Address and country come from Cloudflare's trace endpoint; city and ISP from the lookup service in DNS & Network settings and may be approximate.",
                    color = c.text3, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(Space.m))
        NeonButton("Refresh", { vm.checkIp() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
    }
}

