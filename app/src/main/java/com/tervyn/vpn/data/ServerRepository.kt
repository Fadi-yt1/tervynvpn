package com.tervyn.vpn.data

import com.tervyn.vpn.core.Links
import com.tervyn.vpn.core.XrayOutbound
import com.tervyn.vpn.database.ServerDao
import com.tervyn.vpn.database.ServerEntity
import com.tervyn.vpn.domain.ProxyEndpoint
import com.tervyn.vpn.domain.ProxyProtocol
import com.tervyn.vpn.domain.Server
import com.tervyn.vpn.domain.ServerOrigin
import com.tervyn.vpn.domain.TestResult
import com.tervyn.vpn.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class ServerRepository(private val dao: ServerDao, private val cipher: SecretCipher) {

    companion object {
        fun idForEndpoint(ep: ProxyEndpoint) = "p_" + sha(ep.key)
        fun idForLink(link: String) = "x_" + sha(link.substringBefore('#').trim())
        private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
    }

    val servers: Flow<List<Server>> = dao.observeAll().map { list -> list.mapNotNull(::toDomain) }
    fun observe(id: String): Flow<Server?> = dao.observe(id).map { it?.let(::toDomain) }

    suspend fun all() = dao.all().mapNotNull(::toDomain)
    suspend fun byOrigins(origins: Set<ServerOrigin>) = dao.byOrigins(origins.map { it.name }).mapNotNull(::toDomain)
    suspend fun byId(id: String) = dao.byId(id)?.let(::toDomain)

    fun toDomain(e: ServerEntity): Server? {
        val origin = runCatching { ServerOrigin.valueOf(e.origin) }.getOrNull() ?: return null
        return if (e.kind == "XRAY") {
            val link = e.xrayLinkEnc?.let(cipher::decrypt) ?: return null
            Server(e.id, e.name, e.protocolLabel, null, link, origin, e.subscriptionId, e.countryCode, e.exitIp, e.latencyMs,
                e.alive, e.failCount, e.lastChecked, e.lastUsed, e.favorite, e.addedAt)
        } else {
            val proto = runCatching { ProxyProtocol.valueOf(e.kind) }.getOrNull() ?: return null
            val pass = e.passwordEnc?.let { cipher.decrypt(it) ?: return null }
            val ep = ProxyEndpoint(proto, e.host, e.port, e.username, pass)
            Server(e.id, e.name, e.protocolLabel, ep, null, origin, e.subscriptionId, e.countryCode, e.exitIp, e.latencyMs,
                e.alive, e.failCount, e.lastChecked, e.lastUsed, e.favorite, e.addedAt)
        }
    }

    private fun toEntity(s: Server, existing: ServerEntity?): ServerEntity {
        val ep = s.endpoint
        return ServerEntity(
            id = s.id, name = s.name, protocolLabel = s.protocolLabel,
            kind = if (s.isXray) "XRAY" else ep!!.protocol.name,
            host = ep?.host ?: runCatching { Links.parse(s.xrayLink!!).address }.getOrDefault(""),
            port = ep?.port ?: runCatching { Links.parse(s.xrayLink!!).port }.getOrDefault(0),
            username = ep?.username,
            passwordEnc = ep?.password?.let(cipher::encrypt),
            xrayLinkEnc = s.xrayLink?.let(cipher::encrypt),
            origin = s.origin.name, subscriptionId = s.subscriptionId,
            countryCode = s.countryCode ?: existing?.countryCode, exitIp = s.exitIp ?: existing?.exitIp,
            latencyMs = s.latencyMs ?: existing?.latencyMs, alive = if (s.lastChecked > 0) s.alive else existing?.alive ?: false,
            failCount = if (s.lastChecked > 0) s.failCount else existing?.failCount ?: 0,
            lastChecked = maxOf(s.lastChecked, existing?.lastChecked ?: 0), lastUsed = maxOf(s.lastUsed, existing?.lastUsed ?: 0),
            favorite = s.favorite || existing?.favorite == true, addedAt = existing?.addedAt ?: s.addedAt,
        )
    }

    suspend fun save(servers: List<Server>) {
        if (servers.isEmpty()) return
        val existing = dao.all().associateBy { it.id }
        dao.upsert(servers.map { toEntity(it, existing[it.id]) })
    }

    suspend fun save(server: Server) = save(listOf(server))

    fun endpointServer(ep: ProxyEndpoint, origin: ServerOrigin, name: String? = null) = Server(
        id = idForEndpoint(ep), name = name ?: "${ep.host}:${ep.port}",
        protocolLabel = if (ep.protocol == ProxyProtocol.SOCKS5) "SOCKS5" else "HTTP", endpoint = ep, origin = origin,
    )

    fun linkServer(link: String, o: XrayOutbound, origin: ServerOrigin, subscriptionId: Long? = null, addedAt: Long = System.currentTimeMillis()) = Server(
        id = idForLink(link), name = com.tervyn.vpn.core.Links.cleanName(o.name).ifBlank { "${o.protocol.label} ${o.address}" }, protocolLabel = o.displayProtocol,
        xrayLink = link, origin = origin, subscriptionId = subscriptionId, addedAt = addedAt,
    )

    /** Replaces the servers of [origin] with [fresh]; favourites and servers present in [fresh] keep their test history. */
    suspend fun replaceOrigin(origin: ServerOrigin, fresh: List<Server>) {
        val keep = fresh.map { it.id }.toSet()
        dao.byOrigins(listOf(origin.name)).filter { it.id !in keep && !it.favorite }.forEach { dao.delete(it.id) }
        // A server listed by a higher-priority built-in list keeps its origin.
        val existing = dao.all().associateBy { it.id }
        dao.upsert(fresh.map { srv ->
            val old = existing[srv.id]
            toEntity(srv, old).copy(addedAt = srv.addedAt, origin = if (old != null && old.origin == ServerOrigin.TERVYN_FAST.name && origin == ServerOrigin.TERVYN_ALL) old.origin else srv.origin.name)
        })
    }

    suspend fun recordTest(id: String, r: TestResult) =
        dao.updateTest(id, r.ok, if (r.ok) r.latencyMs else null, r.exitIp, r.countryCode, System.currentTimeMillis())

    suspend fun setFavorite(id: String, fav: Boolean) = dao.setFavorite(id, fav)
    suspend fun markUsed(id: String) = dao.markUsed(id, System.currentTimeMillis())
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun deleteOrigin(origin: ServerOrigin) = dao.deleteByOrigin(origin.name)
    suspend fun deleteSubscription(id: Long) = dao.deleteBySubscription(id)
    suspend fun pruneFree() = dao.pruneFree(maxFails = 3, olderThan = System.currentTimeMillis() - 6 * 3600_000L)
    suspend fun count(origin: ServerOrigin) = dao.countByOrigin(origin.name)
}
