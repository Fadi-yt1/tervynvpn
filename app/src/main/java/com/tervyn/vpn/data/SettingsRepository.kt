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
import com.tervyn.vpn.core.ProxySources
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.domain.SourceMode
import com.tervyn.vpn.domain.VpnMode
import com.tervyn.vpn.net.DohForwarder
import com.tervyn.vpn.net.ProxyTester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class SplitMode(val label: String) { BYPASS("Selected apps bypass the VPN"), ONLY_SELECTED("Only selected apps use the VPN") }

enum class AppTheme(val label: String) { MIDNIGHT("Midnight (default)"), AMOLED("AMOLED black"), DEEP_BLUE("Deep blue") }

@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    val autoSwitch: Boolean = true,
    val selectedServerId: String? = null,
    val preferredCountry: String? = null,
    val mode: VpnMode = VpnMode.ALL_APPS,
    val sourceMode: SourceMode = SourceMode.FREE,
    val autoConnectOnBoot: Boolean = false,
    val autoConnectOnLaunch: Boolean = false,
    val reconnectOnNetworkChange: Boolean = true,
    val dohUrl: String = DohForwarder.PRESETS.values.first(),
    val appLock: Boolean = false,
    val biometric: Boolean = false,
    val lockTimeoutSec: Int = 60,
    val notifyConnect: Boolean = true,
    val notifyDisconnect: Boolean = true,
    val notifySwitch: Boolean = true,
    val notifyFailure: Boolean = true,
    val showSpeedInNotification: Boolean = true,
    val vibration: Boolean = true,
    val sound: Boolean = false,
    val theme: AppTheme = AppTheme.MIDNIGHT,
    val excludedApps: Set<String> = emptySet(),
    val splitMode: SplitMode = SplitMode.BYPASS,
    val includedApps: Set<String> = emptySet(),
    val notificationsEnabled: Boolean = true,
    val dataAlertMb: Int = 0,
    val ipLookupUrl: String = DEFAULT_IP_LOOKUP,
    val traceUrl: String = ProxyTester.DEFAULT_TRACE_URL,
    val testConcurrency: Int = 64,
    val testTimeoutMs: Int = 8000,
    val switchThresholdPct: Int = 40,
    val healthCheckSec: Int = 120,
    val listRefreshMin: Int = 30,
    val disabledSources: Set<String> = emptySet(),
    val customSources: List<String> = emptyList(),
    val proxioProtocol: String = "socks5",
    val proxioLimit: Int = 50,
    val defaultImportProtocol: ProxyProtocol = ProxyProtocol.SOCKS5,
    val lastConnectedAt: Long = 0,
) {
    companion object {
        /** Returns JSON with ip, city, region, country, org. `{ip}` is replaced with the address. */
        const val DEFAULT_IP_LOOKUP = "https://ipinfo.io/{ip}/json"
    }
    val enabledSources: List<String> get() = (ProxySources.DEFAULT.map { it.url } + customSources).filter { it !in disabledSources }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("tervyn_settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding")
        val autoSwitch = booleanPreferencesKey("auto_switch")
        val selected = stringPreferencesKey("selected_server")
        val country = stringPreferencesKey("preferred_country")
        val mode = stringPreferencesKey("mode")
        val source = stringPreferencesKey("source_mode")
        val boot = booleanPreferencesKey("auto_boot")
        val launch = booleanPreferencesKey("auto_launch")
        val netChange = booleanPreferencesKey("reconnect_net_change")
        val doh = stringPreferencesKey("doh_url")
        val lock = booleanPreferencesKey("app_lock")
        val bio = booleanPreferencesKey("biometric")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val nConnect = booleanPreferencesKey("n_connect")
        val nDisconnect = booleanPreferencesKey("n_disconnect")
        val nSwitch = booleanPreferencesKey("n_switch")
        val nFail = booleanPreferencesKey("n_fail")
        val nSpeed = booleanPreferencesKey("n_speed")
        val vibration = booleanPreferencesKey("vibration")
        val sound = booleanPreferencesKey("sound")
        val theme = stringPreferencesKey("theme")
        val excluded = stringSetPreferencesKey("excluded_apps")
        val splitMode = stringPreferencesKey("split_mode")
        val included = stringSetPreferencesKey("included_apps")
        val nAll = booleanPreferencesKey("n_all")
        val dataAlert = intPreferencesKey("data_alert_mb")
        val ipLookup = stringPreferencesKey("ip_lookup")
        val trace = stringPreferencesKey("trace_url")
        val concurrency = intPreferencesKey("concurrency")
        val timeout = intPreferencesKey("timeout")
        val threshold = intPreferencesKey("switch_threshold")
        val health = intPreferencesKey("health_sec")
        val refresh = intPreferencesKey("refresh_min")
        val disabledSources = stringSetPreferencesKey("disabled_sources")
        val customSources = stringPreferencesKey("custom_sources")
        val proxioProto = stringPreferencesKey("proxio_proto")
        val proxioLimit = intPreferencesKey("proxio_limit")
        val importProto = stringPreferencesKey("import_proto")
        val lastConnected = longPreferencesKey("last_connected")
        val pinHash = stringPreferencesKey("pin_hash")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> read(p) }
    val pinHash: Flow<String?> = context.dataStore.data.map { it[K.pinHash] }

    suspend fun current(): AppSettings = settings.first()

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            autoSwitch = p[K.autoSwitch] ?: d.autoSwitch,
            selectedServerId = p[K.selected],
            preferredCountry = p[K.country],
            mode = p[K.mode]?.let { runCatching { VpnMode.valueOf(it) }.getOrNull() } ?: d.mode,
            sourceMode = p[K.source]?.let { runCatching { SourceMode.valueOf(it) }.getOrNull() } ?: d.sourceMode,
            autoConnectOnBoot = p[K.boot] ?: d.autoConnectOnBoot,
            autoConnectOnLaunch = p[K.launch] ?: d.autoConnectOnLaunch,
            reconnectOnNetworkChange = p[K.netChange] ?: d.reconnectOnNetworkChange,
            dohUrl = p[K.doh] ?: d.dohUrl,
            appLock = p[K.lock] ?: d.appLock,
            biometric = p[K.bio] ?: d.biometric,
            lockTimeoutSec = p[K.lockTimeout] ?: d.lockTimeoutSec,
            notifyConnect = p[K.nConnect] ?: d.notifyConnect,
            notifyDisconnect = p[K.nDisconnect] ?: d.notifyDisconnect,
            notifySwitch = p[K.nSwitch] ?: d.notifySwitch,
            notifyFailure = p[K.nFail] ?: d.notifyFailure,
            showSpeedInNotification = p[K.nSpeed] ?: d.showSpeedInNotification,
            vibration = p[K.vibration] ?: d.vibration,
            sound = p[K.sound] ?: d.sound,
            theme = p[K.theme]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: d.theme,
            excludedApps = p[K.excluded] ?: d.excludedApps,
            splitMode = p[K.splitMode]?.let { runCatching { SplitMode.valueOf(it) }.getOrNull() } ?: d.splitMode,
            includedApps = p[K.included] ?: d.includedApps,
            notificationsEnabled = p[K.nAll] ?: d.notificationsEnabled,
            dataAlertMb = p[K.dataAlert] ?: d.dataAlertMb,
            ipLookupUrl = p[K.ipLookup] ?: d.ipLookupUrl,
            traceUrl = p[K.trace] ?: d.traceUrl,
            testConcurrency = p[K.concurrency] ?: d.testConcurrency,
            testTimeoutMs = p[K.timeout] ?: d.testTimeoutMs,
            switchThresholdPct = p[K.threshold] ?: d.switchThresholdPct,
            healthCheckSec = p[K.health] ?: d.healthCheckSec,
            listRefreshMin = p[K.refresh] ?: d.listRefreshMin,
            disabledSources = p[K.disabledSources] ?: d.disabledSources,
            customSources = p[K.customSources]?.split('\n')?.filter { it.isNotBlank() } ?: d.customSources,
            proxioProtocol = p[K.proxioProto] ?: d.proxioProtocol,
            proxioLimit = p[K.proxioLimit] ?: d.proxioLimit,
            defaultImportProtocol = p[K.importProto]?.let { runCatching { ProxyProtocol.valueOf(it) }.getOrNull() } ?: d.defaultImportProtocol,
            lastConnectedAt = p[K.lastConnected] ?: 0,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(read(p))
            p[K.onboarding] = s.onboardingDone
            p[K.autoSwitch] = s.autoSwitch
            if (s.selectedServerId != null) p[K.selected] = s.selectedServerId else p.remove(K.selected)
            if (s.preferredCountry != null) p[K.country] = s.preferredCountry else p.remove(K.country)
            p[K.mode] = s.mode.name
            p[K.source] = s.sourceMode.name
            p[K.boot] = s.autoConnectOnBoot
            p[K.launch] = s.autoConnectOnLaunch
            p[K.netChange] = s.reconnectOnNetworkChange
            p[K.doh] = s.dohUrl
            p[K.lock] = s.appLock
            p[K.bio] = s.biometric
            p[K.lockTimeout] = s.lockTimeoutSec
            p[K.nConnect] = s.notifyConnect
            p[K.nDisconnect] = s.notifyDisconnect
            p[K.nSwitch] = s.notifySwitch
            p[K.nFail] = s.notifyFailure
            p[K.nSpeed] = s.showSpeedInNotification
            p[K.vibration] = s.vibration
            p[K.sound] = s.sound
            p[K.theme] = s.theme.name
            p[K.excluded] = s.excludedApps
            p[K.splitMode] = s.splitMode.name
            p[K.included] = s.includedApps
            p[K.nAll] = s.notificationsEnabled
            p[K.dataAlert] = s.dataAlertMb.coerceIn(0, 100_000)
            p[K.ipLookup] = s.ipLookupUrl
            p[K.trace] = s.traceUrl
            p[K.concurrency] = s.testConcurrency.coerceIn(1, 128)
            p[K.timeout] = s.testTimeoutMs.coerceIn(2000, 30000)
            p[K.threshold] = s.switchThresholdPct.coerceIn(10, 90)
            p[K.health] = s.healthCheckSec.coerceIn(30, 1800)
            p[K.refresh] = s.listRefreshMin.coerceIn(5, 240)
            p[K.disabledSources] = s.disabledSources
            p[K.customSources] = s.customSources.joinToString("\n")
            p[K.proxioProto] = s.proxioProtocol
            p[K.proxioLimit] = s.proxioLimit.coerceIn(1, 1000)
            p[K.importProto] = s.defaultImportProtocol.name
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
