package dev.hokutaka.meshi.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val database: LedgerDatabase) {
    private val dao = database.ledgerDao()

    val transactions: Flow<List<LedgerTransaction>> = dao.observeTransactions()
    val categories: Flow<List<Category>> = dao.observeCategories()

    suspend fun initialize() = database.withTransaction {
        if (dao.categoryCount() == 0) {
            dao.insertCategories(
                listOf(
                    Category("food", "食費", "expense", 0),
                    Category("daily", "日用品", "expense", 1),
                    Category("transport", "交通費", "expense", 2),
                    Category("housing", "住居", "expense", 3),
                    Category("hobby", "趣味・娯楽", "expense", 4),
                    Category("other_out", "その他", "expense", 5),
                    Category("salary", "給与", "income", 0),
                    Category("other_in", "その他収入", "income", 1)
                )
            )
        }
    }

    suspend fun save(
        existing: LedgerTransaction?,
        kind: String,
        amountYen: Long,
        date: LocalDate,
        categoryId: String,
        note: String
    ) {
        require(kind == "expense" || kind == "income")
        require(amountYen > 0) { "金額は1円以上にしてください" }
        require(categoryId.isNotBlank())
        dao.upsertTransaction(
            LedgerTransaction(
                id = existing?.id ?: UUID.randomUUID().toString(),
                kind = kind,
                amountYen = amountYen,
                dateEpochDay = date.toEpochDay(),
                categoryId = categoryId,
                note = note.trim(),
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(id: String) = dao.deleteTransaction(id)
}
