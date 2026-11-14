package com.tervyn.vpn

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke UI test on a real device/emulator (the full flows also run on the JVM via Robolectric). */
@RunWith(AndroidJUnit4::class)
class UiInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun homeOrOnboardingAppears() {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(3000)
        rule.waitUntil(10_000) {
            rule.mainClock.advanceTimeBy(100)
            rule.onAllNodesWithText("Not connected").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Skip").performClick()
            rule.mainClock.advanceTimeBy(500)
        }
    }
}
