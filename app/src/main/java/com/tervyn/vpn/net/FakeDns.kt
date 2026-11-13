package com.tervyn.vpn.net

import com.tervyn.vpn.vpn.Packets

/**
 * Maps domain names to synthetic IPv4 addresses from the benchmarking range 198.18.0.0/15 (RFC 2544).
 *
 * When an app resolves a routed domain it receives a synthetic address; when it later opens a TCP
 * connection to that address the engine looks the domain back up and asks the proxy to connect to the
 * *domain* — so the real resolution happens at the proxy and no DNS query reaches the local ISP.
 *
 * The first /24 (198.18.0.0 – 198.18.0.255) is reserved for the TUN interface itself.
 */
class FakeDns(private val capacity: Int = 60_000) {
    companion object {
        val BASE = Packets.ipFromString("198.18.0.0")
        const val PREFIX = 15
        const val SIZE = 1 shl (32 - PREFIX)          // 131072 addresses
        const val RESERVED = 256
        val TUN_ADDRESS = Packets.ipFromString("198.18.0.1")
        val DNS_ADDRESS = Packets.ipFromString("198.18.0.2")
        const val TTL_SECONDS = 30

        fun inRange(ip: Int) = (ip - BASE).toLong() and 0xFFFFFFFFL < SIZE
    }

    private val byDomain = object : LinkedHashMap<String, Int>(1024, 0.75f, true) {}
    private val byIp = HashMap<Int, String>()
    private var cursor = RESERVED

    init { require(capacity in 1..(SIZE - RESERVED)) }

    @Synchronized
    fun ipFor(domain: String): Int {
        val d = domain.lowercase().trimEnd('.')
        byDomain[d]?.let { return it }
        val ip: Int
        if (byDomain.size >= capacity || cursor >= SIZE) {
            // Recycle the least recently used address
            val eldest = byDomain.entries.iterator().next()
            byDomain.remove(eldest.key)
            ip = eldest.value
        } else {
            ip = BASE + cursor++
        }
        byDomain[d] = ip
        byIp[ip] = d
        return ip
    }

    @Synchronized
    fun domainFor(ip: Int): String? {
        val d = byIp[ip] ?: return null
        if (byDomain[d] != ip) return null // stale
        return d
    }

    fun isFake(ip: Int) = inRange(ip) && (ip - BASE) >= RESERVED

    @Synchronized
    fun size() = byDomain.size

    @Synchronized
    fun clear() { byDomain.clear(); byIp.clear(); cursor = RESERVED }
}
