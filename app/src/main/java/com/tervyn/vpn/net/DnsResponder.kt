package com.tervyn.vpn.net

import com.tervyn.vpn.core.Domains

/**
 * Local DNS resolver behind the TUN.
 *  - Routed names: A -> synthetic address from [FakeDns]; AAAA/HTTPS/SVCB -> empty answer, so apps use
 *    IPv4 through the tunnel and never receive real addresses that could bypass it.
 *  - Everything else is forwarded with [forward] (DNS-over-HTTPS; through the proxy in All Apps mode,
 *    directly to the encrypted resolver in Only My Sites mode). Failures yield SERVFAIL.
 */
class DnsResponder(
    private val fakeDns: FakeDns,
    @Volatile var shouldRoute: (String) -> Boolean,
    private val forward: suspend (ByteArray) -> ByteArray?,
) {
    suspend fun respond(query: ByteArray): ByteArray? {
        val q = DnsMessage.parseQuery(query) ?: return null
        val routed = q.qclass == 1 && q.name.isNotEmpty() && !Domains.isLocalName(q.name) && shouldRoute(q.name)
        if (routed) {
            when (q.qtype) {
                DnsMessage.TYPE_A -> return DnsMessage.answerA(query, q, listOf(fakeDns.ipFor(q.name)), FakeDns.TTL_SECONDS)
                DnsMessage.TYPE_AAAA, DnsMessage.TYPE_HTTPS, DnsMessage.TYPE_SVCB -> return DnsMessage.empty(query, q)
            }
        }
        val resp = runCatching { forward(query) }.getOrNull()
        if (resp == null || resp.size < 12 || resp[0] != query[0] || resp[1] != query[1]) {
            return DnsMessage.empty(query, q, DnsMessage.RCODE_SERVFAIL)
        }
        return resp
    }
}
