package dev.hokutaka.meshi

import android.app.Application
import androidx.room.Room
import dev.hokutaka.meshi.data.LedgerDatabase
import dev.hokutaka.meshi.data.LedgerRepository
import dev.hokutaka.meshi.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class MeshiApplication : Application() {
    val repository: LedgerRepository by lazy {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKeyManager(this).loadOrCreate()
        // SQLCipher holds this byte array for delayed opens and reopens.
        // Clearing it before Room opens the file would make the DB unusable.
        val database = Room.databaseBuilder(this, LedgerDatabase::class.java, "meshi.db")
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
        LedgerRepository(database)
    }
}
