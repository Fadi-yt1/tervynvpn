package com.tervyn.vpn.ui.screens

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tervyn.vpn.BuildConfig
import com.tervyn.vpn.R
import com.tervyn.vpn.data.AppTheme
import com.tervyn.vpn.net.DohForwarder
import com.tervyn.vpn.security.PinHasher
import com.tervyn.vpn.services.TervynTileService
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.CountryFlag
import com.tervyn.vpn.ui.components.GlassCard
import com.tervyn.vpn.ui.components.NeonButton
import com.tervyn.vpn.ui.components.NotificationCard
import com.tervyn.vpn.ui.components.ProxyInputField
import com.tervyn.vpn.ui.components.ScreenScaffold
import com.tervyn.vpn.ui.components.SectionHeader
import com.tervyn.vpn.ui.components.SettingsRow
import com.tervyn.vpn.ui.components.TervynLogo
import com.tervyn.vpn.ui.components.ToggleSetting
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.components.phaseColor
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.showBiometric
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.utils.Format
import kotlinx.coroutines.launch

fun openVpnSettings(ctx: Context) = runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

@Composable
private fun Confirm(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Tervyn.colors
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.card, title = { Text(title, color = c.text) }, text = { Text(text, color = c.text2) },
        confirmButton = { TextButton({ onConfirm(); onDismiss() }) { Text("Confirm", color = c.red) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = c.text2) } })
}

@Composable
fun SettingsScreen(vm: MainViewModel, go: (String) -> Unit, connect: () -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    var exportWithServers by rememberSaveable { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { vm.exportConfig(uri, exportWithServers) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch { vm.importConfig(uri) } }

    ScreenScaffold("Settings", "Customize your VPN experience", onBack = null) {
        GlassCard(highlight = true, onClick = { if (status.isConnected) go(Routes.CONNECTED) else if (!status.isActive) connect() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = phaseColor(status.phase), modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("VPN Status", color = c.text, style = MaterialTheme.typography.titleMedium)
                    Text(status.phase.label, color = phaseColor(status.phase), style = MaterialTheme.typography.titleSmall)
                    if (status.isConnected) Text("IP: ${status.exitIp ?: "—"} • ${Format.countryName(status.countryCode)}", color = c.text2, style = MaterialTheme.typography.bodySmall)
                    else if (!status.isActive) Text("Tap to connect", color = c.text2, style = MaterialTheme.typography.bodySmall)
                }
                if (status.isConnected) CountryFlag(status.countryCode, 32.dp)
            }
        }
        SectionHeader("VPN Preferences", Icons.Rounded.Bolt)
        GlassCard {
            ToggleSetting(Icons.Rounded.Bolt, "Auto-Switch to Fastest Server", "Switch when a faster server is available", s.autoSwitch, { vm.setAutoSwitch(it) }, tint = c.amber)
            SettingsRow(Icons.Rounded.Language, "Choose Country", s.preferredCountry?.let { Format.countryName(it) } ?: "Fastest Available", { go(Routes.COUNTRIES) }, valueColor = c.cyan)
            SettingsRow(Icons.Rounded.Apps, "VPN Mode", s.mode.label, { go(Routes.SITES) }, valueColor = c.cyan)
            SettingsRow(Icons.Rounded.Speed, "Auto-Switch Configuration", "Threshold ${s.switchThresholdPct}% · checks every ${s.healthCheckSec}s", { go(Routes.AUTO_SWITCH) })
            SettingsRow(Icons.Rounded.CallSplit, "App Exclusions", "${s.excludedApps.size} apps bypass the VPN", { go(Routes.SPLIT) })
            SettingsRow(Icons.Rounded.Dns, "DNS Settings", DohForwarder.PRESETS.entries.firstOrNull { it.value == s.dohUrl }?.key ?: s.dohUrl, { go(Routes.DNS) })
        }
        SectionHeader("Connection", Icons.Rounded.PowerSettingsNew)
        GlassCard {
            ToggleSetting(Icons.Rounded.PowerSettingsNew, "Connect when Tervyn opens", null, s.autoConnectOnLaunch, { v -> vm.updateSettings { it.copy(autoConnectOnLaunch = v) } })
            ToggleSetting(Icons.Rounded.RestartAlt, "Connect after reboot", "Requires the VPN permission to be granted", s.autoConnectOnBoot, { v -> vm.updateSettings { it.copy(autoConnectOnBoot = v) } })
            ToggleSetting(Icons.Rounded.WifiTethering, "Reconnect on network change", "Wi-Fi ↔ mobile data", s.reconnectOnNetworkChange, { v -> vm.updateSettings { it.copy(reconnectOnNetworkChange = v) } })
            SettingsRow(Icons.Rounded.AllInclusive, "Always-on VPN", "Set in Android VPN settings (gear next to Tervyn)", { openVpnSettings(ctx) })
            SettingsRow(Icons.Rounded.Block, "Block connections without VPN", "Kill switch — enable in Android VPN settings", { openVpnSettings(ctx) })
        }
        SectionHeader("Server Source", Icons.Rounded.Storage)
        GlassCard { SettingsRow(Icons.Rounded.Storage, "Server Source", s.sourceMode.label, { go(Routes.SOURCES) }, valueColor = c.cyan) }
        SectionHeader("Privacy & Security", Icons.Rounded.Security)
        GlassCard {
            SettingsRow(Icons.Rounded.VerifiedUser, "Check My IP", "View your current IP and country", { go(Routes.IP) })
            SettingsRow(Icons.Rounded.Policy, "Privacy & Security Center", null, { go(Routes.PRIVACY) })
            SettingsRow(Icons.Rounded.Lock, "App Lock & Biometrics", if (s.appLock) "On" else "Off", { go(Routes.APP_LOCK) })
            SettingsRow(Icons.Rounded.Shield, "App Permissions", null, { go(Routes.PERMISSIONS) })
        }
        SectionHeader("Monitoring", Icons.Rounded.Dashboard)
        GlassCard {
            SettingsRow(Icons.Rounded.Speed, "Speed Test", null, { go(Routes.SPEED) })
            SettingsRow(Icons.Rounded.Dashboard, "Connection Statistics", null, { go(Routes.STATS) })
            SettingsRow(Icons.Rounded.History, "Connection History & Analytics", null, { go(Routes.HISTORY) })
            SettingsRow(Icons.Rounded.BugReport, "Network Diagnostics", null, { go(Routes.DIAGNOSTICS) })
            SettingsRow(Icons.Rounded.NotificationsActive, "VPN Connection Notifications", null, { go(Routes.NOTIFICATIONS) })
            SettingsRow(Icons.Rounded.Widgets, "Quick Settings & Notifications", null, { go(Routes.QUICK) })
        }
        SectionHeader("Appearance & Data", Icons.Rounded.Palette)
        GlassCard {
            SettingsRow(Icons.Rounded.Palette, "Theme", s.theme.label, { go(Routes.THEME) })
            ToggleSetting(Icons.Rounded.Storage, "Include servers in export", "Exports credentials — keep the file private", exportWithServers, { exportWithServers = it })
            SettingsRow(Icons.Rounded.Upload, "Export configuration", null, { exporter.launch("tervyn-config.json") })
            SettingsRow(Icons.Rounded.Download, "Import configuration", null, { importer.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
            SettingsRow(Icons.Rounded.CleaningServices, "Clear cache", "Free server cache and temp files", { confirm = "cache" })
            SettingsRow(Icons.Rounded.DeleteSweep, "Clear history", null, { confirm = "history" })
            SettingsRow(Icons.Rounded.RestartAlt, "Reset settings", null, { confirm = "reset" }, tint = c.red)
        }
        SectionHeader("About", Icons.Rounded.Info)
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TervynLogo(44.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Tervyn VPN", color = c.text, style = MaterialTheme.typography.titleSmall)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = c.text2, style = MaterialTheme.typography.bodySmall)
                }
            }
            SettingsRow(Icons.AutoMirrored.Rounded.Help, "Help & Support", null, { go(Routes.HELP) })
            SettingsRow(Icons.Rounded.Info, "About Tervyn", null, { go(Routes.ABOUT) })
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeonButton("Report a Problem", { go(Routes.REPORT) }, Modifier.weight(1f), icon = Icons.Rounded.BugReport, secondary = true)
            NeonButton("Rate Our App", {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { vm.toast("No app store available") }
            }, Modifier.weight(1f), icon = Icons.Rounded.Star)
        }
    }
    when (confirm) {
        "cache" -> Confirm("Clear cache?", "Removes cached free servers and temporary files. Your own servers are kept.", { vm.clearCache() }) { confirm = null }
        "history" -> Confirm("Clear history?", "Deletes connection history, speed tests and events stored on this phone.", { vm.clearHistory() }) { confirm = null }
        "reset" -> Confirm("Reset settings?", "Restores default settings and removes the app lock PIN. Servers and websites are kept.", { vm.resetSettings() }) { confirm = null }
    }
}



@Composable
fun PermissionsScreen(vm: MainViewModel, onBack: () -> Unit, connect: () -> Unit) {
    val c = Tervyn.colors
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val vpnGranted = remember(refresh) { vm.c.controller.needsPermission() == null }
    val notifGranted = remember(refresh) { Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val pm = ctx.getSystemService(PowerManager::class.java)
    val battery = remember(refresh) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    ScreenScaffold("App Permissions & VPN Protection", "Tervyn only asks for what a VPN needs.", onBack, heroIcon = Icons.Rounded.Lock, accentWord = "VPN Protection") {
        GlassCard {
            SettingsRow(Icons.Rounded.Shield, "VPN connection", if (vpnGranted) "Granted" else "Requested when you first connect", if (!vpnGranted) connect else null,
                valueColor = if (vpnGranted) c.green else c.amber)
            SettingsRow(Icons.Rounded.Notifications, "Notifications", if (notifGranted) "Granted" else "Needed to show the connection status",
                if (!notifGranted && Build.VERSION.SDK_INT >= 33) ({ notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) else null, valueColor = if (notifGranted) c.green else c.amber)
            SettingsRow(Icons.Rounded.BatteryAlert, "Battery optimisation", if (battery) "Unrestricted" else "Optimised — the VPN may be stopped during deep sleep", {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, valueColor = if (battery) c.green else c.amber)
            SettingsRow(Icons.Rounded.Fingerprint, "Biometrics", "Used only if you enable App Lock", null)
        }
        Spacer(Modifier.height(Space.m))
        NotificationCard("Not requested", "No location, contacts, camera, microphone, storage, accessibility or account permissions. The app list in App Exclusions uses Android's launcher query and does not read app data.", Tone.SUCCESS)
        Spacer(Modifier.height(Space.s))
        NeonButton("Open app settings", {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, Modifier.fillMaxWidth(), secondary = true)
        Spacer(Modifier.height(Space.s))
        NeonButton("Refresh", { refresh++ }, Modifier.fillMaxWidth(), secondary = true)
    }
}

@Composable
fun QuickSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    ScreenScaffold("Quick Settings & Notifications", null, onBack, heroIcon = Icons.Rounded.Widgets, accentWord = "Notifications") {
        GlassCard {
            SettingsRow(Icons.Rounded.Widgets, "Add Quick Settings tile", "Toggle Tervyn from the notification shade", {
                if (Build.VERSION.SDK_INT >= 33) {
                    val sbm = ctx.getSystemService(StatusBarManager::class.java)
                    sbm.requestAddTileService(ComponentName(ctx, TervynTileService::class.java), "Tervyn VPN",
                        Icon.createWithResource(ctx, R.drawable.ic_stat_tervyn), ctx.mainExecutor) { r -> vm.toast(if (r == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || r == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) "Tile added" else "Tile not added") }
                } else vm.toast("Swipe down twice, tap the pencil and drag \"Tervyn VPN\" into your tiles")
            })
            ToggleSetting(Icons.Rounded.Speed, "Show live speed in notification", null, s.showSpeedInNotification, { v -> vm.updateSettings { it.copy(showSpeedInNotification = v) } })
            ToggleSetting(Icons.Rounded.Vibration, "Vibration", "For connection alerts", s.vibration, { v -> vm.updateSettings { it.copy(vibration = v) } })
            ToggleSetting(Icons.Rounded.VolumeUp, "Notification sound", "For connection alerts", s.sound, { v -> vm.updateSettings { it.copy(sound = v) } })
            SettingsRow(Icons.Rounded.Notifications, "System notification settings", null, {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        Spacer(Modifier.height(Space.m))
        NotificationCard("Persistent notification", "Android requires a visible notification while a VPN runs. It shows status, live speed and a Disconnect button.", Tone.INFO)
    }
}



@Composable
fun ThemeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    ScreenScaffold("Theme", "Tervyn is designed for dark mode", onBack, heroIcon = Icons.Rounded.Palette, accentWord = "Theme") {
        GlassCard {
            AppTheme.entries.forEach { t ->
                SettingsRow(Icons.Rounded.Palette, t.label, null, { vm.updateSettings { it.copy(theme = t) } }) {
                    if (s.theme == t) Icon(Icons.Rounded.VerifiedUser, "Selected", tint = c.green)
                }
            }
        }
    }
}
