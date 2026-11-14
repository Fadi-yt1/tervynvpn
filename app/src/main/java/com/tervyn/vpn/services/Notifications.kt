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
    private const val CH_EVENTS = "vpn_events"
    private val LEGACY_CHANNELS = listOf("vpn_events_quiet", "vpn_events_vibrate", "vpn_events_sound", "vpn_events_sound_vibrate")

    enum class Kind { CONNECT, DISCONNECT, SWITCH, FAILURE, DATA }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "VPN status", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Tervyn VPN or Secure DNS is running"; setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "Connection events", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Connected, disconnected, server switched, connection failed"
        })
        LEGACY_CHANNELS.forEach { runCatching { nm.deleteNotificationChannel(it) } }
    }

    private fun openApp(ctx: Context) =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    fun status(ctx: Context, s: VpnStatus, showSpeed: Boolean, dnsProvider: String? = null): android.app.Notification {
        val dnsOnly = s.phase == Phase.DNS_ONLY
        val title = when (s.phase) {
            Phase.CONNECTED -> "Connected" + (s.server?.let { " · ${it.name}" } ?: "")
            Phase.DNS_ONLY -> "Secure DNS active"
            else -> s.phase.label
        }
        val text = when {
            dnsOnly -> "Encrypted lookups via ${dnsProvider ?: "DNS-over-HTTPS"}" + if (s.dnsQueries > 0) " · ${s.dnsQueries} queries" else ""
            s.phase == Phase.CONNECTED && showSpeed -> "↓ ${Format.speed(s.downBps)}   ↑ ${Format.speed(s.upBps)}" + (s.exitIp?.let { "   IP $it" } ?: "")
            s.phase == Phase.CONNECTED -> s.exitIp?.let { "Exit IP $it" } ?: "Protected"
            else -> s.message ?: ""
        }
        val action = if (dnsOnly) {
            PendingIntent.getService(ctx, 2, Intent(ctx, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_STOP_DNS)
                .putExtra(TervynVpnService.EXTRA_DISABLE_SETTING, true), PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(ctx, 1, Intent(ctx, TervynVpnService::class.java).setAction(TervynVpnService.ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE)
        }
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
            .addAction(0, if (dnsOnly) "Turn off" else "Disconnect", action)
            .build()
    }

    fun updateStatus(ctx: Context, s: VpnStatus, showSpeed: Boolean, dnsProvider: String? = null) {
        if (!canPost(ctx)) return
        runCatching { NotificationManagerCompat.from(ctx).notify(STATUS_ID, status(ctx, s, showSpeed, dnsProvider)) }
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun event(ctx: Context, s: AppSettings, title: String, text: String, kind: Kind) {
        val enabled = when (kind) {
            Kind.CONNECT -> s.notifyConnect
            Kind.DISCONNECT -> s.notifyDisconnect
            Kind.SWITCH -> s.notifySwitch
            Kind.FAILURE -> s.notifyFailure
            Kind.DATA -> s.dataAlertMb > 0
        }
        if (!s.notificationsEnabled || !enabled || !canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_EVENTS)
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
