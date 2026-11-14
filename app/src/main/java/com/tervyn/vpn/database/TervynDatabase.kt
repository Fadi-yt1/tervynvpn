package com.tervyn.vpn.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ServerEntity::class, SubscriptionEntity::class, DomainEntity::class, HistoryEntity::class, DiagnosticEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class TervynDatabase : RoomDatabase() {
    abstract fun servers(): ServerDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun domains(): DomainDao
    abstract fun history(): HistoryDao
    abstract fun diagnostics(): DiagnosticDao

    companion object {
        /** v2: declared country for built-in servers; the retired public-list sources are dropped. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE servers ADD COLUMN declaredCountry TEXT")
                db.execSQL("DELETE FROM servers WHERE origin IN ('FREE_PUBLIC','PROXIO','INTEGRATION','LIST_FILE','TERVYN_FAST','TERVYN_ALL')")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun create(context: Context): TervynDatabase =
            Room.databaseBuilder(context, TervynDatabase::class.java, "tervyn.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
