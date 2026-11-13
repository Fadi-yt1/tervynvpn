package com.tervyn.vpn.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.tervyn.vpn.PendingActions
import com.tervyn.vpn.core.Links
import com.tervyn.vpn.core.ProxyList
import com.tervyn.vpn.core.ProxySources
import com.tervyn.vpn.core.XrayOutbound
import com.tervyn.vpn.core.XrayProtocol
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.domain.SourceMode
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.LoadingIndicator
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.ProtocolCard
import com.tervyn.vpn.ui.components.ProxyInputField
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SectionHeader
import com.tervyn.vpn.ui.components.SettingsRow
import com.tervyn.vpn.ui.components.ToggleSetting
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs a suspend import action, showing progress and the resulting message. */
class AsyncAction {
    var running by mutableStateOf(false)
    var result by mutableStateOf<Pair<String, Boolean>?>(null)
}

@Composable
fun ResultCard(a: AsyncAction, vm: MainViewModel) {
    val progress by vm.progress.collectAsState()
    if (a.running) LoadingIndicator(if (progress.running) "${progress.phase}: ${progress.tested}/${progress.total} (${progress.working} working)" else "Working…")
    a.result?.let { (msg, ok) -> NotificationCard(if (ok) "Done" else "Not completed", msg, if (ok) Tone.SUCCESS else Tone.ERROR) }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = c.cyan, unselectedColor = c.text3))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, color = c.text2, style = MaterialTheme.typography.bodySmall)
        }
    }
}


@Composable
fun FreeSourcesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    var custom by remember { mutableStateOf("") }
    ScreenScaffold("Free public servers", "Proxy lists Tervyn downloads and tests", onBack, heroArt = com.tervyn.vpn.R.drawable.art_dns, accentWord = "servers") {
        NotificationCard("Unknown operators", "These are open proxies run by unknown people. Every server is tested over HTTPS with certificate validation, but do not use them for banking or sensitive logins.", Tone.WARNING)
        Spacer(Modifier.height(Space.m))
        GlassCard {
            ProxySources.DEFAULT.forEach { src ->
                ToggleSetting(null, src.name, src.protocol.name, src.url !in s.disabledSources, { on ->
                    vm.updateSettings { st -> st.copy(disabledSources = if (on) st.disabledSources - src.url else st.disabledSources + src.url) }
                })
            }
        }
        SectionHeader("Custom list URLs")
        s.customSources.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u, color = c.text2, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.updateSettings { st -> st.copy(customSources = st.customSources - u) } }) { Icon(Icons.Rounded.Delete, "Remove", tint = c.red) }
            }
        }
        ProxyInputField(custom, { custom = it }, "https://… list URL", keyboardType = KeyboardType.Uri)
        Spacer(Modifier.height(Space.s))
        NeonButton("Add list URL", {
            val u = custom.trim()
            if (u.startsWith("https://") && u.length < 500) { vm.updateSettings { st -> st.copy(customSources = (st.customSources + u).distinct()) }; custom = "" }
            else vm.toast("Enter an https:// URL")
        }, Modifier.fillMaxWidth(), secondary = true)
        Spacer(Modifier.height(Space.m))
        NeonButton("Refresh & test now", { vm.refreshServers() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
    }
}

@Composable
fun ProxioScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val action = remember { AsyncAction() }
    var key by remember { mutableStateOf("") }
    var proto by rememberSaveable { mutableStateOf(s.proxioProtocol) }
    var limit by rememberSaveable { mutableStateOf(s.proxioLimit.toString()) }
    val hasKey = vm.proxioKeySet()
    ScreenScaffold("Proxio.io", "Private proxies from your Proxio account", onBack, heroArt = com.tervyn.vpn.R.drawable.art_privacy, accentWord = "Proxio") {
        GlassCard {
            ProxyInputField(key, { key = it.trim() }, if (hasKey) "API key (saved — enter to replace)" else "API key", secret = true)
            Text("Stored encrypted with the Android Keystore; never logged.", color = c.text3, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(Space.m))
            Text("Protocol", color = c.text, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf("socks5" to "SOCKS5", "http" to "HTTP", "both" to "Both").forEach { (v, l) ->
                    FilterChip(proto == v, { proto = v }, label = { Text(l) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.blue.copy(alpha = .3f), selectedLabelColor = c.cyan, labelColor = c.text2))
                }
            }
            Spacer(Modifier.height(Space.s))
            ProxyInputField(limit, { if (it.length <= 4 && it.all(Char::isDigit)) limit = it }, "Number of servers (1–1000)", keyboardType = KeyboardType.Number)
        }
        Spacer(Modifier.height(Space.m))
        NeonButton("Save & Load", {
            scope.launch {
                action.running = true; action.result = null
                val r = vm.saveProxio(key.ifBlank { null }, proto, limit.toIntOrNull()?.coerceIn(1, 1000) ?: 50)
                key = ""
                action.running = false; action.result = r.message to (r.added > 0)
            }
        }, Modifier.fillMaxWidth(), enabled = !action.running && (key.isNotBlank() || hasKey), icon = Icons.Rounded.Key)
        Spacer(Modifier.height(Space.s))
        if (hasKey) NeonButton("Remove API key", { vm.clearProxioKey() }, Modifier.fillMaxWidth(), secondary = true)
        Spacer(Modifier.height(Space.m))
        ResultCard(action, vm)
        Spacer(Modifier.height(Space.m))
        NeonButton("Use Proxio.io as server source", { vm.setSourceMode(SourceMode.PROXIO) }, Modifier.fillMaxWidth(), secondary = true)
        Spacer(Modifier.height(Space.s))
        NeonButton("Use Proxio.io + free servers", { vm.setSourceMode(SourceMode.PROXIO_PLUS_FREE) }, Modifier.fillMaxWidth(), secondary = true)
    }
}



@Composable
fun AdvancedConfigScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val scope = rememberCoroutineScope()
    val action = remember { AsyncAction() }
    var proto by rememberSaveable { mutableStateOf(XrayProtocol.VLESS) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("443") }
    var secret by remember { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("aes-256-gcm") }
    var flow by rememberSaveable { mutableStateOf("") }
    var network by rememberSaveable { mutableStateOf("tcp") }
    var security by rememberSaveable { mutableStateOf("tls") }
    var sni by rememberSaveable { mutableStateOf("") }
    var fp by rememberSaveable { mutableStateOf("chrome") }
    var pbk by rememberSaveable { mutableStateOf("") }
    var sid by rememberSaveable { mutableStateOf("") }
    var hostH by rememberSaveable { mutableStateOf("") }
    var path by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    ScreenScaffold("Advanced Configuration", "Manual Xray outbound", onBack, heroArt = com.tervyn.vpn.R.drawable.art_protocol, accentWord = "Configuration") {
        GlassCard {
            Text("Protocol", color = c.text, style = MaterialTheme.typography.titleSmall)
            ChipRow(XrayProtocol.entries.filter { it != XrayProtocol.HTTP && it != XrayProtocol.SOCKS }.map { it.name to it.label }, proto.name) { proto = XrayProtocol.valueOf(it) }
            ProxyInputField(name, { name = it.take(60) }, "Name")
            ProxyInputField(address, { address = it.trim() }, "Server address", keyboardType = KeyboardType.Uri)
            ProxyInputField(port, { if (it.length <= 5 && it.all(Char::isDigit)) port = it }, "Port", keyboardType = KeyboardType.Number)
            when (proto) {
                XrayProtocol.VLESS, XrayProtocol.VMESS -> ProxyInputField(secret, { secret = it.trim() }, "UUID (id)", secret = true)
                else -> ProxyInputField(secret, { secret = it }, "Password", secret = true)
            }
            if (proto == XrayProtocol.SHADOWSOCKS) {
                ChipRow(listOf("aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm").map { it to it }, method) { method = it }
            }
            if (proto == XrayProtocol.VLESS) {
                Text("Flow", color = c.text2, style = MaterialTheme.typography.labelMedium)
                ChipRow(listOf("" to "None", "xtls-rprx-vision" to "Vision"), flow) { flow = it }
            }
        }
        if (proto != XrayProtocol.SHADOWSOCKS) {
            Spacer(Modifier.height(Space.m))
            GlassCard {
                Text("Transport", color = c.text, style = MaterialTheme.typography.titleSmall)
                ChipRow(listOf("tcp" to "TCP", "ws" to "WebSocket", "grpc" to "gRPC", "xhttp" to "XHTTP", "httpupgrade" to "HTTPUpgrade"), network) { network = it }
                Text("Security", color = c.text, style = MaterialTheme.typography.titleSmall)
                ChipRow(listOf("none" to "None", "tls" to "TLS") + if (proto == XrayProtocol.VLESS || proto == XrayProtocol.TROJAN) listOf("reality" to "REALITY") else emptyList(), security) { security = it }
                if (security != "none") {
                    ProxyInputField(sni, { sni = it.trim() }, "SNI (server name)")
                    ProxyInputField(fp, { fp = it.trim() }, "uTLS fingerprint (chrome, firefox, safari…)")
                }
                if (security == "reality") {
                    ProxyInputField(pbk, { pbk = it.trim() }, "REALITY public key (pbk)")
                    ProxyInputField(sid, { sid = it.trim() }, "Short ID (sid)")
                }
                if (security == "tls") ProxyInputField(pin, { pin = it.trim() }, "Pinned certificate SHA-256 (optional)")
                if (network != "tcp") {
                    ProxyInputField(hostH, { hostH = it.trim() }, "Host header (optional)")
                    ProxyInputField(path, { path = it.trim() }, if (network == "grpc") "Service name" else "Path")
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        NeonButton("Save & Test", {
            val o = XrayOutbound(
                protocol = proto, address = address, port = port.toIntOrNull() ?: 0, name = name,
                id = if (proto == XrayProtocol.VLESS || proto == XrayProtocol.VMESS) secret else "",
                password = if (proto == XrayProtocol.TROJAN || proto == XrayProtocol.SHADOWSOCKS) secret else "",
                method = if (proto == XrayProtocol.SHADOWSOCKS) method else if (proto == XrayProtocol.VMESS) "auto" else "",
                flow = if (proto == XrayProtocol.VLESS) flow else "", network = if (proto == XrayProtocol.SHADOWSOCKS) "tcp" else network,
                security = if (proto == XrayProtocol.SHADOWSOCKS) "none" else security, sni = sni, fingerprint = fp, publicKey = pbk, shortId = sid,
                host = hostH, path = if (network == "grpc") "" else path, serviceName = if (network == "grpc") path else "", pinnedPeerCertSha256 = pin,
            )
            val link = Links.toLink(o)
            val err = vm.linkError(link)
            if (err != null) { action.result = err to false; return@NeonButton }
            scope.launch {
                action.running = true; action.result = null
                val r = vm.importLinks(link)
                action.running = false; action.result = r.message to (r.working > 0)
            }
        }, Modifier.fillMaxWidth(), enabled = !action.running && address.isNotBlank() && port.isNotBlank() && secret.isNotBlank())
        Spacer(Modifier.height(Space.m))
        ResultCard(action, vm)
    }
}

@Composable
fun ChipRow(options: List<Pair<String, String>>, value: String, onSelect: (String) -> Unit) {
    val c = Tervyn.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, l) ->
            FilterChip(value == v, { onSelect(v) }, label = { Text(l, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.blue.copy(alpha = .3f), selectedLabelColor = c.cyan, labelColor = c.text2))
        }
    }
}

fun decodeQr(ctx: android.content.Context, uri: Uri): String? = runCatching {
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    val scaled = if (bmp.width > 1600) android.graphics.Bitmap.createScaledBitmap(bmp, 1600, bmp.height * 1600 / bmp.width, true) else bmp
    val px = IntArray(scaled.width * scaled.height)
    scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(scaled.width, scaled.height, px)))).text
}.getOrNull()


@Composable
fun SubscriptionsScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val subs by vm.subscriptions.collectAsState()
    val servers by vm.servers.collectAsState()
    val scope = rememberCoroutineScope()
    val action = remember { AsyncAction() }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    ScreenScaffold("Subscriptions & Servers", "Server lists from your provider", onBack, heroArt = com.tervyn.vpn.R.drawable.art_import, accentWord = "Servers") {
        GlassCard {
            ProxyInputField(name, { name = it.take(40) }, "Name (optional)")
            ProxyInputField(url, { url = it.trim() }, "Subscription URL (https://…)", keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(Space.s))
            NeonButton("Add & Update", {
                scope.launch {
                    action.running = true; action.result = null
                    val r = vm.addSubscription(name, url)
                    action.running = false; action.result = r.message to (r.added > 0)
                    if (r.added > 0) { name = ""; url = "" }
                }
            }, Modifier.fillMaxWidth(), enabled = url.startsWith("https://") && !action.running)
        }
        Spacer(Modifier.height(Space.m))
        ResultCard(action, vm)
        SectionHeader("Your subscriptions", Icons.Rounded.Cloud)
        if (subs.isEmpty()) Text("No subscriptions yet.", color = c.text2)
        subs.forEach { sub ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sub.name, color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text("${sub.count} servers · updated ${Format.ago(sub.lastUpdated)}", color = c.text2, style = MaterialTheme.typography.bodySmall)
                        sub.error?.let { Text(it, color = c.red, style = MaterialTheme.typography.bodySmall) }
                    }
                    IconButton(onClick = { scope.launch { action.running = true; val r = vm.refreshSubscription(sub); action.running = false; action.result = r.message to (r.added > 0) } }) {
                        Icon(Icons.Rounded.Refresh, "Update", tint = c.cyan)
                    }
                    IconButton(onClick = { vm.deleteSubscription(sub.id) }) { Icon(Icons.Rounded.Delete, "Delete", tint = c.red) }
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        SectionHeader("Your own servers", Icons.Rounded.Storage)
        val own = servers.filter { it.origin.name in setOf("CUSTOM", "SUBSCRIPTION", "INTEGRATION", "LIST_FILE", "PROXIO") }
        if (own.isEmpty()) Text("No servers yet. Add one from Add a server.", color = c.text2)
        own.take(100).forEach { srv ->
            SettingsRow(Icons.Rounded.Dns, srv.name, "${srv.protocolLabel} · ${if (srv.alive) "${srv.latencyMs} ms" else "not verified"}", { go(Routes.server(srv.id)) })
        }
        Spacer(Modifier.height(Space.m))
        NeonButton("Add a server", { go(Routes.PROTOCOLS) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, secondary = true)
        Spacer(Modifier.height(Space.s))
        NeonButton("Use my servers as source", { vm.setSourceMode(SourceMode.OWN) }, Modifier.fillMaxWidth(), secondary = true)
    }
}

@Composable
fun ListImportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val action = remember { AsyncAction() }
    var text by rememberSaveable { mutableStateOf("") }
    var proto by rememberSaveable { mutableStateOf(settings?.defaultImportProtocol ?: ProxyProtocol.SOCKS5) }
    var replace by rememberSaveable { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching { vm.readText(uri) }.onSuccess { text = it }.onFailure { vm.toast(it.message ?: "Cannot read file") } }
    }
    val preview = remember(text, proto) { if (text.isBlank()) null else ProxyList.parse(text, proto) }
    ScreenScaffold("Proxy List Import", "Import a .txt file or paste a list", onBack, heroArt = com.tervyn.vpn.R.drawable.art_import, accentWord = "Import") {
        NeonButton("Choose .txt file", { picker.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Description, secondary = true)
        Spacer(Modifier.height(Space.s))
        ProxyInputField(text.take(20_000), { text = it }, "…or paste proxies here", singleLine = false, minLines = 5,
            placeholder = "host:port\nhost:port:user:pass\nuser:pass@host:port\nsocks5://user:pass@host:port")
        Text("Formats: host:port · host:port:user:pass · user:pass@host:port · user:pass:host:port · http:// / socks5:// URLs", color = c.text3, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Space.s))
        GlassCard {
            Text("Protocol for lines without a scheme", color = c.text, style = MaterialTheme.typography.titleSmall)
            ChipRow(listOf(ProxyProtocol.SOCKS5.name to "SOCKS5", ProxyProtocol.HTTP.name to "HTTP"), proto.name) { proto = ProxyProtocol.valueOf(it) }
            ToggleSetting(null, "Replace the previous imported list", null, replace, { replace = it })
        }
        preview?.let { Text("${it.proxies.size} valid · ${it.invalidLines} invalid · ${it.duplicates} duplicates", color = c.cyan, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp)) }
        NeonButton("Save & Test", {
            scope.launch {
                action.running = true; action.result = null
                val r = vm.importProxyList(text, proto, replace)
                action.running = false; action.result = r.message to (r.added > 0)
            }
        }, Modifier.fillMaxWidth(), enabled = (preview?.proxies?.isNotEmpty() == true) && !action.running)
        Spacer(Modifier.height(Space.m))
        ResultCard(action, vm)
        Spacer(Modifier.height(Space.s))
        NeonButton("Use imported list as source", { vm.setSourceMode(SourceMode.LIST_FILE) }, Modifier.fillMaxWidth(), secondary = true)
    }
}

@Composable
fun IntegrationScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val scope = rememberCoroutineScope()
    val action = remember { AsyncAction() }
    var text by rememberSaveable { mutableStateOf("") }
    val preview = remember(text) { if (text.isBlank()) null else com.tervyn.vpn.core.ProxySnippet.parse(text) }
    ScreenScaffold("Proxy Integration", "Paste the example from Webshare, Bright Data, Oxylabs, IPRoyal…", onBack, heroArt = com.tervyn.vpn.R.drawable.art_protocol, accentWord = "Integration") {
        ProxyInputField(text, { text = it.take(20_000) }, "cURL, Python or Node.js example", singleLine = false, minLines = 6,
            placeholder = "curl -x http://user:pass@host:port https://ipinfo.io")
        Spacer(Modifier.height(Space.s))
        preview?.let { p ->
            if (p.proxies.isEmpty()) Text("No proxy detected yet.", color = c.text2, style = MaterialTheme.typography.bodySmall)
            else GlassCard {
                Text("Detected (${p.detected})", color = c.cyan, style = MaterialTheme.typography.titleSmall)
                p.proxies.forEach { Text("• $it", color = c.text, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(Space.s))
        NeonButton("Save & Test", {
            scope.launch {
                action.running = true; action.result = null
                val r = vm.importSnippet(text)
                action.running = false; action.result = r.message to (r.working > 0)
            }
        }, Modifier.fillMaxWidth(), enabled = preview?.proxies?.isNotEmpty() == true && !action.running, icon = Icons.Rounded.Security)
        Spacer(Modifier.height(Space.m))
        ResultCard(action, vm)
        Spacer(Modifier.height(Space.m))
        NotificationCard("Safe parsing", "The pasted code is only read as text to extract host, port, protocol, username and password. It is never executed.", Tone.INFO)
        Spacer(Modifier.height(Space.s))
        NeonButton("Use integrated proxies as source", { vm.setSourceMode(SourceMode.INTEGRATION) }, Modifier.fillMaxWidth(), secondary = true)
    }
}
