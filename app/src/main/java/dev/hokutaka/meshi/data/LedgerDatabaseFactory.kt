package dev.hokutaka.meshi.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Single source of truth for Room configuration in production and compatibility tests.
 *
 * Add each non-destructive migration here before increasing LedgerDatabase.version.
 * Never use fallbackToDestructiveMigration for user data.
 */
object LedgerDatabaseFactory {
    private val migrations: Array<Migration> = emptyArray()

    fun builder(context: Context, name: String): RoomDatabase.Builder<LedgerDatabase> =
        Room.databaseBuilder(context, LedgerDatabase::class.java, name)
            .addMigrations(*migrations)
}
