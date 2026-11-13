package com.tervyn.vpn.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.BuildConfig
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.ProxyInputField
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SettingsRow
import com.tervyn.vpn.ui.components.TervynLogo
import com.tervyn.vpn.ui.components.ToggleSetting
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HelpScreen(onBack: () -> Unit, go: (String) -> Unit) {
    ScreenScaffold("Help & Support", null, onBack, heroIcon = Icons.Rounded.SupportAgent, accentWord = "Support") {
        GlassCard {
            SettingsRow(Icons.Rounded.QuestionAnswer, "FAQ", "Answers to common questions", { go(Routes.FAQ) })
            SettingsRow(Icons.Rounded.BugReport, "Network diagnostics", "Find out why something doesn't work", { go(Routes.DIAGNOSTICS) })
            SettingsRow(Icons.Rounded.Speed, "Speed test", null, { go(Routes.SPEED) })
            SettingsRow(Icons.Rounded.Send, "Report a problem", "Prepare a report to share", { go(Routes.REPORT) })
        }
        Spacer(Modifier.height(Space.m))
        NotificationCard("Quick fixes", "• Connection fails: tap Connect again — free servers change often.\n• Sites not routed in Only My Sites: set Android Private DNS to Automatic, then reload the site.\n• VPN stops overnight: allow Tervyn to run unrestricted in Battery settings.\n• Some apps refuse VPNs: add them to App Exclusions.", Tone.INFO)
    }
}

private val faq = listOf(
    "Is Tervyn really free?" to "Yes. No account, ads or subscription. Free mode uses public proxies found on the internet; you can also add your own servers or Proxio.io proxies.",
    "Are free servers safe?" to "They are run by unknown people. Tervyn only accepts servers that pass an HTTPS test with full certificate verification, and HTTPS sites stay encrypted end-to-end, but the operator can see which sites you connect to. Don't use free servers for banking or sensitive logins.",
    "Why is the connection slow sometimes?" to "Free proxies are shared and short-lived. Auto-switch moves you to a server that is at least 40% faster (configurable) and replaces dead servers automatically. Your own servers are much faster and more reliable.",
    "What is 'Only My Sites'?" to "Only the websites and app presets you choose use the VPN. Tervyn recognises them by their DNS names; everything else uses your normal connection. It requires Android Private DNS to be Automatic or Off.",
    "Does Tervyn support UDP, games or calls?" to "HTTP and SOCKS proxies carry TCP only, so UDP (including QUIC and many voice/game protocols) is refused rather than leaked. Browsers and most apps fall back to TCP automatically.",
    "Does it leak DNS or IPv6?" to "In All Apps mode all DNS is answered locally and resolved at the proxy (or via encrypted DNS through the tunnel), and IPv6 is captured and dropped. You can check with Network Diagnostics and Check My IP.",
    "Which protocols can I import?" to "VLESS (TCP, REALITY, Vision, WebSocket, gRPC, XHTTP, HTTPUpgrade), VMess, Trojan, Shadowsocks (incl. 2022 ciphers), SOCKS5 and HTTP — via share links, QR images, subscriptions or manual configuration. They run through the official Xray-core.",
    "How do I use Always-on VPN / kill switch?" to "Open Settings → Connection → Always-on VPN. In Android's VPN settings tap the gear next to Tervyn and enable 'Always-on VPN' and optionally 'Block connections without VPN'.",
    "Does Tervyn keep logs?" to "Connection history is stored only on your phone and can be cleared at any time. Nothing is sent to Tervyn. Third-party proxy operators may log — Tervyn cannot verify their policies.",
    "Can I add a website from Chrome?" to "Yes: in Chrome tap Share → Tervyn VPN, confirm, and the site (with subdomains) is added to Only My Sites.",
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    val c = Tervyn.colors
    ScreenScaffold("FAQ", null, onBack, heroIcon = Icons.Rounded.QuestionAnswer, accentWord = "FAQ") {
        faq.forEach { (q, a) ->
            var open by rememberSaveable(q) { mutableStateOf(false) }
            GlassCard(Modifier.animateContentSize(), onClick = { open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q, color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.text2)
                }
                if (open) { Spacer(Modifier.height(Space.s)); Text(a, color = c.text2, style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(Space.s))
        }
    }
}

@Composable
fun ReportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()
    val checks by vm.diagnostics.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var includeDiag by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { if (checks.isEmpty()) vm.runDiagnostics() }
    ScreenScaffold("Report a Problem", "Nothing is sent automatically", onBack, heroIcon = Icons.Rounded.BugReport, accentWord = "Problem") {
        ProxyInputField(text, { text = it.take(4000) }, "What happened?", singleLine = false, minLines = 5)
        Spacer(Modifier.height(Space.s))
        GlassCard {
            ToggleSetting(Icons.Rounded.BugReport, "Include technical details", "App version, Android version, status and diagnostic results. No IP addresses, server credentials or websites.", includeDiag, { includeDiag = it })
        }
        Spacer(Modifier.height(Space.m))
        NeonButton("Share report…", {
            val body = buildString {
                appendLine(text.trim()); appendLine()
                if (includeDiag) {
                    appendLine("--- Tervyn VPN ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, ABI ${Build.SUPPORTED_ABIS.firstOrNull()}")
                    appendLine("State: ${status.phase} · mode ${settings?.mode} · source ${settings?.sourceMode} · protocol ${status.server?.protocolLabel ?: "-"}")
                    status.error?.let { appendLine("Last error: $it") }
                    checks.forEach { appendLine("[${when (it.ok) { true -> "OK"; false -> "FAIL"; null -> "INFO" }}] ${it.name}") }
                }
            }
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Tervyn VPN problem report").putExtra(Intent.EXTRA_TEXT, body)
            ctx.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Send, enabled = text.isNotBlank())
        Spacer(Modifier.height(Space.m))
        Text("You choose where the report goes (email, messenger, issue tracker). Review it before sending.", color = c.text3, style = MaterialTheme.typography.labelSmall)
    }
}


@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val c = Tervyn.colors
    val sections = listOf(
        "Summary" to "Tervyn VPN has no accounts, no advertising and no analytics. The app does not send your data to the Tervyn developers.",
        "Data on your device" to "Settings, your servers, websites for Only My Sites and connection history (time, duration, server name, country, protocol and data volume) are stored locally. Credentials are encrypted with the Android Keystore. You can clear history and settings at any time; uninstalling removes everything. Android backups of this data are disabled.",
        "Network requests Tervyn makes" to "• Downloading public proxy lists (raw.githubusercontent.com and any custom list URLs you add).\n• Testing servers via https://www.cloudflare.com/cdn-cgi/trace (reveals the server's IP to Cloudflare).\n• Encrypted DNS (DNS-over-HTTPS) to the resolver you choose.\n• Check My IP: the lookup service you configure (default ipinfo.io), sent through the VPN when connected.\n• Speed tests: speed.cloudflare.com.\n• Proxio.io API and subscription URLs, only if you configure them.",
        "Your traffic" to "When connected, your traffic passes through the server you use. Free public proxies are operated by unknown third parties who may log or inspect unencrypted traffic and connection metadata. Tervyn cannot verify any third-party logging policy and does not guarantee anonymity.",
        "Permissions" to "VPN (to create the tunnel), notifications (connection status), network state, boot completed (optional auto-connect), biometrics (optional app lock). No location, contacts, storage or accessibility access.",
        "Children" to "Tervyn is not directed at children and does not knowingly collect any personal data.",
        "Changes" to "Changes to this policy ship with app updates and are shown on this screen.",
    )
    ScreenScaffold("Privacy Policy", null, onBack, heroIcon = Icons.Rounded.Policy, accentWord = "Policy") {
        sections.forEach { (t, b) ->
            GlassCard {
                Text(t, color = c.text, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(b, color = c.text2, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Space.s))
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    var showMpl by remember { mutableStateOf(false) }
    val mpl by produceState<String?>(null, showMpl) {
        if (showMpl) value = withContext(Dispatchers.IO) { ctx.assets.open("licenses/xray-core-MPL-2.0.txt").bufferedReader().readText() }
    }
    val libs = listOf(
        "Xray-core v26.9.30" to "Mozilla Public License 2.0 · © Project X. Unmodified official source, built for Android. Source: https://github.com/XTLS/Xray-core",
        "AndroidX / Jetpack Compose, Room, DataStore, WorkManager, Biometric" to "Apache License 2.0 · © The Android Open Source Project",
        "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0 · © JetBrains s.r.o.",
        "OkHttp" to "Apache License 2.0 · © Square, Inc.",
        "ZXing (core)" to "Apache License 2.0 · © ZXing authors",
        "Material Icons" to "Apache License 2.0 · © Google",
    )
    ScreenScaffold("Open Source Licenses", null, onBack, heroIcon = Icons.Rounded.Gavel, accentWord = "Licenses") {
        libs.forEach { (n, l) ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Code, null, tint = c.cyan); Spacer(Modifier.width(Space.m))
                    Column { Text(n, color = c.text, style = MaterialTheme.typography.titleSmall); Text(l, color = c.text2, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        NeonButton(if (showMpl) "Hide MPL-2.0 text" else "Show Xray-core license (MPL-2.0)", { showMpl = !showMpl }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Article, secondary = true)
        Spacer(Modifier.height(Space.s))
        NeonButton("Xray-core source code", {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/XTLS/Xray-core/tree/v26.9.30")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, Modifier.fillMaxWidth(), secondary = true)
        if (showMpl) { Spacer(Modifier.height(Space.s)); Text(mpl ?: "Loading…", color = c.text2, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(Space.s))
        Text("Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0", color = c.text3, style = MaterialTheme.typography.labelSmall)
    }
}
