package com.tervyn.vpn.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tervyn.vpn.MainActivity
import com.tervyn.vpn.TervynApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Drives the real app UI (MainActivity, navigation, view model, DataStore, Room) under Robolectric.
 * Screenshots are written to app/build/screenshots for visual review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 35], qualifiers = "w411dp-h891dp-xxhdpi")
class UiFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as TervynApp

    private fun advance(ms: Long = 600) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }

    private fun waitText(text: String, timeout: Long = 10_000) {
        rule.waitUntil(timeout) { advance(100); rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        val sdk = android.os.Build.VERSION.SDK_INT
        runCatching {
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "api${sdk}_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun onboard() {
        rule.mainClock.autoAdvance = false
        advance(2500) // animated splash
        // DataStore's delegate is process-wide, so a previous test in this JVM may already have onboarded.
        rule.waitUntil(10_000) {
            advance(100)
            rule.onAllNodesWithText("Welcome to").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Tap to Connect").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Tap to Connect").fetchSemanticsNodes().isNotEmpty()) return
        shot("01_onboarding")
        rule.onNodeWithText("Skip", substring = true).performClick(); advance()
        waitText("Before you start")
        shot("02_onboarding_warning")
        rule.onNodeWithText("Explore first").performScrollTo().performClick(); advance()
        waitText("Tap to Connect")
    }

    @Test fun onboardingThenHome() {
        onboard()
        rule.onNodeWithText("Fastest Available").assertIsDisplayed()
        rule.onNodeWithContentDescription("Connect").assertIsDisplayed()
        shot("03_home")
        assertTrue(runBlocking { app.container.settings.current().onboardingDone })
    }

    @Test fun tabsAndScreensAreReachable() {
        onboard()
        rule.onNodeWithText("Servers").performClick(); advance()
        waitText("Choose a country or use the fastest server")
        shot("04_servers")
        rule.onNodeWithText("See All").performClick(); advance()
        waitText("App Presets")
        shot("05_sites")
        rule.onNodeWithText("Settings").performClick(); advance()
        waitText("VPN Preferences")
        shot("06_settings")
        rule.onNodeWithText("Servers").performClick(); advance()
        waitText("Source")
        rule.onNodeWithText("Source").performScrollTo().performClick(); advance()
        waitText("Configure")
        shot("07_sources")
    }

    @Test fun addWebsiteAndSwitchMode() {
        onboard()
        rule.onNodeWithText("See All").performClick(); advance()
        waitText("Add Website")
        rule.onNodeWithText("Add Website").performClick(); advance()
        rule.onNode(hasText("Website (e.g. example.com or a link)")).performTextInput("https://www.Example.org/some/page?x=1")
        rule.onNodeWithText("Add").performClick(); advance()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.domains.all() }.any { it.domain == "example.org" } }
        waitText("example.org")
        rule.onNodeWithContentDescription("Only My Sites mode").performClick(); advance()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.settings.current().mode.name } == "ONLY_MY_SITES" }
        shot("08_sites_added")
        // Preset toggle adds all of a service's domains
        rule.onNodeWithContentDescription("YouTube").performScrollTo().performClick(); advance()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.domains.all() }.any { it.domain == "googlevideo.com" } }
    }

    @Test fun invalidWebsiteIsRejected() {
        onboard()
        rule.onNodeWithText("See All").performClick(); advance()
        waitText("Add Website")
        rule.onNodeWithText("Add Website").performClick(); advance()
        rule.onNode(hasText("Website (e.g. example.com or a link)")).performTextInput("javascript:alert(1)")
        rule.onNodeWithText("Add").performClick(); advance()
        waitText("Not a valid website")
        assertEquals(0, runBlocking { app.container.domains.all() }.size)
    }

    @Test fun addCustomProxyFormValidates() {
        onboard()
        rule.onNodeWithText("Servers").performClick(); advance()
        waitText("Add server")
        rule.onNodeWithText("Add server").performScrollTo().performClick(); advance()
        waitText("Choose the protocol")
        shot("09_protocols")
        rule.onNodeWithText("SOCKS5").performClick(); advance()
        rule.onNodeWithText("Continue").performScrollTo().performClick(); advance()
        waitText("Enter your proxy details")
        rule.onNode(hasText("Server Address")).performTextInput("bad host!")
        advance()
        waitText("Invalid host")
        shot("10_add_proxy")
    }
}
