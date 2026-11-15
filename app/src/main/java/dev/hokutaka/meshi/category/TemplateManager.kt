package dev.hokutaka.meshi.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hokutaka.meshi.LedgerViewModel
import dev.hokutaka.meshi.data.Category
import dev.hokutaka.meshi.data.EntryTemplate

@Composable
fun TemplateManager(
    viewModel: LedgerViewModel,
    templates: List<EntryTemplate>,
    categories: List<Category>,
    close: () -> Unit
) {
    var editing by remember { mutableStateOf<EntryTemplate?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EntryTemplate?>(null) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("expense") }
    var amount by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    fun openForm(template: EntryTemplate?) {
        editing = template
        name = template?.name.orEmpty()
        kind = template?.kind ?: "expense"
        amount = template?.amountYen?.toString().orEmpty()
        categoryId = template?.categoryId.orEmpty()
        note = template?.note.orEmpty()
        formOpen = true
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("テンプレート") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("よく使う金額・カテゴリー・メモを保存できます。")
                Button(onClick = { openForm(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("＋ テンプレートを追加")
                }
                if (templates.isEmpty()) Text("テンプレートはまだありません")
                templates.forEach { template ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(template.name + "  ¥" + template.amountYen)
                        Row {
                            TextButton(onClick = { openForm(template) }) { Text("編集") }
                            TextButton(onClick = { deleting = template }) { Text("削除") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("閉じる") } }
    )

    if (formOpen) {
        val options = categories.filter { it.kind == kind }
            .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.label })
        val selectedCategory = options.firstOrNull { it.id == categoryId }?.id
            ?: options.firstOrNull()?.id
        AlertDialog(
            onDismissRequest = { formOpen = false },
            title = { Text(if (editing == null) "テンプレート追加" else "テンプレート編集") },
            text = {
                Column(
                    Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("テンプレート名") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        value = amount, onValueChange = { amount = it.filter { c -> c in '0'..'9' } },
                        label = { Text("金額（円）") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Text("カテゴリー")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category.id,
                                onClick = { categoryId = category.id },
                                label = { Text(category.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("メモ（任意）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = name.trim().isNotEmpty() && name.trim().length <= 40 &&
                        amount.toLongOrNull()?.let { it in 1..1_000_000_000_000L } == true &&
                        selectedCategory != null,
                    onClick = {
                        val category = selectedCategory ?: return@Button
                        viewModel.saveTemplate(editing, name, kind, amount, category, note) {
                            formOpen = false
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { formOpen = false }) { Text("キャンセル") }
            }
        )
    }

    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("テンプレートを削除") },
            text = { Text("「" + template.name + "」を削除しますか？取引履歴は消えません。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeTemplate(template.id) { deleting = null }
                }) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("キャンセル") }
            }
        )
    }
}
