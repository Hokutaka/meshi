package dev.hokutaka.meshi.data

import androidx.room.withTransaction
import dev.hokutaka.meshi.backup.LedgerSnapshot
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val database: LedgerDatabase) {
    private val dao = database.ledgerDao()

    val transactions: Flow<List<LedgerTransaction>> = dao.observeTransactions()
    val categories: Flow<List<Category>> = dao.observeCategories()
    val templates: Flow<List<EntryTemplate>> = dao.observeTemplates()

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

    suspend fun addCategory(kind: String, name: String) = database.withTransaction {
        require(kind == "expense" || kind == "income") { "取引種別が正しくありません" }
        val label = CategoryRules.normalizeLabel(name)
        require(dao.countDuplicateCategories(kind, label, "") == 0) {
            "同じ名前のカテゴリーがあります"
        }
        val siblings = dao.categoriesByKind(kind)
        val order = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.insertCategories(listOf(Category(UUID.randomUUID().toString(), label, kind, order)))
    }

    suspend fun renameCategory(id: String, name: String) = database.withTransaction {
        val current = dao.categoryById(id) ?: error("カテゴリーが見つかりません")
        val label = CategoryRules.normalizeLabel(name)
        require(dao.countDuplicateCategories(current.kind, label, id) == 0) {
            "同じ名前のカテゴリーがあります"
        }
        dao.updateCategory(current.copy(label = label))
    }

    suspend fun moveCategory(id: String, direction: Int) = database.withTransaction {
        val current = dao.categoryById(id) ?: error("カテゴリーが見つかりません")
        val siblings = dao.categoriesByKind(current.kind)
        val reordered = CategoryRules.reorder(siblings, id, direction)
        reordered.forEach { updated ->
            val old = siblings.first { it.id == updated.id }
            if (updated.sortOrder != old.sortOrder) {
                dao.updateCategory(updated)
            }
        }
    }

    suspend fun removeCategory(id: String) = database.withTransaction {
        val current = dao.categoryById(id) ?: error("カテゴリーが見つかりません")
        require(dao.countTransactionsInCategory(id) == 0) {
            "取引に使われているカテゴリーは削除できません"
        }
        require(dao.countTemplatesInCategory(id) == 0) {
            "テンプレートに使われているカテゴリーは削除できません"
        }
        require(dao.countCategoriesByKind(current.kind) > 1) {
            "カテゴリーは支出・収入それぞれに1つ以上必要です"
        }
        dao.deleteCategory(id)
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
        database.withTransaction {
            require(dao.categoryById(categoryId)?.kind == kind) {
                "カテゴリーが見つからないか、取引種別と一致しません"
            }
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
    }

    suspend fun delete(id: String) = dao.deleteTransaction(id)

    suspend fun saveTemplate(
        existing: EntryTemplate?,
        name: String,
        kind: String,
        amountYen: Long,
        categoryId: String,
        note: String
    ) = database.withTransaction {
        val trimmedName = name.trim()
        val trimmedNote = note.trim()
        require(trimmedName.isNotEmpty() && trimmedName.length <= 40) {
            "テンプレート名は1〜40文字で入力してください"
        }
        require(kind == "expense" || kind == "income") { "取引種別が正しくありません" }
        require(amountYen in 1..1_000_000_000_000L) { "金額が正しくありません" }
        require(trimmedNote.length <= 4096) { "メモが長すぎます" }
        require(dao.categoryById(categoryId)?.kind == kind) {
            "カテゴリーが見つからないか、取引種別と一致しません"
        }
        if (existing == null) {
            require(dao.snapshotTemplates().size < 200) { "テンプレートは200件までです" }
            dao.insertTemplates(listOf(EntryTemplate(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                kind = kind,
                amountYen = amountYen,
                categoryId = categoryId,
                note = trimmedNote,
                sortOrder = (dao.maxTemplateSortOrder() ?: -1) + 1
            )))
        } else {
            val current = dao.templateById(existing.id) ?: error("テンプレートが見つかりません")
            dao.updateTemplate(current.copy(
                name = trimmedName,
                kind = kind,
                amountYen = amountYen,
                categoryId = categoryId,
                note = trimmedNote
            ))
        }
    }

    suspend fun removeTemplate(id: String) = dao.deleteTemplate(id)

    suspend fun snapshot(): LedgerSnapshot = database.withTransaction {
        LedgerSnapshot(
            categories = dao.snapshotCategories(),
            transactions = dao.snapshotTransactions(),
            templates = dao.snapshotTemplates()
        )
    }

    // Validation is completed before this call. A failed insert rolls the
    // entire replacement back, preserving the existing ledger.
    suspend fun restore(snapshot: LedgerSnapshot) = database.withTransaction {
        dao.deleteAllTransactions()
        dao.deleteAllTemplates()
        dao.deleteAllCategories()
        dao.insertCategories(snapshot.categories)
        dao.insertTransactions(snapshot.transactions)
        dao.insertTemplates(snapshot.templates)
    }
}
