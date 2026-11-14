package com.tervyn.vpn.core

/** Timings and thresholds. Deliberately not user-facing: sensible defaults that the app owns. */
object Tuning {
    /** Per-server HTTPS verification timeout. */
    const val TEST_TIMEOUT_MS = 8_000
    /** Servers tested in parallel (Xray servers share one process; each gets its own local SOCKS port). */
    const val TEST_CONCURRENCY = 24
    /** A backup must be at least this much faster (latency reduced by this %) before auto-switch moves. */
    const val SWITCH_THRESHOLD_PCT = 40
    /** Health check interval while connected. */
    const val HEALTH_CHECK_SEC = 120
    /** Re-test backups / replace dead built-in servers this often while connected. */
    const val BUILTIN_REFRESH_MIN = 30
    /** A verification result younger than this is trusted for the fast connect path. */
    const val RECENT_RESULT_MS = 45 * 60_000L
}
