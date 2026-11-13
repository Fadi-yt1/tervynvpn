package com.tervyn.vpn.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.tervyn.vpn.ui.screens.*
import com.tervyn.vpn.ui.theme.TervynTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every redesigned screen with the real ViewModel and saves a tall screenshot to
 * app/build/screenshots/gallery for design review. Also acts as a smoke test: each screen must compose.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2100dp-xxhdpi")
class ScreenGalleryTest(private val name: String) {
    @get:Rule val rule = createComposeRule()

    companion object {
        private val screens: Map<String, @Composable (MainViewModel) -> Unit> = linkedMapOf(
            "home" to { vm -> HomeScreen(vm, {}, {}) },
            "home_connected" to { vm -> HomeScreen(vm, {}, {}) },
            "servers" to { vm -> ServersScreen(vm, {}, {}) },
            "settings" to { vm -> SettingsScreen(vm, {}, {}, {}) },
            "connecting" to { vm -> ConnectingScreen(vm, {}, {}) },
            "connected" to { vm -> ConnectedScreen(vm, {}, {}) },
            "check_ip" to { vm -> CheckIpScreen(vm, {}) },
            "speed_test" to { vm -> SpeedTestScreen(vm, {}, {}) },
            "history" to { vm -> HistoryScreen(vm, {}) },
            "diagnostics" to { vm -> DiagnosticsScreen(vm, {}, {}) },
            "sources" to { vm -> SourcesScreen(vm, {}, {}) },
            "protocols" to { _ -> ProtocolSelectionScreen({}, {}) },
            "add_proxy" to { vm -> AddProxyScreen(vm, {}, {}) },
            "import_link" to { vm -> ImportLinkScreen(vm, {}, {}) },
            "dns" to { vm -> DnsScreen(vm, {}) },
            "privacy" to { vm -> PrivacyCenterScreen(vm, {}, {}) },
            "notifications" to { vm -> NotificationsScreen(vm, {}) },
            "split" to { vm -> SplitTunnelScreen(vm, {}) },
            "about" to { vm -> AboutScreen(vm, {}, {}) },
            "permissions" to { vm -> PermissionsScreen(vm, {}, {}) },
            "auto_switch" to { vm -> AutoSwitchScreen(vm, {}) },
            "sites" to { vm -> SitesScreen(vm, {}) },
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = screens.keys.map { arrayOf<Any>(it) }
    }

    @Test fun renders() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        // Display-only sample state so connection screens can be reviewed without a device.
        val sample = com.tervyn.vpn.domain.Server("s1", "Free SOCKS5 · 203.0.113.7", "SOCKS5", origin = com.tervyn.vpn.domain.ServerOrigin.FREE_PUBLIC,
            countryCode = "US", latencyMs = 28, alive = true, lastChecked = System.currentTimeMillis())
        when (name) {
            "connecting" -> vm.c.controller.set { com.tervyn.vpn.vpn.VpnStatus(phase = com.tervyn.vpn.vpn.Phase.TESTING, tested = 410, testTotal = 600, working = 7, server = sample) }
            "connected", "home_connected" -> vm.c.controller.set { com.tervyn.vpn.vpn.VpnStatus(phase = com.tervyn.vpn.vpn.Phase.CONNECTED, server = sample, countryCode = "US",
                exitIp = "203.0.113.7", pingMs = 28, downBps = 3_100_000, upBps = 2_325_000, connectedSince = System.currentTimeMillis() - 754_000) }
        }
        rule.mainClock.autoAdvance = false
        rule.setContent { TervynTheme { screens.getValue(name)(vm) } }
        repeat(10) { rule.mainClock.advanceTimeBy(200); rule.waitForIdle() }
        val dir = File("build/screenshots/gallery").apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
