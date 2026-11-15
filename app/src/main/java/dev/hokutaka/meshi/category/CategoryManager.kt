package dev.hokutaka.meshi.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

@Composable
fun CategoryManager(
    viewModel: LedgerViewModel,
    categories: List<Category>,
    usedCategoryIds: Set<String>,
    close: () -> Unit
) {
    var kind by remember { mutableStateOf("expense") }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Category?>(null) }
    var renameTo by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val shown = categories.filter { it.kind == kind }
        .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.label }.thenBy { it.id })

    AlertDialog(
        onDismissRequest = close,
        title = { Text("カテゴリー") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == "expense",
                        onClick = { kind = "expense"; name = "" },
                        label = { Text("支出") }
                    )
                    FilterChip(
                        selected = kind == "income",
                        onClick = { kind = "income"; name = "" },
                        label = { Text("収入") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("新しいカテゴリー") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = name.trim().isNotEmpty(),
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            viewModel.addCategory(kind, name) { name = "" }
                        }
                    ) { Text("追加") }
                }

                shown.forEachIndexed { index, category ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(category.label)
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            TextButton(
                                enabled = index > 0,
                                onClick = { viewModel.moveCategory(category.id, -1) }
                            ) { Text("↑") }
                            TextButton(
                                enabled = index < shown.lastIndex,
                                onClick = { viewModel.moveCategory(category.id, 1) }
                            ) { Text("↓") }
                            TextButton(onClick = {
                                editing = category
                                renameTo = category.label
                            }) { Text("名前変更") }
                            TextButton(
                                enabled = category.id !in usedCategoryIds && shown.size > 1,
                                onClick = { deleting = category }
                            ) { Text("削除") }
                        }
                    }
                }
                Text("取引で使用中のカテゴリーは削除できません。")
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("閉じる") } }
    )

    editing?.let { target ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("カテゴリー名を変更") },
            text = {
                OutlinedTextField(
                    value = renameTo,
                    onValueChange = { renameTo = it },
                    label = { Text("カテゴリー名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = renameTo.trim().isNotEmpty(),
                    onClick = {
                        viewModel.renameCategory(target.id, renameTo) {
                            editing = null
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("キャンセル") }
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("カテゴリーを削除") },
            text = { Text("「" + target.label + "」を削除しますか？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.removeCategory(target.id) {
                        deleting = null
                    }
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("キャンセル") }
            }
        )
    }
}
