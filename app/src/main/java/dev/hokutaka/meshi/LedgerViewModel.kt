package dev.hokutaka.meshi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.hokutaka.meshi.data.Category
import dev.hokutaka.meshi.data.EntryTemplate
import dev.hokutaka.meshi.data.LedgerRepository
import dev.hokutaka.meshi.data.LedgerTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerUiState(
    val transactions: List<LedgerTransaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val templates: List<EntryTemplate> = emptyList()
)

class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {
    val state: StateFlow<LedgerUiState> = combine(
        repository.transactions,
        repository.categories,
        repository.templates
    ) { transactions, categories, templates ->
        LedgerUiState(transactions, categories, templates)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            runCatching { repository.initialize() }
                .onFailure { _message.value = "初期化に失敗しました: " + it.message }
        }
    }

    fun save(
        existing: LedgerTransaction?,
        kind: String,
        amount: String,
        date: String,
        categoryId: String,
        note: String,
        done: (Boolean) -> Unit
    ) {
        val value = amount.trim().toLongOrNull()
        if (value == null || value <= 0) {
            _message.value = "金額は1円以上の整数で入力してください"
            done(false)
            return
        }
        val parsedDate = try {
            LocalDate.parse(date.trim())
        } catch (_: Exception) {
            _message.value = "日付はYYYY-MM-DDで入力してください"
            done(false)
            return
        }
        if (state.value.categories.none { it.id == categoryId && it.kind == kind }) {
            _message.value = "カテゴリーを選択してください"
            done(false)
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.save(existing, kind, value, parsedDate, categoryId, note)
            }.onSuccess {
                done(true)
            }.onFailure {
                _message.value = "保存できませんでした: " + it.message
                done(false)
            }
        }
    }

    fun delete(transaction: LedgerTransaction) {
        viewModelScope.launch {
            runCatching { repository.delete(transaction.id) }
                .onSuccess { }
                .onFailure { _message.value = "削除できませんでした: " + it.message }
        }
    }

    fun addCategory(kind: String, name: String, done: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.addCategory(kind, name) }
                .onSuccess { done() }
                .onFailure { _message.value = it.message ?: "カテゴリーの追加に失敗しました" }
        }
    }

    fun renameCategory(id: String, name: String, done: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.renameCategory(id, name) }
                .onSuccess { done() }
                .onFailure { _message.value = it.message ?: "カテゴリー名の変更に失敗しました" }
        }
    }

    fun moveCategory(id: String, direction: Int) {
        viewModelScope.launch {
            runCatching { repository.moveCategory(id, direction) }
                .onFailure { _message.value = it.message ?: "並び替えに失敗しました" }
        }
    }

    fun removeCategory(id: String, done: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.removeCategory(id) }
                .onSuccess { done() }
                .onFailure { _message.value = it.message ?: "カテゴリーを削除できませんでした" }
        }
    }

    fun saveTemplate(
        existing: EntryTemplate?,
        name: String,
        kind: String,
        amount: String,
        categoryId: String,
        note: String,
        done: () -> Unit
    ) {
        val value = amount.trim().toLongOrNull()
        if (value == null) {
            _message.value = "金額は1円以上の整数で入力してください"
            return
        }
        viewModelScope.launch {
            runCatching { repository.saveTemplate(existing, name, kind, value, categoryId, note) }
                .onSuccess { done() }
                .onFailure { _message.value = it.message ?: "テンプレートを保存できませんでした" }
        }
    }

    fun removeTemplate(id: String, done: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.removeTemplate(id) }
                .onSuccess { done() }
                .onFailure { _message.value = it.message ?: "テンプレートを削除できませんでした" }
        }
    }

    fun clearMessage() { _message.value = null }

    class Factory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LedgerViewModel::class.java))
            return LedgerViewModel(repository) as T
        }
    }
}
