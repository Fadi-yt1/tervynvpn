package com.tervyn.vpn.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.tervyn.vpn.BuildConfig
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.data.AppTheme
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.net.DohForwarder
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.components.Banner
import com.tervyn.vpn.ui.components.Card
import com.tervyn.vpn.ui.components.Divider
import com.tervyn.vpn.ui.components.PrimaryButton
import com.tervyn.vpn.ui.components.Screen
import com.tervyn.vpn.ui.components.SecondaryButton
import com.tervyn.vpn.ui.components.SectionTitle
import com.tervyn.vpn.ui.components.SegmentedControl
import com.tervyn.vpn.ui.components.SettingRow
import com.tervyn.vpn.ui.components.SwitchRow
import com.tervyn.vpn.ui.components.TervynTextField
import com.tervyn.vpn.ui.components.Tone
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.theme.Space
import com.tervyn.vpn.ui.theme.Tervyn
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, go: (String) -> Unit) {
    val c = Tervyn.colors
    val s = vm.settings.collectAsState().value ?: AppSettings()
    val status by vm.status.collectAsState()
    val domains by vm.domains.collectAsState()
    val context = LocalContext.current

    Screen(title = "Settings", onBack = null) {
        SectionTitle("Connection")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SwitchRow(Icons.Rounded.Bolt, "Fastest available", "Pick the fastest verified server and switch when a clearly faster one appears",
                s.autoSwitch) { on -> vm.setAutoSwitch(on) }
            Divider()
            SwitchRow(Icons.Rounded.Wifi, "Reconnect on network change", "Re-verify the server when Wi-Fi or mobile data changes", s.reconnectOnNetworkChange) { on -> vm.updateSettings { it.copy(reconnectOnNetworkChange = on) } }
            Divider()
            SwitchRow(Icons.Rounded.PowerSettingsNew, "Connect when the app opens", null, s.autoConnectOnLaunch) { on -> vm.updateSettings { it.copy(autoConnectOnLaunch = on) } }
            Divider()
            SwitchRow(Icons.Rounded.RestartAlt, "Connect after device restart", "Needs the VPN permission to be granted once", s.autoConnectOnBoot) { on -> vm.updateSettings { it.copy(autoConnectOnBoot = on) } }
            Divider()
            SettingRow(Icons.Rounded.Security, "Always-on VPN & kill switch", "Managed by Android in system settings",
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { vm.toast("VPN settings are not available on this device") } },
                subtitleColor = if (status.alwaysOn == true) c.success else null)
        }

        SectionTitle("Privacy")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SettingRow(Icons.Rounded.Dns, "Secure DNS", (if (s.secureDns) "On · " else "Off · ") + (DohForwarder.PRESETS.entries.firstOrNull { it.value == s.dohUrl }?.key ?: "Custom"),
                onClick = { go(Routes.DNS) }, subtitleColor = if (status.isDnsOnly) c.success else null)
            Divider()
            SettingRow(Icons.Rounded.Language, "Routing mode", if (s.mode == VpnMode.ALL_APPS) "All apps" else "Only my sites · ${domains.count { it.enabled }} sites", onClick = { go(Routes.SITES) })
            Divider()
            SettingRow(Icons.Rounded.CallSplit, "Split tunnelling", when {
                s.includedApps.isNotEmpty() && s.splitMode == com.tervyn.vpn.data.SplitMode.ONLY_SELECTED -> "Only ${s.includedApps.size} apps use the VPN"
                s.excludedApps.isNotEmpty() -> "${s.excludedApps.size} apps bypass the VPN"
                else -> "All apps use the VPN"
            }, onClick = { go(Routes.SPLIT) })
            Divider()
            SettingRow(Icons.Rounded.Lock, "App lock", if (s.appLock) (if (s.biometric) "PIN + biometrics" else "PIN") else "Off", onClick = { go(Routes.APP_LOCK) })
        }

        SectionTitle("Notifications")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SettingRow(Icons.Rounded.Notifications, "Notifications", if (s.notificationsEnabled) "Connection events on" else "Connection events off", onClick = { go(Routes.NOTIFICATIONS) })
        }

        SectionTitle("Appearance")
        SegmentedControl(AppTheme.entries.map { it.label }, s.theme.ordinal, { vm.updateSettings { st -> st.copy(theme = AppTheme.entries[it]) } })

        SectionTitle("Tools")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SettingRow(Icons.Rounded.Public, "Check my IP", "See the address and location websites see", onClick = { go(Routes.IP) })
            Divider()
            SettingRow(Icons.Rounded.Speed, "Speed test", "Measure the connection, direct or through the server", onClick = { go(Routes.SPEED) })
            Divider()
            SettingRow(Icons.Rounded.MonitorHeart, "Diagnostics", "Check internet, DNS, VPN permission and Xray", onClick = { go(Routes.DIAGNOSTICS) })
            Divider()
            SettingRow(Icons.Rounded.History, "Connection history", null, onClick = { go(Routes.HISTORY) })
        }

        SectionTitle("Data")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SettingRow(Icons.Rounded.Backup, "Backup & restore", "Export or import your settings, websites and servers", onClick = { go(Routes.BACKUP) })
            Divider()
            SettingRow(Icons.Rounded.Info, "About Tervyn", "Version ${BuildConfig.VERSION_NAME} · licences · privacy", onClick = { go(Routes.ABOUT) })
        }
        Spacer(Modifier.height(Space.l))
    }
}

// ---- notifications -------------------------------------------------------------------------

@Composable
fun NotificationsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = vm.settings.collectAsState().value ?: AppSettings()
    val c = Tervyn.colors
    var alert by rememberSaveable(s.dataAlertMb) { mutableStateOf(if (s.dataAlertMb > 0) s.dataAlertMb.toString() else "") }
    Screen(title = "Notifications", onBack = onBack) {
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SwitchRow(Icons.Rounded.Notifications, "Connection events", "Connected, disconnected, switched, failed", s.notificationsEnabled) { on -> vm.updateSettings { it.copy(notificationsEnabled = on) } }
            Divider()
            SwitchRow(null, "Connected", null, s.notifyConnect, enabled = s.notificationsEnabled) { on -> vm.updateSettings { it.copy(notifyConnect = on) } }
            SwitchRow(null, "Disconnected", null, s.notifyDisconnect, enabled = s.notificationsEnabled) { on -> vm.updateSettings { it.copy(notifyDisconnect = on) } }
            SwitchRow(null, "Server switched", null, s.notifySwitch, enabled = s.notificationsEnabled) { on -> vm.updateSettings { it.copy(notifySwitch = on) } }
            SwitchRow(null, "Connection failed", null, s.notifyFailure, enabled = s.notificationsEnabled) { on -> vm.updateSettings { it.copy(notifyFailure = on) } }
        }
        SectionTitle("Status notification")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SwitchRow(Icons.Rounded.Speed, "Show live speed", "Download and upload rate in the persistent notification", s.showSpeedInNotification) { on -> vm.updateSettings { it.copy(showSpeedInNotification = on) } }
        }
        SectionTitle("Data usage alert")
        Card {
            Text("Notify once a session has used this much data. Leave empty to turn the alert off.", style = MaterialTheme.typography.bodySmall, color = c.text2)
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TervynTextField(alert, { alert = it.filter(Char::isDigit).take(6) }, "Megabytes", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                Spacer(Modifier.width(Space.m))
                SecondaryButton("Save", { vm.updateSettings { it.copy(dataAlertMb = alert.toIntOrNull() ?: 0) }; vm.toast("Saved") })
            }
        }
        Spacer(Modifier.height(Space.m))
        Text("The persistent status notification is required by Android while the VPN or Secure DNS is running. Icon: Tervyn's bolt.", style = MaterialTheme.typography.bodySmall, color = c.text3)
        @Suppress("unused") val icon = Icons.Rounded.DataUsage
    }
}

// ---- app lock ------------------------------------------------------------------------------

@Composable
fun AppLockScreen(vm: MainViewModel, onBack: () -> Unit, activity: FragmentActivity) {
    val c = Tervyn.colors
    val s = vm.settings.collectAsState().value ?: AppSettings()
    val pinHash by vm.pinHash.collectAsState()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var setting by rememberSaveable { mutableStateOf(false) }
    val canBio = remember {
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    Screen(title = "App lock", onBack = onBack) {
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SwitchRow(Icons.Rounded.Lock, "Require a PIN to open Tervyn", if (s.appLock) "On" else "Off", s.appLock) { on ->
                if (on) setting = true else vm.setPin(null)
            }
            if (s.appLock) {
                Divider()
                SwitchRow(Icons.Rounded.Fingerprint, "Unlock with biometrics", if (canBio) "Fingerprint or face, with PIN as fallback" else "No biometrics enrolled on this device",
                    s.biometric, enabled = canBio) { on -> vm.updateSettings { it.copy(biometric = on) } }
                Divider()
                SettingRow(Icons.Rounded.Timer, "Lock after leaving the app", when (s.lockTimeoutSec) { 0 -> "Immediately"; 60 -> "1 minute"; 300 -> "5 minutes"; else -> "${s.lockTimeoutSec / 60} minutes" })
                SegmentedControl(listOf("Now", "1 min", "5 min", "30 min"), listOf(0, 60, 300, 1800).indexOf(s.lockTimeoutSec).coerceAtLeast(0),
                    { vm.updateSettings { st -> st.copy(lockTimeoutSec = listOf(0, 60, 300, 1800)[it]) } }, Modifier.padding(bottom = Space.m))
            }
        }
        if (s.appLock && pinHash != null && !setting) {
            Spacer(Modifier.height(Space.m))
            SecondaryButton("Change PIN", { setting = true }, Modifier.fillMaxWidth())
        }
        if (setting) {
            Spacer(Modifier.height(Space.l))
            Card {
                Text("Choose a 4–8 digit PIN", style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(Space.m))
                TervynTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, "PIN", keyboardType = KeyboardType.NumberPassword, secret = true)
                Spacer(Modifier.height(Space.s))
                TervynTextField(confirm, { confirm = it.filter(Char::isDigit).take(8) }, "Confirm PIN", keyboardType = KeyboardType.NumberPassword, secret = true,
                    error = if (confirm.isNotEmpty() && confirm != pin) "PINs do not match" else null)
                Spacer(Modifier.height(Space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    SecondaryButton("Cancel", { setting = false; pin = ""; confirm = "" }, Modifier.weight(1f))
                    PrimaryButton("Save PIN", { vm.setPin(pin); setting = false; pin = ""; confirm = ""; vm.toast("App lock enabled") }, Modifier.weight(1f), enabled = pin.length >= 4 && pin == confirm)
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        Text("The PIN is stored only as a salted hash on this device. If you forget it, clear the app's data to reset it.", style = MaterialTheme.typography.bodySmall, color = c.text3)
    }
}

// ---- backup --------------------------------------------------------------------------------

@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = Tervyn.colors
    val scope = rememberCoroutineScope()
    var includeServers by rememberSaveable { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) scope.launch { vm.exportConfig(uri, includeServers) } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch { vm.importConfig(uri) } }
    Screen(title = "Backup & restore", onBack = onBack) {
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            SwitchRow(Icons.Rounded.Dns, "Include my servers and subscriptions", "The file will contain server credentials — keep it private", includeServers) { includeServers = it }
        }
        Spacer(Modifier.height(Space.m))
        PrimaryButton("Export to file", { exporter.launch("tervyn-backup.json") }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Backup)
        Spacer(Modifier.height(Space.s))
        SecondaryButton("Import from file", { importer.launch(arrayOf("application/json", "text/*", "*/*")) }, Modifier.fillMaxWidth())
        SectionTitle("Reset")
        Banner("Reset all settings", "Restores defaults and removes the app lock. Your servers and websites are kept.", Tone.WARNING) {
            SecondaryButton("Reset settings", { vm.resetSettings() }, danger = true)
        }
        Spacer(Modifier.height(Space.m))
        Text("Backups never include the built-in servers (they ship with the app) or the app-lock PIN.", style = MaterialTheme.typography.bodySmall, color = c.text3)
        @Suppress("unused") val icon = Icons.Rounded.AppSettingsAlt
    }
}

@Suppress("unused")
private val palette = Icons.Rounded.Palette
