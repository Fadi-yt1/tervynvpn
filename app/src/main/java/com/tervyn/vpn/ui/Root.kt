package com.tervyn.vpn.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tervyn.vpn.MainActivity
import com.tervyn.vpn.PendingActions
import com.tervyn.vpn.data.AppTheme
import com.tervyn.vpn.ui.navigation.Routes
import com.tervyn.vpn.ui.screens.*
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.ui.theme.TervynTheme
import com.tervyn.vpn.vpn.Phase

/** Returns an action that asks for the VPN consent (and notification permission) when needed, then connects. */
@Composable
fun rememberConnectAction(vm: MainViewModel, activity: Activity): () -> Unit {
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.connect() else vm.toast("VPN permission is required to connect")
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(vm) {
        {
            (activity as? MainActivity)?.notificationPermission?.let { perm ->
                if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) notifLauncher.launch(perm)
            }
            val intent = vm.c.controller.needsPermission()
            if (intent != null) vpnLauncher.launch(intent) else vm.connect()
        }
    }
}

@Composable
fun TervynRoot(vm: MainViewModel, activity: FragmentActivity) {
    val settings by vm.settings.collectAsState()
    TervynTheme(settings?.theme ?: AppTheme.MIDNIGHT) {
        val s = settings
        if (s == null) {
            Box(Modifier.fillMaxSize().background(Tervyn.colors.bg))
            return@TervynTheme
        }
        // ---- app lock gate ----
        val pinHash by vm.pinHash.collectAsState()
        var unlocked by rememberSaveable { mutableStateOf(false) }
        var backgroundedAt by remember { mutableLongStateOf(0L) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner, s.appLock, s.lockTimeoutSec) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_STOP) backgroundedAt = System.currentTimeMillis()
                if (e == Lifecycle.Event.ON_START && backgroundedAt > 0 && s.appLock &&
                    System.currentTimeMillis() - backgroundedAt > s.lockTimeoutSec * 1000L) unlocked = false
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        if (s.appLock && pinHash != null && !unlocked) {
            LockScreen(vm, biometricEnabled = s.biometric, onBiometric = { showBiometric(activity) { unlocked = true } }, onUnlocked = { unlocked = true })
            return@TervynTheme
        }

        if (!s.onboardingDone) {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            if (showSplash) SplashScreen { showSplash = false }
            else OnboardingScreen(vm, rememberConnectAction(vm, activity))
            return@TervynTheme
        }
        MainNav(vm, activity)
    }
}

fun showBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    if (BiometricManager.from(activity).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) return
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Tervyn VPN").setSubtitle("Use your fingerprint or face")
            .setNegativeButtonText("Use PIN").setAllowedAuthenticators(allowed).build()
    )
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.SERVERS, "Servers", Icons.Rounded.BarChart),
    Tab(Routes.SITES, "My Sites", Icons.Rounded.TrackChanges),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun MainNav(vm: MainViewModel, activity: FragmentActivity) {
    val nav = rememberNavController()
    val connect = rememberConnectAction(vm, activity)
    val snackbar = remember { SnackbarHostState() }
    val c = Tervyn.colors
    val status by vm.status.collectAsState()
    val connectRequested by PendingActions.connectRequested.collectAsState()
    val pendingLink by PendingActions.importLink.collectAsState()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(connectRequested) { if (connectRequested) { PendingActions.connectRequested.value = false; connect() } }
    LaunchedEffect(pendingLink) { if (pendingLink != null) nav.navigate(Routes.IMPORT_LINK) }
    LaunchedEffect(Unit) {
        val s = vm.c.settings.current()
        if (s.autoConnectOnLaunch && !vm.status.value.isActive && vm.c.controller.needsPermission() == null) vm.connect()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar(containerColor = c.bg2, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigateTab(t.route) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.cyan, selectedTextColor = c.cyan, indicatorColor = c.blue.copy(alpha = .22f),
                            unselectedIconColor = c.text2, unselectedTextColor = c.text2,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
            composable(Routes.HOME) { HomeScreen(vm, connect, go) }
            composable(Routes.CONNECTING) { ConnectingScreen(vm, back, onConnected = { nav.navigate(Routes.CONNECTED) { popUpTo(Routes.HOME) } }) }
            composable(Routes.CONNECTED) { ConnectedScreen(vm, back, go) }
            composable(Routes.SERVERS) { ServersScreen(vm, go, onBack = null) }
            composable(Routes.SERVER, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                ServerDetailsScreen(vm, it.arguments?.getString("id") ?: "", back)
            }
            composable(Routes.IP) { CheckIpScreen(vm, back) }
            composable(Routes.ADVANCED) { AdvancedConfigScreen(vm, back) }
            composable(Routes.ADD_PROXY) { AddProxyScreen(vm, back) }
            composable(Routes.IMPORT_LINK) { ImportLinkScreen(vm, back) }
            composable(Routes.SUBSCRIPTIONS) { SubscriptionsScreen(vm, back, go) }
            composable(Routes.LIST_IMPORT) { ListImportScreen(vm, back) }
            composable(Routes.INTEGRATION) { IntegrationScreen(vm, back) }
            composable(Routes.PROTOCOLS) { ProtocolSelectionScreen(back, go) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, go, connect) }
            composable(Routes.SITES) { SitesScreen(vm, go) }
            composable(Routes.SPLIT) { SplitTunnelScreen(vm, back) }
            composable(Routes.COUNTRIES) { CountryScreen(vm, back) }
            composable(Routes.AUTO_SWITCH) { AutoSwitchScreen(vm, back) }
            composable(Routes.DNS) { DnsScreen(vm, back) }
            composable(Routes.PRIVACY) { PrivacyCenterScreen(vm, back, go) }
            composable(Routes.PERMISSIONS) { PermissionsScreen(vm, back, connect) }
            composable(Routes.QUICK) { QuickSettingsScreen(vm, back) }
            composable(Routes.SPEED) { SpeedTestScreen(vm, back) }
            composable(Routes.HISTORY) { HistoryScreen(vm, back) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(vm, back) }
            composable(Routes.STATS) { StatsScreen(vm, back) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(vm, back) }
            composable(Routes.APP_LOCK) { AppLockScreen(vm, back, activity) }
            composable(Routes.HELP) { HelpScreen(back, go) }
            composable(Routes.FAQ) { FaqScreen(back) }
            composable(Routes.REPORT) { ReportScreen(vm, back) }
            composable(Routes.ABOUT) { AboutScreen(vm, back, go) }
            composable(Routes.POLICY) { PrivacyPolicyScreen(back) }
            composable(Routes.LICENSES) { LicensesScreen(back) }
            composable(Routes.SOURCES) { SourcesScreen(vm, back, go) }
            composable(Routes.PROXIO) { ProxioScreen(vm, back) }
            composable(Routes.FREE_SOURCES) { FreeSourcesScreen(vm, back) }
            composable(Routes.THEME) { ThemeScreen(vm, back) }
        }
    }
    // Follow the connection flow: show the connecting screen while busy from Home.
    LaunchedEffect(status.phase) {
        if (route == Routes.HOME && status.phase in setOf(Phase.FETCHING, Phase.TESTING, Phase.CONNECTING)) nav.navigate(Routes.CONNECTING) { launchSingleTop = true }
    }
}

fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
