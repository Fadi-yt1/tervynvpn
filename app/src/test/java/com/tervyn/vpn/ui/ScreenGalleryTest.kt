package com.tervyn.vpn.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.ui.screens.AboutScreen
import com.tervyn.vpn.ui.screens.AddServerScreen
import com.tervyn.vpn.ui.screens.CheckIpScreen
import com.tervyn.vpn.ui.screens.DiagnosticsScreen
import com.tervyn.vpn.ui.screens.DnsScreen
import com.tervyn.vpn.ui.screens.HistoryScreen
import com.tervyn.vpn.ui.screens.HomeScreen
import com.tervyn.vpn.ui.screens.NotificationsScreen
import com.tervyn.vpn.ui.screens.OnboardingScreen
import com.tervyn.vpn.ui.screens.PrivacyScreen
import com.tervyn.vpn.ui.screens.ServerDetailScreen
import com.tervyn.vpn.ui.screens.ServersScreen
import com.tervyn.vpn.ui.screens.SettingsScreen
import com.tervyn.vpn.ui.screens.SitesScreen
import com.tervyn.vpn.ui.screens.SpeedTestScreen
import com.tervyn.vpn.ui.screens.SubscriptionsScreen
import com.tervyn.vpn.ui.theme.TervynTheme
import com.tervyn.vpn.vpn.Phase
import com.tervyn.vpn.vpn.VpnStatus
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen with the real ViewModel and saves a tall screenshot to app/build/screenshots/gallery
 * for design review. Also acts as a smoke test: each screen must compose without throwing.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp-xxhdpi")
class ScreenGalleryTest(private val name: String) {
    @get:Rule val rule = createComposeRule()

    companion object {
        private val screens: Map<String, @Composable (MainViewModel) -> Unit> = linkedMapOf(
            "home" to { vm -> HomeScreen(vm, {}, {}, {}) },
            "home_connected" to { vm -> HomeScreen(vm, {}, {}, {}) },
            "home_dns_only" to { vm -> HomeScreen(vm, {}, {}, {}) },
            "home_failed" to { vm -> HomeScreen(vm, {}, {}, {}) },
            "servers" to { vm -> ServersScreen(vm, {}) },
            "server_detail" to { vm -> ServerDetailScreen(vm, "b_de", {}, {}) },
            "add_server" to { vm -> AddServerScreen(vm, {}) },
            "subscriptions" to { vm -> SubscriptionsScreen(vm, {}, {}) },
            "dns" to { vm -> DnsScreen(vm, {}, {}) },
            "settings" to { vm -> SettingsScreen(vm, {}) },
            "notifications" to { vm -> NotificationsScreen(vm, {}) },
            "sites" to { vm -> SitesScreen(vm, {}) },
            "check_ip" to { vm -> CheckIpScreen(vm, {}) },
            "speed_test" to { vm -> SpeedTestScreen(vm, {}) },
            "diagnostics" to { vm -> DiagnosticsScreen(vm, {}) },
            "history" to { vm -> HistoryScreen(vm, {}) },
            "about" to { vm -> AboutScreen(vm, {}, {}) },
            "privacy" to { _ -> PrivacyScreen({}) },
            "onboarding" to { vm -> OnboardingScreen(vm) {} },
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = screens.keys.map { arrayOf<Any>(it) }
    }

    @Test fun renders() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        runBlocking { vm.c.serverManager.ensureBuiltin() }
        val sample = Server("b_de", "Germany", "VLESS · REALITY", xrayLink = "vless://x@h:443", origin = ServerOrigin.BUILTIN,
            countryCode = "DE", declaredCountry = "DE", latencyMs = 41, alive = true, lastChecked = System.currentTimeMillis())
        when (name) {
            "home_connected" -> vm.c.controller.set {
                VpnStatus(phase = Phase.CONNECTED, server = sample, countryCode = "DE", exitIp = "203.0.113.7", pingMs = 41,
                    downBps = 3_100_000, upBps = 2_325_000, connectedSince = System.currentTimeMillis() - 754_000)
            }
            "home_dns_only" -> vm.c.controller.set { VpnStatus(phase = Phase.DNS_ONLY, dnsQueries = 128) }
            "home_failed" -> vm.c.controller.set { VpnStatus(phase = Phase.FAILED, error = "No working server found (20 tested). Check your internet connection and try again.") }
            else -> vm.c.controller.set { VpnStatus() }
        }
        rule.mainClock.autoAdvance = false
        rule.setContent { TervynTheme { screens.getValue(name)(vm) } }
        repeat(10) { rule.mainClock.advanceTimeBy(200); rule.waitForIdle() }
        val dir = File("build/screenshots/gallery").apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
