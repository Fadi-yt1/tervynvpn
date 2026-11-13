package com.tervyn.vpn.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [ServerEntity::class, SubscriptionEntity::class, DomainEntity::class, HistoryEntity::class, DiagnosticEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TervynDatabase : RoomDatabase() {
    abstract fun servers(): ServerDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun domains(): DomainDao
    abstract fun history(): HistoryDao
    abstract fun diagnostics(): DiagnosticDao

    companion object {
        /** Explicit migrations are registered here as the schema evolves (schemas are exported to app/schemas). */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): TervynDatabase =
            Room.databaseBuilder(context, TervynDatabase::class.java, "tervyn.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
