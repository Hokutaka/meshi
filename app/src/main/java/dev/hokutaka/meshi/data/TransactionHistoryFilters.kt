package dev.hokutaka.meshi.data

/**
 * Read-only filtering of the selected month's history. The caller provides
 * month-scoped records; filtering never changes totals or persisted data.
 */
object TransactionHistoryFilters {
    fun filter(
        records: List<LedgerTransaction>,
        categories: List<Category>,
        query: String,
        kind: String = "all"
    ): List<LedgerTransaction> {
        require(kind == "all" || kind == "expense" || kind == "income")
        val term = query.trim()
        val labels = categories.associate { it.id to it.label }
        return records.filter { entry ->
            (kind == "all" || entry.kind == kind) &&
                (term.isEmpty() ||
                    entry.note.contains(term, ignoreCase = true) ||
                    (labels[entry.categoryId] ?: "未分類").contains(term, ignoreCase = true))
        }
    }
}
