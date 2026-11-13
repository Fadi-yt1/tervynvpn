package com.tervyn.vpn.core

import com.tervyn.vpn.domain.ProxyProtocol

/**
 * Public free-proxy lists. These are third-party lists of *unvetted* open proxies; every entry is tested
 * over HTTPS with certificate validation before it can be used, and users are warned that operators of
 * free proxies are unknown. All URLs below were verified to respond with proxy lines when added.
 * The list is user-configurable (Settings → Server Source → Free public servers).
 */
object ProxySources {
    data class Source(val name: String, val url: String, val protocol: ProxyProtocol)

    private const val GH = "https://raw.githubusercontent.com"

    val DEFAULT = listOf(
        Source("TheSpeedX SOCKS5", "$GH/TheSpeedX/PROXY-List/master/socks5.txt", ProxyProtocol.SOCKS5),
        Source("TheSpeedX HTTP", "$GH/TheSpeedX/PROXY-List/master/http.txt", ProxyProtocol.HTTP),
        Source("monosans SOCKS5", "$GH/monosans/proxy-list/main/proxies/socks5.txt", ProxyProtocol.SOCKS5),
        Source("monosans HTTP", "$GH/monosans/proxy-list/main/proxies/http.txt", ProxyProtocol.HTTP),
        Source("hookzof SOCKS5", "$GH/hookzof/socks5_list/master/proxy.txt", ProxyProtocol.SOCKS5),
        Source("proxifly SOCKS5", "$GH/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt", ProxyProtocol.SOCKS5),
        Source("proxifly HTTP", "$GH/proxifly/free-proxy-list/main/proxies/protocols/http/data.txt", ProxyProtocol.HTTP),
        Source("jetkai SOCKS5", "$GH/jetkai/proxy-list/main/online-proxies/txt/proxies-socks5.txt", ProxyProtocol.SOCKS5),
        Source("jetkai HTTP", "$GH/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt", ProxyProtocol.HTTP),
        Source("ShiftyTR SOCKS5", "$GH/ShiftyTR/Proxy-List/master/socks5.txt", ProxyProtocol.SOCKS5),
        Source("clarketm HTTP", "$GH/clarketm/proxy-list/master/proxy-list-raw.txt", ProxyProtocol.HTTP),
        Source("sunny9577 SOCKS5", "$GH/sunny9577/proxy-scraper/master/generated/socks5_proxies.txt", ProxyProtocol.SOCKS5),
        Source("sunny9577 HTTP", "$GH/sunny9577/proxy-scraper/master/generated/http_proxies.txt", ProxyProtocol.HTTP),
        Source("roosterkid HTTPS", "$GH/roosterkid/openproxylist/main/HTTPS_RAW.txt", ProxyProtocol.HTTP),
        Source("hideip.me SOCKS5", "$GH/zloi-user/hideip.me/main/socks5.txt", ProxyProtocol.SOCKS5),
        Source("hideip.me HTTPS", "$GH/zloi-user/hideip.me/main/https.txt", ProxyProtocol.HTTP),
        Source("r00tee SOCKS5", "$GH/r00tee/Proxy-List/main/Socks5.txt", ProxyProtocol.SOCKS5),
        Source("r00tee HTTPS", "$GH/r00tee/Proxy-List/main/Https.txt", ProxyProtocol.HTTP),
    )

    /** Protocol for a custom source URL (by file name), defaulting to HTTP. */
    fun guessProtocol(url: String): ProxyProtocol = if (url.contains("socks", ignoreCase = true)) ProxyProtocol.SOCKS5 else ProxyProtocol.HTTP
}
