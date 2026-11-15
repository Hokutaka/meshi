package dev.hokutaka.meshi

import dev.hokutaka.meshi.data.LedgerTransaction
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateAndMoneyTest {
    @Test fun epochDayRoundTrip() {
        val today = LocalDate.parse("2026-11-15")
        assertEquals(today, LocalDate.ofEpochDay(today.toEpochDay()))
    }

    @Test fun transactionsCanBeSummedWithoutItemRecords() {
        val entries = listOf(
            LedgerTransaction("1", "expense", 980L, 1L, "food", "", 0),
            LedgerTransaction("2", "expense", 250L, 1L, "daily", "", 0),
            LedgerTransaction("3", "income", 10000L, 1L, "salary", "", 0)
        )
        assertEquals(1230L, entries.filter { it.kind == "expense" }.sumOf { it.amountYen })
        assertEquals(10000L, entries.filter { it.kind == "income" }.sumOf { it.amountYen })
    }
}
