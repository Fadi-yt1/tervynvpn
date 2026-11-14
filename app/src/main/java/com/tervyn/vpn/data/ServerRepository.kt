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
import com.tervyn.vpn.utils.Format
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class ServerRepository(private val dao: ServerDao, private val cipher: SecretCipher) {

    companion object {
        fun idForEndpoint(ep: ProxyEndpoint) = "p_" + sha(ep.key)
        fun idForLink(link: String) = "x_" + sha(link.substringBefore('#').trim())
        /** Built-in servers have one stable id per country so a replaced link keeps the user's selection. */
        fun idForBuiltin(countryCode: String) = "b_" + countryCode.lowercase()
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
            Server(e.id, e.name, e.protocolLabel, null, link, origin, e.subscriptionId, e.countryCode, e.declaredCountry, e.exitIp, e.latencyMs,
                e.alive, e.failCount, e.lastChecked, e.lastUsed, e.favorite, e.addedAt)
        } else {
            val proto = runCatching { ProxyProtocol.valueOf(e.kind) }.getOrNull() ?: return null
            val pass = e.passwordEnc?.let { cipher.decrypt(it) ?: return null }
            val ep = ProxyEndpoint(proto, e.host, e.port, e.username, pass)
            Server(e.id, e.name, e.protocolLabel, ep, null, origin, e.subscriptionId, e.countryCode, e.declaredCountry, e.exitIp, e.latencyMs,
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
            countryCode = s.countryCode ?: existing?.countryCode, declaredCountry = s.declaredCountry ?: existing?.declaredCountry,
            exitIp = s.exitIp ?: existing?.exitIp,
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
        id = idForLink(link), name = Links.cleanName(o.name).ifBlank { "${o.protocol.label} ${o.address}" }, protocolLabel = o.displayProtocol,
        xrayLink = link, origin = origin, subscriptionId = subscriptionId, declaredCountry = Format.countryCodeFromFlag(o.name), addedAt = addedAt,
    )

    /** A built-in server: named after its country, with a stable per-country id. */
    fun builtinServer(countryCode: String, link: String, o: XrayOutbound, slot: Int) = Server(
        id = idForBuiltin(countryCode), name = Format.countryName(countryCode), protocolLabel = o.displayProtocol,
        xrayLink = link, origin = ServerOrigin.BUILTIN, declaredCountry = countryCode, addedAt = slot.toLong(),
    )

    /**
     * Installs or updates the built-in servers. A slot whose link changed loses its test history (it is a different
     * server now); untouched slots keep theirs.
     */
    suspend fun installBuiltin(fresh: List<Server>) {
        val existing = dao.byOrigins(listOf(ServerOrigin.BUILTIN.name)).associateBy { it.id }
        val keep = fresh.map { it.id }.toSet()
        existing.values.filter { it.id !in keep }.forEach { dao.delete(it.id) }
        dao.upsert(fresh.map { srv ->
            val old = existing[srv.id]
            val sameLink = old?.xrayLinkEnc?.let(cipher::decrypt)?.substringBefore('#') == srv.xrayLink?.substringBefore('#')
            if (sameLink) toEntity(srv, old).copy(addedAt = srv.addedAt, name = srv.name, declaredCountry = srv.declaredCountry)
            else toEntity(srv, null).copy(favorite = old?.favorite == true, lastUsed = old?.lastUsed ?: 0)
        })
    }

    /** Points a built-in slot at a replacement link (same id, so the user's selection survives). */
    suspend fun replaceBuiltinLink(id: String, link: String, o: XrayOutbound) {
        val old = dao.byId(id) ?: return
        val cc = old.declaredCountry ?: return
        dao.upsert(toEntity(builtinServer(cc, link, o, old.addedAt.toInt()), null).copy(favorite = old.favorite, lastUsed = old.lastUsed, addedAt = old.addedAt))
    }

    suspend fun recordTest(id: String, r: TestResult) =
        dao.updateTest(id, r.ok, if (r.ok) r.latencyMs else null, r.exitIp, r.countryCode, System.currentTimeMillis())

    suspend fun setFavorite(id: String, fav: Boolean) = dao.setFavorite(id, fav)
    suspend fun markUsed(id: String) = dao.markUsed(id, System.currentTimeMillis())
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun deleteOrigin(origin: ServerOrigin) = dao.deleteByOrigin(origin.name)
    suspend fun deleteSubscription(id: Long) = dao.deleteBySubscription(id)
    suspend fun count(origin: ServerOrigin) = dao.countByOrigin(origin.name)
}
