package com.tervyn.vpn.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Credentials (password, share link) are stored only in encrypted columns (`*Enc`). */
@Entity(tableName = "servers", indices = [Index("origin"), Index("alive"), Index("countryCode")])
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocolLabel: String,
    /** "HTTP" / "SOCKS5" for plain proxies, "XRAY" for share-link servers. */
    val kind: String,
    val host: String,
    val port: Int,
    val username: String?,
    val passwordEnc: String?,
    val xrayLinkEnc: String?,
    val origin: String,
    val subscriptionId: Long?,
    val countryCode: String?,
    val exitIp: String?,
    val latencyMs: Int?,
    val alive: Boolean,
    val failCount: Int,
    val lastChecked: Long,
    val lastUsed: Long,
    val favorite: Boolean,
    val addedAt: Long,
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val urlEnc: String,
    val lastUpdated: Long = 0,
    val serverCount: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "domains")
data class DomainEntity(
    @PrimaryKey val domain: String,
    val label: String,
    val enabled: Boolean = true,
    /** Preset id this domain was added by, or null for a custom website. */
    val presetId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history", indices = [Index("startedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val serverName: String,
    val countryCode: String?,
    val protocol: String,
    val mode: String,
    val bytesDown: Long = 0,
    val bytesUp: Long = 0,
    /** CONNECTED, ENDED, FAILED */
    val status: String,
    val detail: String? = null,
)

@Entity(tableName = "diagnostics", indices = [Index("time")])
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    /** SPEED_TEST, DIAGNOSTICS, EVENT */
    val type: String,
    val title: String,
    val summary: String,
    val pingMs: Int? = null,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
)
