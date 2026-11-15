package dev.hokutaka.meshi.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "ledger_transactions",
    indices = [Index(value = ["dateEpochDay"]), Index(value = ["categoryId"])]
)
data class LedgerTransaction(
    @PrimaryKey val id: String,
    val kind: String,
    val amountYen: Long,
    val dateEpochDay: Long,
    val categoryId: String,
    val note: String,
    val createdAtMillis: Long
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val label: String,
    val kind: String,
    val sortOrder: Int
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_transactions ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeTransactions(): Flow<List<LedgerTransaction>>

    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, label")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: LedgerTransaction)

    @Query("DELETE FROM ledger_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategories(categories: List<Category>)
}

@Database(
    entities = [LedgerTransaction::class, Category::class],
    version = 1,
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}
