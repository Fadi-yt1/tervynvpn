package com.tervyn.vpn.xray

import com.tervyn.vpn.core.XrayOutbound
import com.tervyn.vpn.core.XrayProtocol
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Generates Xray-core JSON configurations. Each advanced server is exposed as a local SOCKS5 endpoint
 * on 127.0.0.1 protected with a random username/password, so the VPN engine can treat it like any
 * other SOCKS5 proxy.
 *
 * Field names follow Xray-core v26.9.30 (`infra/conf`). Notably `allowInsecure` was removed upstream in
 * favour of `pinnedPeerCertSha256` / `verifyPeerCertByName`, so insecure TLS can never be enabled here.
 */
object XrayConfig {

    data class LocalSocks(val port: Int, val user: String, val pass: String)

    fun build(outbound: XrayOutbound, local: LocalSocks, logLevel: String = "warning"): String = buildJsonObject {
        putJsonObject("log") { put("loglevel", logLevel); put("access", "none") }
        // Server host names are resolved by Xray's own DNS-over-HTTPS client (IP-literal endpoints, no bootstrap),
        // independent of the platform resolver.
        putJsonObject("dns") {
            putJsonArray("servers") {
                add(JsonPrimitive("https+local://1.1.1.1/dns-query"))
                add(JsonPrimitive("https+local://8.8.8.8/dns-query"))
            }
            put("queryStrategy", "UseIP")
        }
        putJsonArray("inbounds") {
            add(buildJsonObject {
                put("tag", "socks-in")
                put("listen", "127.0.0.1")
                put("port", local.port)
                put("protocol", "socks")
                putJsonObject("settings") {
                    put("auth", "password")
                    putJsonArray("accounts") { add(buildJsonObject { put("user", local.user); put("pass", local.pass) }) }
                    put("udp", false)
                }
            })
        }
        putJsonArray("outbounds") {
            add(outboundJson(outbound, "proxy"))
            add(buildJsonObject { put("tag", "block"); put("protocol", "blackhole") })
        }
        putJsonObject("routing") { put("domainStrategy", "AsIs") }
    }.toString()

    fun outboundJson(o: XrayOutbound, tag: String): JsonObject = buildJsonObject {
        put("tag", tag)
        put("protocol", when (o.protocol) {
            XrayProtocol.VLESS -> "vless"
            XrayProtocol.VMESS -> "vmess"
            XrayProtocol.TROJAN -> "trojan"
            XrayProtocol.SHADOWSOCKS -> "shadowsocks"
            XrayProtocol.SOCKS -> "socks"
            XrayProtocol.HTTP -> "http"
        })
        putJsonObject("settings") {
            put("address", o.address)
            put("port", o.port)
            when (o.protocol) {
                XrayProtocol.VLESS -> {
                    put("id", o.id)
                    put("encryption", o.encryption.ifEmpty { "none" })
                    if (o.flow.isNotEmpty()) put("flow", o.flow)
                }
                XrayProtocol.VMESS -> { put("id", o.id); put("security", o.method.ifEmpty { "auto" }) }
                XrayProtocol.TROJAN -> put("password", o.password)
                XrayProtocol.SHADOWSOCKS -> { put("method", o.method); put("password", o.password) }
                XrayProtocol.SOCKS, XrayProtocol.HTTP -> if (o.username.isNotEmpty()) { put("user", o.username); put("pass", o.password) }
            }
        }
        put("streamSettings", if (o.protocol == XrayProtocol.SHADOWSOCKS) buildJsonObject {
            put("network", "raw")
            putJsonObject("sockopt") { put("domainStrategy", "UseIP") }
        } else streamSettings(o))
    }

    private fun streamSettings(o: XrayOutbound): JsonObject = buildJsonObject {
        val network = if (o.network == "tcp") "raw" else o.network
        put("network", network)
        put("security", o.security)
        putJsonObject("sockopt") { put("domainStrategy", "UseIP") }
        when (o.security) {
            "tls" -> putJsonObject("tlsSettings") {
                put("serverName", o.sni.ifEmpty { o.host.ifEmpty { o.address } })
                if (o.fingerprint.isNotEmpty()) put("fingerprint", o.fingerprint)
                if (o.alpn.isNotEmpty()) put("alpn", JsonArray(o.alpn.map(::JsonPrimitive)))
                if (o.pinnedPeerCertSha256.isNotEmpty()) put("pinnedPeerCertSha256", o.pinnedPeerCertSha256)
                if (o.verifyPeerCertByName.isNotEmpty()) put("verifyPeerCertByName", o.verifyPeerCertByName)
            }
            "reality" -> putJsonObject("realitySettings") {
                put("serverName", o.sni)
                put("fingerprint", o.fingerprint.ifEmpty { "chrome" })
                put("publicKey", o.publicKey)
                if (o.shortId.isNotEmpty()) put("shortId", o.shortId)
                if (o.spiderX.isNotEmpty()) put("spiderX", o.spiderX)
            }
        }
        when (network) {
            "ws" -> putJsonObject("wsSettings") {
                put("path", o.path.ifEmpty { "/" })
                if (o.host.isNotEmpty()) put("host", o.host)
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", o.serviceName)
                put("multiMode", o.mode == "multi")
            }
            "xhttp" -> putJsonObject("xhttpSettings") {
                put("path", o.path.ifEmpty { "/" })
                if (o.host.isNotEmpty()) put("host", o.host)
                put("mode", o.mode.ifEmpty { "auto" })
            }
            "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                put("path", o.path.ifEmpty { "/" })
                if (o.host.isNotEmpty()) put("host", o.host)
            }
            "raw" -> if (o.headerType == "http") putJsonObject("rawSettings") {
                putJsonObject("header") {
                    put("type", "http")
                    putJsonObject("request") {
                        putJsonArray("path") { add(JsonPrimitive(o.path.ifEmpty { "/" })) }
                        if (o.host.isNotEmpty()) putJsonObject("headers") {
                            put("Host", buildJsonArray { o.host.split(',').forEach { add(JsonPrimitive(it.trim())) } })
                        }
                    }
                }
            }
        }
    }

    @Suppress("unused")
    private fun JsonElement.str() = toString()
}
