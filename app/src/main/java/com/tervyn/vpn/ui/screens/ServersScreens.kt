package com.tervyn.vpn.ui.screens

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.core.ProxyList
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.data.SubscriptionRepository
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.Banner
import com.tervyn.vpn.ui.components.Card
import com.tervyn.vpn.ui.components.EmptyState
import com.tervyn.vpn.ui.components.FlagTile
import com.tervyn.vpn.ui.components.GhostButton
import com.tervyn.vpn.ui.components.IconBadge
import com.tervyn.vpn.ui.components.KeyValueRow
import com.tervyn.vpn.ui.components.LatencyBadge
import com.tervyn.vpn.ui.components.Pill
import com.tervyn.vpn.ui.components.PrimaryButton
import com.tervyn.vpn.ui.components.Screen
import com.tervyn.vpn.ui.components.SecondaryButton
import com.tervyn.vpn.ui.components.SectionTitle
import com.tervyn.vpn.ui.components.TervynTextField
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import kotlinx.coroutines.launch

// ---- list ----------------------------------------------------------------------------------

private fun serverOrder(a: Server) = when { a.alive -> 0; a.lastChecked == 0L -> 1; else -> 2 }

@Composable
fun ServersScreen(vm: MainViewModel, go: (String) -> Unit) {
    val c = Tervyn.colors
    val servers by vm.servers.collectAsState()
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val progress by vm.progress.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val status by vm.status.collectAsState()
    val builtin = servers.filter { it.isBuiltin }.sortedWith(compareBy<Server> { serverOrder(it) }.thenBy { it.latencyMs ?: Int.MAX_VALUE }.thenBy { it.name })
    val own = servers.filter { !it.isBuiltin }.sortedWith(compareBy<Server> { serverOrder(it) }.thenBy { it.latencyMs ?: Int.MAX_VALUE }.thenBy { it.addedAt })
    val working = servers.count { it.alive }

    Screen(
        title = "Servers", onBack = null,
        subtitle = "$working of ${servers.size} verified working",
        actions = {
            val angle = if (refreshing) rememberInfiniteTransition(label = "refresh").animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin").value else 0f
            IconButton(onClick = { vm.refreshServers() }, enabled = !refreshing, modifier = Modifier.semantics { contentDescription = "Test all servers" }) {
                Icon(Icons.Rounded.Refresh, null, tint = c.text2, modifier = if (refreshing) Modifier.rotate(angle) else Modifier)
            }
            IconButton(onClick = { go(Routes.ADD_SERVER) }, modifier = Modifier.semantics { contentDescription = "Add server" }) {
                Icon(Icons.Rounded.Add, null, tint = c.text)
            }
        },
    ) {
        if (progress.running) {
            Card(padding = androidx.compose.foundation.layout.PaddingValues(Space.m)) {
                Text("${progress.phase} · ${progress.tested}/${progress.total} · ${progress.working} working", style = MaterialTheme.typography.labelMedium, color = c.text2)
                Spacer(Modifier.height(Space.s))
                LinearProgressIndicator(
                    progress = { if (progress.total > 0) progress.tested.toFloat() / progress.total else 0f },
                    modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.cardHigh,
                )
            }
            Spacer(Modifier.height(Space.m))
        }

        FastestRow(selected = settings.autoSwitch, onClick = { vm.selectServer(null) })

        SectionTitle("Built-in · ${builtin.size} countries")
        if (builtin.isEmpty()) {
            Banner("Installing built-in servers", "The shipped server list is being prepared.", Tone.INFO)
        }
        builtin.forEach { s ->
            ServerRow(s, selected = !settings.autoSwitch && settings.selectedServerId == s.id, connected = status.isConnected && status.server?.id == s.id,
                testing = progress.running && s.lastChecked == 0L, onClick = { vm.selectServer(s.id) }, onInfo = { go(Routes.server(s.id)) })
        }

        SectionTitle("Your servers") { GhostButton("Subscriptions", { go(Routes.SUBSCRIPTIONS) }) }
        if (own.isEmpty()) {
            EmptyState(Icons.Rounded.Dns, "No servers of your own yet", "Add a VLESS, VMess, Trojan, Shadowsocks, Hysteria2, SOCKS5 or HTTP server, or a subscription URL.",
                action = { PrimaryButton("Add server", { go(Routes.ADD_SERVER) }, icon = Icons.Rounded.Add) })
        } else {
            own.forEach { s ->
                ServerRow(s, selected = !settings.autoSwitch && settings.selectedServerId == s.id, connected = status.isConnected && status.server?.id == s.id,
                    testing = progress.running && s.lastChecked == 0L, onClick = { vm.selectServer(s.id) }, onInfo = { go(Routes.server(s.id)) })
            }
        }
        Spacer(Modifier.height(Space.l))
        Text(
            "Built-in servers are free community servers. Tervyn verifies each one with an HTTPS test before it is shown as working, and replaces a slot that stays down.",
            style = MaterialTheme.typography.bodySmall, color = c.text3,
        )
    }
}

@Composable
private fun FastestRow(selected: Boolean, onClick: () -> Unit) {
    val c = Tervyn.colors
    Card(onClick = onClick, highlighted = selected, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Bolt, c.accent2, 44.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Fastest available", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("Picks the fastest verified server and switches when a clearly faster one appears", style = MaterialTheme.typography.bodySmall, color = c.text2)
            }
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = c.accent)
        }
    }
}

@Composable
fun ServerRow(server: Server, selected: Boolean, connected: Boolean, testing: Boolean, onClick: () -> Unit, onInfo: () -> Unit) {
    val c = Tervyn.colors
    Card(
        Modifier.padding(bottom = Space.s), onClick = onClick, highlighted = selected,
        padding = androidx.compose.foundation.layout.PaddingValues(start = Space.l, end = Space.xs, top = Space.s, bottom = Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagTile(server.displayCountry)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (connected) { Spacer(Modifier.width(Space.s)); Pill("Connected", c.success) }
                    else if (server.favorite) { Spacer(Modifier.width(Space.xs)); Icon(Icons.Rounded.Star, null, tint = c.warning, modifier = Modifier.size(14.dp)) }
                }
                val where = when {
                    server.countryMismatch -> "exit ${Format.countryName(server.countryCode)}"
                    server.verified && server.countryCode != null -> "location verified"
                    server.lastChecked > 0 -> "last check ${Format.ago(server.lastChecked)}"
                    else -> "not tested yet"
                }
                val proto = server.protocolLabel.split(" · ").take(2).joinToString(" · ")
                Text("$proto · $where", style = MaterialTheme.typography.bodySmall, color = if (server.countryMismatch) c.warning else c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.s))
            LatencyBadge(server.latencyMs, server.alive, server.lastChecked > 0, testing = testing && !server.verified)
            IconButton(onClick = onInfo, modifier = Modifier.semantics { contentDescription = "Details of ${server.name}" }) {
                Icon(Icons.Rounded.Info, null, tint = c.text3)
            }
        }
    }
}

// ---- details -------------------------------------------------------------------------------

@Composable
fun ServerDetailScreen(vm: MainViewModel, id: String, onBack: () -> Unit, connect: () -> Unit) {
    val c = Tervyn.colors
    val servers by vm.servers.collectAsState()
    val server = servers.firstOrNull { it.id == id }
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val status by vm.status.collectAsState()
    var testing by remember { mutableStateOf(false) }
    Screen(title = server?.name ?: "Server", onBack = onBack) {
        if (server == null) { EmptyState(Icons.Rounded.Dns, "Server not found", "It may have been removed."); return@Screen }
        val isSelected = !settings.autoSwitch && settings.selectedServerId == server.id
        val isConnected = status.isConnected && status.server?.id == server.id
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlagTile(server.displayCountry, 56.dp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.headlineSmall, color = c.text)
                    Text(server.protocolLabel, style = MaterialTheme.typography.bodyMedium, color = c.text2)
                }
                LatencyBadge(server.latencyMs, server.alive, server.lastChecked > 0, testing)
            }
            Spacer(Modifier.height(Space.l))
            KeyValueRow("Status", when { isConnected -> "Connected"; server.alive -> "Working"; server.lastChecked > 0 -> "Offline"; else -> "Not tested yet" },
                when { isConnected || server.alive -> c.success; server.lastChecked > 0 -> c.danger; else -> c.text2 })
            KeyValueRow("Verified exit country", server.countryCode?.let { "${Format.flag(it)} ${Format.countryName(it)}" } ?: "Not verified yet")
            if (server.declaredCountry != null) KeyValueRow("Listed as", "${Format.flag(server.declaredCountry)} ${Format.countryName(server.declaredCountry)}")
            KeyValueRow("Exit IP", server.exitIp ?: "—")
            KeyValueRow("Last checked", Format.ago(server.lastChecked))
            KeyValueRow("Source", when (server.origin) { ServerOrigin.BUILTIN -> "Built-in"; ServerOrigin.CUSTOM -> "Added by you"; ServerOrigin.SUBSCRIPTION -> "Subscription" })
            KeyValueRow("Address", server.endpoint?.let { "${it.host}:${it.port}" } ?: runCatching { com.tervyn.vpn.core.Links.parse(server.xrayLink!!) }.getOrNull()?.let { "${it.address}:${it.port}" } ?: "—")
        }
        if (server.countryMismatch) {
            Spacer(Modifier.height(Space.m))
            Banner("Location differs from the listing", "The provider lists this server in ${Format.countryName(server.declaredCountry)}, but its traffic exits in ${Format.countryName(server.countryCode)}.", Tone.WARNING)
        }
        Spacer(Modifier.height(Space.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            SecondaryButton(if (testing) "Testing…" else "Test now", { testing = true; vm.testServer(server) { testing = false } }, Modifier.weight(1f), icon = Icons.Rounded.Sync, enabled = !testing)
            SecondaryButton(if (server.favorite) "Unfavourite" else "Favourite", { vm.toggleFavorite(server) }, Modifier.weight(1f), icon = if (server.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder)
        }
        Spacer(Modifier.height(Space.m))
        when {
            isConnected -> SecondaryButton("Disconnect", { vm.disconnect() }, Modifier.fillMaxWidth())
            status.isActive -> PrimaryButton("Switch to this server", { vm.selectServer(server.id) }, Modifier.fillMaxWidth())
            else -> PrimaryButton(if (isSelected) "Connect" else "Select and connect", { vm.selectAndConnect(server.id, connect) }, Modifier.fillMaxWidth())
        }
        if (!server.isBuiltin) {
            Spacer(Modifier.height(Space.m))
            SecondaryButton("Remove server", { vm.deleteServer(server); onBack() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Delete, danger = true)
        }
    }
}

// ---- add -----------------------------------------------------------------------------------

private fun looksLikeSubscription(s: String): Boolean {
    val t = s.trim()
    if (!t.startsWith("https://", true) && !t.startsWith("http://", true)) return false
    if (t.contains('\n')) return false
    // A proxy URL carries credentials or a bare host:port; a subscription has a path.
    return ProxyList.parseUrl(t) == null || t.substringAfter("://").contains('/')
}

@Composable
fun AddServerScreen(vm: MainViewModel, onBack: () -> Unit, initial: String? = null) {
    val c = Tervyn.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    var text by rememberSaveable { mutableStateOf(initial ?: "") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MainViewModel.ImportResult?>(null) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { busy = true; result = runCatching { vm.importLinksFromFile(uri) }.getOrElse { MainViewModel.ImportResult(0, 0, 0, it.message ?: "Could not read file") }; busy = false }
    }
    val error = text.trim().takeIf { it.isNotEmpty() && !it.contains('\n') && !looksLikeSubscription(it) }?.let { vm.linkError(it) }
        ?.takeIf { ProxyList.parseUrl(text.trim()) == null }

    Screen(title = "Add server", onBack = onBack) {
        Text("Paste one or more share links, or a subscription URL.", style = MaterialTheme.typography.bodyMedium, color = c.text2)
        Spacer(Modifier.height(Space.m))
        TervynTextField(text, { text = it; result = null }, "Link or subscription URL", placeholder = "vless://… · trojan://… · https://provider/sub", singleLine = false, minLines = 4, error = error)
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SecondaryButton("Paste", {
                scope.launch {
                    val clip: ClipData? = clipboard.getClipEntry()?.clipData
                    val t = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (t.isNullOrBlank()) vm.toast("Clipboard is empty") else text = t.trim()
                }
            }, Modifier.weight(1f), icon = Icons.Rounded.ContentPaste)
            SecondaryButton("From file", { picker.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }, Modifier.weight(1f), icon = Icons.Rounded.FolderOpen)
        }
        Spacer(Modifier.height(Space.m))
        PrimaryButton(
            if (looksLikeSubscription(text)) "Add subscription" else "Add and test", {
                scope.launch {
                    busy = true
                    result = if (looksLikeSubscription(text)) vm.addSubscription("", text.trim()) else vm.importLinks(text)
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = text.isNotBlank() && error == null && !busy, loading = busy,
        )
        result?.let { r ->
            Spacer(Modifier.height(Space.m))
            Banner(if (r.added > 0) "Done" else "Nothing added", r.message, if (r.added == 0) Tone.ERROR else if (r.working > 0) Tone.SUCCESS else Tone.WARNING,
                action = if (r.added > 0) ({ GhostButton("Back to servers", onBack) }) else null)
        }

        SectionTitle("Supported")
        Text("VLESS (REALITY, Vision, WebSocket, gRPC, XHTTP, HTTPUpgrade), VMess, Trojan, Shadowsocks, Hysteria2, SOCKS5 and HTTP proxies. Certificates are always verified — links asking for insecure TLS are imported without that flag.",
            style = MaterialTheme.typography.bodySmall, color = c.text3)

        SectionTitle("Manual proxy") { GhostButton(if (manual) "Hide" else "Show", { manual = !manual }) }
        if (manual) ManualProxyForm(vm) { result = it }
    }
}

@Composable
private fun ManualProxyForm(vm: MainViewModel, onResult: (MainViewModel.ImportResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val hostError = host.takeIf { it.isNotBlank() }?.let { if (!ProxyList.isValidHost(it.trim().removeSurrounding("[", "]"))) "Invalid host" else null }
    val portError = port.takeIf { it.isNotBlank() }?.let { p -> if (p.toIntOrNull()?.let(ProxyList::validPort) != true) "Port must be 1–65535" else null }
    Card {
        TervynTextField(host, { host = it }, "Server address", placeholder = "proxy.example.com", error = hostError)
        Spacer(Modifier.height(Space.s))
        TervynTextField(port, { port = it.filter(Char::isDigit).take(5) }, "Port", keyboardType = KeyboardType.Number, error = portError)
        Spacer(Modifier.height(Space.s))
        TervynTextField(user, { user = it }, "Username (optional)")
        Spacer(Modifier.height(Space.s))
        TervynTextField(pass, { pass = it }, "Password (optional)", secret = true)
        Spacer(Modifier.height(Space.s))
        TervynTextField(name, { name = it }, "Name (optional)")
        Spacer(Modifier.height(Space.m))
        PrimaryButton("Add and test", {
            scope.launch {
                busy = true
                val (srv, r) = vm.addCustomProxyAuto(host.trim(), port.toInt(), user.ifBlank { null }, pass.ifBlank { null }, name)
                onResult(MainViewModel.ImportResult(1, if (r.ok) 1 else 0, 0, if (r.ok) "${srv.name} works (${srv.protocolLabel}, ${r.latencyMs} ms)" else "${srv.name} saved, but the test failed: ${r.error}"))
                busy = false
            }
        }, Modifier.fillMaxWidth(), enabled = host.isNotBlank() && port.isNotBlank() && hostError == null && portError == null && !busy, loading = busy)
    }
}

// ---- subscriptions -------------------------------------------------------------------------

@Composable
fun SubscriptionsScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val subs by vm.subscriptions.collectAsState()
    val scope = rememberCoroutineScope()
    var busyId by remember { mutableStateOf<Long?>(null) }
    Screen(title = "Subscriptions", onBack = onBack, actions = {
        IconButton(onClick = { go(Routes.ADD_SERVER) }, modifier = Modifier.semantics { contentDescription = "Add subscription" }) { Icon(Icons.Rounded.Add, null, tint = c.text) }
    }) {
        if (subs.isEmpty()) {
            EmptyState(Icons.Rounded.RssFeed, "No subscriptions", "A subscription URL keeps a list of servers up to date automatically.",
                action = { PrimaryButton("Add subscription", { go(Routes.ADD_SERVER) }, icon = Icons.Rounded.Add) })
        }
        subs.forEach { s -> SubscriptionRow(s, busy = busyId == s.id, onRefresh = {
            scope.launch { busyId = s.id; val r = vm.refreshSubscription(s); vm.toast(r.message); busyId = null }
        }, onDelete = { vm.deleteSubscription(s.id) }) }
    }
}

@Composable
private fun SubscriptionRow(s: SubscriptionRepository.Subscription, busy: Boolean, onRefresh: () -> Unit, onDelete: () -> Unit) {
    val c = Tervyn.colors
    Card(Modifier.padding(bottom = Space.s), padding = androidx.compose.foundation.layout.PaddingValues(start = Space.l, end = Space.xs, top = Space.s, bottom = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.RssFeed, c.accent2)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (s.error != null) "Error: ${s.error}" else "${s.count} servers · updated ${Format.ago(s.lastUpdated)}",
                    style = MaterialTheme.typography.bodySmall, color = if (s.error != null) c.danger else c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Refresh ${s.name}" }) { Icon(Icons.Rounded.Refresh, null, tint = c.text2) }
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "Delete ${s.name}" }) { Icon(Icons.Rounded.Delete, null, tint = c.text3) }
        }
    }
}

@Suppress("unused")
private fun Modifier.selectable(onClick: () -> Unit) = clickable(role = Role.Button, onClick = onClick)
