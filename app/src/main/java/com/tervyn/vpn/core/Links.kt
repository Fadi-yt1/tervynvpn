package com.tervyn.vpn.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/** Protocols that are run through Xray-core. */
@Serializable
enum class XrayProtocol(val label: String) {
    VLESS("VLESS"), VMESS("VMess"), TROJAN("Trojan"), SHADOWSOCKS("Shadowsocks"), SOCKS("SOCKS5"), HTTP("HTTP"), HYSTERIA2("Hysteria2")
}

/**
 * Normalised description of an Xray outbound, parsed from a share link or built by the manual
 * configuration screen. Converted to JSON by [com.tervyn.vpn.xray.XrayConfig].
 */
@Serializable
data class XrayOutbound(
    val protocol: XrayProtocol,
    val address: String,
    val port: Int,
    val name: String = "",
    /** VLESS/VMess UUID. */
    val id: String = "",
    /** Trojan / Shadowsocks / SOCKS / HTTP password. */
    val password: String = "",
    /** SOCKS / HTTP username. */
    val username: String = "",
    /** Shadowsocks cipher or VMess security (auto, aes-128-gcm, ...). */
    val method: String = "",
    /** VLESS encryption (normally "none"). */
    val encryption: String = "none",
    /** VLESS flow, e.g. xtls-rprx-vision. */
    val flow: String = "",
    /** raw/tcp, ws, grpc, xhttp, httpupgrade. */
    val network: String = "tcp",
    /** none, tls, reality. */
    val security: String = "none",
    val sni: String = "",
    val fingerprint: String = "",
    val alpn: List<String> = emptyList(),
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    val mode: String = "",
    val headerType: String = "",
    /** Certificate pinning: SHA-256 of the peer certificate (hex, comma separated). */
    val pinnedPeerCertSha256: String = "",
    val verifyPeerCertByName: String = "",
    /** Hysteria2 obfuscation ("salamander") and its password. */
    val obfs: String = "",
    val obfsPassword: String = "",
    /** The link asked for insecure TLS; it is ignored (Xray removed allowInsecure) and surfaced as a warning. */
    val insecureRequested: Boolean = false,
) {
    val displayProtocol: String
        get() = buildString {
            append(protocol.label)
            if (protocol == XrayProtocol.HYSTERIA2) { if (obfs.isNotEmpty()) append(" · ${obfs.replaceFirstChar { it.uppercase() }}"); return@buildString }
            if (security == "reality") append(" · REALITY")
            else if (security == "tls") append(" · TLS")
            if (flow.contains("vision")) append(" · Vision")
            if (network !in listOf("tcp", "raw")) append(" · ${network.uppercase()}")
        }
}

class LinkParseException(message: String) : Exception(message)

/** Parses vless://, vmess://, trojan://, ss://, socks://, socks5://, http(s):// share links. */
object Links {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val supportedSchemes = listOf("vless", "vmess", "trojan", "ss", "socks", "socks5", "http", "https")

    fun isShareLink(s: String): Boolean {
        val scheme = s.trim().substringBefore("://", "").lowercase()
        return scheme in listOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2")
    }

    fun parse(link: String): XrayOutbound {
        // Some subscription generators HTML-escape the query separator.
        val s = link.trim().replace("&amp;", "&")
        val scheme = s.substringBefore("://", "").lowercase()
        return when (scheme) {
            "vless" -> parseVless(s)
            "vmess" -> parseVmess(s)
            "trojan" -> parseTrojan(s)
            "ss" -> parseShadowsocks(s)
            "socks", "socks5", "socks5h" -> parseSocks(s)
            "http", "https" -> parseHttp(s)
            "hysteria2", "hy2" -> parseHysteria2(s)
            "tuic" -> throw LinkParseException("TUIC is not supported by Xray-core")
            "ssr" -> throw LinkParseException("ShadowsocksR is not supported by Xray-core")
            "hysteria" -> throw LinkParseException("Hysteria v1 is not supported by Xray-core (only Hysteria2)")
            else -> throw LinkParseException("Unsupported link type: ${scheme.ifEmpty { "unknown" }}")
        }
    }

    /** Parses many links (subscription bodies may be base64 encoded). Returns successes and the error count. */
    /** Result of parsing a subscription: usable links, skipped counts by reason, and profile metadata. */
    data class Subscription(
        val links: List<Pair<String, XrayOutbound>>,
        val skipped: Map<String, Int>,
        val title: String?,
        val updateIntervalHours: Int?,
    ) {
        val skippedTotal get() = skipped.values.sum()
    }

    fun parseSubscription(body: String): Subscription {
        val decoded = decodeSubscription(body)
        var title: String? = null
        var interval: Int? = null
        val ok = LinkedHashMap<String, Pair<String, XrayOutbound>>()
        val skipped = LinkedHashMap<String, Int>()
        decoded.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("#profile-title:", true) -> title = line.substringAfter(':').trim().take(80)
                line.startsWith("#profile-update-interval:", true) -> interval = line.substringAfter(':').trim().toIntOrNull()
                line.contains("://") && !line.startsWith("#") -> try {
                    val o = parse(line)
                    ok.putIfAbsent(line.substringBefore('#').trim(), line to o)
                } catch (e: Exception) {
                    val reason = (e.message ?: "Invalid link").let { m ->
                        when {
                            m.contains("TUIC") -> "TUIC (unsupported)"
                            m.contains("ShadowsocksR") -> "ShadowsocksR (unsupported)"
                            m.contains("Hysteria v1") -> "Hysteria v1 (unsupported)"
                            m.contains("no TLS/REALITY") && m.contains("VLESS") -> "Unencrypted VLESS (rejected by Xray)"
                            m.contains("no TLS") && m.contains("Trojan") -> "Unencrypted Trojan (rejected by Xray)"
                            m.contains("no encryption") -> "Unencrypted Shadowsocks (rejected by Xray)"
                            m.contains("port hopping") -> "Port hopping (unsupported)"
                            else -> "Invalid or unsupported"
                        }
                    }
                    skipped[reason] = (skipped[reason] ?: 0) + 1
                }
            }
        }
        return Subscription(ok.values.toList(), skipped, title, interval)
    }

    /**
     * Turns provider names such as "0002 | 🇳🇱 Netherlands | [38ms17.0mbps] | 🏴 Black | TG: @x" into
     * "🇳🇱 Netherlands · 0002". Advertising and provider-measured speed claims are dropped.
     */
    fun cleanName(raw: String): String {
        val parts = raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return raw.trim().take(60)
        val keep = parts.filterNot { p ->
            p.contains("TG:", true) || p.contains('@') || p.startsWith("[") || p.contains("t.me", true) || p.contains("http", true) ||
                p.startsWith("🏴") || p.equals("Black", true) || p.equals("White", true)
        }
        val id = keep.firstOrNull { it.all { ch -> ch.isDigit() } }
        val rest = keep.filter { it != id }
        return (rest.joinToString(" · ") + (id?.let { " · $it" } ?: "")).trim().ifEmpty { raw.trim() }.take(60)
    }

    fun parseMany(body: String): Pair<List<Pair<String, XrayOutbound>>, Int> {
        val decoded = decodeSubscription(body)
        val ok = mutableListOf<Pair<String, XrayOutbound>>()
        var errors = 0
        decoded.lineSequence().map { it.trim() }.filter { it.contains("://") }.forEach { line ->
            try { ok += line to parse(line) } catch (_: Exception) { errors++ }
        }
        return ok to errors
    }

    fun decodeSubscription(body: String): String {
        val trimmed = body.trim()
        if (trimmed.contains("://")) return trimmed
        val compact = trimmed.replace(Regex("\\s"), "")
        return b64DecodeOrNull(compact) ?: trimmed
    }

    // ---------------------------------------------------------------------------------------

    private data class Parts(
        val userInfo: String, val host: String, val port: Int, val query: Map<String, String>, val fragment: String,
    )

    private fun split(link: String): Parts {
        val noScheme = link.substringAfter("://")
        val fragment = if (noScheme.contains('#')) decode(noScheme.substringAfterLast('#')) else ""
        val withoutFrag = noScheme.substringBeforeLast('#')
        val queryStr = if (withoutFrag.contains('?')) withoutFrag.substringAfter('?') else ""
        val authority = withoutFrag.substringBefore('?').substringBefore('/').replace("%20", "").trim()
        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at) else ""
        val hp = ProxyList.splitHostPort(authority.substring(at + 1))
            ?: throw LinkParseException("Missing host or port")
        if (!ProxyList.isValidHost(hp.first)) throw LinkParseException("Invalid host")
        if (!ProxyList.validPort(hp.second)) throw LinkParseException("Invalid port")
        val query = queryStr.split('&').filter { it.contains('=') }.associate {
            it.substringBefore('=').lowercase() to decode(it.substringAfter('='))
        }
        return Parts(userInfo, hp.first, hp.second, query, fragment)
    }

    private fun Map<String, String>.alpn(): List<String> =
        this["alpn"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun insecure(q: Map<String, String>) = q["allowinsecure"] in listOf("1", "true") || q["insecure"] in listOf("1", "true")

    private fun normalizeNetwork(n: String?): String = when (n?.lowercase()) {
        null, "", "tcp", "raw" -> "tcp"
        "ws", "websocket" -> "ws"
        "grpc", "gun" -> "grpc"
        "xhttp", "splithttp" -> "xhttp"
        "httpupgrade" -> "httpupgrade"
        else -> throw LinkParseException("Unsupported transport: $n")
    }

    private fun parseVless(link: String): XrayOutbound {
        val p = split(link)
        val id = decode(p.userInfo)
        if (!isUuidLike(id)) throw LinkParseException("Invalid VLESS id")
        val q = p.query
        val security = (q["security"] ?: "none").lowercase().let { if (it.isEmpty() || it == "false" || it == "0") "none" else it }
        if (security !in listOf("none", "tls", "reality")) throw LinkParseException("Unsupported security: $security")
        if (security == "reality" && q["pbk"].isNullOrEmpty()) throw LinkParseException("REALITY link is missing pbk (public key)")
        val encryption = q["encryption"]?.ifEmpty { null } ?: "none"
        if (security == "none" && encryption == "none" && !isPrivateAddress(p.host)) {
            // Xray-core refuses unencrypted VLESS to public servers; report it at import time.
            throw LinkParseException("This VLESS server uses no TLS/REALITY, so traffic would be unencrypted. Xray-core rejects it.")
        }
        return XrayOutbound(
            protocol = XrayProtocol.VLESS, address = p.host, port = p.port, name = p.fragment, id = id,
            encryption = q["encryption"]?.ifEmpty { null } ?: "none", flow = q["flow"] ?: "",
            network = normalizeNetwork(q["type"]), security = security,
            sni = q["sni"] ?: q["peer"] ?: "", fingerprint = q["fp"] ?: "", alpn = q.alpn(),
            publicKey = q["pbk"] ?: "", shortId = q["sid"] ?: "", spiderX = q["spx"] ?: "",
            host = q["host"] ?: "", path = q["path"] ?: "", serviceName = q["servicename"] ?: q["path"].takeIf { q["type"] == "grpc" } ?: "",
            mode = q["mode"] ?: "", headerType = q["headertype"] ?: "",
            pinnedPeerCertSha256 = q["pcs"] ?: "", verifyPeerCertByName = q["vcn"] ?: "",
            insecureRequested = insecure(q),
        )
    }

    private fun parseTrojan(link: String): XrayOutbound {
        val p = split(link)
        val pass = decode(p.userInfo)
        if (pass.isEmpty()) throw LinkParseException("Trojan link has no password")
        val q = p.query
        val security = (q["security"] ?: "tls").lowercase()
        if (security == "none" && !isPrivateAddress(p.host)) {
            throw LinkParseException("This Trojan server uses no TLS, so traffic would be unencrypted. Xray-core rejects it.")
        }
        return XrayOutbound(
            protocol = XrayProtocol.TROJAN, address = p.host, port = p.port, name = p.fragment, password = pass,
            flow = q["flow"] ?: "",
            network = normalizeNetwork(q["type"]), security = if (security == "none") "none" else security,
            sni = q["sni"] ?: q["peer"] ?: "", fingerprint = q["fp"] ?: "", alpn = q.alpn(),
            publicKey = q["pbk"] ?: "", shortId = q["sid"] ?: "", spiderX = q["spx"] ?: "",
            host = q["host"] ?: "", path = q["path"] ?: "", serviceName = q["servicename"] ?: "", mode = q["mode"] ?: "",
            pinnedPeerCertSha256 = q["pcs"] ?: "", verifyPeerCertByName = q["vcn"] ?: "",
            insecureRequested = insecure(q),
        )
    }

    private fun parseVmess(link: String): XrayOutbound {
        val body = link.substringAfter("://").substringBefore('#')
        val decoded = b64DecodeOrNull(body) ?: throw LinkParseException("VMess link is not valid base64")
        val obj: JsonObject = try { json.parseToJsonElement(decoded).jsonObject } catch (e: Exception) {
            throw LinkParseException("VMess link JSON is invalid")
        }
        fun f(k: String) = (obj[k] as? JsonPrimitive)?.contentOrNull?.trim() ?: ""
        val host = f("add")
        val port = f("port").toIntOrNull() ?: throw LinkParseException("Invalid port")
        if (!ProxyList.isValidHost(host) || !ProxyList.validPort(port)) throw LinkParseException("Invalid host or port")
        val id = f("id")
        if (!isUuidLike(id)) throw LinkParseException("Invalid VMess id")
        val tls = f("tls").lowercase()
        val net = normalizeNetwork(f("net").ifEmpty { "tcp" })
        return XrayOutbound(
            protocol = XrayProtocol.VMESS, address = host, port = port, name = f("ps"), id = id,
            method = f("scy").ifEmpty { "auto" }, network = net,
            security = if (tls == "tls") "tls" else "none", sni = f("sni"), fingerprint = f("fp"),
            alpn = f("alpn").split(',').map { it.trim() }.filter { it.isNotEmpty() },
            host = f("host"), path = f("path"), serviceName = if (net == "grpc") f("path") else "",
            headerType = f("type").takeIf { it != "none" } ?: "",
        )
    }

    private fun parseHysteria2(link: String): XrayOutbound {
        val noScheme = link.substringAfter("://")
        val authority = noScheme.substringBefore('#').substringBefore('?').substringBefore('/')
        val hostPort = authority.substringAfterLast('@')
        val portStr = hostPort.substringAfterLast(':', "")
        if (portStr.contains(',') || portStr.contains('-')) throw LinkParseException("Hysteria2 port hopping is not supported")
        val p = split(link)
        val q = p.query
        val auth = decode(p.userInfo)
        if (auth.isEmpty()) throw LinkParseException("Hysteria2 link has no password")
        val obfs = q["obfs"]?.lowercase() ?: ""
        if (obfs.isNotEmpty() && obfs != "salamander") throw LinkParseException("Unsupported Hysteria2 obfuscation: $obfs")
        if (obfs == "salamander" && q["obfs-password"].isNullOrEmpty()) throw LinkParseException("Salamander obfuscation needs obfs-password")
        return XrayOutbound(
            protocol = XrayProtocol.HYSTERIA2, address = p.host, port = p.port, name = p.fragment, password = auth,
            security = "tls", sni = q["sni"] ?: q["peer"] ?: "", alpn = q.alpn(),
            obfs = obfs, obfsPassword = q["obfs-password"] ?: "",
            insecureRequested = insecure(q),
        )
    }

    private val ssCiphers = setOf(
        "aes-128-gcm", "aes-256-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305", "xchacha20-poly1305",
        "xchacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305", "none", "plain",
    )

    private fun parseShadowsocks(link: String): XrayOutbound {
        val noScheme = link.substringAfter("://")
        val name = if (noScheme.contains('#')) decode(noScheme.substringAfterLast('#')) else ""
        var main = noScheme.substringBeforeLast('#').substringBefore('?').substringBefore("/?")
        // Legacy form: ss://base64(method:password@host:port)
        if (!main.contains('@')) {
            main = b64DecodeOrNull(main.trimEnd('/')) ?: throw LinkParseException("Invalid Shadowsocks link")
        }
        val at = main.lastIndexOf('@')
        if (at < 0) throw LinkParseException("Invalid Shadowsocks link")
        var userInfo = main.substring(0, at)
        val hp = ProxyList.splitHostPort(main.substring(at + 1).replace("%20", "").trim().trimEnd('/')) ?: throw LinkParseException("Missing host or port")
        if (!userInfo.contains(':') || !ssCiphers.contains(decode(userInfo).substringBefore(':').lowercase())) {
            userInfo = b64DecodeOrNull(decode(userInfo)) ?: decode(userInfo)
        } else {
            userInfo = decode(userInfo)
        }
        val method = userInfo.substringBefore(':').lowercase()
        val password = userInfo.substringAfter(':', "")
        if (method !in ssCiphers) throw LinkParseException("Unsupported Shadowsocks cipher: $method")
        if ((method == "none" || method == "plain") && !isPrivateAddress(hp.first)) {
            throw LinkParseException("This Shadowsocks server uses no encryption (no TLS/REALITY equivalent). Xray-core rejects it.")
        }
        if (method.startsWith("2022-")) {
            val need = if (method.contains("128")) 16 else 32
            val keys = password.split(':')
            val ok = keys.all { k -> runCatching { Base64.getDecoder().decode(k).size == need }.getOrDefault(false) }
            if (!ok) throw LinkParseException("$method needs a base64 key of $need bytes")
        }
        if (!ProxyList.isValidHost(hp.first) || !ProxyList.validPort(hp.second)) throw LinkParseException("Invalid host or port")
        return XrayOutbound(protocol = XrayProtocol.SHADOWSOCKS, address = hp.first, port = hp.second, name = name, method = method, password = password)
    }

    private fun parseSocks(link: String): XrayOutbound {
        val p = split(link)
        var info = decode(p.userInfo)
        if (info.isNotEmpty() && !info.contains(':')) info = b64DecodeOrNull(info) ?: info
        return XrayOutbound(
            protocol = XrayProtocol.SOCKS, address = p.host, port = p.port, name = p.fragment,
            username = info.substringBefore(':', ""), password = info.substringAfter(':', ""),
        )
    }

    private fun parseHttp(link: String): XrayOutbound {
        val p = split(link)
        val info = decode(p.userInfo)
        return XrayOutbound(
            protocol = XrayProtocol.HTTP, address = p.host, port = p.port, name = p.fragment,
            username = info.substringBefore(':', ""), password = info.substringAfter(':', ""),
            security = if (link.startsWith("https", true)) "tls" else "none",
        )
    }

    // ---- export ----------------------------------------------------------------------------

    /** Builds a share link (used for export / QR display). */
    fun toLink(o: XrayOutbound): String {
        val host = if (o.address.contains(':')) "[${o.address}]" else o.address
        fun q(vararg pairs: Pair<String, String>) = pairs.filter { it.second.isNotEmpty() }
            .joinToString("&") { "${it.first}=${enc(it.second)}" }
        val stream = arrayOf(
            "type" to o.network, "security" to o.security, "sni" to o.sni, "fp" to o.fingerprint,
            "alpn" to o.alpn.joinToString(","), "pbk" to o.publicKey, "sid" to o.shortId, "spx" to o.spiderX,
            "host" to o.host, "path" to o.path, "serviceName" to o.serviceName, "mode" to o.mode,
            "pcs" to o.pinnedPeerCertSha256, "vcn" to o.verifyPeerCertByName,
        )
        val frag = if (o.name.isNotEmpty()) "#" + enc(o.name) else ""
        return when (o.protocol) {
            XrayProtocol.VLESS -> "vless://${o.id}@$host:${o.port}?" + q("encryption" to o.encryption, "flow" to o.flow, *stream) + frag
            XrayProtocol.TROJAN -> "trojan://${enc(o.password)}@$host:${o.port}?" + q("flow" to o.flow, *stream) + frag
            XrayProtocol.VMESS -> {
                val obj = linkedMapOf(
                    "v" to "2", "ps" to o.name, "add" to o.address, "port" to o.port.toString(), "id" to o.id, "aid" to "0",
                    "scy" to o.method.ifEmpty { "auto" }, "net" to o.network, "type" to o.headerType.ifEmpty { "none" },
                    "host" to o.host, "path" to if (o.network == "grpc") o.serviceName else o.path,
                    "tls" to if (o.security == "tls") "tls" else "", "sni" to o.sni, "alpn" to o.alpn.joinToString(","), "fp" to o.fingerprint,
                )
                val jsonStr = JsonObject(obj.mapValues { JsonPrimitive(it.value) }).toString()
                "vmess://" + Base64.getEncoder().encodeToString(jsonStr.toByteArray())
            }
            XrayProtocol.SHADOWSOCKS -> {
                val ui = Base64.getUrlEncoder().withoutPadding().encodeToString("${o.method}:${o.password}".toByteArray())
                "ss://$ui@$host:${o.port}$frag"
            }
            XrayProtocol.HYSTERIA2 -> "hysteria2://${enc(o.password)}@$host:${o.port}?" +
                q("sni" to o.sni, "alpn" to o.alpn.joinToString(","), "obfs" to o.obfs, "obfs-password" to o.obfsPassword) + frag
            XrayProtocol.SOCKS -> "socks://" + (if (o.username.isNotEmpty()) Base64.getUrlEncoder().withoutPadding().encodeToString("${o.username}:${o.password}".toByteArray()) + "@" else "") + "$host:${o.port}$frag"
            XrayProtocol.HTTP -> (if (o.security == "tls") "https" else "http") + "://" + (if (o.username.isNotEmpty()) "${enc(o.username)}:${enc(o.password)}@" else "") + "$host:${o.port}$frag"
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** RFC 1918 / loopback / link-local / CGNAT IPv4, unique-local IPv6, or a .local/.lan name. */
    fun isPrivateAddress(host: String): Boolean {
        if (ProxyList.isIpv4(host)) {
            val p = host.split('.').map { it.toInt() }
            return p[0] == 10 || p[0] == 127 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168) ||
                (p[0] == 169 && p[1] == 254) || (p[0] == 100 && p[1] in 64..127)
        }
        val h = host.lowercase()
        return h.startsWith("fc") || h.startsWith("fd") || h == "::1" || h.endsWith(".local") || h.endsWith(".lan") || h == "localhost"
    }

    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** Xray accepts real UUIDs or any 1–30 byte string (mapped to a UUID). */
    fun isUuidLike(s: String) = uuidRegex.matches(s) || (s.isNotEmpty() && s.length <= 30 && s.none { it.isWhitespace() })

    fun b64DecodeOrNull(s: String): String? {
        val clean = s.trim().replace("-", "+").replace("_", "/")
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return try {
            val bytes = Base64.getDecoder().decode(padded)
            val str = String(bytes, Charsets.UTF_8)
            if (str.any { it == '�' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') }) null else str
        } catch (_: IllegalArgumentException) { null }
    }

    private fun decode(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
