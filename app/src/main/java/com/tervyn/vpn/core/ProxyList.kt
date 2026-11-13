package com.tervyn.vpn.core

import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import java.net.URI
import java.net.URLDecoder

/**
 * Parses proxy lists (one proxy per line). Supported line formats:
 *  - `host:port`
 *  - `host:port:user:pass`
 *  - `user:pass@host:port`
 *  - `user:pass:host:port`
 *  - full URLs: `http://`, `https://` (HTTP CONNECT), `socks5://`, `socks5h://`, `socks://`, with optional credentials
 * Lines may carry trailing columns (e.g. `1.2.3.4:1080 US`) and `#` comments. Invalid lines are skipped.
 */
object ProxyList {

    data class ParseReport(val proxies: List<ProxyEndpoint>, val invalidLines: Int, val duplicates: Int)

    fun parse(text: String, defaultProtocol: ProxyProtocol = ProxyProtocol.HTTP): ParseReport {
        val seen = LinkedHashMap<String, ProxyEndpoint>()
        var invalid = 0
        var dupes = 0
        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val ep = parseLine(line, defaultProtocol)
            if (ep == null) invalid++
            else if (seen.putIfAbsent(ep.key, ep) != null) dupes++
        }
        return ParseReport(seen.values.toList(), invalid, dupes)
    }

    fun parseLine(input: String, defaultProtocol: ProxyProtocol = ProxyProtocol.HTTP): ProxyEndpoint? {
        val token = input.trim().split(Regex("[\\s,;|]+")).firstOrNull()?.trim() ?: return null
        if (token.isEmpty() || token.length > 512) return null
        val schemeIdx = token.indexOf("://")
        if (schemeIdx > 0) return parseUrl(token)
        return parseBare(token, defaultProtocol)
    }

    fun protocolForScheme(scheme: String): ProxyProtocol? = when (scheme.lowercase()) {
        "http", "https" -> ProxyProtocol.HTTP
        "socks5", "socks5h", "socks" -> ProxyProtocol.SOCKS5
        else -> null
    }

    fun parseUrl(url: String): ProxyEndpoint? {
        val scheme = url.substringBefore("://")
        val proto = protocolForScheme(scheme) ?: return null
        val rest = url.substringAfter("://").substringBefore('/').substringBefore('?')
        val ep = parseBare(rest, proto) ?: return null
        return ep.copy(protocol = proto)
    }

    private fun parseBare(s: String, proto: ProxyProtocol): ProxyEndpoint? {
        // user:pass@host:port (userinfo may contain ':' in the password, so split on the LAST '@')
        val at = s.lastIndexOf('@')
        if (at > 0) {
            val userInfo = s.substring(0, at)
            val hp = splitHostPort(s.substring(at + 1)) ?: return null
            val user = decode(userInfo.substringBefore(':'))
            val pass = if (userInfo.contains(':')) decode(userInfo.substringAfter(':')) else null
            return build(proto, hp.first, hp.second, user, pass)
        }
        if (s.startsWith("[")) { // [ipv6]:port
            val hp = splitHostPort(s) ?: return null
            return build(proto, hp.first, hp.second, null, null)
        }
        val parts = s.split(':')
        return when (parts.size) {
            2 -> build(proto, parts[0], parts[1].toIntOrNull() ?: return null, null, null)
            4 -> {
                val p1 = parts[1].toIntOrNull()
                val p3 = parts[3].toIntOrNull()
                when {
                    p1 != null && isValidHost(parts[0]) && validPort(p1) -> build(proto, parts[0], p1, parts[2], parts[3])
                    p3 != null && isValidHost(parts[2]) && validPort(p3) -> build(proto, parts[2], p3, parts[0], parts[1])
                    else -> null
                }
            }
            else -> {
                // host:port:user:pass where the password itself contains ':'
                if (parts.size > 4) {
                    val p1 = parts[1].toIntOrNull()
                    if (p1 != null && isValidHost(parts[0])) {
                        return build(proto, parts[0], p1, parts[2], parts.drop(3).joinToString(":"))
                    }
                }
                null
            }
        }
    }

    /** Splits `host:port` or `[v6]:port`. */
    fun splitHostPort(s: String): Pair<String, Int>? {
        val str = s.trim()
        if (str.startsWith("[")) {
            val end = str.indexOf(']')
            if (end < 0 || end + 2 > str.length || str[end + 1] != ':') return null
            val port = str.substring(end + 2).toIntOrNull() ?: return null
            return str.substring(1, end) to port
        }
        val idx = str.lastIndexOf(':')
        if (idx <= 0) return null
        val port = str.substring(idx + 1).toIntOrNull() ?: return null
        return str.substring(0, idx) to port
    }

    private fun build(proto: ProxyProtocol, host: String, port: Int, user: String?, pass: String?): ProxyEndpoint? {
        val h = host.trim().removeSurrounding("[", "]")
        if (!isValidHost(h) || !validPort(port)) return null
        val u = user?.takeIf { it.isNotEmpty() }
        if (u != null && (u.length > 255 || (pass?.length ?: 0) > 255)) return null
        return ProxyEndpoint(proto, h, port, u, if (u != null) pass ?: "" else null)
    }

    fun validPort(p: Int) = p in 1..65535

    private val hostnameRegex = Regex("^(?=.{1,253}$)([a-zA-Z0-9_]([a-zA-Z0-9_-]{0,61}[a-zA-Z0-9])?)(\\.[a-zA-Z0-9_]([a-zA-Z0-9_-]{0,61}[a-zA-Z0-9])?)*$")

    fun isIpv4(h: String): Boolean {
        val p = h.split('.')
        return p.size == 4 && p.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) && it.toInt() <= 255 }
    }

    fun isIpv6(h: String): Boolean = h.contains(':') && h.all { it.isLetterOrDigit() || it == ':' || it == '.' } &&
        runCatching { URI("http://[$h]/").host != null }.getOrDefault(false)

    fun isValidHost(h: String): Boolean {
        if (h.isEmpty()) return false
        if (isIpv4(h)) return true
        if (h.contains(':')) return isIpv6(h)
        // Reject things that look like a malformed IPv4 address (all digits and dots)
        if (h.all { it.isDigit() || it == '.' }) return false
        return hostnameRegex.matches(h)
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    /** Formats an endpoint back to a URL (used for export). Includes the password. */
    fun toUrl(ep: ProxyEndpoint): String {
        val scheme = if (ep.protocol == ProxyProtocol.SOCKS5) "socks5" else "http"
        val host = if (ep.host.contains(':')) "[${ep.host}]" else ep.host
        val auth = if (ep.hasAuth) "${enc(ep.username!!)}:${enc(ep.password ?: "")}@" else ""
        return "$scheme://$auth$host:${ep.port}"
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
