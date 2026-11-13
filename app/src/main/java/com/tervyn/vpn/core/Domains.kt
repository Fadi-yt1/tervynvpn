package com.tervyn.vpn.core

import java.net.IDN
import java.net.URI

/** Domain normalisation, validation, matching and the app presets for "Only My Sites". */
object Domains {

    data class Preset(val id: String, val label: String, val domains: List<String>, val cidrs: List<String> = emptyList())

    /**
     * Domain sets for popular services. Some apps (notably Telegram) connect to hard-coded IP addresses
     * rather than resolving names, so their published IP ranges are routed too.
     */
    val presets = listOf(
        Preset("youtube", "YouTube", listOf("youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "ggpht.com", "youtube-nocookie.com", "youtubei.googleapis.com", "yt3.ggpht.com")),
        Preset("instagram", "Instagram", listOf("instagram.com", "cdninstagram.com", "ig.me")),
        Preset("facebook", "Facebook", listOf("facebook.com", "fb.com", "fbcdn.net", "facebook.net", "fb.me", "messenger.com", "fbsbx.com")),
        Preset("x", "X", listOf("x.com", "twitter.com", "twimg.com", "t.co", "x.ai")),
        Preset("discord", "Discord", listOf("discord.com", "discord.gg", "discordapp.com", "discordapp.net", "discord.media", "discordcdn.com")),
        Preset(
            "telegram", "Telegram", listOf("telegram.org", "t.me", "telegram.me", "telesco.pe", "tdesktop.com", "telegra.ph"),
            cidrs = listOf("91.108.4.0/22", "91.108.8.0/22", "91.108.12.0/22", "91.108.16.0/22", "91.108.20.0/22", "91.108.56.0/22", "149.154.160.0/20", "95.161.64.0/20", "185.76.151.0/24"),
        ),
        Preset("chatgpt", "ChatGPT", listOf("openai.com", "chatgpt.com", "oaistatic.com", "oaiusercontent.com", "chat.com")),
        Preset("claude", "Claude", listOf("claude.ai", "anthropic.com", "claudeusercontent.com", "claude.com")),
        Preset("netflix", "Netflix", listOf("netflix.com", "netflix.net", "nflxvideo.net", "nflximg.net", "nflxext.com", "nflxso.net")),
        Preset("whatsapp", "WhatsApp", listOf("whatsapp.com", "whatsapp.net", "wa.me")),
        Preset("spotify", "Spotify", listOf("spotify.com", "scdn.co", "spotifycdn.com", "spotify.design")),
        Preset("reddit", "Reddit", listOf("reddit.com", "redd.it", "redditmedia.com", "redditstatic.com")),
        Preset("tiktok", "TikTok", listOf("tiktok.com", "tiktokcdn.com", "tiktokv.com", "byteoversea.com", "ibytedtos.com")),
        Preset("google", "Google", listOf("google.com", "googleapis.com", "gstatic.com", "googleusercontent.com")),
    )

    private val labelRegex = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    /**
     * Normalises user input or a shared URL into a registrable host name, or returns null when invalid.
     * Accepts `https://www.example.com/path?x`, `example.com`, `*.example.com`, `.example.com`, IDNs.
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty() || s.length > 2048) return null
        if (s.any { it.isISOControl() || it.isWhitespace() }) {
            s = s.split(Regex("\\s+")).firstOrNull { it.contains('.') } ?: return null
        }
        if (s.contains("://")) {
            val scheme = s.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") return null
            s = runCatching { URI(s).host }.getOrNull() ?: s.substringAfter("://").substringBefore('/').substringBefore('?')
        } else {
            s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        }
        s = s.substringAfterLast('@').removePrefix("*.").trimStart('.').trimEnd('.')
        if (s.startsWith("[")) return null
        s = s.substringBefore(':') // strip port
        val ascii = try { IDN.toASCII(s, IDN.ALLOW_UNASSIGNED).lowercase() } catch (_: Exception) { return null }
        if (ascii.length > 253 || !ascii.contains('.')) return null
        if (ProxyList.isIpv4(ascii)) return null
        val labels = ascii.split('.')
        if (labels.any { !labelRegex.matches(it) }) return null
        if (labels.last().all { it.isDigit() }) return null
        return ascii.removePrefix("www.").takeIf { it.contains('.') } ?: ascii
    }

    /** Matches a domain and all of its subdomains. */
    class Matcher(domains: Collection<String>) {
        private val set = domains.map { it.lowercase().trimEnd('.') }.toHashSet()
        fun matches(host: String): Boolean {
            var h = host.lowercase().trimEnd('.')
            while (true) {
                if (set.contains(h)) return true
                val dot = h.indexOf('.')
                if (dot < 0) return false
                h = h.substring(dot + 1)
            }
        }
        val isEmpty get() = set.isEmpty()
    }

    /** Names that must never be routed through a remote proxy (local network names, reverse lookups). */
    fun isLocalName(name: String): Boolean {
        val n = name.lowercase().trimEnd('.')
        return !n.contains('.') || n.endsWith(".local") || n.endsWith(".arpa") || n.endsWith(".lan") ||
            n.endsWith(".internal") || n.endsWith(".localhost") || n == "localhost"
    }

    /** Parses a CIDR into (network, prefix). */
    fun parseCidr(cidr: String): Pair<Int, Int>? {
        val ip = cidr.substringBefore('/')
        val prefix = cidr.substringAfter('/', "32").toIntOrNull() ?: return null
        if (!ProxyList.isIpv4(ip) || prefix !in 0..32) return null
        val n = com.tervyn.vpn.vpn.Packets.ipFromString(ip)
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        return (n and mask) to prefix
    }

    fun cidrContains(net: Pair<Int, Int>, ip: Int): Boolean {
        val mask = if (net.second == 0) 0 else (-1 shl (32 - net.second))
        return (ip and mask) == net.first
    }
}
