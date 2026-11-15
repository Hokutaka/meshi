package dev.hokutaka.meshi.data

import java.time.YearMonth

data class CategoryExpense(
    val categoryId: String,
    val label: String,
    val amountYen: Long
)

data class MonthlyLedgerReport(
    val records: List<LedgerTransaction>,
    val spentYen: Long,
    val earnedYen: Long,
    val categoryExpenses: List<CategoryExpense>
)

/**
 * Read-only summaries of existing transactions.
 *
 * Group by stable category ID instead of the display label so renaming a
 * category never changes historical totals. Income is excluded from the
 * expense breakdown. Future itemized transactions can evolve this logic
 * without migrating or rewriting current v1 data.
 */
object LedgerSummaries {
    fun forMonth(
        transactions: List<LedgerTransaction>,
        categories: List<Category>,
        month: YearMonth
    ): MonthlyLedgerReport {
        val firstDay = month.atDay(1).toEpochDay()
        val nextMonth = month.plusMonths(1).atDay(1).toEpochDay()
        val records = transactions.filter { it.dateEpochDay >= firstDay && it.dateEpochDay < nextMonth }
        val expenses = records.filter { it.kind == "expense" }
        val earnedYen = records.asSequence()
            .filter { it.kind == "income" }
            .sumOf { it.amountYen }
        val categoryLabels = categories.filter { it.kind == "expense" }
            .associate { it.id to it.label }

        val categoryExpenses = expenses.groupBy { it.categoryId }
            .map { (categoryId, entries) ->
                CategoryExpense(
                    categoryId = categoryId,
                    label = categoryLabels[categoryId] ?: "未分類",
                    amountYen = entries.sumOf { it.amountYen }
                )
            }
            .sortedWith(
                compareByDescending<CategoryExpense> { it.amountYen }
                    .thenBy { it.label }
                    .thenBy { it.categoryId }
            )

        return MonthlyLedgerReport(
            records = records,
            spentYen = expenses.sumOf { it.amountYen },
            earnedYen = earnedYen,
            categoryExpenses = categoryExpenses
        )
    }
}
