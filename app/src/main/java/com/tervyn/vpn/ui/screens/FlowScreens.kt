package com.tervyn.vpn.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tervyn.vpn.core.Links
import com.tervyn.vpn.database.HistoryEntity
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.AnimatedGlobe
import com.tervyn.vpn.ui.components.ArcGauge
import com.tervyn.vpn.ui.components.BrandBar
import com.tervyn.vpn.ui.components.CountryFlag
import com.tervyn.vpn.ui.components.EmptyState
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.GlowShield
import com.tervyn.vpn.ui.components.InfoRow
import com.tervyn.vpn.ui.components.LineChart
import com.tervyn.vpn.ui.components.LoadingIndicator
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.PingIndicator
import com.tervyn.vpn.ui.components.Pill
import com.tervyn.vpn.ui.components.ProgressRing
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SectionHeader
import com.tervyn.vpn.ui.components.StatTile
import com.tervyn.vpn.ui.components.StatusBanner
import com.tervyn.vpn.ui.components.TervynLogo
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.components.twoTone
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import com.tervyn.vpn.vpn.Phase
import com.tervyn.vpn.vpn.VpnStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Real progress derived from the connection phase and the number of servers tested so far. */
fun connectProgress(s: VpnStatus): Float = when (s.phase) {
    Phase.FETCHING -> 0.08f + 0.07f * (if (s.testTotal > 0) s.tested.toFloat() / s.testTotal else 0f)
    Phase.TESTING -> 0.15f + 0.55f * (if (s.testTotal > 0) s.tested.toFloat() / s.testTotal else 0f).coerceAtMost(1f)
    Phase.CONNECTING, Phase.SWITCHING, Phase.RECONNECTING -> 0.8f
    Phase.CONNECTED -> 1f
    else -> 0f
}

@Composable
private fun StepRow(state: Int, title: String, value: String? = null, valueColor: Color = Tervyn.colors.green) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            2 -> Box(Modifier.size(28.dp).clip(CircleShape).background(c.green.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = c.green, modifier = Modifier.size(18.dp))
            }
            1 -> CircularProgressIndicator(Modifier.size(26.dp), color = c.cyan, strokeWidth = 2.5.dp, trackColor = c.bg2)
            -1 -> Icon(Icons.Rounded.Cancel, null, tint = c.red, modifier = Modifier.size(28.dp))
            else -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = c.text3, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(Space.m))
        Text(title, color = if (state == 1) c.cyan else if (state == 0) c.text3 else c.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = valueColor, style = MaterialTheme.typography.titleSmall)
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
    val pct = (connectProgress(status) * 100).toInt()
    Column(Modifier.fillMaxSize().background(c.backgroundGradient).statusBarsPadding().verticalScroll(rememberScrollState())) {
        BrandBar(onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (failed) "Connection failed" else "Connecting…", style = MaterialTheme.typography.displaySmall, color = if (failed) c.red else c.text)
            Text(if (failed) (status.error ?: "") else "Setting up your secure VPN connection", style = MaterialTheme.typography.bodyLarge, color = c.text2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.l))
            Box(contentAlignment = Alignment.Center) {
                AnimatedGlobe(Modifier.size(300.dp), active = true)
                ProgressRing(connectProgress(status), 250.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlowShield(null, 130.dp) { TervynLogo(64.dp) }
                        Text("$pct%", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.text)
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            GlassCard(highlight = true) {
                StepRow(st(0), "Fetching servers…", if (status.phase == Phase.FETCHING && status.testTotal > 0) "${status.tested}/${status.testTotal} lists" else null, c.cyan)
                StepRow(st(1), "Testing servers…", if (status.testTotal > 0 && idx >= 1) "${status.working} working" else null)
                StepRow(st(2), "Establishing secure connection…", status.server?.latencyMs?.takeIf { idx >= 2 }?.let { "$it ms" })
                StepRow(st(3), "Verifying exit IP…")
            }
            val srv = status.server
            if (srv != null) {
                Spacer(Modifier.height(Space.m))
                GlassCard(highlight = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountryFlag(srv.countryCode, 56.dp)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(srv.countryCode?.let { Format.countryName(it) } ?: srv.name, style = MaterialTheme.typography.titleLarge, color = c.text)
                            Text(if (srv.countryCode != null) "Fastest server · ${srv.protocolLabel}" else srv.protocolLabel, color = c.text2)
                        }
                        PingIndicator(srv.latencyMs)
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(20.dp)).padding(Space.m),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = c.cyan)
                Spacer(Modifier.width(Space.s))
                Text(twoTone("Only verified HTTPS servers are used by Tervyn VPN", "Tervyn VPN", c.text2, c.cyan), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Space.l))
            NeonButton(if (failed) "Back" else "Cancel", { if (status.isActive) vm.disconnect() else onBack() }, Modifier.fillMaxWidth(), secondary = true)
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
fun ConnectedScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    val now = rememberNow(status.isConnected)
    LaunchedEffect(status.phase) { if (!status.isActive) onBack() }
    Column(Modifier.fillMaxSize().background(c.backgroundGradient).statusBarsPadding().verticalScroll(rememberScrollState())) {
        BrandBar(onBack, onSettings = { go(Routes.SETTINGS) })
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedGlobe(Modifier.size(280.dp), active = true)
                GlowShield(Icons.Rounded.Check, 220.dp, c.green)
            }
            Text(twoTone("VPN ${if (status.isConnected) "Connected" else status.phase.label}", if (status.isConnected) "Connected" else null, c.text, c.green),
                style = MaterialTheme.typography.displaySmall)
            Text(if (status.isConnected) "Your traffic now leaves through ${status.exitIp ?: "the VPN server"}" else status.message ?: "",
                color = c.text2, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.l))
            GlassCard(highlight = true, onClick = { go(Routes.SERVERS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountryFlag(status.countryCode, 56.dp)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(Format.countryName(status.countryCode), style = MaterialTheme.typography.titleLarge, color = c.text)
                        Text(status.server?.name ?: "", color = c.text2, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    PingIndicator(status.pingMs)
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.text2)
                }
            }
            Spacer(Modifier.height(Space.m))
            Row(Modifier.clip(RoundedCornerShape(24.dp)).background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(24.dp)).padding(horizontal = Space.xl, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = c.green, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(Space.m))
                Column {
                    Text("Connection Time", color = c.text2, style = MaterialTheme.typography.labelMedium)
                    Text(Format.duration(now - status.connectedSince), color = c.text, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(Modifier.height(Space.m))
            GlassCard(highlight = true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigStat(Icons.Rounded.ArrowDownward, "Download", Format.speed(status.downBps), c.cyan)
                    BigStat(Icons.Rounded.ArrowUpward, "Upload", Format.speed(status.upBps), c.purple)
                    BigStat(Icons.Rounded.Speed, "Ping", status.pingMs?.let { "$it ms" } ?: "—", c.green)
                }
            }
            Spacer(Modifier.height(Space.m))
            StatusBanner("Your Connection is Protected", "Apps' traffic is routed through ${status.server?.protocolLabel ?: "the VPN"} · ${status.mode.label}", Icons.Rounded.VerifiedUser, c.green)
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NeonButton("Disconnect", { vm.disconnect() }, Modifier.weight(1f), icon = Icons.Rounded.PowerSettingsNew, secondary = true)
                NeonButton("Check My IP", { go(Routes.IP) }, Modifier.weight(1f), icon = Icons.Rounded.BarChart)
            }
            Spacer(Modifier.height(Space.s))
            NeonButton("Connection Statistics", { go(Routes.STATS) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Insights, secondary = true)
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun BigStat(icon: ImageVector, label: String, value: String, tint: Color) {
    val c = Tervyn.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        Text(label, color = c.text2, style = MaterialTheme.typography.labelMedium)
        Text(value, color = c.text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun CheckIpScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val ip by vm.ip.collectAsState()
    val loading by vm.ipLoading.collectAsState()
    val status by vm.status.collectAsState()
    LaunchedEffect(status.isConnected) { vm.checkIp() }
    ScreenScaffold("Check My IP", "View your current IP address and location", onBack) {
        val (info, err) = ip
        when {
            loading && info == null -> LoadingIndicator("Checking your public IP…")
            err != null -> NotificationCard("Could not check IP", err, Tone.ERROR)
            info != null -> {
                GlassCard(highlight = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlowShield(if (info.viaVpn) Icons.Rounded.Check else Icons.Rounded.PriorityHigh, 110.dp, if (info.viaVpn) c.cyan else c.amber)
                        Spacer(Modifier.width(Space.m))
                        Column {
                            Text("Your IP Address", color = c.text2, style = MaterialTheme.typography.titleSmall)
                            Text(info.ip, color = c.text, fontSize = if (info.ip.length > 15) 20.sp else 30.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Pill(if (info.viaVpn) "✓ VPN Connected" else "VPN off — real IP", if (info.viaVpn) c.green else c.amber)
                        }
                    }
                }
                Spacer(Modifier.height(Space.m))
                GlassCard {
                    InfoRow(Icons.Rounded.Language, "Country", Format.countryName(info.countryCode)) { Text(Format.flag(info.countryCode), fontSize = 22.sp) }
                    InfoRow(Icons.Rounded.Place, "Location", listOfNotNull(info.city, info.region).joinToString(", ").ifEmpty { "Not available" })
                    InfoRow(Icons.Rounded.Router, "ISP", info.isp ?: "Not available")
                    InfoRow(Icons.Rounded.Lock, "Connection", if (info.viaVpn) "VPN · ${status.server?.protocolLabel ?: ""}" else "Direct", if (info.viaVpn) c.green else c.amber)
                }
                Spacer(Modifier.height(Space.m))
                GlassCard(padding = PaddingValues(0.dp)) {
                    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                        AnimatedGlobe(Modifier.size(200.dp), active = info.viaVpn)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Place, null, tint = c.cyan, modifier = Modifier.size(40.dp))
                            Text(Format.flag(info.countryCode), fontSize = 26.sp)
                        }
                    }
                }
                Spacer(Modifier.height(Space.m))
                if (info.viaVpn) StatusBanner("Your Connection is Protected", "Websites see the VPN server's address, not yours.", Icons.Rounded.VerifiedUser, c.green)
                else StatusBanner("Not Protected", "This is your real address. Connect to hide it from websites.", Icons.Rounded.GppMaybe, c.amber)
                Spacer(Modifier.height(Space.s))
                Text("IP and country: Cloudflare trace. City/ISP: lookup service from DNS settings (approximate).", color = c.text3, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeonButton(if (loading) "Checking…" else "Refresh", { vm.checkIp() }, Modifier.weight(1f), icon = Icons.Rounded.Refresh, secondary = true, enabled = !loading)
            NeonButton("Share IP", {
                ip.first?.let { i ->
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "${i.ip} (${Format.countryName(i.countryCode)})"), "Share IP").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, Modifier.weight(1f), icon = Icons.Rounded.Share, secondary = true, enabled = ip.first != null)
        }
    }
}

@Composable
fun ServerDetailsScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    val c = Tervyn.colors
    val servers by vm.servers.collectAsState()
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()
    val srv = servers.firstOrNull { it.id == id }
    var testing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.backgroundGradient).statusBarsPadding().verticalScroll(rememberScrollState())) {
        BrandBar(onBack)
        if (srv == null) { EmptyState(Icons.Rounded.Search, "Server not found", "It may have been removed."); return@Column }
        val active = status.server?.id == srv.id && status.isConnected
        val o = srv.xrayLink?.let { runCatching { Links.parse(it) }.getOrNull() }
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd) {
                GlowShield(Icons.Rounded.Dns, 130.dp)
                CountryFlag(srv.countryCode, 52.dp)
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(if (srv.countryCode != null) Format.countryName(srv.countryCode) else "Unverified", style = MaterialTheme.typography.headlineSmall, color = c.text, fontWeight = FontWeight.Bold)
                Text(srv.name, color = c.text2, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Pill(if (active) "● Connected" else if (srv.alive) "● Online" else if (srv.lastChecked > 0) "● Offline" else "Not tested", if (active || srv.alive) c.green else c.text2)
            }
        }
        Column(Modifier.padding(Space.gutter)) {
            GlassCard(highlight = true) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Info, null, tint = c.cyan); Spacer(Modifier.width(Space.s)); Text("Server Information", color = c.text, style = MaterialTheme.typography.titleMedium) }
                InfoRow(Icons.Rounded.Language, "Country", if (srv.countryCode != null) Format.countryName(srv.countryCode) else "Not verified")
                InfoRow(Icons.Rounded.Storage, "Server Name", srv.name)
                InfoRow(Icons.Rounded.Hub, "Address", srv.endpoint?.let { "${it.host}:${it.port}" } ?: o?.let { "${it.address}:${it.port}" } ?: "—")
                InfoRow(Icons.Rounded.Public, "Exit IP", srv.exitIp ?: "—")
                InfoRow(Icons.Rounded.Security, "Protocol", srv.protocolLabel)
                InfoRow(Icons.Rounded.Timer, "Ping", if (srv.alive && srv.latencyMs != null) "${srv.latencyMs} ms" else "—", c.green)
                InfoRow(Icons.Rounded.History, "Last verified", Format.ago(srv.lastChecked))
                if (srv.endpoint?.hasAuth == true || o?.username?.isNotEmpty() == true) InfoRow(Icons.Rounded.Key, "Login", "Encrypted on device")
                o?.let {
                    InfoRow(Icons.Rounded.SwapHoriz, "Transport", it.network.uppercase())
                    InfoRow(Icons.Rounded.Lock, "Security", it.security.uppercase() + if (it.pinnedPeerCertSha256.isNotEmpty()) " · pinned" else "")
                }
            }
            Spacer(Modifier.height(Space.m))
            if (active) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                StatTile("Download", Format.speed(status.downBps), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
                StatTile("Upload", Format.speed(status.upBps), Icons.Rounded.ArrowUpward, Modifier.weight(1f), c.purple)
            }
            if (srv.origin == ServerOrigin.FREE_PUBLIC) { Spacer(Modifier.height(Space.s)); NotificationCard("Free public server", "Operated by an unknown third party. Avoid sensitive logins through it.", Tone.WARNING) }
            if (o?.insecureRequested == true) { Spacer(Modifier.height(Space.s)); NotificationCard("Insecure TLS requested", "Ignored — certificates are always verified. Use pcs (pinning) for self-signed servers.", Tone.WARNING) }
            Spacer(Modifier.height(Space.m))
            NeonButton(if (testing) "Testing…" else "Test Server", { testing = true; vm.testServer(srv) { testing = false } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Bolt, enabled = !testing)
            Spacer(Modifier.height(Space.s))
            NeonButton(if (!settings!!.autoSwitch && settings!!.selectedServerId == srv.id) "Selected server" else "Use This Server", { vm.selectServer(srv.id); onBack() },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.Power, secondary = true)
            Text("Selecting a specific server turns auto-switch off.", color = c.text3, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NeonButton(if (srv.favorite) "Favorite ★" else "Add to Favorites", { vm.toggleFavorite(srv) }, Modifier.weight(1f), icon = Icons.Rounded.Star, secondary = true)
                NeonButton("Delete", { vm.deleteServer(srv); onBack() }, Modifier.weight(1f), icon = Icons.Rounded.Delete, danger = true)
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
fun SpeedTestScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val st by vm.speed.collectAsState()
    val status by vm.status.collectAsState()
    val servers by vm.servers.collectAsState()
    val past by vm.speedTests.collectAsState()
    ScreenScaffold("Server Speed Test", if (status.isConnected) "Measured through the VPN" else "Measured on your direct connection (VPN off)", onBack, heroIcon = Icons.Rounded.Speed, accentWord = "Speed") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ArcGauge(st.liveMbps ?: st.downMbps, 100.0, "Mbps", if (st.running && st.stage.startsWith("Upload")) "Uploading…" else "Download Speed")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            GlassCard(Modifier.weight(1f), highlight = st.upMbps != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowUpward, null, tint = c.purple, modifier = Modifier.size(34.dp))
                    Column { Text(st.upMbps?.let { "%.1f".format(it) } ?: "—", style = MaterialTheme.typography.headlineSmall, color = c.text); Text("Upload Mbps", color = c.text2, style = MaterialTheme.typography.labelMedium) }
                }
            }
            GlassCard(Modifier.weight(1f), highlight = st.latency != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = c.green, modifier = Modifier.size(34.dp))
                    Column { Text(st.latency?.let { "$it ms" } ?: "—", style = MaterialTheme.typography.headlineSmall, color = c.text); Text("Ping / Latency", color = c.text2, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountryFlag(if (status.isConnected) status.countryCode else null, 48.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Test route", color = c.text2, style = MaterialTheme.typography.labelMedium)
                    Text(if (status.isConnected) Format.countryName(status.countryCode) else "Direct connection", color = c.text, style = MaterialTheme.typography.titleMedium)
                    Text(if (status.isConnected) status.server?.name ?: "" else "Connect to measure the VPN", color = c.text2, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Jitter / loss", color = c.text2, style = MaterialTheme.typography.labelMedium)
                    Text("${st.jitter?.let { "$it ms" } ?: "—"} · ${st.loss?.let { "$it%" } ?: "—"}", color = c.green, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (st.running) Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = c.cyan, strokeWidth = 2.dp); Spacer(Modifier.width(Space.s)); Text(st.stage, color = c.text2)
        }
        st.error?.let { Spacer(Modifier.height(Space.s)); NotificationCard("Speed test failed", it, Tone.ERROR) }
        Spacer(Modifier.height(Space.m))
        NeonButton(if (st.running) "Testing…" else if (st.downMbps != null) "Test Again" else "Start Test", { vm.runSpeedTest() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Bolt, enabled = !st.running)
        val top = vm.ranked(servers.filter { it.alive && it.countryCode != null }).distinctBy { it.countryCode }.take(4)
        if (top.isNotEmpty()) {
            SectionHeader("Top Servers (verified latency)", Icons.Rounded.EmojiEvents) { androidx.compose.material3.TextButton({ go(Routes.SERVERS) }) { Text("See All", color = c.cyan) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                top.forEach { s ->
                    GlassCard(Modifier.weight(1f), onClick = { go(Routes.server(s.id)) }, padding = PaddingValues(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Format.flag(s.countryCode), fontSize = 20.sp); Spacer(Modifier.width(4.dp))
                            Column { Text(s.countryCode ?: "", color = c.text, style = MaterialTheme.typography.labelLarge); Text("${s.latencyMs} ms", color = c.green, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
        if (past.isNotEmpty()) {
            SectionHeader("Previous results", Icons.Rounded.History)
            past.take(8).forEach { r -> Text("• ${r.summary} — ${r.title}, ${Format.ago(r.time)}", color = c.text2, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Spacer(Modifier.height(Space.s))
        Text("Measured live against speed.cloudflare.com. Latency is the HTTPS round-trip; loss is the share of failed probe requests.", color = c.text3, style = MaterialTheme.typography.labelSmall)
    }
}

private fun dayStartOf(t: Long): Long = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

@Composable
private fun OverviewCard(icon: ImageVector, title: String, value: String, delta: Int?, tint: Color, modifier: Modifier) {
    val c = Tervyn.colors
    GlassCard(modifier, padding = PaddingValues(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.width(Space.s))
            Column {
                Text(title, color = c.text2, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Text(value, color = c.text, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                if (delta != null) Text("${if (delta >= 0) "▲" else "▼"} ${kotlin.math.abs(delta)}% vs last week", color = if (delta >= 0) c.green else c.amber, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val history by vm.history.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val dayFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault()) }
    val today = dayStartOf(now)
    val weekStart = today - 6 * 86_400_000L
    fun dur(e: HistoryEntity) = ((e.endedAt ?: now) - e.startedAt).coerceAtLeast(0)
    fun bytes(e: HistoryEntity) = e.bytesDown + e.bytesUp
    val thisWeek = history.filter { it.startedAt >= weekStart }
    val lastWeek = history.filter { it.startedAt in (weekStart - 7 * 86_400_000L) until weekStart }
    fun delta(a: Double, b: Double): Int? = if (b <= 0.0) null else ((a - b) / b * 100).toInt()
    ScreenScaffold("Connection History & Analytics", "Stored only on this phone", onBack, heroIcon = Icons.Rounded.BarChart, accentWord = "Analytics") {
        com.tervyn.vpn.ui.components.SegmentedTabs(listOf("Overview" to Icons.Rounded.BarChart, "History" to Icons.Rounded.History, "Data" to Icons.Rounded.Storage), tab, { tab = it })
        Spacer(Modifier.height(Space.m))
        when (tab) {
            0, 2 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    OverviewCard(Icons.Rounded.VerifiedUser, "Connection time (7 d)", Format.duration(thisWeek.sumOf(::dur)).substringBeforeLast(':'), delta(thisWeek.sumOf(::dur).toDouble(), lastWeek.sumOf(::dur).toDouble()), c.green, Modifier.weight(1f))
                    OverviewCard(Icons.Rounded.Storage, "Data (7 d)", Format.bytes(thisWeek.sumOf(::bytes)), delta(thisWeek.sumOf(::bytes).toDouble(), lastWeek.sumOf(::bytes).toDouble()), c.cyan, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    OverviewCard(Icons.Rounded.Bolt, "Sessions (7 d)", thisWeek.size.toString(), delta(thisWeek.size.toDouble(), lastWeek.size.toDouble()), c.purple, Modifier.weight(1f))
                    OverviewCard(Icons.Rounded.Language, "Countries (7 d)", thisWeek.mapNotNull { it.countryCode }.toSet().size.toString(), null, c.blue, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.m))
                GlassCard(highlight = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ShowChart, null, tint = c.cyan); Spacer(Modifier.width(Space.s))
                        Text(if (tab == 0) "Usage overview · hours per day" else "Data per day · MB", color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Pill("Last 7 days", c.cyan)
                    }
                    Spacer(Modifier.height(Space.s))
                    val days = (6 downTo 0).map { today - it * 86_400_000L }
                    LineChart(days.map { d ->
                        val list = history.filter { it.startedAt in d until d + 86_400_000L }
                        dayFmt.format(Date(d)) to if (tab == 0) list.sumOf(::dur) / 3_600_000.0 else list.sumOf(::bytes) / 1_048_576.0
                    }, if (tab == 0) "h" else "MB")
                }
            }
        }
        SectionHeader("Recent Connection History", Icons.Rounded.History)
        if (history.isEmpty()) EmptyState(Icons.Rounded.History, "No sessions yet", "Connections you make appear here.")
        GlassCard {
            history.take(if (tab == 1) 200 else 6).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CountryFlag(e.countryCode, 40.dp)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(Format.countryName(e.countryCode), color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text(e.serverName, color = c.text2, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text("${Format.duration(dur(e))} · ${Format.bytes(bytes(e))}", color = c.text3, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Pill(when (e.status) { "CONNECTED" -> if (e.endedAt == null) "● Active" else "Connected"; "FAILED" -> "Failed"; "SWITCHED" -> "Switched"; else -> "Ended" },
                            when (e.status) { "FAILED" -> c.red; "CONNECTED" -> c.green; else -> c.text2 })
                        Text(timeFmt.format(Date(e.startedAt)), color = c.text3, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        NeonButton("Clear history", { vm.clearHistory() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteSweep, secondary = true)
    }
}

@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val checks by vm.diagnostics.collectAsState()
    val running by vm.diagRunning.collectAsState()
    val single by vm.checkResults.collectAsState()
    val singleRunning by vm.checkRunning.collectAsState()
    val status by vm.status.collectAsState()
    val speed by vm.speed.collectAsState()
    val online = checks.firstOrNull()?.ok ?: single["internet"]?.ok
    ScreenScaffold("Network Diagnostics", "Test your connection and find issues quickly", onBack, heroIcon = Icons.Rounded.MonitorHeart, accentWord = "Diagnostics") {
        GlassCard(highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (online == false) Icons.Rounded.Cancel else Icons.Rounded.CheckCircle, null, tint = if (online == false) c.red else c.green, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Connection Status", color = c.text2, style = MaterialTheme.typography.labelMedium)
                    Text(when (online) { true -> "Online"; false -> "Offline"; null -> "Not tested" }, color = if (online == false) c.red else c.green, style = MaterialTheme.typography.headlineSmall)
                    Text("VPN: ${status.phase.label}", color = c.text2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Build, null, tint = c.cyan); Spacer(Modifier.width(Space.s)); Text("Diagnostic Tools", color = c.text, style = MaterialTheme.typography.titleMedium) }
            listOf(
                Triple("internet", Icons.Rounded.Language, "Internet Connectivity"), Triple("dns", Icons.Rounded.Dns, "DNS Lookup"),
                Triple("proxy", Icons.Rounded.VpnLock, "Proxy Health Check"), Triple("loss", Icons.Rounded.SwapHoriz, "Request Loss"),
                Triple("latency", Icons.Rounded.Timer, "Latency (HTTPS)"),
            ).forEach { (id, icon, title) ->
                val r = single[id]
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = c.cyan, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text(r?.detail ?: "Not run yet", color = when (r?.ok) { true -> c.green; false -> c.red; null -> c.text2 }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (id in singleRunning) CircularProgressIndicator(Modifier.size(24.dp), color = c.cyan, strokeWidth = 2.dp)
                    else androidx.compose.material3.OutlinedButton({ vm.runCheck(id) }, border = androidx.compose.foundation.BorderStroke(1.dp, c.blue)) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = c.text, modifier = Modifier.size(18.dp)); Text("Test", color = c.text)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.BarChart, null, tint = c.cyan); Spacer(Modifier.width(Space.s)); Text("Recent Results", color = c.text, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigStat(Icons.Rounded.Public, "Ping", speed.latency?.let { "$it ms" } ?: "—", c.green)
                BigStat(Icons.Rounded.ArrowDownward, "Download", speed.downMbps?.let { "%.1f".format(it) } ?: "—", c.purple)
                BigStat(Icons.Rounded.ArrowUpward, "Upload", speed.upMbps?.let { "%.1f".format(it) } ?: "—", c.cyan)
                BigStat(Icons.Rounded.SignalCellularAlt, "Loss", speed.loss?.let { "$it%" } ?: "—", c.amber)
            }
            Text("From the last speed test (Mbps).", color = c.text3, style = MaterialTheme.typography.labelSmall)
        }
        if (checks.isNotEmpty()) {
            SectionHeader("Full network test")
            checks.forEach { ch ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Icon(when (ch.ok) { true -> Icons.Rounded.CheckCircle; false -> Icons.Rounded.Cancel; null -> Icons.Rounded.Info }, null,
                        tint = when (ch.ok) { true -> c.green; false -> c.red; null -> c.amber }, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.s))
                    Column { Text(ch.name, color = c.text, style = MaterialTheme.typography.titleSmall); Text(ch.detail, color = c.text2, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        NeonButton(if (running) "Running…" else "Run Full Network Test", { vm.runDiagnostics() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh, enabled = !running)
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeonButton("Troubleshooting", { go(Routes.HELP) }, Modifier.weight(1f), icon = Icons.Rounded.Lightbulb, secondary = true)
            NeonButton("Clear Cache", { vm.clearCache() }, Modifier.weight(1f), icon = Icons.Rounded.CleaningServices, secondary = true)
        }
    }
}
