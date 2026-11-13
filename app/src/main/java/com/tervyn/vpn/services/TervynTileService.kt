package com.tervyn.vpn.services

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tervyn.vpn.MainActivity
import com.tervyn.vpn.TervynApp
import com.tervyn.vpn.vpn.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: one tap connects/disconnects; shows the live phase. */
class TervynTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val controller get() = (application as TervynApp).container.controller

    override fun onStartListening() {
        job = scope.launch {
            controller.status.collect { s ->
                val tile = qsTile ?: return@collect
                tile.state = when {
                    s.phase == Phase.CONNECTED -> Tile.STATE_ACTIVE
                    s.isActive -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                }
                tile.label = "Tervyn VPN"
                if (Build.VERSION.SDK_INT >= 29) tile.subtitle = when (s.phase) {
                    Phase.CONNECTED -> s.countryCode ?: "Connected"
                    Phase.DISCONNECTED -> "Off"
                    else -> s.phase.label
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() { job?.cancel(); job = null }

    override fun onClick() {
        if (controller.status.value.isActive) { controller.disconnect(); return }
        if (controller.needsPermission() != null) {
            // The VPN consent dialog needs an activity.
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_CONNECT, true)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated") startActivityAndCollapse(intent)
            }
            return
        }
        controller.connect()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
