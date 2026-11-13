package com.tervyn.vpn.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import com.tervyn.vpn.BuildConfig
import com.tervyn.vpn.PendingActions
import com.tervyn.vpn.core.ProxyList
import com.tervyn.vpn.core.ProxySnippet
import com.tervyn.vpn.data.SplitMode
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.domain.SourceMode
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.net.DohForwarder
import com.tervyn.vpn.security.PinHasher
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.GlowShield
import com.tervyn.vpn.ui.components.LoadingIndicator
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.OptionCard
import com.tervyn.vpn.ui.components.Pill
import com.tervyn.vpn.ui.components.ProtocolCard
import com.tervyn.vpn.ui.components.ProxyInputField
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SectionHeader
import com.tervyn.vpn.ui.components.SegmentedTabs
import com.tervyn.vpn.ui.components.SettingsRow
import com.tervyn.vpn.ui.components.StatusBanner
import com.tervyn.vpn.ui.components.TervynLogo
import com.tervyn.vpn.ui.components.ToggleSetting
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.showBiometric
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun CardTitle(icon: ImageVector, title: String, subtitle: String?, tint: Color = Tervyn.colors.cyan) {
    val c = Tervyn.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Space.s)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(Space.m))
        Column {
            Text(title, color = c.text, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, color = c.text2, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- Server source ------------------------------------------------------------------------

@Composable
fun SourcesScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val proxioAction = remember { AsyncAction() }
    val snippetAction = remember { AsyncAction() }
    var key by remember { mutableStateOf("") }
    var proto by rememberSaveable { mutableStateOf(s.proxioProtocol) }
    var limit by rememberSaveable { mutableStateOf(s.proxioLimit.toString()) }
    var snippet by rememberSaveable { mutableStateOf("") }
    val options = listOf(
        Triple(SourceMode.FREE, Icons.Rounded.Public, "18 public lists"),
        Triple(SourceMode.PROXIO, Icons.Rounded.Key, "Your API key"),
        Triple(SourceMode.PROXIO_PLUS_FREE, Icons.Rounded.RocketLaunch, "Proxio first"),
        Triple(SourceMode.OWN, Icons.Rounded.Storage, "Including VLESS"),
        Triple(SourceMode.INTEGRATION, Icons.Rounded.Extension, "curl / Python / Node.js"),
        Triple(SourceMode.LIST_FILE, Icons.Rounded.Description, ".txt file"),
    )
    ScreenScaffold("Advanced Settings", "Configure your server source and proxy options", onBack) {
        GlassCard(highlight = true) {
            CardTitle(Icons.Rounded.Storage, "Server Source", "Choose how to get your VPN servers")
            options.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    row.forEach { (m, icon, sub) ->
                        OptionCard(m.label.replace(" servers", "").replace(" (.txt file)", ""), sub, icon, s.sourceMode == m, { vm.setSourceMode(m) }, Modifier.weight(1f))
                    }
                }
            }
        }
        if (s.sourceMode == SourceMode.PROXIO || s.sourceMode == SourceMode.PROXIO_PLUS_FREE) {
            Spacer(Modifier.height(Space.m))
            GlassCard(highlight = true) {
                CardTitle(Icons.Rounded.Key, "Proxio.io Configuration", "Enter your API key and choose options", c.purple)
                ProxyInputField(key, { key = it.trim() }, if (vm.proxioKeySet()) "API key saved — enter to replace" else "Enter your Proxio.io API key", secret = true)
                Spacer(Modifier.height(Space.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Proxy type", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
                    Box(Modifier.weight(1f)) { ChipRow(listOf("socks5" to "SOCKS5", "http" to "HTTP", "both" to "Both"), proto) { proto = it } }
                }
                ProxyInputField(limit, { if (it.length <= 4 && it.all(Char::isDigit)) limit = it }, "Servers (1–1000)", keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(Space.s))
                NeonButton("Save & Load", {
                    scope.launch {
                        proxioAction.running = true; proxioAction.result = null
                        val r = vm.saveProxio(key.ifBlank { null }, proto, limit.toIntOrNull()?.coerceIn(1, 1000) ?: 50)
                        key = ""; proxioAction.running = false; proxioAction.result = r.message to (r.added > 0)
                    }
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Download, enabled = !proxioAction.running && (key.isNotBlank() || vm.proxioKeySet()))
                Spacer(Modifier.height(Space.s))
                ResultCard(proxioAction, vm)
            }
        }
        if (s.sourceMode == SourceMode.INTEGRATION) {
            Spacer(Modifier.height(Space.m))
            GlassCard(highlight = true) {
                CardTitle(Icons.Rounded.Code, "Proxy Integration", "Paste the code example from your provider")
                ProxyInputField(snippet, { snippet = it.take(20_000) }, "curl --proxy \"http://USER:PASS@host:port/\" …", singleLine = false, minLines = 3)
                val det = remember(snippet) { if (snippet.isBlank()) null else ProxySnippet.parse(snippet) }
                det?.let { d -> Text(if (d.proxies.isEmpty()) "No proxy detected yet" else "Detected (${d.detected}): ${d.proxies.joinToString()}", color = if (d.proxies.isEmpty()) c.text2 else c.green, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(Space.s))
                NeonButton("Save & Test", {
                    scope.launch {
                        snippetAction.running = true; snippetAction.result = null
                        val r = vm.importSnippet(snippet); snippetAction.running = false; snippetAction.result = r.message to (r.working > 0)
                    }
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check, enabled = det?.proxies?.isNotEmpty() == true && !snippetAction.running)
                Spacer(Modifier.height(Space.s))
                ResultCard(snippetAction, vm)
            }
        }
        if (s.sourceMode == SourceMode.OWN || s.sourceMode == SourceMode.LIST_FILE || s.sourceMode == SourceMode.FREE) {
            Spacer(Modifier.height(Space.m))
            NeonButton("Configure ${s.sourceMode.label}", {
                go(when (s.sourceMode) { SourceMode.OWN -> Routes.PROTOCOLS; SourceMode.LIST_FILE -> Routes.LIST_IMPORT; else -> Routes.FREE_SOURCES })
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Tune)
        }
        SectionHeader("Additional Options", Icons.Rounded.Settings)
        GlassCard {
            SettingsRow(Icons.Rounded.Refresh, "Refresh Server List", "Download and test servers now", { vm.refreshServers(); go(Routes.SERVERS) })
            SettingsRow(Icons.Rounded.Link, "Import share link / QR", "vless://, vmess://, trojan://, ss://", { go(Routes.IMPORT_LINK) })
            SettingsRow(Icons.Rounded.Cloud, "Subscriptions", "Manage subscription URLs", { go(Routes.SUBSCRIPTIONS) })
            SettingsRow(Icons.Rounded.Add, "Add custom proxy", "HTTP or SOCKS5", { go(Routes.ADD_PROXY) })
            SettingsRow(Icons.Rounded.Tune, "Manual Xray configuration", null, { go(Routes.ADVANCED) })
        }
        Spacer(Modifier.height(Space.s))
        Text("IPv6 through the tunnel is not offered: proxies used here carry IPv4 TCP, so IPv6 is blocked in All Apps mode to prevent leaks.",
            color = c.text3, style = MaterialTheme.typography.labelSmall)
    }
}

// ---- Protocol selection -------------------------------------------------------------------

@Composable
fun ProtocolSelectionScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val protos = listOf(
        listOf("VLESS", "Fast · Secure · Modern (REALITY, Vision, WS, gRPC, XHTTP)", "Recommended"),
        listOf("VMess", "Reliable · Flexible", "Xray"),
        listOf("Trojan", "TLS · Looks like HTTPS", "Xray"),
        listOf("Shadowsocks", "Simple · Fast (incl. 2022 ciphers)", "Xray"),
        listOf("SOCKS5", "Flexible · Widely used", "Native"),
        listOf("HTTP", "Basic · Universal (CONNECT)", "Native"),
    )
    val icons = listOf(Icons.Rounded.Bolt, Icons.Rounded.Shuffle, Icons.Rounded.Shield, Icons.Rounded.Send, Icons.Rounded.Hub, Icons.Rounded.Language)
    ScreenScaffold("VPN Protocol Selection", "Choose the protocol of the server you want to add.", onBack, heroArt = com.tervyn.vpn.R.drawable.art_protocol, accentWord = "Protocol") {
        protos.forEachIndexed { i, (name, desc, badge) ->
            GlassCard(highlight = sel == i, onClick = { sel = i }, padding = PaddingValues(horizontal = Space.m, vertical = Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(sel == i, { sel = i }, colors = RadioButtonDefaults.colors(selectedColor = c.cyan, unselectedColor = c.text3))
                    Icon(icons[i], null, tint = if (i == 0) c.cyan else c.purple, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = c.text, style = MaterialTheme.typography.titleMedium)
                        Text(desc, color = c.text2, style = MaterialTheme.typography.bodySmall)
                    }
                    Pill(badge, if (i == 0) c.green else if (badge == "Native") c.blue else c.cyan)
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        StatusBanner("Recommended: VLESS", "VLESS with REALITY or TLS offers the best balance of speed, security and censorship resistance.", Icons.Rounded.Lightbulb, c.amber)
        Spacer(Modifier.height(Space.m))
        NeonButton("Continue", { go(if (sel >= 4) Routes.ADD_PROXY else Routes.IMPORT_LINK) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check)
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeonButton("Manual config", { go(Routes.ADVANCED) }, Modifier.weight(1f), secondary = true)
            NeonButton("Subscription", { go(Routes.SUBSCRIPTIONS) }, Modifier.weight(1f), secondary = true)
        }
    }
}

// ---- Add custom proxy ---------------------------------------------------------------------

@Composable
fun AddProxyScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val scope = rememberCoroutineScope()
    val action = remember { AsyncAction() }
    var type by rememberSaveable { mutableStateOf("SOCKS5") }
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val hostErr = if (host.isNotBlank() && !ProxyList.isValidHost(host.trim())) "Invalid host" else null
    val portErr = if (port.isNotBlank() && port.toIntOrNull()?.let(ProxyList::validPort) != true) "1–65535" else null
    val valid = host.isNotBlank() && port.isNotBlank() && hostErr == null && portErr == null
    ScreenScaffold("Add Custom Proxy", "Enter your proxy details or import a list", onBack) {
        SegmentedTabs(listOf("Manual Entry" to Icons.Rounded.Keyboard, "Import List (.txt)" to Icons.Rounded.Description, "From Code" to Icons.Rounded.Code), 0, { i ->
            if (i == 1) go(Routes.LIST_IMPORT) else if (i == 2) go(Routes.INTEGRATION)
        })
        Spacer(Modifier.height(Space.m))
        GlassCard(highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storage, null, tint = c.cyan); Spacer(Modifier.width(Space.s))
                Text("Proxy Type", color = c.text, style = MaterialTheme.typography.titleMedium)
            }
            ChipRow(listOf("SOCKS5" to "SOCKS5", "HTTP" to "HTTP", "AUTO" to "Auto"), type) { type = it }
            if (type == "AUTO") Text("Auto tries SOCKS5 first, then HTTP CONNECT, and keeps the one that works.", color = c.text3, style = MaterialTheme.typography.labelSmall)
            ProxyInputField(host, { host = it.trim() }, "Server Address", placeholder = "e.g. 1.2.3.4 or proxy.example.com", keyboardType = KeyboardType.Uri, error = hostErr)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ProxyInputField(port, { if (it.length <= 5 && it.all(Char::isDigit)) port = it }, "Port", Modifier.weight(1f), placeholder = "e.g. 8080", keyboardType = KeyboardType.Number, error = portErr)
                ProxyInputField(user, { user = it.take(255) }, "Username (optional)", Modifier.weight(1.3f))
            }
            ProxyInputField(pass, { pass = it.take(255) }, "Password (optional)", secret = true)
            ProxyInputField(name, { name = it.take(60) }, "Name (optional)")
            Spacer(Modifier.height(Space.s))
            NeonButton(if (action.running) "Testing…" else "Test Connection", {
                scope.launch {
                    action.running = true; action.result = null
                    val p = port.toInt(); val u = user.ifBlank { null }; val pw = if (user.isBlank()) null else pass
                    val (srv, r) = if (type == "AUTO") vm.addCustomProxyAuto(host.trim(), p, u, pw, name)
                    else vm.addCustomProxy(ProxyEndpoint(ProxyProtocol.valueOf(type), host.trim(), p, u, pw), name)
                    action.running = false
                    action.result = (if (r.ok) "${srv.protocolLabel} proxy works: ${r.latencyMs} ms, exit ${r.exitIp} (${r.countryCode ?: "?"}). Saved to your servers."
                    else "Saved, but the HTTPS test failed: ${r.error}") to r.ok
                }
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Bolt, enabled = valid && !action.running)
        }
        Spacer(Modifier.height(Space.m))
        GlassCard(onClick = { go(Routes.LIST_IMPORT) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Description, null, tint = c.cyan, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Import Proxy List", color = c.text, style = MaterialTheme.typography.titleMedium)
                    Text("Select a .txt file with proxies", color = c.text2, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = c.text2)
            }
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = c.green, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Space.s))
                Column {
                    Text("Supported formats:", color = c.green, style = MaterialTheme.typography.labelLarge)
                    Text("host:port:user:pass, user:pass@host:port, user:pass:host:port, http:// or socks5:// URLs", color = c.text2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        action.result?.let { (msg, ok) ->
            if (ok) StatusBanner("Connection Successful!", msg, Icons.Rounded.VerifiedUser, c.green) else NotificationCard("Not working", msg, Tone.ERROR)
            Spacer(Modifier.height(Space.m))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeonButton("Cancel", onBack, Modifier.weight(1f), icon = Icons.Rounded.Close, secondary = true)
            NeonButton("Done", { go(Routes.SERVERS) }, Modifier.weight(1f), icon = Icons.Rounded.Save, enabled = action.result?.second == true)
        }
    }
}

// ---- Import configuration -----------------------------------------------------------------

@Composable
fun ImportLinkScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val action = remember { AsyncAction() }
    var text by rememberSaveable { mutableStateOf("") }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    val pending by PendingActions.importLink.collectAsState()
    LaunchedEffect(pending) { pending?.let { text = it; showPaste = true; PendingActions.importLink.value = null } }
    fun run(block: suspend () -> MainViewModel.ImportResult) = scope.launch {
        action.running = true; action.result = null
        val r = block(); action.running = false; action.result = r.message to (r.added > 0)
    }
    val qr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { decodeQr(ctx, uri) }
            if (r == null) vm.toast("No QR code found in the image") else { text = r; showPaste = true }
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) run { vm.importLinksFromFile(uri) } }
    @Composable
    fun Option(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
        GlassCard(highlight = true, onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = c.cyan, modifier = Modifier.size(48.dp)); Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) { Text(title, color = c.text, style = MaterialTheme.typography.titleMedium); Text(sub, color = c.text2, style = MaterialTheme.typography.bodySmall) }
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.blue.copy(alpha = .25f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChevronRight, null, tint = c.text) }
            }
        }
        Spacer(Modifier.height(Space.s))
    }
    ScreenScaffold("Import VLESS / VMess Configuration", "Add your server configuration in multiple ways", onBack, heroArt = com.tervyn.vpn.R.drawable.art_import, accentWord = "VLESS / VMess") {
        Option(Icons.Rounded.QrCode2, "Scan QR Code from Image", "Pick a screenshot or photo of the QR code (no camera permission needed)") { qr.launch("image/*") }
        Option(Icons.Rounded.Link, "Paste Share Link", "Paste your vless://, vmess://, trojan:// or ss:// link") { showPaste = true }
        Option(Icons.Rounded.Description, "Add from Subscription URL", "Import and update multiple servers") { go(Routes.SUBSCRIPTIONS) }
        Option(Icons.Rounded.UploadFile, "Import from File", "A .txt file containing share links (one per line, or base64)") { file.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }
        if (showPaste) {
            ProxyInputField(text, { text = it }, "Share link(s)", singleLine = false, minLines = 3, placeholder = "vless://uuid@host:443?security=reality&…")
            Spacer(Modifier.height(Space.s))
            NeonButton("Import Configuration", { run { vm.importLinks(text) } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check, enabled = text.isNotBlank() && !action.running)
            Spacer(Modifier.height(Space.s))
        }
        ResultCard(action, vm)
        Spacer(Modifier.height(Space.s))
        StatusBanner("Supported Protocols", "VLESS (REALITY, Vision, WS, gRPC, XHTTP, HTTPUpgrade), VMess, Trojan, Shadowsocks, SOCKS5, HTTP — run through Xray-core with verified TLS.", Icons.Rounded.Lightbulb, c.amber)
        if (!vm.c.xray.available) { Spacer(Modifier.height(Space.s)); NotificationCard("Xray unavailable", "This build has no Xray binary for this device's CPU.", Tone.ERROR) }
    }
}

// ---- DNS ----------------------------------------------------------------------------------

@Composable
fun DnsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    var custom by rememberSaveable { mutableStateOf(if (s.dohUrl in DohForwarder.PRESETS.values) "" else s.dohUrl) }
    var lookup by rememberSaveable { mutableStateOf(s.ipLookupUrl) }
    val current = DohForwarder.PRESETS.entries.firstOrNull { it.value == s.dohUrl }?.key ?: "Custom DNS"
    val tints = listOf(c.amber, c.blue, c.purple, c.green, c.red)
    ScreenScaffold("DNS Settings & Configuration", "Choose your encrypted DNS provider or set a custom one.", onBack, heroArt = com.tervyn.vpn.R.drawable.art_dns, accentWord = "DNS") {
        GlassCard(highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Settings, null, tint = c.cyan, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) { Text("Current DNS", color = c.text, style = MaterialTheme.typography.titleMedium); Text(s.dohUrl.removePrefix("https://"), color = c.text2, style = MaterialTheme.typography.bodySmall) }
                Pill("✓ $current", c.green)
            }
        }
        SectionHeader("DNS-over-HTTPS Providers")
        DohForwarder.PRESETS.entries.forEachIndexed { i, (name, url) ->
            GlassCard(highlight = s.dohUrl == url, onClick = { vm.updateSettings { it.copy(dohUrl = url) } }, padding = PaddingValues(Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Dns, null, tint = tints[i % tints.size], modifier = Modifier.size(36.dp)); Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) { Text(name, color = c.text, style = MaterialTheme.typography.titleMedium); Text(url.removePrefix("https://"), color = c.text2, style = MaterialTheme.typography.bodySmall) }
                    RadioButton(s.dohUrl == url, { vm.updateSettings { it.copy(dohUrl = url) } }, colors = RadioButtonDefaults.colors(selectedColor = c.cyan, unselectedColor = c.text3))
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        GlassCard(highlight = current == "Custom DNS") {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Tune, null, tint = c.purple, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(Space.m)); Text("Custom DNS", color = c.text, style = MaterialTheme.typography.titleMedium) }
            ProxyInputField(custom, { custom = it.trim() }, "https://…/dns-query", keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(Space.s))
            NeonButton("Use custom resolver", { if (custom.startsWith("https://")) vm.updateSettings { it.copy(dohUrl = custom) } else vm.toast("Must start with https://") },
                Modifier.fillMaxWidth(), secondary = true, enabled = custom.isNotBlank())
        }
        SectionHeader("Advanced Options", Icons.Rounded.Tune)
        GlassCard {
            ProxyInputField(lookup, { lookup = it.trim() }, "IP lookup URL ({ip} is replaced)", keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(Space.s))
            NeonButton("Save lookup service", { if (lookup.startsWith("https://")) vm.updateSettings { it.copy(ipLookupUrl = lookup) } else vm.toast("Must start with https://") }, Modifier.fillMaxWidth(), secondary = true)
        }
        Spacer(Modifier.height(Space.m))
        NotificationCard("How DNS is protected", "All Apps: every query is answered by Tervyn and resolved at the proxy or via DoH through the tunnel. Only My Sites: your sites resolve at the proxy; other names use the resolver above instead of your ISP.", Tone.INFO)
        Spacer(Modifier.height(Space.s))
        NotificationCard("Android Private DNS", "A specific Private DNS hostname bypasses Tervyn's resolver, so Only My Sites cannot detect sites. Use Automatic or Off.", Tone.WARNING)
    }
}

// ---- Privacy center -----------------------------------------------------------------------

@Composable
fun PrivacyCenterScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    val connected = status.isConnected
    val allApps = s.mode == VpnMode.ALL_APPS
    data class Item(val icon: ImageVector, val title: String, val sub: String, val active: Boolean?)
    val items = listOf(
        Item(Icons.Rounded.Language, "DNS Leak Protection", if (allApps) "All DNS answered inside the tunnel" else "Your sites resolve at the proxy", if (connected) true else null),
        Item(Icons.Rounded.Block, "IPv6 Leak Protection", if (allApps) "IPv6 captured and dropped" else "No IPv6 answers for your sites", if (connected) allApps else null),
        Item(Icons.Rounded.Lock, "Encrypted DNS (DoH)", "Resolver: ${DohForwarder.PRESETS.entries.firstOrNull { it.value == s.dohUrl }?.key ?: "custom"}", true),
        Item(Icons.Rounded.FlashOn, "Kill Switch", when (status.lockdown) { true -> "\"Block connections without VPN\" is on"; false -> "Off — enable in Android VPN settings"; null -> "Set in Android VPN settings" }, status.lockdown),
        Item(Icons.Rounded.AllInclusive, "Always-on VPN", when (status.alwaysOn) { true -> "Enabled for Tervyn"; false -> "Off — enable in Android VPN settings"; null -> "Set in Android VPN settings" }, status.alwaysOn),
        Item(Icons.Rounded.Fingerprint, "App Lock", if (s.appLock) "PIN${if (s.biometric) " + biometrics" else ""}" else "Off", s.appLock),
    )
    val known = items.filter { it.active != null }
    val score = if (known.isEmpty()) 0 else known.count { it.active == true } * 100 / known.size
    ScreenScaffold("Privacy & Security Center", "What Tervyn protects — measured, not promised.", onBack, heroArt = com.tervyn.vpn.R.drawable.art_privacy, accentWord = "Security Center") {
        GlassCard(highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = if (connected) c.green else c.amber, modifier = Modifier.size(52.dp)); Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Security Status", color = c.text, style = MaterialTheme.typography.titleMedium)
                    Text(if (!connected) "VPN is off" else if (score >= 80) "Well protected" else "Partially protected", color = if (connected && score >= 80) c.green else c.amber,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                if (connected) Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { score / 100f }, modifier = Modifier.size(72.dp), color = c.green, trackColor = c.bg2, strokeWidth = 6.dp)
                    Text("$score%", color = c.text, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(if (connected) "Score = share of the checks below that are active right now." else "Connect to see which protections are active.", color = c.text3, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Space.s))
        items.forEach { it2 ->
            GlassCard(padding = PaddingValues(Space.m), onClick = when (it2.title) {
                "Kill Switch", "Always-on VPN" -> ({ openVpnSettings(ctx) })
                "App Lock" -> ({ go(Routes.APP_LOCK) })
                "Encrypted DNS (DoH)" -> ({ go(Routes.DNS) })
                else -> null
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(it2.icon, null, tint = c.cyan, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) { Text(it2.title, color = c.text, style = MaterialTheme.typography.titleMedium); Text(it2.sub, color = c.text2, style = MaterialTheme.typography.bodySmall) }
                    Pill(when (it2.active) { true -> "✓ Active"; false -> "Off"; null -> "When connected" }, when (it2.active) { true -> c.green; false -> c.amber; null -> c.text2 })
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        GlassCard {
            SettingsRow(Icons.Rounded.Apps, "App Permissions", "Manage what Tervyn may access", { go(Routes.PERMISSIONS) })
            SettingsRow(Icons.Rounded.BugReport, "Run leak & network diagnostics", null, { go(Routes.DIAGNOSTICS) })
            SettingsRow(Icons.Rounded.Policy, "Privacy Policy", null, { go(Routes.POLICY) })
        }
        Spacer(Modifier.height(Space.m))
        NotificationCard("Honest limits", "A VPN hides your IP from websites; it does not make you anonymous or block trackers, cookies or fingerprinting. Free public proxies are run by unknown operators — avoid sensitive logins over them.", Tone.WARNING)
    }
}

// ---- Notifications ------------------------------------------------------------------------

@Composable
fun NotificationsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val events by vm.events.collectAsState()
    val s = settings ?: return
    ScreenScaffold("VPN Connection Notifications", "Stay informed about your VPN connection status.", onBack, heroArt = com.tervyn.vpn.R.drawable.art_bell, accentWord = "Notifications") {
        GlassCard(highlight = true) {
            ToggleSetting(Icons.Rounded.NotificationsActive, "Enable Notifications", "Get important VPN alerts", s.notificationsEnabled, { v -> vm.updateSettings { it.copy(notificationsEnabled = v) } })
        }
        SectionHeader("Notification Types")
        GlassCard {
            val on = s.notificationsEnabled
            ToggleSetting(Icons.Rounded.CheckCircle, "Connection Status", "When the VPN connects or disconnects", s.notifyConnect && s.notifyDisconnect,
                { v -> vm.updateSettings { it.copy(notifyConnect = v, notifyDisconnect = v) } }, enabled = on, tint = c.green)
            ToggleSetting(Icons.Rounded.Storage, "Server Switch Alerts", "When Tervyn changes server", s.notifySwitch, { v -> vm.updateSettings { it.copy(notifySwitch = v) } }, enabled = on)
            ToggleSetting(Icons.Rounded.FlashOn, "Connection Issues", "When a server stops responding", s.notifyFailure, { v -> vm.updateSettings { it.copy(notifyFailure = v) } }, enabled = on)
            ToggleSetting(Icons.Rounded.BarChart, "Data Usage Alerts", if (s.dataAlertMb > 0) "Alert when a session passes ${s.dataAlertMb} MB" else "Off", s.dataAlertMb > 0,
                { v -> vm.updateSettings { it.copy(dataAlertMb = if (v) 1024 else 0) } }, enabled = on)
            if (s.dataAlertMb > 0) ChipRow(listOf("256" to "256 MB", "1024" to "1 GB", "5120" to "5 GB", "10240" to "10 GB"), s.dataAlertMb.toString()) { v ->
                vm.updateSettings { it.copy(dataAlertMb = v.toInt()) }
            }
        }
        SectionHeader("Notification Settings", Icons.Rounded.Settings)
        GlassCard {
            ToggleSetting(Icons.Rounded.Vibration, "Vibration", "Vibrate on alerts", s.vibration, { v -> vm.updateSettings { it.copy(vibration = v) } })
            ToggleSetting(Icons.Rounded.VolumeUp, "Sound", "Play the notification sound", s.sound, { v -> vm.updateSettings { it.copy(sound = v) } })
            ToggleSetting(Icons.Rounded.Speed, "Live speed in status notification", null, s.showSpeedInNotification, { v -> vm.updateSettings { it.copy(showSpeedInNotification = v) } })
        }
        SectionHeader("Recent events", Icons.Rounded.History)
        if (events.isEmpty()) Text("No events yet.", color = c.text2)
        events.take(30).forEach { e ->
            NotificationCard("${e.title} · ${com.tervyn.vpn.utils.Format.ago(e.time)}", e.summary,
                when { e.title.contains("fail", true) || e.title.contains("unavailable", true) -> Tone.ERROR; e.title.contains("Connected") -> Tone.SUCCESS; else -> Tone.INFO })
            Spacer(Modifier.height(Space.s))
        }
        StatusBanner("Tip", "The persistent status notification is required by Android while a VPN runs; it has a Disconnect button.", Icons.Rounded.Info, c.cyan)
    }
}

// ---- App lock -----------------------------------------------------------------------------

@Composable
fun AppLockScreen(vm: MainViewModel, onBack: () -> Unit, activity: FragmentActivity) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val pinHash by vm.pinHash.collectAsState()
    val s = settings ?: return
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val canBio = remember {
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val enabled = pinHash != null && s.appLock
    ScreenScaffold("App Lock & Biometric Security", "Lock Tervyn and protect your settings with biometrics.", onBack, heroArt = com.tervyn.vpn.R.drawable.art_lock, accentWord = "Biometric Security") {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            listOf(Triple(Icons.Rounded.Fingerprint, "Fingerprint", canBio), Triple(Icons.Rounded.Face, "Face Unlock", canBio), Triple(Icons.Rounded.Pin, "PIN Code", true)).forEach { (icon, label, ok) ->
                GlassCard(Modifier.weight(1f), highlight = enabled && (label == "PIN Code" || s.biometric), padding = PaddingValues(Space.m)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = if (ok) c.cyan else c.text3, modifier = Modifier.size(40.dp))
                        Text(label, color = c.text, style = MaterialTheme.typography.labelLarge)
                        Text(if (!ok) "Not set up" else if (label == "Face Unlock") "Where supported" else "Available", color = if (ok) c.green else c.text3, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        if (!enabled) {
            GlassCard(highlight = true) {
                Text("Create a PIN (4–8 digits) to enable App Lock", color = c.text, style = MaterialTheme.typography.titleSmall)
                ProxyInputField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, "PIN", keyboardType = KeyboardType.NumberPassword, secret = true)
                ProxyInputField(pin2, { if (it.length <= 8 && it.all(Char::isDigit)) pin2 = it }, "Repeat PIN", keyboardType = KeyboardType.NumberPassword, secret = true,
                    error = if (pin2.isNotEmpty() && pin2 != pin) "PINs do not match" else null)
            }
            Spacer(Modifier.height(Space.m))
            NeonButton("Enable App Lock", { vm.setPin(pin); pin = ""; pin2 = ""; vm.toast("App lock enabled") }, Modifier.fillMaxWidth(),
                enabled = PinHasher.isValidPin(pin) && pin == pin2, icon = Icons.Rounded.Shield)
        } else {
            GlassCard(highlight = true) {
                ToggleSetting(Icons.Rounded.Lock, "Enable App Lock", "Require authentication to open Tervyn VPN", true, { if (!it) vm.setPin(null) })
            }
            Spacer(Modifier.height(Space.m))
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Settings, null, tint = c.cyan); Spacer(Modifier.width(Space.s)); Text("Lock Settings", color = c.text, style = MaterialTheme.typography.titleMedium) }
                ToggleSetting(Icons.Rounded.Fingerprint, "Biometric unlock", if (canBio) "Fingerprint, or face where the device supports it" else "No biometrics enrolled", s.biometric,
                    { v -> if (v) showBiometric(activity) { vm.updateSettings { it.copy(biometric = true) } } else vm.updateSettings { it.copy(biometric = false) } }, enabled = canBio)
                Text("Auto Lock — lock when in background for", color = c.text2, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                ChipRow(listOf("0" to "Immediately", "30" to "30 s", "60" to "1 min", "300" to "5 min"), s.lockTimeoutSec.toString()) { v -> vm.updateSettings { it.copy(lockTimeoutSec = v.toInt()) } }
                SettingsRow(Icons.Rounded.Shield, "Failed attempts", "Unlock is paused for 30 s after 5 wrong PINs", null)
            }
        }
        Spacer(Modifier.height(Space.m))
        NotificationCard("How it's stored", "The PIN is kept only as a salted PBKDF2 hash. Biometric data never leaves Android's secure hardware. App Lock protects Tervyn itself; locking other apps would require accessibility access, which Tervyn does not request.", Tone.INFO)
    }
}

// ---- Split tunnelling ---------------------------------------------------------------------

private data class AppEntry(val pkg: String, val label: String, val icon: Drawable?)

@Composable
fun SplitTunnelScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName != ctx.packageName }
                .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString(), runCatching { pm.getApplicationIcon(it) }.getOrNull()) }
                .sortedBy { it.label.lowercase() }
        }
    }
    val only = s.splitMode == SplitMode.ONLY_SELECTED
    val selected = if (only) s.includedApps else s.excludedApps
    fun toggle(pkg: String, on: Boolean) {
        val next = if (on) selected + pkg else selected - pkg
        if (only) vm.setIncludedApps(next) else vm.setExcludedApps(next)
    }
    ScreenScaffold("Split Tunneling & App Exclusions", "Choose which apps use the VPN and which bypass it.", onBack, heroArt = com.tervyn.vpn.R.drawable.art_split, accentWord = "App Exclusions") {
        SegmentedTabs(listOf("Bypass selected apps" to Icons.Rounded.RemoveCircleOutline, "Only selected use VPN" to Icons.Rounded.Lock), if (only) 1 else 0, { i ->
            vm.setSplitMode(if (i == 1) SplitMode.ONLY_SELECTED else SplitMode.BYPASS)
        })
        Spacer(Modifier.height(Space.s))
        Text(if (only) "Only the checked apps are routed through Tervyn (${s.includedApps.size} selected). With none selected, all apps use the VPN."
        else "Checked apps use your normal connection (${s.excludedApps.size} selected).", color = c.text2, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Space.s))
        ProxyInputField(query, { query = it }, "Search apps")
        Spacer(Modifier.height(Space.s))
        val list = apps
        if (list == null) LoadingIndicator("Loading apps…")
        else GlassCard {
            Row { Text(if (only) "Apps that will use VPN" else "Apps that will bypass VPN", color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text("${selected.size} selected", color = if (only) c.green else c.amber, style = MaterialTheme.typography.labelLarge) }
            list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
                .sortedByDescending { it.pkg in selected }
                .forEach { app ->
                    val checked = app.pkg in selected
                    Row(Modifier.fillMaxWidth().clickable { toggle(app.pkg, !checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bmp = remember(app.pkg) { app.icon?.toBitmap(96, 96)?.asImageBitmap() }
                        if (bmp != null) Image(bmp, null, Modifier.size(40.dp)) else Icon(Icons.Rounded.Apps, null, tint = c.text3, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, color = c.text, style = MaterialTheme.typography.titleSmall)
                            Text(if (checked) (if (only) "Uses the VPN" else "Bypasses the VPN") else app.pkg, color = if (checked) c.cyan else c.text3, style = MaterialTheme.typography.labelSmall)
                        }
                        Checkbox(checked, { toggle(app.pkg, it) }, colors = CheckboxDefaults.colors(checkedColor = c.blue))
                    }
                }
        }
        Spacer(Modifier.height(Space.m))
        StatusBanner("Tip", "Exclude apps that refuse VPNs (some banking apps) or that need local network access. With \"Block connections without VPN\", bypassed apps lose internet.", Icons.Rounded.Lightbulb, c.amber)
    }
}

// ---- About --------------------------------------------------------------------------------

@Composable
fun AboutScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val xrayVersion by produceState<String?>(null) { value = withContext(Dispatchers.IO) { vm.c.xray.version() } }
    ScreenScaffold("About Tervyn", "Fast. Secure. Free. A VPN client with no accounts and no ads.", onBack, heroArt = com.tervyn.vpn.R.drawable.art_about, accentWord = "Tervyn") {
        GlassCard(highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TervynLogo(84.dp); Spacer(Modifier.width(Space.l))
                Column {
                    Text("Tervyn VPN", color = c.text, style = MaterialTheme.typography.headlineSmall)
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = c.text2)
                    Text("Xray-core ${xrayVersion?.substringAfter("Xray ")?.substringBefore(" ") ?: "n/a"}", color = c.text3, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(Space.m))
            Text("Connect freely. Choose what you route.", color = c.cyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Spacer(Modifier.height(Space.m))
        GlassCard {
            SettingsRow(Icons.Rounded.Policy, "Privacy Policy", "How Tervyn handles data", { go(Routes.POLICY) })
            SettingsRow(Icons.Rounded.Gavel, "Terms of Use", "Free software, provided as is", { go(Routes.TERMS) })
            SettingsRow(Icons.Rounded.Code, "Open Source Licenses", "Xray-core (MPL-2.0) and libraries", { go(Routes.LICENSES) })
            SettingsRow(Icons.Rounded.SupportAgent, "Contact Support", "Prepare a problem report", { go(Routes.REPORT) })
            SettingsRow(Icons.Rounded.QuestionAnswer, "Help & FAQ", null, { go(Routes.HELP) })
        }
        Spacer(Modifier.height(Space.m))
        StatusBanner("Our Mission", "A fast, free VPN client that is honest about what it does: no accounts, no ads, no tracking.", Icons.Rounded.TrackChanges, c.purple)
        Spacer(Modifier.height(Space.m))
        Text("Made with 💙 for your privacy", color = c.text2, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun TermsScreen(onBack: () -> Unit) {
    val c = Tervyn.colors
    ScreenScaffold("Terms of Use", null, onBack, heroArt = com.tervyn.vpn.R.drawable.art_privacy, accentWord = "Terms") {
        listOf(
            "Free software" to "Tervyn VPN is provided free of charge, as is, without warranty of any kind.",
            "Third-party servers" to "Free public proxies and servers you add are operated by third parties. You are responsible for which servers you use and for complying with their terms and with local laws.",
            "Acceptable use" to "Do not use Tervyn for illegal activity, to attack networks, or to violate the rights of others.",
            "No guarantees" to "Availability, speed and the privacy practices of third-party servers cannot be guaranteed.",
            "Open source components" to "Xray-core is licensed under MPL-2.0; see Open Source Licenses.",
        ).forEach { (t, b) ->
            GlassCard { Text(t, color = c.text, style = MaterialTheme.typography.titleSmall); Text(b, color = c.text2, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(Space.s))
        }
    }
}

