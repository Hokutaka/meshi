package dev.hokutaka.meshi.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v1 schema is loaded from the frozen Room export in androidTest assets,
 * not constructed by the current Room compiler. This remains usable when
 * the production database evolves to version 2 or later.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseCompatibilityTest {
    private fun createV1Database(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val asset = instrumentation.context.assets.open(
            "dev.hokutaka.meshi.data.LedgerDatabase/1.json"
        ).bufferedReader().use { JSONObject(it.readText()) }
        val schema = asset.getJSONObject("database")
        assertEquals(1, schema.getInt("version"))
        val databaseFile = context.getDatabasePath(name)
        databaseFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT INTO categories (id, label, kind, sortOrder) " +
                    "VALUES ('food', '食費', 'expense', 0)"
            )
            db.execSQL(
                "INSERT INTO categories (id, label, kind, sortOrder) " +
                    "VALUES ('salary', '給与', 'income', 0)"
            )
            db.execSQL(
                "INSERT INTO ledger_transactions " +
                    "(id, kind, amountYen, dateEpochDay, categoryId, note, createdAtMillis) " +
                    "VALUES ('old-expense', 'expense', 1250, 20000, 'food', '昼ごはん', 1234)"
            )
            db.execSQL(
                "INSERT INTO ledger_transactions " +
                    "(id, kind, amountYen, dateEpochDay, categoryId, note, createdAtMillis) " +
                    "VALUES ('old-income', 'income', 250000, 20000, 'salary', '', 1235)"
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    @Test fun publishedV1DatabaseRetainsTransactionsWhenOpenedByCurrentRoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "legacy-" + UUID.randomUUID() + ".db"
        createV1Database(name)
        val reopened = LedgerDatabaseFactory.builder(context, name).build()
        try {
            runBlocking {
                val snapshot = LedgerRepository(reopened).snapshot()
                assertEquals(2, snapshot.transactions.size)
                assertEquals(2, snapshot.categories.size)
                assertTrue(snapshot.templates.isEmpty())
                assertEquals(1250L, snapshot.transactions.first().amountYen)
                assertEquals("昼ごはん", snapshot.transactions.first().note)
                assertEquals("food", snapshot.transactions.first().categoryId)
                assertEquals(250000L, snapshot.transactions.last().amountYen)
                assertEquals(248750L, snapshot.transactions.sumOf {
                    if (it.kind == "income") it.amountYen else -it.amountYen
                })
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
