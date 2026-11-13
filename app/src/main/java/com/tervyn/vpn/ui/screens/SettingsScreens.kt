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
fun QuickSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    ScreenScaffold("Quick Settings & Notifications", null, onBack, heroArt = com.tervyn.vpn.R.drawable.art_bell, accentWord = "Notifications") {
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
    ScreenScaffold("Theme", "Tervyn is designed for dark mode", onBack, heroArt = com.tervyn.vpn.R.drawable.art_about, accentWord = "Theme") {
        GlassCard {
            AppTheme.entries.forEach { t ->
                SettingsRow(Icons.Rounded.Palette, t.label, null, { vm.updateSettings { it.copy(theme = t) } }) {
                    if (s.theme == t) Icon(Icons.Rounded.VerifiedUser, "Selected", tint = c.green)
                }
            }
        }
    }
}
