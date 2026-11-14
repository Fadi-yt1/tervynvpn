package com.tervyn.vpn.ui.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.data.SplitMode
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.Banner
import com.tervyn.vpn.ui.components.Card
import com.tervyn.vpn.ui.components.EmptyState
import com.tervyn.vpn.ui.components.Screen
import com.tervyn.vpn.ui.components.SectionTitle
import com.tervyn.vpn.ui.components.SegmentedControl
import com.tervyn.vpn.ui.components.TervynSwitch
import com.tervyn.vpn.ui.components.TervynTextField
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.theme.Radius
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- only my sites -------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SitesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val s = vm.settings.collectAsState().value ?: AppSettings()
    val domains by vm.domains.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val custom = domains.filter { it.presetId == null }
    val activePresets = domains.mapNotNull { it.presetId }.toSet()

    fun add() {
        val d = vm.addDomain(input)
        if (d == null) error = "Not a valid website" else { error = null; input = ""; vm.toast("$d added") }
    }

    Screen(title = "Routing", onBack = onBack) {
        SegmentedControl(VpnMode.entries.map { it.label }, s.mode.ordinal, { vm.setMode(VpnMode.entries[it]) })
        Spacer(Modifier.height(Space.s))
        Text(
            if (s.mode == VpnMode.ALL_APPS) "All apps and websites go through the server. The sites below are used when you switch to Only my sites."
            else "Only the websites and services below go through the server. Everything else connects directly, so your other apps keep full speed.",
            style = MaterialTheme.typography.bodySmall, color = c.text2,
        )

        SectionTitle("Add a website")
        Row(verticalAlignment = Alignment.Top) {
            TervynTextField(input, { input = it; error = null }, "Website", Modifier.weight(1f), placeholder = "example.com or a link", error = error)
            Spacer(Modifier.width(Space.s))
            Box(
                Modifier.padding(top = 6.dp).size(52.dp).clip(RoundedCornerShape(Radius.m)).background(if (input.isNotBlank()) c.accent else c.cardHigh)
                    .clickable(enabled = input.isNotBlank(), role = Role.Button, onClick = ::add).semantics { contentDescription = "Add website" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, tint = if (input.isNotBlank()) c.onAccent else c.text3) }
        }
        Spacer(Modifier.height(Space.xs))
        Text("Tip: share a link from your browser to Tervyn to add it here.", style = MaterialTheme.typography.bodySmall, color = c.text3)

        SectionTitle("Services")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Domains.presets.forEach { p ->
                val on = p.id in activePresets
                PresetChip(p.label, on) { vm.togglePreset(p, !on) }
            }
        }

        SectionTitle("Your websites")
        if (custom.isEmpty()) {
            Text("No websites added yet.", style = MaterialTheme.typography.bodyMedium, color = c.text3)
        } else {
            Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                custom.forEachIndexed { i, d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Language, null, tint = c.accent2, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.m))
                        Text(d.domain, style = MaterialTheme.typography.titleSmall, color = if (d.enabled) c.text else c.text3, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TervynSwitch(d.enabled, { vm.setDomainEnabled(d.domain, it) })
                        IconButton(onClick = { vm.removeDomain(d.domain) }, modifier = Modifier.semantics { contentDescription = "Remove ${d.domain}" }) { Icon(Icons.Rounded.Close, null, tint = c.text3) }
                    }
                    if (i < custom.lastIndex) com.tervyn.vpn.ui.components.Divider()
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        Banner("How detection works", "In Only my sites mode Tervyn answers DNS for the listed names with private addresses and routes those connections through the server. Telegram's published IP ranges are routed too, because the app does not use DNS.", Tone.INFO)
    }
}

@Composable
private fun PresetChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = Tervyn.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) c.accent.copy(alpha = .16f) else c.card)
            .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(50))
            .clickable(role = Role.Checkbox, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)
            .semantics { contentDescription = label + if (on) ", added" else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) { Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) c.text else c.text2)
    }
}

// ---- split tunnelling ----------------------------------------------------------------------

private data class AppEntry(val pkg: String, val label: String, val icon: android.graphics.Bitmap?)

@Composable
fun SplitTunnelScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val s = vm.settings.collectAsState().value ?: AppSettings()
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launchable = pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.toSet()
            launchable.filter { it != context.packageName }.mapNotNull { pkg ->
                runCatching {
                    val ai: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                    AppEntry(pkg, pm.getApplicationLabel(ai).toString(), runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96) }.getOrNull())
                }.getOrNull()
            }.sortedBy { it.label.lowercase() }
        }
    }
    val onlySelected = s.splitMode == SplitMode.ONLY_SELECTED
    val chosen = if (onlySelected) s.includedApps else s.excludedApps
    fun toggle(pkg: String) {
        val next = if (pkg in chosen) chosen - pkg else chosen + pkg
        if (onlySelected) vm.setIncludedApps(next) else vm.setExcludedApps(next)
    }
    val shown = apps?.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }

    Screen(title = "Split tunnelling", onBack = onBack, scrollable = false, gutter = 0.dp) {
        Column(Modifier.padding(horizontal = Space.gutter)) {
            SegmentedControl(listOf("Selected apps bypass", "Only selected apps"), if (onlySelected) 1 else 0, { vm.setSplitMode(if (it == 1) SplitMode.ONLY_SELECTED else SplitMode.BYPASS) })
            Spacer(Modifier.height(Space.s))
            Text(if (onlySelected) "Only the ticked apps use the VPN; all others connect directly." else "Ticked apps connect directly; everything else uses the VPN.",
                style = MaterialTheme.typography.bodySmall, color = c.text2)
            Spacer(Modifier.height(Space.m))
            TervynTextField(query, { query = it }, "Search apps", trailing = { Icon(Icons.Rounded.Search, null, tint = c.text3) })
            Spacer(Modifier.height(Space.s))
            Text("${chosen.size} selected", style = MaterialTheme.typography.labelMedium, color = c.text3)
            Spacer(Modifier.height(Space.s))
        }
        if (shown == null) { com.tervyn.vpn.ui.components.Loading("Loading apps…"); return@Screen }
        if (shown.isEmpty()) { EmptyState(Icons.Rounded.Search, "No apps found", "Try a different search."); return@Screen }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.gutter, vertical = Space.s)) {
            items(shown, key = { it.pkg }) { app ->
                val on = app.pkg in chosen
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).clickable(role = Role.Checkbox) { toggle(app.pkg) }.padding(vertical = Space.s, horizontal = Space.xs)
                        .semantics { contentDescription = app.label + if (on) ", selected" else "" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app.icon != null) Image(app.icon.asImageBitmap(), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                    else Spacer(Modifier.size(36.dp))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Checkbox(on, { toggle(app.pkg) }, colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.text3, checkmarkColor = c.onAccent))
                }
            }
        }
    }
}
