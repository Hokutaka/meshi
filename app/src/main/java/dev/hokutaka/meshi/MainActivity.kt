package dev.hokutaka.meshi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import dev.hokutaka.meshi.backup.BackupControls
import dev.hokutaka.meshi.category.CategoryManager
import dev.hokutaka.meshi.category.TemplateManager
import dev.hokutaka.meshi.ui.TransactionEditor
import dev.hokutaka.meshi.data.LedgerRepository
import dev.hokutaka.meshi.data.Category
import dev.hokutaka.meshi.data.LedgerTransaction
import dev.hokutaka.meshi.data.EntryTemplate
import dev.hokutaka.meshi.data.LedgerSummaries
import dev.hokutaka.meshi.data.TransactionHistoryFilters
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
    var templateForEntry by remember { mutableStateOf<EntryTemplate?>(null) }
    var templatePickerOpen by rememberSaveable { mutableStateOf(false) }
    var templateManagerOpen by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var categoryManagerOpen by rememberSaveable { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<LedgerTransaction?>(null) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var categoryBreakdownOpen by rememberSaveable { mutableStateOf(false) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historyKind by rememberSaveable { mutableStateOf("all") }
    val activeMonth = YearMonth.parse(month)
    // Aggregate in memory: no database migrations or changes to saved transactions.
    val report = remember(state.transactions, state.categories, activeMonth) {
        LedgerSummaries.forMonth(state.transactions, state.categories, activeMonth)
    }
    val records = report.records
    val spent = report.spentYen
    val earned = report.earnedYen
    val categoryNames = state.categories.associateBy { it.id }
    val visibleRecords = remember(records, state.categories, historyQuery, historyKind) {
        TransactionHistoryFilters.filter(records, state.categories, historyQuery, historyKind)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("¥") },
                    label = { Text("記録") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("⚙") },
                    label = { Text("設定") }
                )
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
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
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { categoryBreakdownOpen = !categoryBreakdownOpen },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (categoryBreakdownOpen) "カテゴリー別支出を閉じる ▲" else "カテゴリー別支出を見る ▼")
            }
            if (categoryBreakdownOpen) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (report.categoryExpenses.isEmpty()) {
                            Text("この月の支出はありません")
                        } else {
                            report.categoryExpenses.forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.label, modifier = Modifier.weight(1f))
                                    Text(yen(item.amountYen), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { editing = null; templateForEntry = null; editorOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("＋ 取引を追加") }
            if (state.templates.isNotEmpty()) {
                TextButton(
                    onClick = { templatePickerOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("テンプレートから入力") }
            }
            Spacer(Modifier.height(12.dp))
            Text("取引履歴", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = historyQuery,
                    onValueChange = { historyQuery = it },
                    label = { Text("メモ・カテゴリーを検索") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (historyQuery.isNotEmpty()) {
                    TextButton(onClick = { historyQuery = "" }) { Text("消去") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = historyKind == "all",
                    onClick = { historyKind = "all" },
                    label = { Text("すべて") }
                )
                FilterChip(
                    selected = historyKind == "expense",
                    onClick = { historyKind = "expense" },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = historyKind == "income",
                    onClick = { historyKind = "income" },
                    label = { Text("収入") }
                )
            }
            if (records.isEmpty()) {
                Text("この月の記録はありません", modifier = Modifier.padding(top = 24.dp))
            } else if (visibleRecords.isEmpty()) {
                Text("条件に一致する記録はありません", modifier = Modifier.padding(top = 24.dp))
                TextButton(onClick = { historyQuery = ""; historyKind = "all" }) {
                    Text("絞り込みを解除")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleRecords, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth().clickable {
                            editing = entry
                            templateForEntry = null
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
        } else {
            Column(
                Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { categoryManagerOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("カテゴリー管理") }
                OutlinedButton(
                    onClick = { templateManagerOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("テンプレート管理") }
                BackupControls(repository)
            }
        }
    }
    if (categoryManagerOpen) {
        CategoryManager(
            viewModel = viewModel,
            categories = state.categories,
            usedCategoryIds = (state.transactions.map { it.categoryId } +
                state.templates.map { it.categoryId }).toSet(),
            close = { categoryManagerOpen = false }
        )
    }
    if (templatePickerOpen) {
        AlertDialog(
            onDismissRequest = { templatePickerOpen = false },
            title = { Text("テンプレートから入力") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.templates.forEach { template ->
                        TextButton(
                            onClick = {
                                templateForEntry = template
                                editing = null
                                templatePickerOpen = false
                                editorOpen = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(template.name + "  " + yen(template.amountYen))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { templatePickerOpen = false }) { Text("閉じる") }
            }
        )
    }
    if (templateManagerOpen) {
        TemplateManager(
            viewModel = viewModel,
            templates = state.templates,
            categories = state.categories,
            close = { templateManagerOpen = false }
        )
    }
    if (editorOpen) {
        TransactionEditor(
            existing = editing,
            template = templateForEntry,
            categories = state.categories,
            save = { kind, amount, date, category, note, result ->
                viewModel.save(editing, kind, amount, date, category, note, result)
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

