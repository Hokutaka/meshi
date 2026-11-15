package dev.hokutaka.meshi.backup

import dev.hokutaka.meshi.data.Category
import dev.hokutaka.meshi.data.EntryTemplate
import dev.hokutaka.meshi.data.LedgerTransaction
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class LedgerSnapshot(
    val categories: List<Category>,
    val transactions: List<LedgerTransaction>,
    val templates: List<EntryTemplate> = emptyList()
)

/**
 * Portable, independently versioned logical data format.
 * Only authenticated, validated snapshots may be handed to the repository.
 */
object SnapshotCodec {
    private const val version = 2
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
        snapshot.templates.forEach { t ->
            require(t.id.length in 1..maxIdChars && t.categoryId.length in 1..maxIdChars)
            require(t.name.isNotBlank() && t.name.length <= 40)
            require(t.kind == "income" || t.kind == "expense")
            require(t.amountYen in 1..1_000_000_000_000L)
            require(t.note.length <= maxNoteChars)
            require(t.sortOrder in 0..100_000)
            require(categories[t.categoryId]?.kind == t.kind) {
                "テンプレートのカテゴリー参照が正しくありません"
            }
        }
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
        val templates = JSONArray()
        snapshot.templates.forEach { t ->
            templates.put(JSONObject()
                .put("id", t.id)
                .put("name", t.name)
                .put("kind", t.kind)
                .put("amountYen", t.amountYen)
                .put("categoryId", t.categoryId)
                .put("note", t.note)
                .put("sortOrder", t.sortOrder))
        }
        return JSONObject()
            .put("format", "meshi-ledger")
            .put("schemaVersion", version)
            .put("categories", categories)
            .put("transactions", transactions)
            .put("templates", templates)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): LedgerSnapshot {
        require(bytes.size <= BackupCipher.maxFileBytes) { "データが大きすぎます" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.string("format") == "meshi-ledger") { "対応していないデータです" }
        val sourceVersion = root.long("schemaVersion")
        require(sourceVersion in 1..version.toLong()) { "未対応のデータバージョンです" }
        val categoriesJson = root.getJSONArray("categories")
        val transactionsJson = root.getJSONArray("transactions")
        // v1 backups did not contain templates. They are restored as an empty list.
        val templatesJson = if (sourceVersion >= 2) root.getJSONArray("templates") else JSONArray()
        require(categoriesJson.length() <= maxRecords &&
            transactionsJson.length() <= maxRecords && templatesJson.length() <= 200) {
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
        val templates = (0 until templatesJson.length()).map {
            val t = templatesJson.getJSONObject(it)
            EntryTemplate(
                id = t.string("id"),
                name = t.string("name"),
                kind = t.string("kind"),
                amountYen = t.long("amountYen"),
                categoryId = t.string("categoryId"),
                note = t.string("note"),
                sortOrder = t.int("sortOrder")
            )
        }
        return LedgerSnapshot(categories, transactions, templates).also(::validate)
    }

    fun validate(snapshot: LedgerSnapshot) {
        require(snapshot.categories.size <= maxRecords && snapshot.transactions.size <= maxRecords)
        require(snapshot.templates.size <= 200)
        require(snapshot.templates.map { it.id }.toSet().size == snapshot.templates.size) {
            "テンプレートIDが重複しています"
        }
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
