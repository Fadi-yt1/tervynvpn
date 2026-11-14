package com.tervyn.vpn.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY favorite DESC, alive DESC, CASE WHEN latencyMs IS NULL THEN 1 ELSE 0 END, latencyMs ASC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers")
    suspend fun all(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE origin IN (:origins)")
    suspend fun byOrigins(origins: List<String>): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun byId(id: String): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id")
    fun observe(id: String): Flow<ServerEntity?>

    @Upsert suspend fun upsert(list: List<ServerEntity>)
    @Upsert suspend fun upsert(e: ServerEntity)

    @Query("UPDATE servers SET alive = :alive, latencyMs = :latency, exitIp = :ip, countryCode = COALESCE(:country, countryCode), lastChecked = :time, failCount = CASE WHEN :alive THEN 0 ELSE failCount + 1 END WHERE id = :id")
    suspend fun updateTest(id: String, alive: Boolean, latency: Int?, ip: String?, country: String?, time: Long)

    @Query("UPDATE servers SET favorite = :fav WHERE id = :id") suspend fun setFavorite(id: String, fav: Boolean)
    @Query("UPDATE servers SET lastUsed = :time WHERE id = :id") suspend fun markUsed(id: String, time: Long)
    @Query("DELETE FROM servers WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM servers WHERE origin = :origin AND favorite = 0") suspend fun deleteByOrigin(origin: String)
    @Query("DELETE FROM servers WHERE subscriptionId = :sub") suspend fun deleteBySubscription(sub: Long)
    @Query("UPDATE servers SET name = :name, declaredCountry = :declared WHERE id = :id") suspend fun rename(id: String, name: String, declared: String?)
    @Query("SELECT COUNT(*) FROM servers WHERE origin = :origin") suspend fun countByOrigin(origin: String): Int
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY id") fun observeAll(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM subscriptions") suspend fun all(): List<SubscriptionEntity>
    @Insert suspend fun insert(e: SubscriptionEntity): Long
    @Update suspend fun update(e: SubscriptionEntity)
    @Query("DELETE FROM subscriptions WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface DomainDao {
    @Query("SELECT * FROM domains ORDER BY addedAt DESC") fun observeAll(): Flow<List<DomainEntity>>
    @Query("SELECT * FROM domains") suspend fun all(): List<DomainEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(e: DomainEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(e: List<DomainEntity>)
    @Query("UPDATE domains SET enabled = :enabled WHERE domain = :domain") suspend fun setEnabled(domain: String, enabled: Boolean)
    @Query("DELETE FROM domains WHERE domain = :domain") suspend fun delete(domain: String)
    @Query("DELETE FROM domains WHERE presetId = :preset") suspend fun deletePreset(preset: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY startedAt DESC LIMIT 500") fun observeRecent(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history WHERE startedAt >= :since ORDER BY startedAt") suspend fun since(since: Long): List<HistoryEntity>
    @Insert suspend fun insert(e: HistoryEntity): Long
    @Update suspend fun update(e: HistoryEntity)
    @Query("SELECT * FROM history WHERE id = :id") suspend fun byId(id: Long): HistoryEntity?
    @Query("DELETE FROM history") suspend fun clear()
}

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostics ORDER BY time DESC LIMIT 200") fun observeRecent(): Flow<List<DiagnosticEntity>>
    @Query("SELECT * FROM diagnostics WHERE type = :type ORDER BY time DESC LIMIT 50") fun observeType(type: String): Flow<List<DiagnosticEntity>>
    @Insert suspend fun insert(e: DiagnosticEntity): Long
    @Query("DELETE FROM diagnostics") suspend fun clear()
}
