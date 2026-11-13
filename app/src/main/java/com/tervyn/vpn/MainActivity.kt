package com.tervyn.vpn

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.tervyn.vpn.ui.MainViewModel
import com.tervyn.vpn.ui.TervynRoot

class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_CONNECT = "connect"
    }

    lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        handleIntent(intent)
        setContent { TervynRoot(vm, this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_CONNECT, false)) PendingActions.connectRequested.value = true
        val data = intent.dataString
        if (intent.action == Intent.ACTION_VIEW && data != null && com.tervyn.vpn.core.Links.isShareLink(data)) {
            PendingActions.importLink.value = data
        }
    }

    val notificationPermission get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
}

/** Cross-entry-point requests (tile, deep links) consumed by the Compose UI. */
object PendingActions {
    val connectRequested = kotlinx.coroutines.flow.MutableStateFlow(false)
    val importLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}
