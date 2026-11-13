package com.tervyn.vpn.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tervyn.vpn.MainActivity
import com.tervyn.vpn.R
import com.tervyn.vpn.data.AppSettings
import com.tervyn.vpn.utils.Format
import com.tervyn.vpn.vpn.Phase
import com.tervyn.vpn.vpn.TervynVpnService
import com.tervyn.vpn.vpn.VpnStatus

object Notifications {
    const val STATUS_ID = 1001
    private const val CH_STATUS = "vpn_status"
    // Channel sound/vibration cannot be changed after creation, so each combination has its own channel.
    private const val CH_QUIET = "vpn_events_quiet"
    private const val CH_VIBRATE = "vpn_events_vibrate"
    private const val CH_SOUND = "vpn_events_sound"
    private const val CH_BOTH = "vpn_events_sound_vibrate"

    enum class Kind { CONNECT, DISCONNECT, SWITCH, FAILURE }

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "VPN status", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Persistent notification while Tervyn VPN is running"; setShowBadge(false)
        })
        fun ev(id: String, name: String, sound: Boolean, vibrate: Boolean) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Connection events (connected, switched, failed)"
                if (!sound) setSound(null, null)
                enableVibration(vibrate)
                if (!vibrate) vibrationPattern = longArrayOf(0)
            }
        nm.createNotificationChannel(ev(CH_QUIET, "VPN events (silent)", false, false))
        nm.createNotificationChannel(ev(CH_VIBRATE, "VPN events (vibrate)", false, true))
        nm.createNotificationChannel(ev(CH_SOUND, "VPN events (sound)", true, false))
        nm.createNotificationChannel(ev(CH_BOTH, "VPN events (sound + vibrate)", true, true))
    }

    private fun openApp(ctx: Context) =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    fun status(ctx: Context, s: VpnStatus, showSpeed: Boolean): android.app.Notification {
        val title = when (s.phase) {
            Phase.CONNECTED -> "Connected" + (s.server?.let { " · ${it.name}" } ?: "")
            else -> s.phase.label
        }
        val text = when {
            s.phase == Phase.CONNECTED && showSpeed -> "↓ ${Format.speed(s.downBps)}   ↑ ${Format.speed(s.upBps)}" + (s.exitIp?.let { "   IP $it" } ?: "")
            s.phase == Phase.CONNECTED -> s.exitIp?.let { "Exit IP $it" } ?: "Protected"
            else -> s.message ?: ""
        }
        val stop = PendingIntent.getService(ctx, 1, Intent(ctx, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_tervyn)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Disconnect", stop)
            .build()
    }

    fun updateStatus(ctx: Context, s: VpnStatus, showSpeed: Boolean) {
        if (!canPost(ctx)) return
        runCatching { NotificationManagerCompat.from(ctx).notify(STATUS_ID, status(ctx, s, showSpeed)) }
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun event(ctx: Context, s: AppSettings, title: String, text: String, kind: Kind) {
        val enabled = when (kind) {
            Kind.CONNECT -> s.notifyConnect
            Kind.DISCONNECT -> s.notifyDisconnect
            Kind.SWITCH -> s.notifySwitch
            Kind.FAILURE -> s.notifyFailure
        }
        if (!enabled || !canPost(ctx)) return
        val ch = when {
            s.sound && s.vibration -> CH_BOTH
            s.sound -> CH_SOUND
            s.vibration -> CH_VIBRATE
            else -> CH_QUIET
        }
        val n = NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_stat_tervyn)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(2000 + kind.ordinal, n) }
    }
}
