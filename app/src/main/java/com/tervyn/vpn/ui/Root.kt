package com.tervyn.vpn.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.tervyn.vpn.ui.screens.AboutScreen
import com.tervyn.vpn.ui.screens.AddServerScreen
import com.tervyn.vpn.ui.screens.AppLockScreen
import com.tervyn.vpn.ui.screens.BackupScreen
import com.tervyn.vpn.ui.screens.CheckIpScreen
import com.tervyn.vpn.ui.screens.DiagnosticsScreen
import com.tervyn.vpn.ui.screens.DnsScreen
import com.tervyn.vpn.ui.screens.HistoryScreen
import com.tervyn.vpn.ui.screens.HomeScreen
import com.tervyn.vpn.ui.screens.LicensesScreen
import com.tervyn.vpn.ui.screens.LockScreen
import com.tervyn.vpn.ui.screens.NotificationsScreen
import com.tervyn.vpn.ui.screens.OnboardingScreen
import com.tervyn.vpn.ui.screens.PrivacyScreen
import com.tervyn.vpn.ui.screens.ServerDetailScreen
import com.tervyn.vpn.ui.screens.ServersScreen
import com.tervyn.vpn.ui.screens.SettingsScreen
import com.tervyn.vpn.ui.screens.SitesScreen
import com.tervyn.vpn.ui.screens.SpeedTestScreen
import com.tervyn.vpn.ui.screens.SplitTunnelScreen
import com.tervyn.vpn.ui.screens.SubscriptionsScreen
import com.tervyn.vpn.ui.theme.Tervyn
import com.tervyn.vpn.ui.theme.TervynTheme

/**
 * Returns an action that obtains the system VPN consent (and the notification permission on Android 13+)
 * when needed, then runs [onGranted]. Used for both connecting a server and starting Secure DNS, which
 * share the same consent.
 */
@Composable
fun rememberVpnPermissionAction(vm: MainViewModel, activity: Activity, onGranted: () -> Unit): () -> Unit {
    val grantedRef = rememberUpdatedState(onGranted)
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) grantedRef.value() else vm.toast("The VPN permission is required for this")
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(vm) {
        {
            (activity as? MainActivity)?.notificationPermission?.let { perm ->
                if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) notifLauncher.launch(perm)
            }
            val intent = vm.c.controller.needsPermission()
            if (intent != null) vpnLauncher.launch(intent) else grantedRef.value()
        }
    }
}

@Composable
fun TervynRoot(vm: MainViewModel, activity: FragmentActivity) {
    val settings by vm.settings.collectAsState()
    TervynTheme(settings?.theme ?: AppTheme.MIDNIGHT) {
        val s = settings
        if (s == null) { Box(Modifier.fillMaxSize().background(Tervyn.colors.bg)); return@TervynTheme }

        // ---- app lock gate ----
        val pinHash by vm.pinHash.collectAsState()
        var unlocked by rememberSaveable { mutableStateOf(false) }
        var backgroundedAt by remember { mutableLongStateOf(0L) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner, s.appLock, s.lockTimeoutSec) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_STOP) backgroundedAt = System.currentTimeMillis()
                if (e == Lifecycle.Event.ON_START && backgroundedAt > 0 && s.appLock &&
                    System.currentTimeMillis() - backgroundedAt >= s.lockTimeoutSec * 1000L) unlocked = false
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        if (s.appLock && pinHash != null && !unlocked) {
            LockScreen(vm, biometricEnabled = s.biometric, onBiometric = { showBiometric(activity) { unlocked = true } }, onUnlocked = { unlocked = true })
            return@TervynTheme
        }
        if (!s.onboardingDone) { OnboardingScreen(vm) { }; return@TervynTheme }
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

private val tabs = listOf(
    Triple(Routes.HOME, "Home", Icons.Rounded.Home),
    Triple(Routes.SERVERS, "Servers", Icons.Rounded.Dns),
    Triple(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

private fun NavHostController.navigateTab(route: String) = navigate(route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }

@Composable
fun MainNav(vm: MainViewModel, activity: FragmentActivity) {
    val nav = rememberNavController()
    val c = Tervyn.colors
    val snackbar = remember { SnackbarHostState() }
    val connect = rememberVpnPermissionAction(vm, activity, onGranted = { vm.connect() })
    val enableDns = rememberVpnPermissionAction(vm, activity, onGranted = { vm.setSecureDns(true) })
    val connectRequested by PendingActions.connectRequested.collectAsState()
    val pendingLink by PendingActions.importLink.collectAsState()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(connectRequested) { if (connectRequested) { PendingActions.connectRequested.value = false; connect() } }
    LaunchedEffect(pendingLink) { if (pendingLink != null) nav.navigate(Routes.ADD_SERVER) }
    LaunchedEffect(Unit) {
        val s = vm.c.settings.current()
        val st = vm.status.value
        if (vm.c.controller.needsPermission() == null) {
            if (s.autoConnectOnLaunch && !st.isActive) vm.connect()
            else if (s.secureDns && !st.tunnelUp) vm.c.controller.startSecureDns()
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in Routes.tabs
    val go: (String) -> Unit = { r -> if (r in Routes.tabs) nav.navigateTab(r) else nav.navigate(r) }
    val back: () -> Unit = { nav.popBackStack() }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r, onClick = { nav.navigateTab(r) },
                        icon = { Icon(icon, null) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accent, selectedTextColor = c.text, indicatorColor = c.accent.copy(alpha = .16f),
                            unselectedIconColor = c.text3, unselectedTextColor = c.text3,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = if (showBar) padding.calculateBottomPadding() else 0.dp)) {
            composable(Routes.HOME) { HomeScreen(vm, connect, enableDns, go) }
            composable(Routes.SERVERS) { ServersScreen(vm, go) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, go) }
            composable(Routes.SERVER, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                ServerDetailScreen(vm, it.arguments?.getString("id") ?: "", back, connect)
            }
            composable(Routes.ADD_SERVER) {
                val initial = pendingLink
                AddServerScreen(vm, onBack = { PendingActions.importLink.value = null; back() }, initial = initial)
            }
            composable(Routes.SUBSCRIPTIONS) { SubscriptionsScreen(vm, back, go) }
            composable(Routes.DNS) { DnsScreen(vm, back, enableDns) }
            composable(Routes.SITES) { SitesScreen(vm, back) }
            composable(Routes.SPLIT) { SplitTunnelScreen(vm, back) }
            composable(Routes.APP_LOCK) { AppLockScreen(vm, back, activity) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(vm, back) }
            composable(Routes.IP) { CheckIpScreen(vm, back) }
            composable(Routes.SPEED) { SpeedTestScreen(vm, back) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(vm, back) }
            composable(Routes.HISTORY) { HistoryScreen(vm, back) }
            composable(Routes.BACKUP) { BackupScreen(vm, back) }
            composable(Routes.ABOUT) { AboutScreen(vm, back, go) }
            composable(Routes.LICENSES) { LicensesScreen(back) }
            composable(Routes.PRIVACY) { PrivacyScreen(back) }
        }
    }
    @Suppress("unused") val transparent = Color.Transparent
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
