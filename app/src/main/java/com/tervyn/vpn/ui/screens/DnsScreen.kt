package com.tervyn.vpn.ui.screens

import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.net.DohForwarder
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.Banner
import com.tervyn.vpn.ui.components.Card
import com.tervyn.vpn.ui.components.IconBadge
import com.tervyn.vpn.ui.components.KeyValueRow
import com.tervyn.vpn.ui.components.Pill
import com.tervyn.vpn.ui.components.Screen
import com.tervyn.vpn.ui.components.SectionTitle
import com.tervyn.vpn.ui.components.TervynSwitch
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn

/**
 * Secure DNS: encrypted lookups (DNS-over-HTTPS) that run on their own, without a server connection.
 * Independent of the VPN: when a server is connected, names are resolved by that server instead.
 */
@Composable
fun DnsScreen(vm: MainViewModel, onBack: () -> Unit, enableSecureDns: () -> Unit) {
    val c = Tervyn.colors
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val status by vm.status.collectAsState()
    val context = LocalContext.current
    val privateDns = remember {
        if (Build.VERSION.SDK_INT >= 28) runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName
        }.getOrNull() else null
    }

    Screen(title = "Secure DNS", onBack = onBack) {
        Card(highlighted = status.isDnsOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Dns, if (status.isDnsOnly) c.success else c.accent2, 48.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Secure DNS", style = MaterialTheme.typography.titleLarge, color = c.text)
                        Spacer(Modifier.width(Space.s))
                        when {
                            status.isDnsOnly -> Pill("ACTIVE", c.success)
                            settings.secureDns && status.isActive -> Pill("PAUSED", c.warning)
                            settings.secureDns -> Pill("STARTING", c.accent2)
                            else -> Pill("OFF", c.text3)
                        }
                    }
                    Text("Encrypts every DNS lookup while the VPN is off", style = MaterialTheme.typography.bodySmall, color = c.text2)
                }
                Spacer(Modifier.width(Space.s))
                TervynSwitch(settings.secureDns, { on -> if (on) enableSecureDns() else vm.setSecureDns(false) })
            }
            if (status.isDnsOnly) {
                Spacer(Modifier.height(Space.m))
                KeyValueRow("Lookups answered", status.dnsQueries.toString())
                KeyValueRow("Resolver", DohForwarder.PRESETS.entries.firstOrNull { it.value == settings.dohUrl }?.key ?: settings.dohUrl)
            }
        }
        Spacer(Modifier.height(Space.m))
        if (status.isActive && settings.secureDns) {
            Banner("Paused while connected", "A server is connected, so it resolves names for you. Secure DNS resumes automatically when you disconnect.", Tone.INFO)
            Spacer(Modifier.height(Space.m))
        }
        if (privateDns != null) {
            Banner("Android Private DNS is set to \"$privateDns\"", "The system resolver bypasses Tervyn's Secure DNS. Set Private DNS to Automatic or Off in Android settings.", Tone.WARNING)
            Spacer(Modifier.height(Space.m))
        }

        SectionTitle("Resolver")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.s, vertical = Space.xs)) {
            DohForwarder.PRESETS.forEach { (name, url) ->
                val sel = settings.dohUrl == url
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { vm.setDohUrl(url) }.padding(horizontal = Space.s, vertical = Space.m)
                        .semantics { contentDescription = name + if (sel) ", selected" else "" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (sel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (sel) c.accent else c.text3)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(url.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = c.text3)
                    }
                }
            }
        }

        SectionTitle("How it works")
        Text(
            "Secure DNS and the VPN are separate. With Secure DNS on and no server connected, Tervyn creates a tunnel that carries only DNS: your apps' lookups are sent to the chosen resolver over HTTPS, so your network cannot read or alter them. All other traffic takes its normal path and your IP address does not change.\n\n" +
                "When you connect a server, only the server runs: names are resolved through the server and Secure DNS is not used until you disconnect again.",
            style = MaterialTheme.typography.bodySmall, color = c.text2,
        )
    }
}
