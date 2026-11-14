package com.tervyn.vpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.net.DohForwarder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class SplitMode(val label: String) { BYPASS("Selected apps bypass the VPN"), ONLY_SELECTED("Only selected apps use the VPN") }

enum class AppTheme(val label: String) { MIDNIGHT("Midnight"), AMOLED("Pure black"), OCEAN("Ocean") }

/** Everything the user can configure. Timings and thresholds that users never need to touch live in [com.tervyn.vpn.core.Tuning]. */
@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    /** Fastest-available mode: pick and keep the best verified server automatically. */
    val autoSwitch: Boolean = true,
    /** Manually chosen server (auto-switch off) or null for "Fastest available". */
    val selectedServerId: String? = null,
    val mode: VpnMode = VpnMode.ALL_APPS,
    val autoConnectOnBoot: Boolean = false,
    val autoConnectOnLaunch: Boolean = false,
    val reconnectOnNetworkChange: Boolean = true,
    /**
     * Secure DNS: encrypt DNS lookups (DNS-over-HTTPS) while the VPN is *off*. Independent of the server
     * connection — when a server is connected, lookups are resolved through that server instead.
     */
    val secureDns: Boolean = false,
    val dohUrl: String = DohForwarder.PRESETS.values.first(),
    val appLock: Boolean = false,
    val biometric: Boolean = false,
    val lockTimeoutSec: Int = 60,
    val notificationsEnabled: Boolean = true,
    val notifyConnect: Boolean = true,
    val notifyDisconnect: Boolean = true,
    val notifySwitch: Boolean = true,
    val notifyFailure: Boolean = true,
    val showSpeedInNotification: Boolean = true,
    val theme: AppTheme = AppTheme.MIDNIGHT,
    val splitMode: SplitMode = SplitMode.BYPASS,
    val excludedApps: Set<String> = emptySet(),
    val includedApps: Set<String> = emptySet(),
    /** Notify once a session has used this much data (0 = off). */
    val dataAlertMb: Int = 0,
    val lastConnectedAt: Long = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("tervyn_settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding")
        val autoSwitch = booleanPreferencesKey("auto_switch")
        val selected = stringPreferencesKey("selected_server")
        val mode = stringPreferencesKey("mode")
        val boot = booleanPreferencesKey("auto_boot")
        val launch = booleanPreferencesKey("auto_launch")
        val netChange = booleanPreferencesKey("reconnect_net_change")
        val secureDns = booleanPreferencesKey("secure_dns")
        val doh = stringPreferencesKey("doh_url")
        val lock = booleanPreferencesKey("app_lock")
        val bio = booleanPreferencesKey("biometric")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val nAll = booleanPreferencesKey("n_all")
        val nConnect = booleanPreferencesKey("n_connect")
        val nDisconnect = booleanPreferencesKey("n_disconnect")
        val nSwitch = booleanPreferencesKey("n_switch")
        val nFail = booleanPreferencesKey("n_fail")
        val nSpeed = booleanPreferencesKey("n_speed")
        val theme = stringPreferencesKey("theme")
        val splitMode = stringPreferencesKey("split_mode")
        val excluded = stringSetPreferencesKey("excluded_apps")
        val included = stringSetPreferencesKey("included_apps")
        val dataAlert = intPreferencesKey("data_alert_mb")
        val lastConnected = longPreferencesKey("last_connected")
        val pinHash = stringPreferencesKey("pin_hash")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> read(p) }
    val pinHash: Flow<String?> = context.dataStore.data.map { it[K.pinHash] }

    suspend fun current(): AppSettings = settings.first()

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            autoSwitch = p[K.autoSwitch] ?: d.autoSwitch,
            selectedServerId = p[K.selected],
            mode = enumOf(p[K.mode], d.mode),
            autoConnectOnBoot = p[K.boot] ?: d.autoConnectOnBoot,
            autoConnectOnLaunch = p[K.launch] ?: d.autoConnectOnLaunch,
            reconnectOnNetworkChange = p[K.netChange] ?: d.reconnectOnNetworkChange,
            secureDns = p[K.secureDns] ?: d.secureDns,
            dohUrl = p[K.doh]?.takeIf { it.startsWith("https://") } ?: d.dohUrl,
            appLock = p[K.lock] ?: d.appLock,
            biometric = p[K.bio] ?: d.biometric,
            lockTimeoutSec = p[K.lockTimeout] ?: d.lockTimeoutSec,
            notificationsEnabled = p[K.nAll] ?: d.notificationsEnabled,
            notifyConnect = p[K.nConnect] ?: d.notifyConnect,
            notifyDisconnect = p[K.nDisconnect] ?: d.notifyDisconnect,
            notifySwitch = p[K.nSwitch] ?: d.notifySwitch,
            notifyFailure = p[K.nFail] ?: d.notifyFailure,
            showSpeedInNotification = p[K.nSpeed] ?: d.showSpeedInNotification,
            theme = enumOf(p[K.theme], d.theme),
            splitMode = enumOf(p[K.splitMode], d.splitMode),
            excludedApps = p[K.excluded] ?: d.excludedApps,
            includedApps = p[K.included] ?: d.includedApps,
            dataAlertMb = p[K.dataAlert] ?: d.dataAlertMb,
            lastConnectedAt = p[K.lastConnected] ?: 0,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(read(p))
            p[K.onboarding] = s.onboardingDone
            p[K.autoSwitch] = s.autoSwitch
            if (s.selectedServerId != null) p[K.selected] = s.selectedServerId else p.remove(K.selected)
            p[K.mode] = s.mode.name
            p[K.boot] = s.autoConnectOnBoot
            p[K.launch] = s.autoConnectOnLaunch
            p[K.netChange] = s.reconnectOnNetworkChange
            p[K.secureDns] = s.secureDns
            p[K.doh] = s.dohUrl
            p[K.lock] = s.appLock
            p[K.bio] = s.biometric
            p[K.lockTimeout] = s.lockTimeoutSec.coerceIn(0, 3600)
            p[K.nAll] = s.notificationsEnabled
            p[K.nConnect] = s.notifyConnect
            p[K.nDisconnect] = s.notifyDisconnect
            p[K.nSwitch] = s.notifySwitch
            p[K.nFail] = s.notifyFailure
            p[K.nSpeed] = s.showSpeedInNotification
            p[K.theme] = s.theme.name
            p[K.splitMode] = s.splitMode.name
            p[K.excluded] = s.excludedApps
            p[K.included] = s.includedApps
            p[K.dataAlert] = s.dataAlertMb.coerceIn(0, 100_000)
            p[K.lastConnected] = s.lastConnectedAt
        }
    }

    suspend fun setPinHash(hash: String?) {
        context.dataStore.edit { if (hash == null) it.remove(K.pinHash) else it[K.pinHash] = hash }
    }

    suspend fun reset() {
        val keepOnboarding = current().onboardingDone
        context.dataStore.edit { it.clear() }
        update { AppSettings(onboardingDone = keepOnboarding) }
    }
}
