package com.tervyn.vpn.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Downloads a subscription URL (plain or base64 list of share links) and parses it. */
class SubscriptionFetcher(private val http: OkHttpClient) {
    /** Returns the usable links and the number of lines that could not be used. */
    suspend fun fetch(url: String): Pair<List<Pair<String, XrayOutbound>>, Int> = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (!u.startsWith("https://", true) && !u.startsWith("http://", true)) throw IOException("Subscription URL must start with http(s)://")
        http.newCall(Request.Builder().url(u).header("User-Agent", "TervynVPN/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Subscription returned HTTP ${r.code}")
            val body = r.body.string()
            if (body.length > 5_000_000) throw IOException("Subscription too large")
            val sub = Links.parseSubscription(body)
            sub.links to sub.skippedTotal
        }
    }
}
