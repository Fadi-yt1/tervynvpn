package com.tervyn.vpn.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.tervyn.vpn.TervynApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Optionally reconnects after boot / app update, only if the VPN permission is already granted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val container = (context.applicationContext as TervynApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = container.settings.current()
                if (s.autoConnectOnBoot && s.onboardingDone && VpnService.prepare(context) == null) container.controller.connect()
            } finally {
                pending.finish()
            }
        }
    }
}
