package com.tervyn.vpn.di

import android.app.Application
import com.tervyn.vpn.core.ServerManager
import com.tervyn.vpn.core.SubscriptionFetcher
import com.tervyn.vpn.data.DomainRepository
import com.tervyn.vpn.data.HistoryRepository
import com.tervyn.vpn.data.ServerRepository
import com.tervyn.vpn.data.SettingsRepository
import com.tervyn.vpn.data.SubscriptionRepository
import com.tervyn.vpn.database.TervynDatabase
import com.tervyn.vpn.net.DohForwarder
import com.tervyn.vpn.net.HttpsOverProxy
import com.tervyn.vpn.security.SecretCipher
import com.tervyn.vpn.vpn.VpnController
import com.tervyn.vpn.xray.XrayManager
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual dependency container (kept simple and explicit; no reflection-based DI). */
class AppContainer(val app: Application) {
    val db by lazy { TervynDatabase.create(app) }
    val cipher by lazy { SecretCipher.forDevice(app) }
    val settings by lazy { SettingsRepository(app) }
    val servers by lazy { ServerRepository(db.servers(), cipher) }
    val domains by lazy { DomainRepository(db.domains()) }
    val history by lazy { HistoryRepository(db.history(), db.diagnostics()) }
    val subscriptions by lazy { SubscriptionRepository(db.subscriptions(), cipher) }

    /** Direct HTTP client. Tervyn's own traffic is excluded from its tunnel, so this never loops through the VPN. */
    val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    }
    val httpsOverProxy by lazy { HttpsOverProxy() }
    val xray by lazy { XrayManager(File(app.applicationInfo.nativeLibraryDir, "libxray.so"), File(app.noBackupFilesDir, "xray")) }
    val subscriptionFetcher by lazy { SubscriptionFetcher(http) }
    val serverManager by lazy { ServerManager(app, settings, servers, http, xray) }
    val controller by lazy { VpnController(app, settings) }
    val doh by lazy { DohForwarder({ dohUrlCache }, http) }

    @Volatile var dohUrlCache: String = DohForwarder.PRESETS.values.first()
}
