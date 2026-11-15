package dev.hokutaka.meshi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import dev.hokutaka.meshi.backup.BackupControls
import dev.hokutaka.meshi.data.LedgerRepository
import dev.hokutaka.meshi.data.Category
import dev.hokutaka.meshi.data.LedgerTransaction
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as MeshiApplication).repository
        val vm = ViewModelProvider(this, LedgerViewModel.Factory(repository))[LedgerViewModel::class.java]
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { LedgerScreen(vm, repository) }
            }
        }
    }
}

private fun yen(amount: Long): String = "¥" + NumberFormat.getIntegerInstance(Locale.JAPAN).format(amount)

@Composable
private fun LedgerScreen(viewModel: LedgerViewModel, repository: LedgerRepository) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    var editing by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<LedgerTransaction?>(null) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val activeMonth = YearMonth.parse(month)
    val records = state.transactions.filter {
        YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == activeMonth
    }
    val spent = records.filter { it.kind == "expense" }.sumOf { it.amountYen }
    val earned = records.filter { it.kind == "income" }.sumOf { it.amountYen }
    val categoryNames = state.categories.associateBy { it.id }

    Scaffold { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { month = activeMonth.minusMonths(1).toString() }) { Text("‹ 前月") }
                Text(
                    activeMonth.year.toString() + "年" + activeMonth.monthValue + "月",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { month = activeMonth.plusMonths(1).toString() }) { Text("翌月 ›") }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("支出  " + yen(spent), style = MaterialTheme.typography.headlineSmall)
                    Text("収入  " + yen(earned))
                    Text("差引  " + yen(earned - spent))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { editing = null; editorOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("＋ 取引を追加") }
            BackupControls(repository)
            Spacer(Modifier.height(12.dp))
            Text("取引履歴", style = MaterialTheme.typography.titleMedium)
            if (records.isEmpty()) {
                Text("この月の記録はありません", modifier = Modifier.padding(top = 24.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(records, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth().clickable {
                            editing = entry
                            editorOpen = true
                        }) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(categoryNames[entry.categoryId]?.label ?: "未分類",
                                        fontWeight = FontWeight.SemiBold)
                                    Text(LocalDate.ofEpochDay(entry.dateEpochDay).toString() +
                                        "  " + entry.note, style = MaterialTheme.typography.bodySmall)
                                }
                                Column {
                                    Text((if (entry.kind == "expense") "−" else "＋") +
                                        yen(entry.amountYen), fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { deleteCandidate = entry }) { Text("削除") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editorOpen) {
        TransactionEditor(
            existing = editing,
            categories = state.categories,
            save = { kind, amount, date, category, note ->
                viewModel.save(editing, kind, amount, date, category, note) {
                    editorOpen = false
                }
            },
            close = { editorOpen = false }
        )
    }
    deleteCandidate?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("取引を削除しますか？") },
            text = { Text(yen(entry.amountYen) + "の記録を削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entry)
                    deleteCandidate = null
                }) { Text("削除する") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("キャンセル") } }
        )
    }
    message?.let { value ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("meshi") },
            text = { Text(value) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun TransactionEditor(
    existing: LedgerTransaction?,
    categories: List<Category>,
    save: (String, String, String, String, String) -> Unit,
    close: () -> Unit
) {
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: "expense") }
    var amount by remember(existing?.id) {
        mutableStateOf(existing?.amountYen?.toString().orEmpty())
    }
    var date by remember(existing?.id) {
        mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.dateEpochDay).toString() }
            ?: LocalDate.now().toString())
    }
    var categoryId by remember(existing?.id) { mutableStateOf(existing?.categoryId.orEmpty()) }
    var note by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    val options = categories.filter { it.kind == kind }
    val selectedCategory = options.find { it.id == categoryId }?.id ?: options.firstOrNull()?.id
    val validAmount = amount.toLongOrNull()?.let { it > 0 } == true
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (existing == null) "取引を追加" else "取引を編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == "expense",
                        onClick = { kind = "expense"; categoryId = "" },
                        label = { Text("支出") }
                    )
                    FilterChip(
                        selected = kind == "income",
                        onClick = { kind = "income"; categoryId = "" },
                        label = { Text("収入") }
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit) },
                    label = { Text("金額（円）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("日付（YYYY-MM-DD）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("カテゴリー")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { category ->
                        FilterChip(
                            selected = category.id == selectedCategory,
                            onClick = { categoryId = category.id },
                            label = { Text(category.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("メモ（任意）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = validAmount && selectedCategory != null,
                onClick = {
                    selectedCategory?.let { save(kind, amount, date, it, note) }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = close) { Text("キャンセル") } }
    )
}
