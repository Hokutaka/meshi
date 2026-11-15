package dev.hokutaka.meshi.data

object CategoryRules {
    fun normalizeLabel(input: String): String {
        val label = input.trim()
        require(label.isNotEmpty()) { "カテゴリー名を入力してください" }
        require(label.length <= 40) { "カテゴリー名は40文字以内にしてください" }
        return label
    }

    fun reorder(categories: List<Category>, id: String, direction: Int): List<Category> {
        require(direction == -1 || direction == 1)
        val index = categories.indexOfFirst { it.id == id }
        require(index >= 0) { "カテゴリーが見つかりません" }
        val neighbor = index + direction
        if (neighbor !in categories.indices) return categories
        val ordered = categories.toMutableList()
        val temp = ordered[index]
        ordered[index] = ordered[neighbor]
        ordered[neighbor] = temp
        return ordered.mapIndexed { i, category -> category.copy(sortOrder = i) }
    }
}
