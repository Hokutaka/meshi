package dev.hokutaka.meshi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hokutaka.meshi.data.Category
import dev.hokutaka.meshi.data.LedgerTransaction
import java.time.LocalDate

/**
 * The common path is amount → category → save.
 * Date and memo are available on demand; today's date is the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditor(
    existing: LedgerTransaction?,
    categories: List<Category>,
    save: (kind: String, amount: String, date: String, categoryId: String, note: String,
           result: (Boolean) -> Unit) -> Unit,
    close: () -> Unit
) {
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: "expense") }
    var amount by remember(existing?.id) {
        mutableStateOf(existing?.amountYen?.toString().orEmpty())
    }
    var date by remember(existing?.id) {
        mutableStateOf(
            existing?.let { LocalDate.ofEpochDay(it.dateEpochDay).toString() }
                ?: LocalDate.now().toString()
        )
    }
    var categoryId by remember(existing?.id) {
        mutableStateOf(existing?.categoryId.orEmpty())
    }
    var note by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var detailsExpanded by remember(existing?.id) { mutableStateOf(existing != null) }
    var submitting by remember(existing?.id) { mutableStateOf(false) }

    val options = categories.filter { it.kind == kind }
        .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.label })
    val selectedCategory = options.firstOrNull { it.id == categoryId }?.id
        ?: options.firstOrNull()?.id
    val amountYen = amount.toLongOrNull()
    val validAmount = amountYen != null && amountYen > 0
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = { if (!submitting) close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (existing == null) "取引を追加" else "取引を編集",
                style = MaterialTheme.typography.titleMedium
            )

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
                onValueChange = { amount = it.filter { ch -> ch in '0'..'9' } },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text("金額") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    focus.clearFocus()
                })
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                options.forEach { category ->
                    FilterChip(
                        selected = category.id == selectedCategory,
                        onClick = { categoryId = category.id },
                        label = { Text(category.label) }
                    )
                }
            }

            TextButton(onClick = {
                detailsExpanded = !detailsExpanded
                if (detailsExpanded) {
                    keyboard?.hide()
                    focus.clearFocus()
                }
            }) {
                Text(if (detailsExpanded) "日付・メモを閉じる" else "日付：" + date + "　・ メモ")
            }

            if (detailsExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("日付（YYYY-MM-DD）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = { date = LocalDate.now().toString() }
                    ) { Text("今日") }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("メモ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    val selected = selectedCategory ?: return@Button
                    submitting = true
                    save(kind, amount, date, selected, note) { success ->
                        submitting = false
                        if (success) close()
                    }
                },
                enabled = validAmount && selectedCategory != null && !submitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "保存中…" else "保存") }
        }
    }
}
