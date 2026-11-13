package com.tervyn.vpn.core

import com.tervyn.vpn.domain.Server

/** Server ranking and the auto-switch decision policy (pure logic, unit tested). */
object Ranking {

    /** Lower is better. Unverified/dead servers rank last. Recent failures add a penalty. */
    fun score(s: Server, now: Long = System.currentTimeMillis()): Double {
        if (!s.alive || s.latencyMs == null) return Double.MAX_VALUE
        val agePenalty = ((now - s.lastChecked).coerceAtLeast(0) / 60_000.0) * 2.0 // +2 ms per minute since last check
        val failPenalty = s.failCount * 250.0
        val favoriteBonus = if (s.favorite) -15.0 else 0.0
        return s.latencyMs + agePenalty + failPenalty + favoriteBonus
    }

    fun rank(servers: List<Server>, country: String? = null, now: Long = System.currentTimeMillis()): List<Server> =
        servers.filter { it.alive && it.latencyMs != null && (country == null || it.countryCode == country) }
            .sortedBy { score(it, now) }

    data class SwitchDecision(val switchTo: Server?, val reason: String)

    /**
     * Decides whether to move off the active server.
     * - Active dead: switch to the best backup (only when auto-switch is on).
     * - Active alive: switch only if a backup is at least [thresholdPct]% faster, the advantage was observed on
     *   [requiredConsecutive] consecutive checks (hysteresis), and the last switch was at least [minIntervalMs] ago.
     */
    fun decide(
        autoSwitch: Boolean,
        active: Server?,
        activeAlive: Boolean,
        candidates: List<Server>,
        thresholdPct: Int,
        consecutiveFasterChecks: Int,
        requiredConsecutive: Int = 2,
        lastSwitchAt: Long,
        now: Long,
        minIntervalMs: Long = 5 * 60_000L,
    ): SwitchDecision {
        if (!autoSwitch) return SwitchDecision(null, "Auto-switch disabled")
        val best = candidates.firstOrNull { it.id != active?.id && it.alive && it.latencyMs != null }
        if (active == null || !activeAlive) {
            return if (best != null) SwitchDecision(best, "Active server unavailable") else SwitchDecision(null, "No backup available")
        }
        if (best == null) return SwitchDecision(null, "No faster server")
        val cur = active.latencyMs ?: return SwitchDecision(null, "Active latency unknown")
        val faster = isClearlyFaster(best.latencyMs!!, cur, thresholdPct)
        if (!faster) return SwitchDecision(null, "Current server is fast enough")
        if (consecutiveFasterChecks + 1 < requiredConsecutive) return SwitchDecision(null, "Waiting to confirm faster server")
        if (now - lastSwitchAt < minIntervalMs) return SwitchDecision(null, "Switched recently")
        return SwitchDecision(best, "Found a server ${(100 - best.latencyMs * 100 / cur)}% faster")
    }

    /** `candidate` is at least [thresholdPct]% faster than `current` (latency reduced by thresholdPct %). */
    fun isClearlyFaster(candidateMs: Int, currentMs: Int, thresholdPct: Int): Boolean =
        candidateMs.toLong() * 100 <= currentMs.toLong() * (100 - thresholdPct)
}
