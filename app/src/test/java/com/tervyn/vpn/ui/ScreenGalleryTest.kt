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
@Config(sdk = [35], qualifiers = "w411dp-h1600dp-xxhdpi")
class ScreenGalleryTest(private val name: String) {
    @get:Rule val rule = createComposeRule()

    companion object {
        private val screens: Map<String, @Composable (MainViewModel) -> Unit> = linkedMapOf(
            "connecting" to { vm -> ConnectingScreen(vm, {}, {}) },
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
        rule.mainClock.autoAdvance = false
        rule.setContent { TervynTheme { screens.getValue(name)(vm) } }
        repeat(10) { rule.mainClock.advanceTimeBy(200); rule.waitForIdle() }
        val dir = File("build/screenshots/gallery").apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
