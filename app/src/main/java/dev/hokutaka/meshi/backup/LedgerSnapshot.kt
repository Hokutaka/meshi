package dev.hokutaka.meshi.backup

import dev.hokutaka.meshi.data.Category
import dev.hokutaka.meshi.data.LedgerTransaction
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class LedgerSnapshot(
    val categories: List<Category>,
    val transactions: List<LedgerTransaction>
)

/**
 * Portable, independently versioned logical data format.
 * Only authenticated, validated snapshots may be handed to the repository.
 */
object SnapshotCodec {
    private const val version = 1
    private const val maxRecords = 100_000
    private const val maxNoteChars = 4096
    private const val maxLabelChars = 120
    private const val maxIdChars = 128
    private val minDay = LocalDate.of(1900, 1, 1).toEpochDay()
    private val maxDay = LocalDate.of(2100, 12, 31).toEpochDay()

    fun encode(snapshot: LedgerSnapshot): ByteArray {
        validate(snapshot)
        val categories = JSONArray()
        snapshot.categories.forEach { c ->
            categories.put(JSONObject()
                .put("id", c.id)
                .put("label", c.label)
                .put("kind", c.kind)
                .put("sortOrder", c.sortOrder))
        }
        val transactions = JSONArray()
        snapshot.transactions.forEach { t ->
            transactions.put(JSONObject()
                .put("id", t.id)
                .put("kind", t.kind)
                .put("amountYen", t.amountYen)
                .put("dateEpochDay", t.dateEpochDay)
                .put("categoryId", t.categoryId)
                .put("note", t.note)
                .put("createdAtMillis", t.createdAtMillis))
        }
        return JSONObject()
            .put("format", "meshi-ledger")
            .put("schemaVersion", version)
            .put("categories", categories)
            .put("transactions", transactions)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): LedgerSnapshot {
        require(bytes.size <= BackupCipher.maxFileBytes) { "データが大きすぎます" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.string("format") == "meshi-ledger") { "対応していないデータです" }
        require(root.long("schemaVersion") == version.toLong()) { "未対応のデータバージョンです" }
        val categoriesJson = root.getJSONArray("categories")
        val transactionsJson = root.getJSONArray("transactions")
        require(categoriesJson.length() <= maxRecords && transactionsJson.length() <= maxRecords) {
            "レコード数が多すぎます"
        }
        val categories = (0 until categoriesJson.length()).map {
            val c = categoriesJson.getJSONObject(it)
            Category(
                id = c.string("id"),
                label = c.string("label"),
                kind = c.string("kind"),
                sortOrder = c.int("sortOrder")
            )
        }
        val transactions = (0 until transactionsJson.length()).map {
            val t = transactionsJson.getJSONObject(it)
            LedgerTransaction(
                id = t.string("id"),
                kind = t.string("kind"),
                amountYen = t.long("amountYen"),
                dateEpochDay = t.long("dateEpochDay"),
                categoryId = t.string("categoryId"),
                note = t.string("note"),
                createdAtMillis = t.long("createdAtMillis")
            )
        }
        return LedgerSnapshot(categories, transactions).also(::validate)
    }

    fun validate(snapshot: LedgerSnapshot) {
        require(snapshot.categories.size <= maxRecords && snapshot.transactions.size <= maxRecords)
        require(snapshot.categories.map { it.id }.toSet().size == snapshot.categories.size) {
            "カテゴリーIDが重複しています"
        }
        require(snapshot.transactions.map { it.id }.toSet().size == snapshot.transactions.size) {
            "取引IDが重複しています"
        }
        val categories = snapshot.categories.associateBy { it.id }
        snapshot.categories.forEach { c ->
            require(c.id.length in 1..maxIdChars && c.label.length in 1..maxLabelChars)
            require(c.kind == "income" || c.kind == "expense")
            require(c.sortOrder in 0..100_000)
        }
        snapshot.transactions.forEach { t ->
            require(t.id.length in 1..maxIdChars && t.categoryId.length in 1..maxIdChars)
            require(t.kind == "income" || t.kind == "expense")
            require(t.amountYen > 0 && t.amountYen <= 1_000_000_000_000L)
            require(t.dateEpochDay in minDay..maxDay)
            require(t.createdAtMillis >= 0)
            require(t.note.length <= maxNoteChars)
            require(categories[t.categoryId]?.kind == t.kind) {
                "取引のカテゴリー参照が正しくありません"
            }
        }
    }

    private fun JSONObject.string(key: String): String {
        val value = get(key)
        require(value is String) { "$key は文字列ではありません" }
        return value
    }

    private fun JSONObject.long(key: String): Long {
        val value = get(key)
        require(value is Number && value !is Double && value !is Float) {
            "$key は整数ではありません"
        }
        return value.toLong()
    }

    private fun JSONObject.int(key: String): Int {
        val value = long(key)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return value.toInt()
    }
}
