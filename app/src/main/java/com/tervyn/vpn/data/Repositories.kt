package com.tervyn.vpn.data

import com.tervyn.vpn.core.Domains
import com.tervyn.vpn.database.DiagnosticDao
import com.tervyn.vpn.database.DiagnosticEntity
import com.tervyn.vpn.database.DomainDao
import com.tervyn.vpn.database.DomainEntity
import com.tervyn.vpn.database.HistoryDao
import com.tervyn.vpn.database.HistoryEntity
import com.tervyn.vpn.database.SubscriptionDao
import com.tervyn.vpn.database.SubscriptionEntity
import com.tervyn.vpn.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DomainRepository(private val dao: DomainDao) {
    val domains: Flow<List<DomainEntity>> = dao.observeAll()

    suspend fun enabledDomains(): List<String> = dao.all().filter { it.enabled }.map { it.domain }
    suspend fun enabledCidrs(): List<String> {
        val presetIds = dao.all().filter { it.enabled && it.presetId != null }.map { it.presetId }.toSet()
        return Domains.presets.filter { it.id in presetIds }.flatMap { it.cidrs }
    }

    /** Adds a website; returns the normalised domain or null if invalid. */
    suspend fun add(input: String, label: String? = null): String? {
        val d = Domains.normalize(input) ?: return null
        dao.insert(DomainEntity(d, label ?: d))
        return d
    }

    suspend fun addPreset(p: Domains.Preset) = dao.insertAll(p.domains.map { DomainEntity(it, p.label, presetId = p.id) })
    suspend fun removePreset(id: String) = dao.deletePreset(id)
    suspend fun setEnabled(domain: String, enabled: Boolean) = dao.setEnabled(domain, enabled)
    suspend fun remove(domain: String) = dao.delete(domain)
    suspend fun all() = dao.all()
}

class HistoryRepository(private val dao: HistoryDao, private val diag: DiagnosticDao) {
    val recent: Flow<List<HistoryEntity>> = dao.observeRecent()
    val diagnostics: Flow<List<DiagnosticEntity>> = diag.observeRecent()
    fun speedTests(): Flow<List<DiagnosticEntity>> = diag.observeType("SPEED_TEST")
    fun events(): Flow<List<DiagnosticEntity>> = diag.observeType("EVENT")

    suspend fun start(e: HistoryEntity) = dao.insert(e)
    suspend fun finish(id: Long, bytesDown: Long, bytesUp: Long, status: String, detail: String? = null) {
        val e = dao.byId(id) ?: return
        dao.update(e.copy(endedAt = System.currentTimeMillis(), bytesDown = bytesDown, bytesUp = bytesUp, status = status, detail = detail ?: e.detail))
    }
    suspend fun updateTraffic(id: Long, bytesDown: Long, bytesUp: Long) {
        val e = dao.byId(id) ?: return
        dao.update(e.copy(bytesDown = bytesDown, bytesUp = bytesUp))
    }
    suspend fun since(t: Long) = dao.since(t)
    suspend fun clear() { dao.clear(); diag.clear() }
    suspend fun log(type: String, title: String, summary: String, ping: Int? = null, down: Double? = null, up: Double? = null) =
        diag.insert(DiagnosticEntity(time = System.currentTimeMillis(), type = type, title = title, summary = summary, pingMs = ping, downMbps = down, upMbps = up))
}

class SubscriptionRepository(private val dao: SubscriptionDao, private val cipher: SecretCipher) {
    data class Subscription(val id: Long, val name: String, val url: String, val lastUpdated: Long, val count: Int, val error: String?)

    val subscriptions: Flow<List<Subscription>> = dao.observeAll().map { l -> l.map(::toDomain) }
    private fun toDomain(e: SubscriptionEntity) = Subscription(e.id, e.name, cipher.decrypt(e.urlEnc) ?: "", e.lastUpdated, e.serverCount, e.lastError)
    suspend fun all() = dao.all().map(::toDomain)
    suspend fun add(name: String, url: String) = dao.insert(SubscriptionEntity(name = name, urlEnc = cipher.encrypt(url)))
    suspend fun markUpdated(s: Subscription, count: Int, error: String?) =
        dao.update(SubscriptionEntity(s.id, s.name, cipher.encrypt(s.url), if (error == null) System.currentTimeMillis() else s.lastUpdated, count, error))
    suspend fun delete(id: Long) = dao.delete(id)
}
