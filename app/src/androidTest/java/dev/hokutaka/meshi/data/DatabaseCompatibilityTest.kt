package dev.hokutaka.meshi.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the exported v1 schema from the first signed release. */
@RunWith(AndroidJUnit4::class)
class DatabaseCompatibilityTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LedgerDatabase::class.java
    )

    @Test fun publishedV1DatabaseRetainsTransactionsWhenOpenedByCurrentRoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "legacy-" + UUID.randomUUID() + ".db"

        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO categories (id, label, kind, sortOrder) " +
                    "VALUES ('food', '食費', 'expense', 0)"
            )
            execSQL(
                "INSERT INTO categories (id, label, kind, sortOrder) " +
                    "VALUES ('salary', '給与', 'income', 0)"
            )
            execSQL(
                "INSERT INTO ledger_transactions " +
                    "(id, kind, amountYen, dateEpochDay, categoryId, note, createdAtMillis) " +
                    "VALUES ('old-expense', 'expense', 1250, 20000, 'food', '昼ごはん', 1234)"
            )
            execSQL(
                "INSERT INTO ledger_transactions " +
                    "(id, kind, amountYen, dateEpochDay, categoryId, note, createdAtMillis) " +
                    "VALUES ('old-income', 'income', 250000, 20000, 'salary', '', 1235)"
            )
            close()
        }

        val reopened = LedgerDatabaseFactory.builder(context, name).build()
        try {
            runBlocking {
                val snapshot = LedgerRepository(reopened).snapshot()
                assertEquals(2, snapshot.transactions.size)
                assertEquals(2, snapshot.categories.size)
                assertEquals(1250L, snapshot.transactions.first().amountYen)
                assertEquals("昼ごはん", snapshot.transactions.first().note)
                assertEquals("food", snapshot.transactions.first().categoryId)
                assertEquals(250000L, snapshot.transactions.last().amountYen)
                assertEquals(250000L - 1250L,
                    snapshot.transactions.sumOf { if (it.kind == "income") it.amountYen else -it.amountYen })
            }
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun sqlCipherDataIsReadableAfterClosingAndReopeningDatabase() {
        System.loadLibrary("sqlcipher")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "encrypted-" + UUID.randomUUID() + ".db"
        val passphrase = ByteArray(32) { (it + 51).toByte() }
        val category = Category("food", "食費", "expense", 0)
        val transaction = LedgerTransaction("persistent", "expense", 930L, 20001L, "food", "保存テスト", 42L)

        val original = LedgerDatabaseFactory.builder(context, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .build()
        try {
            runBlocking {
                original.ledgerDao().insertCategories(listOf(category))
                original.ledgerDao().upsertTransaction(transaction)
            }
        } finally {
            original.close()
        }

        val reopened = LedgerDatabaseFactory.builder(context, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .build()
        try {
            runBlocking {
                val snapshot = LedgerRepository(reopened).snapshot()
                assertEquals(listOf(category), snapshot.categories)
                assertEquals(listOf(transaction), snapshot.transactions)
            }
            assertTrue(context.getDatabasePath(name).exists())
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}
