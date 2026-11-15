package dev.hokutaka.meshi.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerSummariesTest {
    private fun entry(
        id: String,
        kind: String,
        amountYen: Long,
        date: String,
        categoryId: String
    ) = LedgerTransaction(
        id = id,
        kind = kind,
        amountYen = amountYen,
        dateEpochDay = LocalDate.parse(date).toEpochDay(),
        categoryId = categoryId,
        note = "",
        createdAtMillis = 0
    )

    private val categories = listOf(
        Category("food", "食費", "expense", 0),
        Category("daily", "日用品", "expense", 1),
        Category("salary", "給与", "income", 0)
    )

    @Test fun monthBoundariesAndIncomeAreHandledSeparately() {
        val transactions = listOf(
            entry("before", "expense", 999, "2026-10-31", "food"),
            entry("meal1", "expense", 1200, "2026-11-01", "food"),
            entry("meal2", "expense", 700, "2026-11-30", "food"),
            entry("daily", "expense", 900, "2026-11-15", "daily"),
            entry("salary", "income", 200000, "2026-11-15", "salary"),
            entry("after", "expense", 123, "2026-12-01", "food")
        )

        val result = LedgerSummaries.forMonth(transactions, categories, YearMonth.of(2026, 11))
        assertEquals(4, result.records.size)
        assertEquals(2800L, result.spentYen)
        assertEquals(200000L, result.earnedYen)
        assertEquals(
            listOf(
                CategoryExpense("food", "食費", 1900L),
                CategoryExpense("daily", "日用品", 900L)
            ),
            result.categoryExpenses
        )
        assertEquals(result.spentYen, result.categoryExpenses.sumOf { it.amountYen })
    }

    @Test fun renamingCategoryOnlyChangesTheDisplayLabel() {
        val entries = listOf(entry("old", "expense", 430, "2026-11-09", "food"))
        val renamed = categories.map { if (it.id == "food") it.copy(label = "外食") else it }

        val result = LedgerSummaries.forMonth(entries, renamed, YearMonth.of(2026, 11))
        assertEquals(listOf(CategoryExpense("food", "外食", 430L)), result.categoryExpenses)
        assertEquals("food", result.records.single().categoryId)
    }

    @Test fun missingCategoryAndEmptyMonthRemainSafe() {
        val entries = listOf(entry("orphan", "expense", 50, "2026-11-12", "no-longer-known"))
        val nov = LedgerSummaries.forMonth(entries, categories, YearMonth.of(2026, 11))
        assertEquals(listOf(CategoryExpense("no-longer-known", "未分類", 50L)), nov.categoryExpenses)

        val dec = LedgerSummaries.forMonth(entries, categories, YearMonth.of(2026, 12))
        assertTrue(dec.records.isEmpty())
        assertTrue(dec.categoryExpenses.isEmpty())
        assertEquals(0L, dec.spentYen)
        assertEquals(0L, dec.earnedYen)
    }

    @Test fun sameLabelDoesNotMergeDifferentCategoryIds() {
        val list = listOf(
            Category("a", "その他", "expense", 0),
            Category("b", "その他", "expense", 1)
        )
        val entries = listOf(
            entry("a1", "expense", 200, "2026-11-01", "a"),
            entry("b1", "expense", 200, "2026-11-01", "b")
        )
        val result = LedgerSummaries.forMonth(entries, list, YearMonth.of(2026, 11))
        assertEquals(listOf("a", "b"), result.categoryExpenses.map { it.categoryId })
        assertEquals(400L, result.spentYen)
    }
}
