package com.tervyn.vpn.core

import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Proxio.io list API: `GET https://proxio.io/api/list?type=<socks5|http>&limit=<n>` with header `X-Key`.
 *
 * The response body format is parsed defensively: plain text lines (`host:port[:user:pass]`, URLs) or JSON
 * (an array, or an object holding an array under `data`/`proxies`/`list`/`result`/`items`) of strings or of
 * objects with host/ip, port, username/user/login, password/pass and protocol/type fields.
 */
class ProxioClient(private val http: OkHttpClient, private val baseUrl: String = "https://proxio.io/api/list") {

    class ProxioException(message: String) : IOException(message)

    suspend fun fetch(apiKey: String, protocol: ProxyProtocol, limit: Int): List<ProxyEndpoint> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key is empty" }
        val type = if (protocol == ProxyProtocol.SOCKS5) "socks5" else "http"
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("type", type).addQueryParameter("limit", limit.coerceIn(1, 1000).toString()).build()
        val req = Request.Builder().url(url).header("X-Key", apiKey.trim()).header("Accept", "application/json, text/plain").build()
        http.newCall(req).execute().use { r ->
            when (r.code) {
                200 -> Unit
                401, 403 -> throw ProxioException("Proxio rejected the API key (HTTP ${r.code})")
                429 -> throw ProxioException("Proxio rate limit reached, try again later")
                else -> throw ProxioException("Proxio returned HTTP ${r.code}")
            }
            val body = r.body.string()
            if (body.length > 5_000_000) throw ProxioException("Response too large")
            parse(body, protocol)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(body: String, defaultProtocol: ProxyProtocol): List<ProxyEndpoint> {
            val t = body.trim()
            if (t.startsWith("{") || t.startsWith("[")) {
                val el = runCatching { json.parseToJsonElement(t) }.getOrNull()
                if (el != null) {
                    if (el is JsonObject) {
                        (el["error"] ?: el["message"])?.let { e ->
                            if (listOf("data", "proxies", "list", "result", "items").none { el[it] is JsonArray }) {
                                throw ProxioException((e as? JsonPrimitive)?.contentOrNull ?: "Proxio error")
                            }
                        }
                    }
                    return fromJson(el, defaultProtocol).distinctBy { it.key }
                }
            }
            return ProxyList.parse(t, defaultProtocol).proxies
        }

        private fun fromJson(el: JsonElement, def: ProxyProtocol): List<ProxyEndpoint> = when (el) {
            is JsonArray -> el.flatMap { fromJson(it, def) }
            is JsonPrimitive -> listOfNotNull(el.contentOrNull?.let { ProxyList.parseLine(it, def) })
            is JsonObject -> {
                val arr = listOf("data", "proxies", "list", "result", "items").firstNotNullOfOrNull { el[it] as? JsonArray }
                if (arr != null) fromJson(arr, def) else listOfNotNull(objToEndpoint(el, def))
            }
        }

        private fun objToEndpoint(o: JsonObject, def: ProxyProtocol): ProxyEndpoint? {
            fun s(vararg keys: String) = keys.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.isNotBlank() } }
            val proxyStr = s("proxy", "url")
            if (proxyStr != null) return ProxyList.parseLine(proxyStr, def)
            val host = s("host", "ip", "address", "server") ?: return null
            val port = s("port")?.toIntOrNull() ?: return null
            val proto = s("protocol", "type", "scheme")?.let { ProxyList.protocolForScheme(it) } ?: def
            val user = s("username", "user", "login")
            val pass = s("password", "pass")
            if (!ProxyList.isValidHost(host) || !ProxyList.validPort(port)) return null
            return ProxyEndpoint(proto, host, port, user, if (user != null) pass ?: "" else null)
        }
    }
}

/** Fetches a subscription URL (base64 or plain list of share links). */
class SubscriptionFetcher(private val http: OkHttpClient) {
    suspend fun fetch(url: String): Pair<List<Pair<String, XrayOutbound>>, Int> = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (!u.startsWith("https://", true) && !u.startsWith("http://", true)) throw IOException("Subscription URL must be http(s)")
        http.newCall(Request.Builder().url(u).header("User-Agent", "TervynVPN/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Subscription returned HTTP ${r.code}")
            val body = r.body.string()
            if (body.length > 5_000_000) throw IOException("Subscription too large")
            Links.parseMany(body)
        }
    }
}
