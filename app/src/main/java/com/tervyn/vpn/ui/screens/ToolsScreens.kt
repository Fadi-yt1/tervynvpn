package com.tervyn.vpn.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.database.HistoryEntity
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.Banner
import com.tervyn.vpn.ui.components.Card
import com.tervyn.vpn.ui.components.EmptyState
import com.tervyn.vpn.ui.components.FlagTile
import com.tervyn.vpn.ui.components.KeyValueRow
import com.tervyn.vpn.ui.components.Pill
import com.tervyn.vpn.ui.components.PrimaryButton
import com.tervyn.vpn.ui.components.Screen
import com.tervyn.vpn.ui.components.SectionTitle
import com.tervyn.vpn.ui.components.StatTile
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// ---- check my ip ---------------------------------------------------------------------------

@Composable
fun CheckIpScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val (info, error) = vm.ip.collectAsState().value
    val loading by vm.ipLoading.collectAsState()
    val status by vm.status.collectAsState()
    LaunchedEffect(status.isConnected) { vm.checkIp() }
    Screen(title = "Check my IP", onBack = onBack) {
        Card(highlighted = info?.viaVpn == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlagTile(info?.countryCode, 56.dp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    Text(if (loading && info == null) "Checking…" else info?.ip ?: "—", style = MaterialTheme.typography.headlineSmall, color = c.text)
                    Text(info?.let { listOfNotNull(it.city, Format.countryName(it.countryCode).takeIf { _ -> it.countryCode != null }).joinToString(", ").ifEmpty { "Location unknown" } } ?: " ",
                        style = MaterialTheme.typography.bodyMedium, color = c.text2)
                }
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(Space.l))
            KeyValueRow("Status", if (info?.viaVpn == true) "Protected — via ${status.server?.name ?: "server"}" else "Not protected — direct connection", if (info?.viaVpn == true) c.success else c.warning)
            KeyValueRow("Network", info?.isp ?: "—")
            KeyValueRow("Region", info?.region ?: "—")
        }
        if (error != null) { Spacer(Modifier.height(Space.m)); Banner("Check failed", error, Tone.ERROR) }
        Spacer(Modifier.height(Space.l))
        PrimaryButton("Check again", { vm.checkIp() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Public, enabled = !loading, loading = loading)
        Spacer(Modifier.height(Space.m))
        Text(
            if (status.isConnected) "This is the address websites see while you are connected. It is fetched through the server with certificate validation."
            else "You are not connected: this is your real public address. Connect a server and check again to compare.",
            style = MaterialTheme.typography.bodySmall, color = c.text3,
        )
    }
}

// ---- speed test ----------------------------------------------------------------------------

@Composable
fun SpeedTestScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val sp by vm.speed.collectAsState()
    val status by vm.status.collectAsState()
    val tests by vm.speedTests.collectAsState()
    Screen(title = "Speed test", onBack = onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Gauge(sp.liveMbps ?: sp.downMbps, 200.0, if (sp.running) sp.stage else if (sp.downMbps != null) "Download" else "Ready", "Mbps")
        }
        Spacer(Modifier.height(Space.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Download", sp.downMbps?.let { "%.1f Mbps".format(Locale.US, it) } ?: "—", Modifier.weight(1f))
            StatTile("Upload", sp.upMbps?.let { "%.1f Mbps".format(Locale.US, it) } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Latency", sp.latency?.let { "$it ms" } ?: "—", Modifier.weight(1f))
            StatTile("Jitter", sp.jitter?.let { "$it ms" } ?: "—", Modifier.weight(1f))
            StatTile("Loss", sp.loss?.let { "$it%" } ?: "—", Modifier.weight(1f))
        }
        if (sp.error != null) { Spacer(Modifier.height(Space.m)); Banner("Test failed", sp.error, Tone.ERROR) }
        Spacer(Modifier.height(Space.l))
        PrimaryButton(if (sp.running) sp.stage else "Start test", { vm.runSpeedTest() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Speed, enabled = !sp.running, loading = sp.running)
        Spacer(Modifier.height(Space.s))
        Text(
            if (status.isConnected) "Measured through ${status.server?.name ?: "the server"} against speed.cloudflare.com." else "Measured on your direct connection against speed.cloudflare.com. Connect a server to measure the tunnel.",
            style = MaterialTheme.typography.bodySmall, color = c.text3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (tests.isNotEmpty()) {
            SectionTitle("Recent tests")
            tests.take(8).forEach { t ->
                Card(Modifier.padding(bottom = Space.s), padding = androidx.compose.foundation.layout.PaddingValues(Space.m)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text("${t.summary} · ${timeOf(t.time)}", style = MaterialTheme.typography.bodySmall, color = c.text2)
                }
            }
        }
    }
}

@Composable
private fun Gauge(value: Double?, max: Double, label: String, unit: String, size: Dp = 220.dp) {
    val c = Tervyn.colors
    val fraction by animateFloatAsState(((value ?: 0.0) / max).toFloat().coerceIn(0f, 1f), tween(500), label = "gauge")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.07f
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(c.cardHigh, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0f) drawArc(Brush.sweepGradient(listOf(c.accent, c.accent2, c.accent)), 135f, 270f * fraction, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.let { String.format(Locale.US, if (it >= 100) "%.0f" else "%.1f", it) } ?: "—", style = MaterialTheme.typography.displayMedium, color = c.text)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = c.text2)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.accent2)
        }
    }
}

// ---- diagnostics ---------------------------------------------------------------------------

@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val checks by vm.diagnostics.collectAsState()
    val running by vm.diagRunning.collectAsState()
    LaunchedEffect(Unit) { if (checks.isEmpty()) vm.runDiagnostics() }
    Screen(title = "Diagnostics", onBack = onBack) {
        val passed = checks.count { it.ok == true }; val failed = checks.count { it.ok == false }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Passed", passed.toString(), Modifier.weight(1f), c.success)
            StatTile("Failed", failed.toString(), Modifier.weight(1f), if (failed > 0) c.danger else c.text)
            StatTile("Info", checks.count { it.ok == null }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.m))
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            if (checks.isEmpty()) com.tervyn.vpn.ui.components.Loading("Running checks…")
            checks.forEachIndexed { i, ch ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.Top) {
                    Icon(
                        when (ch.ok) { true -> Icons.Rounded.CheckCircle; false -> Icons.Rounded.ErrorOutline; null -> Icons.Rounded.RemoveCircleOutline },
                        null, tint = when (ch.ok) { true -> c.success; false -> c.danger; null -> c.text3 }, modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(ch.name, style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(ch.detail, style = MaterialTheme.typography.bodySmall, color = c.text2)
                    }
                }
                if (i < checks.lastIndex) com.tervyn.vpn.ui.components.Divider()
            }
        }
        Spacer(Modifier.height(Space.l))
        PrimaryButton(if (running) "Running…" else "Run again", { vm.runDiagnostics() }, Modifier.fillMaxWidth(), enabled = !running, loading = running)
    }
}

// ---- history -------------------------------------------------------------------------------

private fun timeOf(t: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(t))

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val history by vm.history.collectAsState()
    Screen(title = "Connection history", onBack = onBack, actions = {
        if (history.isNotEmpty()) IconButton(onClick = { vm.clearHistory() }, modifier = Modifier.semantics { contentDescription = "Clear history" }) { Icon(Icons.Rounded.Delete, null, tint = c.text2) }
    }) {
        if (history.isEmpty()) { EmptyState(Icons.Rounded.History, "No sessions yet", "Your connections will be listed here. History stays on this device."); return@Screen }
        val totalDown = history.sumOf { it.bytesDown }; val totalUp = history.sumOf { it.bytesUp }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile("Sessions", history.size.toString(), Modifier.weight(1f))
            StatTile("Downloaded", Format.bytes(totalDown), Modifier.weight(1f))
            StatTile("Uploaded", Format.bytes(totalUp), Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.m))
        history.forEach { h -> HistoryRow(h) }
    }
}

@Composable
private fun HistoryRow(h: HistoryEntity) {
    val c = Tervyn.colors
    Card(Modifier.padding(bottom = Space.s), padding = androidx.compose.foundation.layout.PaddingValues(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagTile(h.countryCode, 40.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(h.serverName, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1)
                val dur = (h.endedAt ?: System.currentTimeMillis()) - h.startedAt
                Text("${timeOf(h.startedAt)} · ${Format.duration(dur)} · ↓${Format.bytes(h.bytesDown)} ↑${Format.bytes(h.bytesUp)}", style = MaterialTheme.typography.bodySmall, color = c.text2)
            }
            Pill(
                when (h.status) { "CONNECTED" -> "Active"; "FAILED" -> "Failed"; "SWITCHED" -> "Switched"; else -> "Ended" },
                when (h.status) { "CONNECTED" -> c.success; "FAILED" -> c.danger; else -> c.text3 },
            )
        }
    }
}

@Suppress("unused")
private val shield = Icons.Rounded.Shield
