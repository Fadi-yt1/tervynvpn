package com.tervyn.vpn.domain

import kotlinx.serialization.Serializable

/** Wire protocol spoken to a proxy by [com.tervyn.vpn.net.ProxyDialer]. */
@Serializable
enum class ProxyProtocol { HTTP, SOCKS5 }

/**
 * A plain HTTP-CONNECT or SOCKS5 proxy endpoint.
 * [toString] never includes the password so endpoints are safe to log.
 */
@Serializable
data class ProxyEndpoint(
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
) {
    val hasAuth: Boolean get() = !username.isNullOrEmpty()

    /** Identity used for de-duplication (credentials included, case-insensitive host). */
    val key: String get() = "${protocol.name.lowercase()}://${username?.let { "$it@" } ?: ""}${host.lowercase()}:$port"

    override fun toString(): String =
        "${protocol.name.lowercase()}://${if (hasAuth) "$username:***@" else ""}$host:$port"
}

/** Where a server came from. */
@Serializable
enum class ServerOrigin {
    /** One of the servers shipped with the app (one per country). */
    BUILTIN,
    /** Added by the user (share link, manual proxy). */
    CUSTOM,
    /** Loaded from a subscription URL the user added. */
    SUBSCRIPTION,
}

enum class VpnMode(val label: String) { ALL_APPS("All apps"), ONLY_MY_SITES("Only my sites") }

/**
 * A server the user can connect to. Either a plain [endpoint] (HTTP/SOCKS5) or an Xray share link
 * ([xrayLink]) which is run through a local Xray instance that exposes a protected SOCKS5 endpoint.
 */
data class Server(
    val id: String,
    val name: String,
    /** Display label of the protocol, e.g. "SOCKS5", "HTTP", "VLESS · REALITY". */
    val protocolLabel: String,
    val endpoint: ProxyEndpoint? = null,
    val xrayLink: String? = null,
    val origin: ServerOrigin,
    val subscriptionId: Long? = null,
    /** Country of the verified exit IP (ISO-3166 alpha-2) — null until a test succeeded. */
    val countryCode: String? = null,
    /** Country announced by the provider/list (ISO-3166 alpha-2). Shown until the exit country is verified. */
    val declaredCountry: String? = null,
    val exitIp: String? = null,
    val latencyMs: Int? = null,
    val alive: Boolean = false,
    val failCount: Int = 0,
    val lastChecked: Long = 0,
    val lastUsed: Long = 0,
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val isXray: Boolean get() = xrayLink != null
    val isBuiltin: Boolean get() = origin == ServerOrigin.BUILTIN
    val verified: Boolean get() = alive && lastChecked > 0
    /** Best known country: the verified exit country, else the declared one. */
    val displayCountry: String? get() = countryCode ?: declaredCountry
    /** The verified exit country differs from what the provider declared. */
    val countryMismatch: Boolean get() = countryCode != null && declaredCountry != null && countryCode != declaredCountry
}

/** Result of testing a server through the HTTPS trace endpoint. */
data class TestResult(
    val ok: Boolean,
    val latencyMs: Int = -1,
    val exitIp: String? = null,
    val countryCode: String? = null,
    val error: String? = null,
)
