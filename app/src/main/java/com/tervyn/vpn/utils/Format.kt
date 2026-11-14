package com.tervyn.vpn.utils

import java.util.Locale

object Format {
    fun speed(bps: Long): String = when {
        bps >= 1_000_000 -> String.format(Locale.US, "%.1f MB/s", bps / 1_000_000.0)
        bps >= 1_000 -> String.format(Locale.US, "%.0f KB/s", bps / 1_000.0)
        else -> "$bps B/s"
    }

    fun mbps(bitsPerSec: Double): String = String.format(Locale.US, "%.1f Mbps", bitsPerSec / 1_000_000)

    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / (1L shl 10).toDouble())
        else -> "$b B"
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
        if (t <= 0) return "never"
        val d = (now - t) / 1000
        return when {
            d < 60 -> "just now"
            d < 3600 -> "${d / 60} min ago"
            d < 86400 -> "${d / 3600} h ago"
            else -> "${d / 86400} d ago"
        }
    }

    /** Regional-indicator emoji flag for an ISO 3166-1 alpha-2 code. */
    fun flag(cc: String?): String {
        if (cc == null || cc.length != 2 || !cc.all { it.isLetter() }) return "🌐"
        val u = cc.uppercase()
        return String(Character.toChars(0x1F1E6 + (u[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (u[1] - 'A')))
    }

    /** ISO code of the first regional-indicator flag in [text] (e.g. "🇩🇪 Germany" -> "DE"), or null. */
    fun countryCodeFromFlag(text: String): String? {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            if (cp in 0x1F1E6..0x1F1FF && i + n < text.length) {
                val cp2 = text.codePointAt(i + n)
                if (cp2 in 0x1F1E6..0x1F1FF) {
                    val code = "" + ('A' + (cp - 0x1F1E6)) + ('A' + (cp2 - 0x1F1E6))
                    return code.takeIf { countryName(it) != it }
                }
            }
            i += n
        }
        return null
    }

    private val shortNames = mapOf("HK" to "Hong Kong", "MO" to "Macao", "TW" to "Taiwan", "US" to "United States", "GB" to "United Kingdom", "AE" to "United Arab Emirates", "KR" to "South Korea")

    fun countryName(cc: String?): String {
        if (cc == null) return "Unknown"
        shortNames[cc.uppercase()]?.let { return it }
        val n = Locale.Builder().setRegion(cc).build().getDisplayCountry(Locale.ENGLISH)
        return n.ifBlank { cc }
    }
}
