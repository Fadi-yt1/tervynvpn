package com.tervyn.vpn.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.database.HistoryEntity
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.CountryFlag
import com.tervyn.vpn.ui.components.EmptyState
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.Pill
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SectionHeader
import com.tervyn.vpn.ui.components.SpeedGauge
import com.tervyn.vpn.ui.components.StatTile
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SpeedTestScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val st by vm.speed.collectAsState()
    val status by vm.status.collectAsState()
    val past by vm.speedTests.collectAsState()
    ScreenScaffold("Speed Test", if (status.isConnected) "Through the VPN (${status.server?.name})" else "Direct connection (VPN is off)", onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SpeedGauge(st.liveMbps ?: st.downMbps, 100.0, "Download", "Mbps")
            SpeedGauge(st.upMbps, 50.0, "Upload", "Mbps")
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Latency", st.latency?.let { "$it ms" } ?: "—", Icons.Rounded.Timer, Modifier.weight(1f))
            StatTile("Jitter", st.jitter?.let { "$it ms" } ?: "—", Icons.Rounded.Insights, Modifier.weight(1f), c.purple)
            StatTile("Request loss", st.loss?.let { "$it%" } ?: "—", Icons.Rounded.Hub, Modifier.weight(1f), c.amber)
        }
        Spacer(Modifier.height(Space.m))
        if (st.running) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.cyan, strokeWidth = 2.dp); Spacer(Modifier.width(Space.s)); Text(st.stage, color = c.text2)
        }
        st.error?.let { NotificationCard("Speed test failed", it, Tone.ERROR) }
        Spacer(Modifier.height(Space.m))
        NeonButton(if (st.running) "Testing…" else "Start test", { vm.runSpeedTest() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow, enabled = !st.running)
        Spacer(Modifier.height(Space.s))
        Text("Measured live against Cloudflare's speed test servers (speed.cloudflare.com). Latency is the HTTPS request round-trip; \"request loss\" is the share of failed probe requests, not ICMP packet loss.",
            color = c.text3, style = MaterialTheme.typography.labelSmall)
        SectionHeader("Previous results", Icons.Rounded.History)
        if (past.isEmpty()) Text("No speed tests yet.", color = c.text2)
        past.take(15).forEach { r ->
            GlassCard {
                Text(r.title, color = c.text, style = MaterialTheme.typography.titleSmall)
                Text("${r.summary} · ${Format.ago(r.time)}", color = c.text2, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Space.s))
        }
    }
}

private fun dayStart(t: Long): Long = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

@Composable
fun BarChart(values: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val c = Tervyn.colors
    val max = (values.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = values.size.coerceAtLeast(1)
            val gap = size.width / n * 0.3f
            val w = size.width / n - gap
            values.forEachIndexed { i, (_, v) ->
                val h = (v / max * (size.height - 4)).toFloat().coerceAtLeast(2f)
                drawRoundRect(Brush.verticalGradient(listOf(c.cyan, c.blue, c.purple)), Offset(i * (w + gap) + gap / 2, size.height - h), Size(w, h), CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth()) { values.forEach { (l, _) -> Text(l, color = c.text3, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)) } }
    }
}

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val history by vm.history.collectAsState()
    val now = System.currentTimeMillis()
    val dayFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val today = dayStart(now)
    val week = (6 downTo 0).map { d -> today - d * 86_400_000L }
    fun traffic(e: HistoryEntity) = (e.bytesDown + e.bytesUp).toDouble()
    fun dur(e: HistoryEntity) = ((e.endedAt ?: now) - e.startedAt).coerceAtLeast(0)
    val perDay = week.map { start -> dayFmt.format(Date(start)) to history.filter { it.startedAt in start until start + 86_400_000L }.sumOf(::traffic) / (1 shl 20) }
    val todayList = history.filter { it.startedAt >= today }
    ScreenScaffold("History & Analytics", "Stored only on this phone", onBack, actions = {
        IconButton(onClick = { vm.clearHistory() }) { Icon(Icons.Rounded.DeleteSweep, "Clear history", tint = c.text2) }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Today", Format.duration(todayList.sumOf(::dur)), Icons.Rounded.Timer, Modifier.weight(1f))
            StatTile("Data today", Format.bytes(todayList.sumOf { it.bytesDown + it.bytesUp }), Icons.Rounded.ArrowDownward, Modifier.weight(1f), c.purple)
            StatTile("Sessions", todayList.size.toString(), Icons.Rounded.History, Modifier.weight(1f), c.green)
        }
        SectionHeader("Last 7 days (MB)", Icons.Rounded.Insights)
        GlassCard { BarChart(perDay) }
        SectionHeader("Sessions", Icons.Rounded.History)
        if (history.isEmpty()) EmptyState(Icons.Rounded.History, "No sessions yet", "Connections you make appear here.")
        history.take(100).forEach { e ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountryFlag(e.countryCode, 36.dp)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(e.serverName, color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("${timeFmt.format(Date(e.startedAt))} · ${Format.duration(dur(e))} · ${e.protocol}", color = c.text2, style = MaterialTheme.typography.bodySmall)
                        Text("↓ ${Format.bytes(e.bytesDown)}  ↑ ${Format.bytes(e.bytesUp)} · ${e.mode.replace('_', ' ').lowercase()}", color = c.text3, style = MaterialTheme.typography.labelSmall)
                    }
                    Pill(e.status.lowercase().replaceFirstChar { it.uppercase() }, when (e.status) { "CONNECTED" -> c.green; "FAILED" -> c.red; else -> c.text2 })
                }
            }
            Spacer(Modifier.height(Space.s))
        }
    }
}

@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val checks by vm.diagnostics.collectAsState()
    val running by vm.diagRunning.collectAsState()
    LaunchedEffect(Unit) { if (checks.isEmpty()) vm.runDiagnostics() }
    ScreenScaffold("Network Diagnostics", "Real checks of your connection", onBack) {
        checks.forEach { ch ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (ch.ok) { true -> Icons.Rounded.CheckCircle; false -> Icons.Rounded.Cancel; null -> Icons.Rounded.Help },
                        null, tint = when (ch.ok) { true -> c.green; false -> c.red; null -> c.amber })
                    Spacer(Modifier.width(Space.m))
                    Column {
                        Text(ch.name, color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text(ch.detail, color = c.text2, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        if (running) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.cyan, strokeWidth = 2.dp); Spacer(Modifier.width(Space.s)); Text("Running checks…", color = c.text2)
        }
        Spacer(Modifier.height(Space.m))
        NeonButton("Run again", { vm.runDiagnostics() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.BugReport, enabled = !running)
    }
}

@Composable
fun StatsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    val downHist = remember { mutableStateListOf<Float>() }
    val upHist = remember { mutableStateListOf<Float>() }
    LaunchedEffect(status.totalDown, status.totalUp, status.downBps) {
        downHist.add(status.downBps.toFloat()); upHist.add(status.upBps.toFloat())
        while (downHist.size > 60) downHist.removeAt(0)
        while (upHist.size > 60) upHist.removeAt(0)
    }
    val now = rememberNow(status.isConnected)
    ScreenScaffold("Connection Statistics", if (status.isConnected) "Live" else "Not connected", onBack) {
        GlassCard {
            Text("Throughput (last 60 s)", color = c.text, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Space.s))
            val maxV = ((downHist + upHist).maxOrNull() ?: 1f).coerceAtLeast(1f)
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                fun line(data: List<Float>, color: androidx.compose.ui.graphics.Color) {
                    if (data.size < 2) return
                    val p = Path()
                    data.forEachIndexed { i, v ->
                        val x = i * size.width / 59f
                        val y = size.height - v / maxV * size.height
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, color, style = Stroke(3f))
                }
                line(downHist.toList(), c.cyan); line(upHist.toList(), c.purple)
            }
            Row { Text("■ Download  ", color = c.cyan, style = MaterialTheme.typography.labelSmall); Text("■ Upload", color = c.purple, style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Download", Format.speed(status.downBps), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
            StatTile("Upload", Format.speed(status.upBps), Icons.Rounded.ArrowUpward, Modifier.weight(1f), c.purple)
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Received", Format.bytes(status.totalDown), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
            StatTile("Sent", Format.bytes(status.totalUp), Icons.Rounded.ArrowUpward, Modifier.weight(1f), c.purple)
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Open connections", status.activeConnections.toString(), Icons.Rounded.Hub, Modifier.weight(1f), c.green)
            StatTile("DNS queries", status.dnsQueries.toString(), Icons.Rounded.Dns, Modifier.weight(1f), c.amber)
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("UDP refused", status.udpBlocked.toString(), Icons.Rounded.Cancel, Modifier.weight(1f), c.red)
            StatTile("Ping", status.pingMs?.let { "$it ms" } ?: "—", Icons.Rounded.Speed, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Duration", if (status.isConnected) Format.duration(now - status.connectedSince) else "—", Icons.Rounded.Timer, Modifier.weight(1f), c.green)
            StatTile("Last health check", Format.ago(status.lastHealthCheck), Icons.Rounded.CheckCircle, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.m))
        Text("All values are measured by the VPN engine on this device.", color = c.text3, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}
