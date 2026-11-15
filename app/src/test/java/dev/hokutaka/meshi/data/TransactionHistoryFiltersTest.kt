package dev.hokutaka.meshi.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionHistoryFiltersTest {
    private val categories = listOf(
        Category("food", "食費", "expense", 0),
        Category("daily", "日用品", "expense", 1),
        Category("salary", "給与", "income", 0)
    )

    private fun entry(
        id: String,
        kind: String,
        categoryId: String,
        date: String,
        note: String = ""
    ) = LedgerTransaction(
        id = id,
        kind = kind,
        amountYen = 500L,
        dateEpochDay = LocalDate.parse(date).toEpochDay(),
        categoryId = categoryId,
        note = note,
        createdAtMillis = 1L
    )

    private val entries = listOf(
        entry("food", "expense", "food", "2026-11-01", "スーパーで買い物"),
        entry("daily", "expense", "daily", "2026-11-02", "Coffee beans"),
        entry("salary", "income", "salary", "2026-11-03", "月給"),
        entry("old", "expense", "food", "2026-10-31", "スーパーで買い物")
    )

    private fun november(): List<LedgerTransaction> =
        LedgerSummaries.forMonth(entries, categories, YearMonth.of(2026, 11)).records

    @Test fun emptyQueryShowsAllMonthRecordsInOriginalOrder() {
        assertEquals(listOf("food", "daily", "salary"),
            TransactionHistoryFilters.filter(november(), categories, "  ").map { it.id })
    }

    @Test fun searchesMemoIgnoringCaseAndOuterWhitespace() {
        assertEquals(listOf("daily"),
            TransactionHistoryFilters.filter(november(), categories, " COFFEE ").map { it.id })
        assertEquals(listOf("food"),
            TransactionHistoryFilters.filter(november(), categories, "スーパー").map { it.id })
    }

    @Test fun searchesCurrentCategoryNameAfterRename() {
        val renamed = categories.map {
            if (it.id == "food") it.copy(label = "外食") else it
        }
        assertEquals(listOf("food"),
            TransactionHistoryFilters.filter(november(), renamed, "外食").map { it.id })
        assertTrue(TransactionHistoryFilters.filter(november(), renamed, "食費").isEmpty())
    }

    @Test fun kindFilterComposesWithTextSearch() {
        assertEquals(listOf("food", "daily"),
            TransactionHistoryFilters.filter(november(), categories, "", "expense").map { it.id })
        assertEquals(listOf("salary"),
            TransactionHistoryFilters.filter(november(), categories, "給与", "income").map { it.id })
        assertTrue(TransactionHistoryFilters.filter(november(), categories, "給与", "expense").isEmpty())
    }

    @Test fun filteringDoesNotChangeOriginalRecordsOrMonthlyTotals() {
        val report = LedgerSummaries.forMonth(entries, categories, YearMonth.of(2026, 11))
        val before = report.records.toList()
        val filtered = TransactionHistoryFilters.filter(report.records, categories, "食費")
        assertEquals(listOf("food"), filtered.map { it.id })
        assertEquals(before, report.records)
        assertEquals(1000L, report.spentYen)
        assertEquals(500L, report.earnedYen)
    }

    @Test fun unknownCategoriesCanBeFoundAsUnclassified() {
        val missing = listOf(entry("missing", "expense", "unknown", "2026-11-05"))
        assertEquals(listOf("missing"),
            TransactionHistoryFilters.filter(missing, categories, "未分類").map { it.id })
    }
}
