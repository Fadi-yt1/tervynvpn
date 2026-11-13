package com.tervyn.vpn.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tervyn.vpn.BuildConfig
import com.tervyn.vpn.R
import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.*
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import com.tervyn.vpn.vpn.Phase
import com.tervyn.vpn.vpn.VpnStatus
import kotlinx.coroutines.launch

private fun mbps(bps: Long) = String.format(java.util.Locale.US, "%.1f", bps * 8 / 1_000_000.0)

@Composable
private fun Screen(content: @Composable ColumnScope.() -> Unit) {
    val c = Tervyn.colors
    Column(Modifier.fillMaxSize().background(c.backgroundGradient).statusBarsPadding().verticalScroll(rememberScrollState()), content = content)
}

@Composable
private fun RowDivider() = HorizontalDivider(color = Tervyn.colors.cardBorder, thickness = 1.dp)

@Composable
private fun PlainSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Tervyn.colors
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF1677FF), checkedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFF1E2A48), uncheckedThumbColor = Color(0xFFDDE6F7), uncheckedBorderColor = c.cardBorder))
}

/** Row with a round tinted icon, title/subtitle and a trailing slot (Home settings card). */
@Composable
private fun RoundIconRow(icon: ImageVector, tint: Color, title: String, sub: String?, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF1B2950), Color(0xFF0B1532)))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.width(Space.l))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, color = c.text2, fontSize = 14.sp)
        }
        trailing()
    }
}

// ============================================================================================
// Home (mockup: "Tap to Connect")
// ============================================================================================

@Composable
fun HomeScreen(vm: MainViewModel, connect: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()
    val domains by vm.domains.collectAsState()
    val s = settings ?: return
    val now = rememberNow(status.isConnected)
    Screen {
        MockHeader(subtitle = "Fast • Secure • Free", logoSize = 64.dp)
        // Hero: world map with the power button (disconnected) or the green shield (connected).
        Box(Modifier.fillMaxWidth().aspectRatio(852f / 400f), contentAlignment = Alignment.Center) {
            if (status.isConnected) {
                Art(R.drawable.art_connected, Modifier.fillMaxSize().clickable { go(Routes.CONNECTED) }, ContentScale.Crop, "Connected — open details")
            } else {
                Art(R.drawable.art_home_map, Modifier.fillMaxSize(), ContentScale.Crop)
                if (status.phase.busy) ProgressRing(connectProgress(status), 150.dp) {}
                Box(Modifier.fillMaxHeight(0.72f).aspectRatio(1f).clip(CircleShape)
                    .clickable(role = Role.Button) { if (status.isActive) vm.disconnect() else connect() }
                    .semantics { contentDescription = if (status.isActive) "Cancel connection" else "Connect" })
            }
        }
        Text(
            when (status.phase) { Phase.DISCONNECTED -> "Tap to Connect"; Phase.CONNECTED -> "Connected"; else -> status.phase.label },
            Modifier.fillMaxWidth(), color = if (status.isConnected) c.green else if (status.phase == Phase.FAILED) c.red else c.text,
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        val sub = when {
            status.phase == Phase.FAILED -> status.error
            status.isConnected -> "${Format.flag(status.countryCode)} ${status.server?.name ?: ""} · IP ${status.exitIp ?: "—"}"
            status.phase.busy -> status.message
            else -> null
        }
        if (sub != null) Text(sub, Modifier.fillMaxWidth().padding(horizontal = Space.gutter), color = c.text2, textAlign = TextAlign.Center, maxLines = 2)
        Column(Modifier.padding(horizontal = Space.gutter)) {
            if (status.isConnected) {
                Spacer(Modifier.height(Space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StatTile("Duration", Format.duration(now - status.connectedSince), Icons.Rounded.Timer, Modifier.weight(1f), c.green)
                    StatTile("Download", "${mbps(status.downBps)} Mbps", Icons.Rounded.ArrowDownward, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StatTile("Upload", "${mbps(status.upBps)} Mbps", Icons.Rounded.ArrowUpward, Modifier.weight(1f), c.purple)
                    StatTile("Ping", status.pingMs?.let { "$it ms" } ?: "—", Icons.Rounded.Speed, Modifier.weight(1f), c.amber)
                }
            }
            if (status.phase == Phase.FAILED) {
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    NeonButton("Retry", connect, Modifier.weight(1f))
                    NeonButton("Diagnose", { go(Routes.DIAGNOSTICS) }, Modifier.weight(1f), secondary = true)
                }
            }
            Spacer(Modifier.height(Space.l))
            ModeToggle(s.mode, { vm.setMode(it); if (it == VpnMode.ONLY_MY_SITES) go(Routes.SITES) })
            Spacer(Modifier.height(Space.l))
            GlassCard(onClick = { go(Routes.SERVERS) }, padding = PaddingValues(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.preferredCountry == null && (s.autoSwitch || s.selectedServerId == null)) Art(R.drawable.art_earth, Modifier.size(72.dp).clip(CircleShape), ContentScale.Crop)
                    else RoundFlag(s.preferredCountry ?: status.countryCode, 72.dp)
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f)) {
                        Text(when {
                            !s.autoSwitch && s.selectedServerId != null -> status.server?.name ?: "Selected server"
                            s.preferredCountry != null -> "Fastest in ${Format.countryName(s.preferredCountry)}"
                            else -> "Fastest Available"
                        }, color = c.text, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bolt, null, tint = c.amber, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.autoSwitch) "Auto-selects the best server" else "Manual server · auto-switch off", color = c.text2, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.cardBorder, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.text, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = c.amber, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(Space.s))
                Text("Quick Access", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(Modifier.clickable { go(Routes.SITES) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("See All", color = Color(0xFF29B6FF), fontSize = 20.sp)
                    Icon(Icons.Rounded.ArrowForward, null, tint = Color(0xFF29B6FF))
                }
            }
            val enabled = domains.mapNotNull { it.presetId }.toSet()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("youtube", "instagram", "x", "discord", "telegram", "chatgpt").forEach { id ->
                    val p = Domains.presets.first { it.id == id }
                    LogoTile(p.label, presetLogo(id), id in enabled, { vm.togglePreset(p, id !in enabled) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Space.l))
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.s)) {
                RoundIconRow(Icons.Rounded.Speed, c.green, "Auto-Switch to Fastest Server", "Switch to a faster server when available", null) {
                    PlainSwitch(s.autoSwitch) { vm.setAutoSwitch(it) }
                }
                RowDivider()
                RoundIconRow(Icons.Rounded.VerifiedUser, Color(0xFF8A7BFF), "Check My IP", "View your current IP and country", { go(Routes.IP) }) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.text, modifier = Modifier.size(32.dp))
                }
                RowDivider()
                RoundIconRow(Icons.Rounded.Lock, c.green, "Always-on VPN (Android)", "Keep VPN active at all times", { openVpnSettings(ctx) }) {
                    PlainSwitch(status.alwaysOn == true) { openVpnSettings(ctx) }
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

// ============================================================================================
// Servers (mockup: country list with latency)
// ============================================================================================

@Composable
fun ServersScreen(vm: MainViewModel, go: (String) -> Unit, onBack: (() -> Unit)?) {
    val c = Tervyn.colors
    val servers by vm.servers.collectAsState()
    val settings by vm.settings.collectAsState()
    val status by vm.status.collectAsState()
    val progress by vm.progress.collectAsState()
    val s = settings ?: return
    val pool = servers.filter { it.origin in s.sourceMode.origins }
    val byCountry = pool.filter { it.alive && it.countryCode != null }.groupBy { it.countryCode!! }
        .mapValues { (_, l) -> vm.ranked(l) }.entries.sortedBy { it.value.firstOrNull()?.latencyMs ?: Int.MAX_VALUE }
    Screen {
        MockHeader()
        TitleRow("Servers", "Choose a country or use the fastest server", onBack) {
            PillButton(if (progress.running) "Stop" else "Refresh", Icons.Rounded.Refresh, { if (progress.running) vm.cancelRefresh() else vm.refreshServers() })
        }
        Column(Modifier.padding(horizontal = Space.gutter)) {
            if (progress.running) {
                Spacer(Modifier.height(Space.s))
                Text("${progress.phase} · ${progress.tested}/${progress.total} · ${progress.working} working", color = c.text2, fontSize = 13.sp)
                LinearProgressIndicator(progress = { if (progress.total == 0) 0f else progress.tested.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(CircleShape), color = c.cyan, trackColor = c.bg2)
            }
            Spacer(Modifier.height(Space.m))
            val fastest = s.autoSwitch && s.preferredCountry == null
            GlassCard(highlight = fastest, onClick = { vm.selectCountry(null) }, padding = PaddingValues(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(R.drawable.art_bolt_badge, Modifier.size(84.dp).clip(CircleShape), ContentScale.Crop)
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f)) {
                        Text("Fastest Available", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text("Automatically selects the best server", color = c.text2, fontSize = 15.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Pill("✓ Recommended", c.green)
                        Spacer(Modifier.height(10.dp))
                        Icon(Icons.Rounded.ChevronRight, null, tint = c.text)
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            if (byCountry.isEmpty()) {
                if (progress.running) LoadingIndicator("Testing servers… countries appear as soon as servers are verified")
                else EmptyState(Icons.Rounded.Public, "No verified servers yet", "Tap Refresh, or just connect — Tervyn finds and tests servers automatically.") {
                    NeonButton("Find servers", { vm.refreshServers() }, icon = Icons.Rounded.Refresh)
                }
            }
            byCountry.forEach { (cc, list) ->
                val best = list.first()
                val selected = s.preferredCountry == cc || (status.isConnected && status.countryCode == cc && !fastest && s.preferredCountry == null)
                GlassCard(highlight = selected, onClick = { vm.selectCountry(cc) }, padding = PaddingValues(horizontal = Space.l, vertical = Space.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundFlag(cc, 64.dp)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(Format.countryName(cc), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.size} server${if (list.size == 1) "" else "s"}", color = c.text2, fontSize = 15.sp)
                        }
                        SignalBars(barsFor(best.latencyMs))
                        Spacer(Modifier.width(8.dp))
                        Text("${best.latencyMs} ms", color = c.green, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(28.dp).background(c.cardBorder))
                        Icon(Icons.Rounded.Bolt, null, tint = Color(0xFF1E7BFF), modifier = Modifier.size(24.dp))
                        Text(best.protocolLabel.substringBefore(" ·"), color = c.text, fontSize = 14.sp, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(30.dp).clip(CircleShape).border(2.dp, if (selected) c.cyan else Color(0xFF7A5CFF), CircleShape), contentAlignment = Alignment.Center) {
                            if (selected) Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
            }
            val pinned = pool.filter { it.favorite || it.lastUsed > 0 }.sortedByDescending { if (it.favorite) Long.MAX_VALUE else it.lastUsed }.take(8)
            if (pinned.isNotEmpty()) {
                SectionHeader("Favorites & Recent", Icons.Rounded.Star)
                pinned.forEach { srv ->
                    ServerCard(srv, selected = !s.autoSwitch && s.selectedServerId == srv.id, onClick = { go(Routes.server(srv.id)) }, onFavorite = { vm.toggleFavorite(srv) })
                    Spacer(Modifier.height(Space.s))
                }
            }
            if (byCountry.isNotEmpty()) {
                SectionHeader("All verified servers", Icons.Rounded.Dns)
                vm.ranked(pool).take(40).forEach { srv ->
                    ServerCard(srv, selected = !s.autoSwitch && s.selectedServerId == srv.id, onClick = { go(Routes.server(srv.id)) }, onFavorite = { vm.toggleFavorite(srv) })
                    Spacer(Modifier.height(Space.s))
                }
            }
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NeonButton("Add server", { go(Routes.PROTOCOLS) }, Modifier.weight(1f), icon = Icons.Rounded.Add, secondary = true)
                NeonButton("Source", { go(Routes.SOURCES) }, Modifier.weight(1f), icon = Icons.Rounded.Storage, secondary = true)
            }
            Spacer(Modifier.height(Space.m))
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(R.drawable.art_status_shield, Modifier.size(64.dp).clip(CircleShape), ContentScale.Crop)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text("Auto-Switch to Fastest Server", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text("Tervyn will switch to a faster server when available", color = c.text2, fontSize = 14.sp)
                    }
                    PlainSwitch(s.autoSwitch) { vm.setAutoSwitch(it) }
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

// ============================================================================================
// My Sites (mockup: Only My Sites)
// ============================================================================================

@Composable
fun SitesScreen(vm: MainViewModel, go: (String) -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val domains by vm.domains.collectAsState()
    val status by vm.status.collectAsState()
    val s = settings ?: return
    var adding by remember { mutableStateOf(false) }
    var showAllPresets by rememberSaveable { mutableStateOf(false) }
    val presetIds = domains.mapNotNull { it.presetId }.toSet()
    Screen {
        MockHeader()
        Column(Modifier.padding(horizontal = Space.gutter)) {
            ModeToggle(s.mode, { vm.setMode(it) })
            Spacer(Modifier.height(Space.l))
            GlassCard(highlight = true, padding = PaddingValues(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(R.drawable.art_sites_shield, Modifier.size(64.dp), ContentScale.Crop)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text("Only My Sites", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("Route only the sites you choose through VPN. Everything else stays on your normal connection.", color = c.text2, fontSize = 15.sp)
                    }
                    Art(R.drawable.art_only_sites, Modifier.size(80.dp), ContentScale.Crop)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = c.amber, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(Space.s))
                Text("App Presets", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Tap to add", color = Color(0xFF29B6FF), fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.AddCircleOutline, null, tint = Color(0xFF29B6FF), modifier = Modifier.size(30.dp))
            }
            val main = listOf("youtube", "instagram", "x", "discord", "telegram", "chatgpt", "claude", "netflix")
            val extra = Domains.presets.map { it.id }.filter { it !in main }
            val shown = main + (if (showAllPresets) extra else emptyList())
            (shown.map { it as String? } + listOf<String?>(null)).chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    row.forEach { id ->
                        if (id == null) LogoTile(if (showAllPresets) "Less" else "More Apps", R.drawable.logo_app_more, false, { showAllPresets = !showAllPresets }, Modifier.weight(1f))
                        else {
                            val p = Domains.presets.first { it.id == id }
                            LogoTile(p.label, presetLogo(id), id in presetIds, { vm.togglePreset(p, id !in presetIds) }, Modifier.weight(1f), presetEmoji[id] ?: "🌐")
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, null, tint = Color(0xFF29B6FF), modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(Space.s))
                Text("My Sites", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${domains.count { it.enabled }} active", color = Color(0xFF29B6FF), fontSize = 18.sp)
            }
            val custom = domains.filter { it.presetId == null }
            if (custom.isEmpty()) Text("No websites yet — add one below or share a link from Chrome to Tervyn VPN.", color = c.text2, fontSize = 14.sp)
            custom.forEach { d ->
                GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val logo = siteLogo(d.domain)
                        if (logo != null) Art(logo, Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)), ContentScale.Crop)
                        else Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(c.bg2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Language, null, tint = c.cyan, modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.width(Space.l))
                        Column(Modifier.weight(1f)) {
                            Text(d.domain, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (d.label != d.domain) d.label else "Includes all subdomains", color = c.text2, fontSize = 15.sp)
                        }
                        PlainSwitch(d.enabled) { vm.setDomainEnabled(d.domain, it) }
                        Spacer(Modifier.width(Space.s))
                        Icon(Icons.Rounded.Delete, "Remove ${d.domain}", tint = c.text2, modifier = Modifier.size(30.dp).clickable { vm.removeDomain(d.domain) })
                    }
                }
                Spacer(Modifier.height(Space.s))
            }
            if (presetIds.isNotEmpty()) Text("Presets on: " + Domains.presets.filter { it.id in presetIds }.joinToString { it.label }, color = c.text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            Spacer(Modifier.height(Space.s))
            GlowOutlineButton("Add Website", { adding = true }, Modifier.fillMaxWidth(), Icons.Rounded.Add)
            Spacer(Modifier.height(Space.m))
            val active = s.mode == VpnMode.ONLY_MY_SITES && status.isConnected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card)
                    .border(1.dp, if (active) c.green.copy(alpha = .5f) else c.cardBorder, RoundedCornerShape(20.dp)).padding(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Art(R.drawable.art_secure_badge, Modifier.size(64.dp).clip(CircleShape), ContentScale.Crop)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(if (active) "Connected to Only My Sites" else if (s.mode == VpnMode.ONLY_MY_SITES) "Only My Sites is selected" else "All Apps mode is on",
                        color = if (active) c.green else c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (active) "Your selected sites are now routed through Tervyn VPN." else if (s.mode == VpnMode.ONLY_MY_SITES) "Connect to route your selected sites." else "Switch to Only My Sites to route only these sites.",
                        color = c.text2, fontSize = 14.sp)
                }
                SignalBars(if (active) 4 else 0, c.green, 30.dp)
            }
            Spacer(Modifier.height(Space.m))
            GlassCard { SettingsRow(Icons.Rounded.CallSplit, "App exclusions", "Apps that always bypass the VPN", { go(Routes.SPLIT) }) }
            Spacer(Modifier.height(Space.s))
            Text("Sites are recognised by DNS name. If Android Private DNS is set to a specific provider, use Automatic or Off; reload already-open pages after adding a site.",
                color = c.text3, fontSize = 12.sp)
            Spacer(Modifier.height(Space.xl))
        }
    }
    if (adding) {
        var input by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { adding = false }, containerColor = c.card,
            title = { Text("Add Website", color = c.text) },
            text = { ProxyInputField(input, { input = it; err = null }, "Website (e.g. example.com or a link)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, error = err) },
            confirmButton = { TextButton({ val d = vm.addDomain(input); if (d == null) err = "Not a valid website" else { adding = false; vm.toast("$d added (subdomains included)") } }) { Text("Add", color = c.cyan) } },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel", color = c.text2) } })
    }
}

// ============================================================================================
// Settings (mockup)
// ============================================================================================

@Composable
private fun SectionTitle(icon: ImageVector, title: String, tint: Color = Tervyn.colors.amber) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(Space.m))
        Text(title, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, go: (String) -> Unit, connect: () -> Unit, onBack: (() -> Unit)? = null) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    var exportWithServers by rememberSaveable { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) scope.launch { vm.exportConfig(uri, exportWithServers) } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch { vm.importConfig(uri) } }
    val blue = Color(0xFF29B6FF)
    Screen {
        MockHeader(showSettings = false)
        TitleRow("Settings", "Customize your VPN experience", onBack)
        Column(Modifier.padding(horizontal = Space.gutter)) {
            Spacer(Modifier.height(Space.s))
            GlassCard(highlight = true, onClick = { if (status.isConnected) go(Routes.CONNECTED) else if (!status.isActive) connect() }, padding = PaddingValues(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(R.drawable.art_status_shield, Modifier.size(96.dp), ContentScale.Crop)
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f)) {
                        Text("VPN Status", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(phaseColor(status.phase)))
                            Spacer(Modifier.width(8.dp))
                            Text(status.phase.label, color = phaseColor(status.phase), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(if (status.isConnected) "IP: ${status.exitIp ?: "—"}  •  ${Format.countryName(status.countryCode)} ${Format.flag(status.countryCode)}" else "Tap to connect",
                            color = c.text2, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.text, modifier = Modifier.size(32.dp))
                }
            }
            SectionTitle(Icons.Rounded.Bolt, "VPN Preferences")
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                SettingsRow(Icons.Rounded.Bolt, "Auto-Switch to Fastest Server", "Switch when a faster server is available", null, c.amber) { PlainSwitch(s.autoSwitch) { vm.setAutoSwitch(it) } }
                RowDivider()
                SettingsRow(Icons.Rounded.Language, "Choose Country", s.preferredCountry?.let { Format.countryName(it) } ?: "Fastest Available", { go(Routes.COUNTRIES) }, blue, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.GridView, "VPN Mode", s.mode.label, { go(Routes.SITES) }, blue, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Storage, "Block Connections Without VPN", if (status.lockdown == true) "On — set in Android VPN settings" else "Prevent internet access without VPN", { openVpnSettings(ctx) }, blue) {
                    PlainSwitch(status.lockdown == true) { openVpnSettings(ctx) }
                }
                RowDivider()
                SettingsRow(Icons.Rounded.AllInclusive, "Always-on VPN", "Keep VPN active at all times", { openVpnSettings(ctx) }, blue) {
                    PlainSwitch(status.alwaysOn == true) { openVpnSettings(ctx) }
                }
                RowDivider()
                SettingsRow(Icons.Rounded.Speed, "Auto-Switch Configuration", "Threshold ${s.switchThresholdPct}% · checks every ${s.healthCheckSec}s", { go(Routes.AUTO_SWITCH) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.CallSplit, "Split Tunneling", "${if (s.splitMode.name == "BYPASS") s.excludedApps.size else s.includedApps.size} apps selected", { go(Routes.SPLIT) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Dns, "DNS Settings", com.tervyn.vpn.net.DohForwarder.PRESETS.entries.firstOrNull { it.value == s.dohUrl }?.key ?: "Custom", { go(Routes.DNS) }, blue)
            }
            SectionTitle(Icons.Rounded.Storage, "Server Source", blue)
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                SettingsRow(Icons.Rounded.Storage, "Server Source", s.sourceMode.label, { go(Routes.SOURCES) }, blue, blue)
            }
            SectionTitle(Icons.Rounded.Lock, "Privacy & Security", blue)
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                SettingsRow(Icons.Rounded.VerifiedUser, "Check My IP", "View your current IP and country", { go(Routes.IP) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Description, "Advanced Settings", "More options and configurations", { go(Routes.SOURCES) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Policy, "Privacy & Security Center", null, { go(Routes.PRIVACY) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Fingerprint, "App Lock & Biometrics", if (s.appLock) "On" else "Off", { go(Routes.APP_LOCK) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Shield, "App Permissions", null, { go(Routes.PERMISSIONS) }, blue)
            }
            SectionTitle(Icons.Rounded.Insights, "Monitoring", blue)
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                SettingsRow(Icons.Rounded.Speed, "Server Speed Test", null, { go(Routes.SPEED) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.BarChart, "Connection History & Analytics", null, { go(Routes.HISTORY) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Dashboard, "Connection Statistics", null, { go(Routes.STATS) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.MonitorHeart, "Network Diagnostics", null, { go(Routes.DIAGNOSTICS) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.NotificationsActive, "VPN Connection Notifications", null, { go(Routes.NOTIFICATIONS) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Widgets, "Quick Settings & Notifications", null, { go(Routes.QUICK) }, blue)
            }
            SectionTitle(Icons.Rounded.Palette, "Appearance & Data", blue)
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                SettingsRow(Icons.Rounded.Palette, "Theme", s.theme.label, { go(Routes.THEME) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.PowerSettingsNew, "Connect when Tervyn opens", null, null, blue) { PlainSwitch(s.autoConnectOnLaunch) { v -> vm.updateSettings { it.copy(autoConnectOnLaunch = v) } } }
                RowDivider()
                SettingsRow(Icons.Rounded.RestartAlt, "Connect after reboot", "Needs the VPN permission", null, blue) { PlainSwitch(s.autoConnectOnBoot) { v -> vm.updateSettings { it.copy(autoConnectOnBoot = v) } } }
                RowDivider()
                SettingsRow(Icons.Rounded.WifiTethering, "Reconnect on network change", "Wi-Fi ↔ mobile data", null, blue) { PlainSwitch(s.reconnectOnNetworkChange) { v -> vm.updateSettings { it.copy(reconnectOnNetworkChange = v) } } }
                RowDivider()
                SettingsRow(Icons.Rounded.Key, "Include servers in export", "Contains credentials — keep the file private", null, blue) { PlainSwitch(exportWithServers) { exportWithServers = it } }
                RowDivider()
                SettingsRow(Icons.Rounded.Upload, "Export configuration", null, { exporter.launch("tervyn-config.json") }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.Download, "Import configuration", null, { importer.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.CleaningServices, "Clear cache", null, { confirm = "cache" }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.DeleteSweep, "Clear history", null, { confirm = "history" }, blue)
                RowDivider()
                SettingsRow(Icons.Rounded.RestartAlt, "Reset settings", null, { confirm = "reset" }, c.red)
            }
            SectionTitle(Icons.Rounded.Info, "About", blue)
            GlassCard(padding = PaddingValues(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TervynLogo(64.dp)
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f).clickable { go(Routes.ABOUT) }) {
                        Text("Tervyn VPN", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("Version ${BuildConfig.VERSION_NAME}", color = c.text2, fontSize = 16.sp)
                    }
                    PillButton("Check for Updates", Icons.Rounded.Sync, {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { vm.toast("Updates are delivered through the store you installed Tervyn from") }
                    })
                }
                Spacer(Modifier.height(Space.s))
                SettingsRow(Icons.Rounded.SupportAgent, "Help & Support", null, { go(Routes.HELP) }, blue)
            }
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                GlowOutlineButton("Contact Support", { go(Routes.REPORT) }, Modifier.weight(1f), Icons.Rounded.HeadsetMic)
                NeonButton("Rate Our App", {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { vm.toast("No app store available") }
                }, Modifier.weight(1f).height(60.dp), icon = Icons.Rounded.Star)
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
    val dismiss = { confirm = null }
    when (confirm) {
        "cache" -> ConfirmDialog("Clear cache?", "Removes cached free servers and temporary files. Your own servers are kept.", { vm.clearCache() }, dismiss)
        "history" -> ConfirmDialog("Clear history?", "Deletes connection history, speed tests and events stored on this phone.", { vm.clearHistory() }, dismiss)
        "reset" -> ConfirmDialog("Reset settings?", "Restores default settings and removes the app lock PIN. Servers and websites are kept.", { vm.resetSettings() }, dismiss)
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Tervyn.colors
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.card, title = { Text(title, color = c.text) }, text = { Text(text, color = c.text2) },
        confirmButton = { TextButton({ onConfirm(); onDismiss() }) { Text("Confirm", color = c.red) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = c.text2) } })
}

// ============================================================================================
// Connecting (mockup)
// ============================================================================================

@Composable
private fun CheckStep(state: Int, title: String, value: String?, valueColor: Color, trailing: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            2 -> Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF0E2B2E)).border(2.dp, c.green.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(c.green), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Color(0xFF0B1A2E), modifier = Modifier.size(20.dp)) }
            }
            1 -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(36.dp), color = Color(0xFF1E88FF), strokeWidth = 3.dp, trackColor = Color(0xFF1E88FF).copy(alpha = .2f))
            -1 -> Icon(Icons.Rounded.Cancel, null, tint = c.red, modifier = Modifier.size(40.dp))
            else -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = c.text3, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(Space.l))
        Text(title, color = if (state == 1) Color(0xFF29B6FF) else if (state == 0) c.text3 else c.text, fontSize = 19.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = valueColor, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        trailing?.invoke()
    }
}

@Composable
fun ConnectingScreen(vm: MainViewModel, onBack: () -> Unit, onConnected: () -> Unit) {
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    LaunchedEffect(status.phase) {
        if (status.phase == Phase.CONNECTED) onConnected()
        if (status.phase == Phase.DISCONNECTED) onBack()
    }
    val failed = status.phase == Phase.FAILED
    val order = listOf(Phase.FETCHING, Phase.TESTING, Phase.CONNECTING, Phase.CONNECTED)
    val idx = order.indexOf(status.phase).coerceAtLeast(0)
    fun st(i: Int) = when { failed && i == idx -> -1; i < idx -> 2; i == idx -> 1; else -> 0 }
    val progress = connectProgress(status)
    Screen {
        MockHeader()
        Text(if (failed) "Connection failed" else "Connecting...", Modifier.fillMaxWidth(), color = if (failed) c.red else c.text, fontSize = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(if (failed) status.error ?: "" else "Setting up your secure VPN connection", Modifier.fillMaxWidth().padding(horizontal = Space.gutter), color = c.text2, fontSize = 19.sp, textAlign = TextAlign.Center)
        // Artwork from the mockup with the live progress drawn on top of its ring.
        Box(Modifier.fillMaxWidth().aspectRatio(852f / 470f), contentAlignment = Alignment.Center) {
            Art(R.drawable.art_connecting, Modifier.fillMaxSize(), ContentScale.Crop)
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 852f
                val center = Offset(430f * k, 228f * k)
                val r = 197f * k
                // Cover the static ring of the artwork, then draw the real progress.
                drawCircle(Color(0xFF040B26), r, center, style = Stroke(34f * k))
                drawArc(Color(0xFF0D1C46), 0f, 360f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(22f * k))
                drawArc(Brush.sweepGradient(listOf(Color(0xFF7DF9FF), Color(0xFF1E88FF), Color(0xFFA100FF), Color(0xFF7DF9FF)), center),
                    -90f, 360f * progress, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(22f * k, cap = StrokeCap.Round))
            }
            // Cover the baked percentage with the live value.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val k = maxWidth / 852f
                Box(Modifier.offset(x = 345f * k, y = 360f * k).size(170f * k, 72f * k).clip(RoundedCornerShape(20.dp)).background(Color(0xFF040A22)), contentAlignment = Alignment.Center) {
                    Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.padding(horizontal = Space.gutter)) {
            GlassCard(highlight = true, padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                CheckStep(if (idx >= 2) 2 else if (failed) -1 else 1, "Testing servers...", status.server?.latencyMs?.takeIf { idx >= 2 }?.let { "$it ms" }
                    ?: if (status.testTotal > 0 && idx <= 1) "${status.tested}/${status.testTotal}" else null, c.green)
                RowDivider()
                CheckStep(if (idx >= 2) 2 else if (status.working > 0) 1 else 0, "Finding fastest server...",
                    status.server?.countryCode?.let { Format.countryName(it) } ?: if (status.working > 0) "${status.working} working" else null, c.text) {
                    status.server?.countryCode?.let { Spacer(Modifier.width(8.dp)); Text(Format.flag(it), fontSize = 22.sp) }
                }
                RowDivider()
                CheckStep(if (idx >= 3) 2 else if (idx == 2) 1 else if (failed) -1 else 0, "Establishing secure connection...", null, c.text)
            }
            val srv = status.server
            Spacer(Modifier.height(Space.l))
            GlassCard(highlight = true, padding = PaddingValues(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundFlag(srv?.countryCode, 84.dp)
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f)) {
                        Text(srv?.countryCode?.let { Format.countryName(it) } ?: "Finding server…", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (srv != null) "Fastest Server" else "Testing ${status.testTotal} candidates", color = c.text2, fontSize = 19.sp)
                    }
                    SignalBars(barsFor(srv?.latencyMs), c.green, 40.dp)
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(24.dp)).background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(24.dp)).padding(Space.l),
                verticalAlignment = Alignment.CenterVertically) {
                Art(R.drawable.art_status_shield, Modifier.size(52.dp), ContentScale.Crop)
                Spacer(Modifier.width(Space.m))
                Text(twoTone("Only verified HTTPS servers are used by Tervyn VPN", "Tervyn VPN", c.text2, Color(0xFF29B6FF)), fontSize = 15.sp)
            }
            Spacer(Modifier.height(Space.xl))
            GlowOutlineButton(if (failed) "Back" else "Cancel", { if (status.isActive) vm.disconnect() else onBack() }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.xl))
        }
    }
}

// ============================================================================================
// Connected (mockup)
// ============================================================================================

@Composable
fun ConnectedScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    val now = rememberNow(status.isConnected)
    LaunchedEffect(status.phase) { if (!status.isActive) onBack() }
    Screen {
        MockHeader()
        Art(R.drawable.art_connected, Modifier.fillMaxWidth().aspectRatio(852f / 395f), ContentScale.Crop)
        Text(twoTone("VPN ${if (status.isConnected) "Connected" else status.phase.label}", if (status.isConnected) "Connected" else null, c.text, Color(0xFF39E66A)),
            Modifier.fillMaxWidth(), fontSize = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(if (status.isConnected) "Your traffic is routed through Tervyn VPN" else status.message ?: "", Modifier.fillMaxWidth(), color = c.text2, fontSize = 19.sp, textAlign = TextAlign.Center)
        Column(Modifier.padding(horizontal = Space.gutter)) {
            Spacer(Modifier.height(Space.l))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF0B1D4A), c.card)))
                    .border(2.dp, Brush.horizontalGradient(listOf(Color(0xFF1E88FF), Color(0xFF39E66A))), RoundedCornerShape(26.dp))
                    .clickable { go(Routes.SERVERS) }.padding(Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundFlag(status.countryCode, 64.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(Format.countryName(status.countryCode), color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (status.server?.let { s -> !s.isXray && s.origin.name == "FREE_PUBLIC" } == true) "Fastest Server" else status.server?.name ?: "", color = c.text2, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SignalBars(barsFor(status.pingMs), c.green, 34.dp)
                Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(40.dp).background(c.cardBorder))
                Icon(Icons.Rounded.ChevronRight, null, tint = c.text, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(Space.l))
            Row(Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(40.dp)).background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(40.dp))
                .padding(horizontal = Space.xxl, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = Color(0xFF39E66A), modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(Space.l))
                Column {
                    Text("Connection Time", color = c.text2, fontSize = 18.sp)
                    Text(Format.duration(now - status.connectedSince), color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(Space.l))
            GlassCard(highlight = true, padding = PaddingValues(vertical = Space.l)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ConnStat(Icons.Rounded.ArrowDownward, "Download", mbps(status.downBps), "Mbps", Color(0xFF29B6FF), Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(90.dp).background(c.cardBorder))
                    ConnStat(Icons.Rounded.ArrowUpward, "Upload", mbps(status.upBps), "Mbps", Color(0xFFB85CFF), Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(90.dp).background(c.cardBorder))
                    ConnStat(Icons.Rounded.Speed, "Ping", status.pingMs?.toString() ?: "—", "ms", Color(0xFF39E66A), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Space.l))
            SecureBanner("Your Connection is Protected", "Protected by Tervyn VPN · ${status.server?.protocolLabel ?: ""}")
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                GlowOutlineButton("Disconnect", { vm.disconnect() }, Modifier.weight(1f), Icons.Rounded.Sync)
                GlowOutlineButton("Check My IP", { go(Routes.IP) }, Modifier.weight(1f), Icons.Rounded.BarChart)
            }
            Spacer(Modifier.height(Space.s))
            NeonButton("Connection Statistics", { go(Routes.STATS) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Insights, secondary = true)
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun ConnStat(icon: ImageVector, label: String, value: String, unit: String, tint: Color, modifier: Modifier) {
    val c = Tervyn.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(40.dp))
        Text(label, color = c.text2, fontSize = 17.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(" $unit", color = c.text, fontSize = 17.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
fun SecureBanner(title: String, text: String, ok: Boolean = true) {
    val c = Tervyn.colors
    val color = if (ok) Color(0xFF39E66A) else c.amber
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = .16f), Color(0xFF071A2A))))
            .border(2.dp, color.copy(alpha = .8f), RoundedCornerShape(26.dp)).padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ok) Art(R.drawable.art_secure_badge, Modifier.size(88.dp).clip(CircleShape), ContentScale.Crop)
        else Icon(Icons.Rounded.GppMaybe, null, tint = color, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(Space.l))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text, color = c.text2, fontSize = 15.sp)
        }
    }
}

// ============================================================================================
// Check My IP (mockup)
// ============================================================================================

@Composable
fun CheckIpScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val ip by vm.ip.collectAsState()
    val loading by vm.ipLoading.collectAsState()
    val status by vm.status.collectAsState()
    LaunchedEffect(status.isConnected) { vm.checkIp() }
    Screen {
        MockHeader()
        TitleRow("Check My IP", "View your current IP address and location", onBack)
        Column(Modifier.padding(horizontal = Space.gutter)) {
            Spacer(Modifier.height(Space.m))
            val (info, err) = ip
            if (err != null) NotificationCard("Could not check IP", err, Tone.ERROR)
            GlassCard(highlight = true, padding = PaddingValues(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(R.drawable.art_ip_shield, Modifier.size(150.dp), ContentScale.Crop)
                    Spacer(Modifier.width(Space.m))
                    Column {
                        Text("Your IP Address", color = c.text2, fontSize = 20.sp)
                        Text(info?.ip ?: if (loading) "Checking…" else "—", color = c.text, fontSize = if ((info?.ip?.length ?: 0) > 15) 22.sp else 34.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (info != null) Pill(if (info.viaVpn) "✓  VPN Connected" else "VPN off — real IP", if (info.viaVpn) Color(0xFF39E66A) else c.amber)
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                IpRow(Icons.Rounded.Language, "Country", Format.countryName(info?.countryCode), lead = { Text(Format.flag(info?.countryCode), fontSize = 26.sp) })
                RowDivider()
                IpRow(Icons.Rounded.Place, "Location", listOfNotNull(info?.city, info?.region).joinToString(", ").ifEmpty { "Not available" })
                RowDivider()
                IpRow(Icons.Rounded.Router, "ISP", info?.isp ?: "Not available")
                RowDivider()
                IpRow(Icons.Rounded.Lock, "Connection", if (info?.viaVpn == true) "Secure (VPN)" else "Direct", if (info?.viaVpn == true) Color(0xFF39E66A) else c.amber,
                    lead = { Icon(if (info?.viaVpn == true) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, tint = if (info?.viaVpn == true) Color(0xFF39E66A) else c.amber, modifier = Modifier.size(28.dp)) })
            }
            Spacer(Modifier.height(Space.l))
            Box(Modifier.fillMaxWidth().aspectRatio(798f / 328f).clip(RoundedCornerShape(26.dp)).border(1.dp, c.cardBorder, RoundedCornerShape(26.dp))) {
                Art(R.drawable.art_ip_map, Modifier.fillMaxSize(), ContentScale.Crop)
                if (info != null) Row(Modifier.align(Alignment.BottomEnd).padding(Space.m).clip(RoundedCornerShape(50)).background(Color(0xCC07132D)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = Color(0xFF29B6FF))
                    Text(" ${Format.flag(info.countryCode)} ${Format.countryName(info.countryCode)}", color = c.text, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(Space.l))
            if (info?.viaVpn == false) SecureBanner("Not Protected", "This is your real address. Connect to hide it from websites.", ok = false)
            else SecureBanner("Your Connection is Secure", "Your online activity is protected by Tervyn VPN.")
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                GlowOutlineButton(if (loading) "Checking…" else "Refresh", { vm.checkIp() }, Modifier.weight(1f), Icons.Rounded.Sync)
                GlowOutlineButton("Share IP", {
                    info?.let { i ->
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${i.ip} (${Format.countryName(i.countryCode)})"), "Share IP")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, Modifier.weight(1f), Icons.Rounded.Share)
            }
            Spacer(Modifier.height(Space.s))
            Text("IP and country: Cloudflare trace. City and ISP: lookup service in DNS settings (approximate).", color = c.text3, fontSize = 12.sp)
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun IpRow(icon: ImageVector, label: String, value: String, valueColor: Color = Tervyn.colors.text, lead: (@Composable () -> Unit)? = null) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF29B6FF), modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(Space.l))
        Text(label, color = c.text2, fontSize = 19.sp, modifier = Modifier.weight(0.9f))
        if (lead != null) { lead(); Spacer(Modifier.width(10.dp)) }
        Text(value, color = valueColor, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.1f), maxLines = 2)
    }
}

// ============================================================================================
// App Permissions & VPN Protection (mockup)
// ============================================================================================

@Composable
fun PermissionsScreen(vm: MainViewModel, onBack: () -> Unit, connect: () -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val vpnGranted = remember(refresh, status.phase) { vm.c.controller.needsPermission() == null }
    val notifGranted = remember(refresh) { Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val battery = remember(refresh) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    Screen {
        MockHeader()
        Text("App Permissions & VPN Protection", Modifier.fillMaxWidth().padding(horizontal = Space.gutter), color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Allow VPN permission to protect your online activity", Modifier.fillMaxWidth(), color = Color(0xFF8FB5FF), fontSize = 18.sp, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(end = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
            Art(R.drawable.art_permissions, Modifier.weight(1.15f).aspectRatio(510f / 360f), ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text("Your Privacy\nOur Priority", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text("Tervyn VPN routes your connection through verified servers. No account, no ads, no tracking.", color = c.text2, fontSize = 16.sp)
            }
        }
        Column(Modifier.padding(horizontal = Space.gutter)) {
            GlassCard(highlight = true, padding = PaddingValues(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Android, null, tint = Color(0xFF3DDC84), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(if (vpnGranted) "VPN Permission Granted" else "VPN Permission Required", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (vpnGranted) "Tervyn can create the VPN connection." else "Allow VPN permission to use Tervyn VPN and protect your connection.", color = c.text2, fontSize = 15.sp)
                    }
                    if (!vpnGranted) NeonButton("Grant Permission ›", connect)
                    else Pill("✓ Granted", Color(0xFF39E66A))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = c.cyan, modifier = Modifier.size(40.dp)); Spacer(Modifier.width(Space.m))
                Text("How It Protects You", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                FeatureCard(Icons.Rounded.VisibilityOff, "Hide Your IP", "Websites see the VPN server's address", Modifier.weight(1f))
                FeatureCard(Icons.Rounded.Lock, "Verified Servers", "Every server passes an HTTPS check", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                FeatureCard(Icons.Rounded.Shield, "No Leaks", "DNS and IPv6 stay inside the tunnel", Modifier.weight(1f))
                FeatureCard(Icons.Rounded.Language, "Access More Content", "Reach sites blocked on your network", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.l))
            GlassCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                SettingsRow(Icons.Rounded.Notifications, "Notifications", if (notifGranted) "Granted" else "Needed for the connection status",
                    if (!notifGranted && Build.VERSION.SDK_INT >= 33) ({ notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) else null, Color(0xFF29B6FF), if (notifGranted) c.green else c.amber)
                RowDivider()
                SettingsRow(Icons.Rounded.BatteryAlert, "Battery optimisation", if (battery) "Unrestricted" else "Optimised — the VPN may stop in deep sleep", {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Color(0xFF29B6FF), if (battery) c.green else c.amber)
                RowDivider()
                SettingsRow(Icons.Rounded.Settings, "App settings", "Manage permissions in Android", {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, Color(0xFF29B6FF))
            }
            Spacer(Modifier.height(Space.l))
            SecureBanner(if (status.isConnected) "VPN Active Protection" else "VPN Not Active", if (status.isConnected) "Your connection is routed through Tervyn VPN." else "Connect to route your traffic through Tervyn.", ok = status.isConnected)
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                GlowOutlineButton("Back", onBack, Modifier.weight(0.8f), Icons.AutoMirrored.Rounded.ArrowBack)
                NeonButton("Continue", { if (!vpnGranted) connect() else { refresh++; onBack() } }, Modifier.weight(1.2f).height(60.dp), icon = Icons.Rounded.Check)
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, sub: String, modifier: Modifier) {
    val c = Tervyn.colors
    GlassCard(modifier, padding = PaddingValues(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF29B6FF), modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(Space.s))
            Column {
                Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = c.text2, fontSize = 14.sp)
            }
        }
    }
}

