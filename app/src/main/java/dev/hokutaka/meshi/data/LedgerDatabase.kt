package dev.hokutaka.meshi.data

import androidx.room.AutoMigration
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

@Entity(tableName = "entry_templates")
data class EntryTemplate(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val amountYen: Long,
    val categoryId: String,
    val note: String,
    val sortOrder: Int
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_transactions ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeTransactions(): Flow<List<LedgerTransaction>>

    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, label")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM entry_templates ORDER BY sortOrder, name, id")
    fun observeTemplates(): Flow<List<EntryTemplate>>

    @Query("SELECT * FROM entry_templates ORDER BY sortOrder, name, id")
    suspend fun snapshotTemplates(): List<EntryTemplate>

    @Query("SELECT * FROM entry_templates WHERE id = :id LIMIT 1")
    suspend fun templateById(id: String): EntryTemplate?

    @Query("SELECT MAX(sortOrder) FROM entry_templates")
    suspend fun maxTemplateSortOrder(): Int?

    @Query("SELECT COUNT(*) FROM entry_templates WHERE categoryId = :categoryId")
    suspend fun countTemplatesInCategory(categoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTemplates(templates: List<EntryTemplate>)

    @androidx.room.Update
    suspend fun updateTemplate(template: EntryTemplate)

    @Query("DELETE FROM entry_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("DELETE FROM entry_templates")
    suspend fun deleteAllTemplates()

    @Query("SELECT * FROM ledger_transactions ORDER BY dateEpochDay, id")
    suspend fun snapshotTransactions(): List<LedgerTransaction>

    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, id")
    suspend fun snapshotCategories(): List<Category>

    @Query("DELETE FROM ledger_transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(transactions: List<LedgerTransaction>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun categoryById(id: String): Category?

    @Query("SELECT * FROM categories WHERE kind = :kind ORDER BY sortOrder, label")
    suspend fun categoriesByKind(kind: String): List<Category>

    @Query("SELECT COUNT(*) FROM categories WHERE kind = :kind")
    suspend fun countCategoriesByKind(kind: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE kind = :kind AND label = :label COLLATE NOCASE AND id != :excludingId")
    suspend fun countDuplicateCategories(kind: String, label: String, excludingId: String): Int

    @Query("SELECT COUNT(*) FROM ledger_transactions WHERE categoryId = :categoryId")
    suspend fun countTransactionsInCategory(categoryId: String): Int

    @androidx.room.Update
    suspend fun updateCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: LedgerTransaction)

    @Query("DELETE FROM ledger_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategories(categories: List<Category>)
}

@Database(
    entities = [LedgerTransaction::class, Category::class, EntryTemplate::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}
