package com.tervyn.vpn

import android.app.Application
import com.tervyn.vpn.di.AppContainer
import com.tervyn.vpn.services.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TervynApp : Application() {
    lateinit var container: AppContainer
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
        appScope.launch {
            container.xray.cleanupStaleConfigs()
            container.settings.settings.collect { container.dohUrlCache = it.dohUrl }
        }
    }
}
