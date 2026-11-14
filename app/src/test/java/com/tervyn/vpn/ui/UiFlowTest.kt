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
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tervyn.vpn.MainActivity
import com.tervyn.vpn.TervynApp
import com.tervyn.vpn.core.BuiltinServers
import com.tervyn.vpn.domain.ServerOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@Config(sdk = [26, 35], qualifiers = "w411dp-h2600dp-xxhdpi")
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
        // DataStore's delegate is process-wide, so a previous test in this JVM may already have onboarded.
        rule.waitUntil(10_000) {
            advance(100)
            rule.onAllNodesWithText("Private browsing", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Not connected").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Not connected").fetchSemanticsNodes().isNotEmpty()) return
        shot("01_onboarding")
        rule.onNodeWithText("Skip").performClick(); advance()
        waitText("Not connected")
    }

    @Test fun onboardingThenHome() {
        onboard()
        rule.onNodeWithContentDescription("Connect").assertIsDisplayed()
        rule.onNodeWithText("Fastest available").assertIsDisplayed()
        rule.onNodeWithText("Secure DNS").assertIsDisplayed()
        shot("02_home")
        assertTrue(runBlocking { app.container.settings.current().onboardingDone })
    }

    @Test fun builtinServersAreInstalledAndListed() {
        onboard()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.servers.count(ServerOrigin.BUILTIN) } == BuiltinServers.all.size }
        rule.onNodeWithText("Servers").performClick(); advance()
        waitText("Built-in · ${BuiltinServers.all.size} countries".uppercase())
        waitText("Germany")
        shot("03_servers")
        // Selecting a server turns auto-switch off; "Fastest available" turns it back on.
        rule.onNodeWithText("Germany").performClick(); advance()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.settings.current() }.selectedServerId == "b_de" }
        assertFalse(runBlocking { app.container.settings.current().autoSwitch })
        rule.onNodeWithText("Fastest available").performClick(); advance()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.settings.current() }.autoSwitch }
        rule.onNodeWithContentDescription("Details of Germany").performClick(); advance()
        waitText("Verified exit country")
        shot("04_server_detail")
    }

    @Test fun secureDnsToggleIsIndependentOfServerSelection() {
        onboard()
        rule.onNodeWithText("Secure DNS").performClick(); advance()
        waitText("Encrypts every DNS lookup")
        shot("05_dns")
        rule.onNodeWithContentDescription("Google (8.8.8.8)", substring = true).performClick(); advance()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.settings.current() }.dohUrl == "https://8.8.8.8/dns-query" }
        // The setting flips without touching the server selection (no VPN consent exists in Robolectric, so no tunnel starts).
        runBlocking { app.container.controller.setSecureDns(true) }
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.settings.current() }.secureDns }
        assertTrue(runBlocking { app.container.settings.current() }.autoSwitch)
        runBlocking { app.container.controller.setSecureDns(false) }
        rule.waitUntil(10_000) { advance(100); !runBlocking { app.container.settings.current() }.secureDns }
    }

    @Test fun addWebsiteAndSwitchMode() {
        onboard()
        rule.onNodeWithText("Settings").performClick(); advance()
        waitText("Routing mode")
        shot("06_settings")
        rule.onNodeWithText("Routing mode").performClick(); advance()
        waitText("Add a website".uppercase())
        rule.onNode(hasText("Website")).performTextInput("https://www.Example.org/some/page?x=1")
        rule.onNodeWithContentDescription("Add website").performClick(); advance()
        assertTrue("view model must use this Application's container", rule.activity.vm.c === app.container)
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.domains.all() }.any { it.domain == "example.org" } }
        waitText("example.org")
        rule.onNodeWithContentDescription("Only my sites").performClick(); advance()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.settings.current().mode.name } == "ONLY_MY_SITES" }
        rule.onNodeWithContentDescription("YouTube").performClick(); advance()
        rule.waitUntil(10_000) { advance(100); runBlocking { app.container.domains.all() }.any { it.domain == "googlevideo.com" } }
        shot("07_sites")
    }

    @Test fun invalidWebsiteIsRejected() {
        onboard()
        rule.onNodeWithText("Settings").performClick(); advance()
        waitText("Routing mode")
        rule.onNodeWithText("Routing mode").performClick(); advance()
        waitText("Add a website".uppercase())
        val before = runBlocking { app.container.domains.all() }.size
        rule.onNode(hasText("Website")).performTextInput("javascript:alert(1)")
        rule.onNodeWithContentDescription("Add website").performClick(); advance()
        waitText("Not a valid website")
        assertEquals(before, runBlocking { app.container.domains.all() }.size)
    }

    @Test fun addServerValidatesLinks() {
        onboard()
        rule.onNodeWithText("Servers").performClick(); advance()
        rule.onNodeWithContentDescription("Add server").performClick(); advance()
        waitText("Paste one or more share links")
        rule.onNode(hasText("Link or subscription URL")).performTextInput("vless://not-a-valid-link")
        advance()
        rule.waitUntil(10_000) { advance(100); rule.onAllNodesWithText("Add and test").fetchSemanticsNodes().isNotEmpty() }
        shot("08_add_server")
        rule.onNodeWithText("Show").performClick(); advance()
        waitText("Server address")
        rule.onNode(hasText("Server address")).performTextInput("bad host!")
        advance()
        waitText("Invalid host")
    }
}
