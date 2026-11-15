package dev.hokutaka.meshi.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.hokutaka.meshi.data.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun BackupControls(repository: LedgerRepository) {
    val context = LocalContext.current
    val service = remember(context, repository) {
        BackupService(context.contentResolver, repository)
    }
    val scope = rememberCoroutineScope()
    var exportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val secret = password.toCharArray()
        password = ""
        confirmation = ""
        exportDialog = false
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    service.export(uri, secret)
                    status = "バックアップを書き出しました"
                } catch (_: Exception) {
                    status = "バックアップの保存に失敗しました。保存先と空き容量を確認してください"
                } finally {
                    secret.fill('\u0000')
                    busy = false
                }
            }
        } else {
            secret.fill('\u0000')
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importUri = uri
            password = ""
            confirmation = ""
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = !busy,
            onClick = { password = ""; confirmation = ""; exportDialog = true }
        ) { Text("バックアップ") }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = !busy,
            onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
        ) { Text("復元") }
    }
    if (busy) {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text("データを処理しています")
        }
    }

    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { exportDialog = false; password = ""; confirmation = "" },
            title = { Text("暗号化バックアップ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("12文字以上のパスフレーズを設定してください。復元時に必要です。")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("パスフレーズ") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("パスフレーズを再入力") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = password.length >= 12 && password == confirmation && !busy,
                    onClick = {
                        exportLauncher.launch("meshi-" + LocalDate.now() + ".meshi")
                    }
                ) { Text("保存先を選択") }
            },
            dismissButton = {
                TextButton(onClick = {
                    exportDialog = false
                    password = ""
                    confirmation = ""
                }) { Text("キャンセル") }
            }
        )
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null; password = "" },
            title = { Text("バックアップを復元") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("現在の取引・カテゴリー・テンプレートを、バックアップの内容ですべて置き換えます。")
                    Text("復元前に現在のデータをバックアップすることをおすすめします。")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("バックアップのパスフレーズ") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(enabled = password.isNotEmpty() && !busy, onClick = {
                    val secret = password.toCharArray()
                    password = ""
                    importUri = null
                    busy = true
                    scope.launch {
                        try {
                            service.restoreFromFile(uri, secret)
                            status = "復元しました"
                        } catch (_: Exception) {
                            status = "復元できませんでした。ファイルとパスフレーズを確認してください。元の記録は保持されています"
                        } finally {
                            secret.fill('\u0000')
                            busy = false
                        }
                    }
                }) { Text("置き換えて復元") }
            },
            dismissButton = {
                TextButton(onClick = { importUri = null; password = "" }) {
                    Text("キャンセル")
                }
            }
        )
    }

    status?.let { result ->
        AlertDialog(
            onDismissRequest = { status = null },
            title = { Text("バックアップ") },
            text = { Text(result) },
            confirmButton = { TextButton(onClick = { status = null }) { Text("OK") } }
        )
    }
}
