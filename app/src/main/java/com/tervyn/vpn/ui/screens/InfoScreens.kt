package com.tervyn.vpn.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.BuildConfig
import com.tervyn.vpn.core.BuiltinServers
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.BrandMark
import com.tervyn.vpn.ui.components.Card
import com.tervyn.vpn.ui.components.Divider
import com.tervyn.vpn.ui.components.KeyValueRow
import com.tervyn.vpn.ui.components.Screen
import com.tervyn.vpn.ui.components.SectionTitle
import com.tervyn.vpn.ui.components.SettingRow
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutScreen(vm: MainViewModel, onBack: () -> Unit, go: (String) -> Unit) {
    val c = Tervyn.colors
    val xray = remember { vm.c.xray.version() }
    Screen(title = "About", onBack = onBack) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.l), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(64.dp)
            Spacer(Modifier.width(Space.l))
            Column {
                Text("Tervyn VPN", style = MaterialTheme.typography.headlineMedium, color = c.text)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium, color = c.text2)
            }
        }
        Text(
            "Tervyn is a free VPN client with no account, no ads and no tracking. It ships ${BuiltinServers.all.size} verified community servers in ${BuiltinServers.all.size} countries, " +
                "supports your own VLESS, VMess, Trojan, Shadowsocks, Hysteria2, SOCKS5 and HTTP servers, and includes Secure DNS that works on its own.",
            style = MaterialTheme.typography.bodyMedium, color = c.text2,
        )
        SectionTitle("Components")
        Card {
            KeyValueRow("Xray-core", xray ?: "Not available on this device")
            KeyValueRow("Engine", "Tervyn user-space TCP/IP stack")
            KeyValueRow("Encrypted DNS", "DNS-over-HTTPS (RFC 8484)")
            KeyValueRow("Minimum Android", "8.0 (API 26)")
        }
        SectionTitle("Legal")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SettingRow(Icons.Rounded.PrivacyTip, "Privacy", "What the app does and does not collect", onClick = { go(Routes.PRIVACY) })
            Divider()
            SettingRow(Icons.Rounded.Code, "Open-source licences", "Xray-core (MPL-2.0), Manrope (OFL-1.1)", onClick = { go(Routes.LICENSES) })
        }
        Spacer(Modifier.height(Space.l))
        Text(
            "Honest limits: the tunnel carries TCP (UDP-only apps and IPv6 are not relayed), and free community servers can see unencrypted traffic and may disappear at any time. See Diagnostics for the state of your connection.",
            style = MaterialTheme.typography.bodySmall, color = c.text3,
        )
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val c = Tervyn.colors
    Screen(title = "Privacy", onBack = onBack) {
        val sections = listOf(
            "No account, no analytics" to "Tervyn has no sign-up, no advertising SDK and no analytics. The app never sends usage data anywhere.",
            "What leaves your device" to "When you connect, your traffic goes to the server you chose (or the fastest verified one). To verify a server and to show your IP, Tervyn fetches www.cloudflare.com/cdn-cgi/trace through that server; Check my IP additionally asks ipinfo.io about the address shown. Server lists are downloaded from the Tervyn list URLs when a built-in slot needs a replacement. Secure DNS sends lookups to the resolver you selected.",
            "What stays on your device" to "Servers, subscriptions, websites, settings, history and the app-lock PIN hash are stored locally only. Server credentials are encrypted with a key in the Android Keystore. Backups are files you create yourself.",
            "Servers" to "Built-in servers are free community servers run by third parties. They can see the destinations you connect to and any traffic that is not itself encrypted (plain HTTP). Use HTTPS websites, as you would on any network.",
            "Permissions" to "VPN (to create the tunnel), notifications (status and events), network state, run at startup (optional auto-connect) and biometrics (optional app lock). Nothing else.",
        )
        sections.forEach { (title, body) ->
            Card(Modifier.padding(bottom = Space.m)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(Space.xs))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = c.text2)
            }
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val c = Tervyn.colors
    val context = LocalContext.current
    var texts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        texts = withContext(Dispatchers.IO) {
            listOf("Xray-core — Mozilla Public License 2.0" to "licenses/xray-core-MPL-2.0.txt", "Manrope — SIL Open Font License 1.1" to "licenses/manrope-OFL-1.1.txt")
                .map { (name, path) -> name to runCatching { context.assets.open(path).bufferedReader().readText() }.getOrDefault("Licence text not bundled.") }
        }
    }
    Screen(title = "Open-source licences", onBack = onBack) {
        Text("Tervyn bundles Xray-core (XTLS project) as an executable and the Manrope typeface. Both are used unmodified under their licences below. Android Jetpack, Kotlin and OkHttp are used under the Apache License 2.0.",
            style = MaterialTheme.typography.bodyMedium, color = c.text2)
        texts.forEach { (name, body) ->
            SectionTitle(name)
            Card { Text(body, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = c.text2) }
        }
    }
}
