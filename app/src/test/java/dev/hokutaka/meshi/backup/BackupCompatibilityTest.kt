package dev.hokutaka.meshi.backup

import dev.hokutaka.meshi.data.EntryTemplate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A frozen v1 snapshot from the first signed release's portable format. */
class BackupCompatibilityTest {
    private fun oldSnapshotBytes(): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("compat/ledger-v1.json")) {
            "Missing v1 backup fixture"
        }.use { it.readBytes() }

    @Test fun publishedV1BackupIsStillReadable() {
        val decoded = SnapshotCodec.decode(oldSnapshotBytes())
        assertEquals(listOf("food", "salary"), decoded.categories.map { it.id })
        assertEquals(listOf("v1-expense", "v1-income"), decoded.transactions.map { it.id })
        assertEquals(1250L, decoded.transactions[0].amountYen)
        assertEquals("昼ごはん", decoded.transactions[0].note)
        assertEquals(250000L, decoded.transactions[1].amountYen)
        assertEquals(emptyList<EntryTemplate>(), decoded.templates)

        // Re-encoding old logical data must not discard any v1 fields.
        assertEquals(decoded, SnapshotCodec.decode(SnapshotCodec.encode(decoded)))
    }

    @Test fun v1SnapshotInsideEncryptedEnvelopeCanBeRestored() {
        val password = "old-backup-password-123".toCharArray()
        val encrypted = BackupCipher.encrypt(oldSnapshotBytes(), password)
        val restored = SnapshotCodec.decode(BackupCipher.decrypt(encrypted, password))
        assertEquals(2, restored.transactions.size)
        assertEquals("昼ごはん", restored.transactions[0].note)
    }

    @Test fun templateRoundTripPreservesCurrentData() {
        val old = SnapshotCodec.decode(oldSnapshotBytes())
        val template = EntryTemplate(
            id = "weekly-shopping",
            name = "週末の買い物",
            kind = "expense",
            amountYen = 2500L,
            categoryId = "food",
            note = "スーパー",
            sortOrder = 0
        )
        val current = old.copy(templates = listOf(template))
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(current))
        assertEquals(current, decoded)
    }

    @Test fun invalidTemplateCategoryCannotEnterBackup() {
        val old = SnapshotCodec.decode(oldSnapshotBytes())
        val template = EntryTemplate("bad", "誤り", "income", 100L, "food", "", 0)
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotCodec.encode(old.copy(templates = listOf(template)))
        }
    }

    @Test fun oldV1BackupCanBeUpgradedWithoutChangingTransactions() {
        val old = SnapshotCodec.decode(oldSnapshotBytes())
        val upgraded = SnapshotCodec.decode(SnapshotCodec.encode(old))
        assertEquals(old.transactions, upgraded.transactions)
        assertEquals(old.categories, upgraded.categories)
        assertEquals(emptyList<EntryTemplate>(), upgraded.templates)
    }

    @Test fun currentBackupWriterUsesVersionTwo() {
        val encoded = SnapshotCodec.encode(SnapshotCodec.decode(oldSnapshotBytes()))
        val json = JSONObject(encoded.toString(Charsets.UTF_8))
        assertEquals("meshi-ledger", json.getString("format"))
        assertEquals(2, json.getInt("schemaVersion"))
    }

    @Test fun futureVersionsMustNotBeSilentlyMisread() {
        val future = JSONObject(oldSnapshotBytes().toString(Charsets.UTF_8))
            .put("schemaVersion", 3)
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotCodec.decode(future.toString().toByteArray(Charsets.UTF_8))
        }
    }

    @Test fun brokenCategoryReferencesAreRejectedBeforeRestore() {
        val broken = JSONObject(oldSnapshotBytes().toString(Charsets.UTF_8))
        broken.getJSONArray("transactions").getJSONObject(0).put("categoryId", "missing")
        assertEquals("missing", broken.getJSONArray("transactions").getJSONObject(0).getString("categoryId"))
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotCodec.decode(broken.toString().toByteArray(Charsets.UTF_8))
        }
    }
}
