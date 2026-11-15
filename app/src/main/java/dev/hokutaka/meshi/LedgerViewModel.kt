package dev.hokutaka.meshi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.hokutaka.meshi.data.Category
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
    val categories: List<Category> = emptyList()
)

class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {
    val state: StateFlow<LedgerUiState> = combine(
        repository.transactions,
        repository.categories
    ) { transactions, categories -> LedgerUiState(transactions, categories) }
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
        done: () -> Unit
    ) {
        val value = amount.trim().toLongOrNull()
        if (value == null || value <= 0) {
            _message.value = "金額は1円以上の整数で入力してください"
            return
        }
        val parsedDate = try {
            LocalDate.parse(date.trim())
        } catch (_: Exception) {
            _message.value = "日付はYYYY-MM-DDで入力してください"
            return
        }
        if (state.value.categories.none { it.id == categoryId && it.kind == kind }) {
            _message.value = "カテゴリーを選択してください"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.save(existing, kind, value, parsedDate, categoryId, note)
            }.onSuccess {
                _message.value = "保存しました"
                done()
            }.onFailure {
                _message.value = "保存できませんでした: " + it.message
            }
        }
    }

    fun delete(transaction: LedgerTransaction) {
        viewModelScope.launch {
            runCatching { repository.delete(transaction.id) }
                .onSuccess { _message.value = "削除しました" }
                .onFailure { _message.value = "削除できませんでした: " + it.message }
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
