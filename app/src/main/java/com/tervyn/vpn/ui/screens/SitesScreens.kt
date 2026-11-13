package com.tervyn.vpn.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.AppTile
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.LoadingIndicator
import com.tervyn.vpn.ui.components.ModeToggle
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.ProxyInputField
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SectionHeader
import com.tervyn.vpn.ui.components.SettingsRow
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SitesScreen(vm: MainViewModel, go: (String) -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val domains by vm.domains.collectAsState()
    val status by vm.status.collectAsState()
    val s = settings ?: return
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val presetIds = domains.mapNotNull { it.presetId }.toSet()
    ScreenScaffold("My Sites", "Choose what goes through the VPN", onBack = null) {
        ModeToggle(s.mode, { vm.setMode(it) })
        Spacer(Modifier.height(Space.m))
        GlassCard(highlight = s.mode == VpnMode.ONLY_MY_SITES) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = c.cyan, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(Space.m))
                Column {
                    Text("Only My Sites", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("Route only the sites you choose through VPN. Everything else stays on your normal connection.", style = MaterialTheme.typography.bodySmall, color = c.text2)
                }
            }
        }
        SectionHeader("App Presets", Icons.Rounded.Star) { Text("Tap to add", color = c.cyan, style = MaterialTheme.typography.labelMedium) }
        Domains.presets.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { p -> AppTile(p.label, presetEmoji[p.id] ?: "🌐", p.id in presetIds, { vm.togglePreset(p, p.id !in presetIds) }, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        SectionHeader("My Sites", Icons.Rounded.Language) { Text("${domains.count { it.enabled }} active", color = c.text2, style = MaterialTheme.typography.labelMedium) }
        ProxyInputField(input, { input = it; error = null }, "Website (e.g. example.com or a link)", keyboardType = KeyboardType.Uri, error = error)
        Spacer(Modifier.height(Space.s))
        NeonButton("Add Website", {
            val d = vm.addDomain(input)
            if (d == null) error = "Not a valid website" else { input = ""; vm.toast("$d added (subdomains included)") }
        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, enabled = input.isNotBlank())
        Text("Tip: in Chrome tap Share → Tervyn VPN to add the current site.", color = c.text3, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(Space.s))
        val custom = domains.filter { it.presetId == null }
        if (custom.isEmpty() && presetIds.isEmpty()) Text("No websites yet.", color = c.text2, style = MaterialTheme.typography.bodyMedium)
        custom.forEach { d ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Language, null, tint = c.cyan)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(d.domain, color = c.text, style = MaterialTheme.typography.titleSmall)
                        Text("Includes all subdomains", color = c.text2, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(d.enabled, { vm.setDomainEnabled(d.domain, it) }, colors = SwitchDefaults.colors(checkedTrackColor = c.blue))
                    IconButton(onClick = { vm.removeDomain(d.domain) }) { Icon(Icons.Rounded.Delete, "Remove ${d.domain}", tint = c.text2) }
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        if (presetIds.isNotEmpty()) {
            Text("Presets: " + Domains.presets.filter { it.id in presetIds }.joinToString { it.label }, color = c.text2, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(Space.m))
        if (s.mode == VpnMode.ONLY_MY_SITES && status.isConnected) {
            NotificationCard("Connected to Only My Sites", "Your selected sites are now routed through Tervyn VPN.", Tone.SUCCESS)
            Spacer(Modifier.height(Space.s))
        }
        GlassCard {
            SettingsRow(Icons.Rounded.CallSplit, "App exclusions", "Apps that always bypass the VPN", { go(Routes.SPLIT) })
        }
        Spacer(Modifier.height(Space.m))
        NotificationCard("How Only My Sites works",
            "Tervyn answers DNS lookups for your sites with private addresses and sends those connections through the VPN by name. " +
                "Limitations: apps must use Android's DNS (if Private DNS is set to a specific provider, sites are not detected — use Automatic or Off); " +
                "apps that connect to hard-coded IPs are only covered when the preset lists their IP ranges (e.g. Telegram); " +
                "sites already opened may need to be reloaded; QUIC/UDP to your sites is refused so apps fall back to TCP.",
            Tone.INFO)
    }
}


