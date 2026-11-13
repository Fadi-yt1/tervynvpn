package com.tervyn.vpn.core

import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {
    private val now = 10_000_000L
    private fun srv(id: String, ms: Int?, alive: Boolean = true, cc: String? = "US", fails: Int = 0, fav: Boolean = false) =
        Server(id, id, "SOCKS5", origin = ServerOrigin.FREE_PUBLIC, latencyMs = ms, alive = alive, countryCode = cc, lastChecked = now, failCount = fails, favorite = fav)

    @Test fun ranksByLatencyAndPenalties() {
        val r = Ranking.rank(listOf(srv("a", 300), srv("b", 100), srv("c", 50, alive = false), srv("d", 90, fails = 1), srv("e", null)), now = now)
        assertEquals(listOf("b", "a", "d"), r.map { it.id })
    }

    @Test fun filtersByCountry() {
        assertEquals(listOf("de"), Ranking.rank(listOf(srv("us", 10), srv("de", 50, cc = "DE")), "DE", now).map { it.id })
    }

    @Test fun fortyPercentRule() {
        assertTrue(Ranking.isClearlyFaster(60, 100, 40))
        assertFalse(Ranking.isClearlyFaster(61, 100, 40))
        assertFalse(Ranking.isClearlyFaster(90, 100, 40))
    }

    @Test fun noSwitchWhenAutoSwitchOff() {
        val d = Ranking.decide(false, srv("a", 500), false, listOf(srv("b", 10)), 40, 5, lastSwitchAt = 0, now = now)
        assertNull(d.switchTo)
    }

    @Test fun failoverWhenActiveDead() {
        val d = Ranking.decide(true, srv("a", 100), false, listOf(srv("b", 300)), 40, 0, lastSwitchAt = now, now = now)
        assertEquals("b", d.switchTo?.id)
    }

    @Test fun switchesOnlyWhenClearlyFasterConfirmedAndNotRecent() {
        val active = srv("a", 200)
        val fast = listOf(srv("b", 100))
        assertNull(Ranking.decide(true, active, true, fast, 40, consecutiveFasterChecks = 0, lastSwitchAt = 0, now = now).switchTo) // needs confirmation
        assertEquals("b", Ranking.decide(true, active, true, fast, 40, consecutiveFasterChecks = 1, lastSwitchAt = 0, now = now).switchTo?.id)
        assertNull(Ranking.decide(true, active, true, fast, 40, consecutiveFasterChecks = 1, lastSwitchAt = now - 60_000, now = now).switchTo) // too soon
        assertNull(Ranking.decide(true, active, true, listOf(srv("c", 150)), 40, 3, lastSwitchAt = 0, now = now).switchTo) // only 25% faster
    }

    @Test fun neverSwitchesToItself() {
        val a = srv("a", 100)
        assertNull(Ranking.decide(true, a, true, listOf(a), 40, 5, lastSwitchAt = 0, now = now).switchTo)
    }
}
