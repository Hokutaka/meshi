package dev.hokutaka.meshi.backup

import android.content.ContentResolver
import android.net.Uri
import dev.hokutaka.meshi.data.LedgerRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupService(
    private val resolver: ContentResolver,
    private val repository: LedgerRepository
) {
    suspend fun export(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val plain = SnapshotCodec.encode(repository.snapshot())
            val encrypted = BackupCipher.encrypt(plain, password)
            resolver.openOutputStream(uri, "w")?.use { out ->
                out.write(encrypted)
                out.flush()
            } ?: error("バックアップ先を開けませんでした")
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun restoreFromFile(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val encrypted = resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                val output = ByteArrayOutputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    require(output.size() <= BackupCipher.maxFileBytes) {
                        "バックアップファイルが大きすぎます"
                    }
                }
                output.toByteArray()
            } ?: error("バックアップファイルを開けませんでした")

            // Authentication, parsing and reference validation happen
            // before modifying the database. Replacement is atomic.
            val snapshot = SnapshotCodec.decode(BackupCipher.decrypt(encrypted, password))
            repository.restore(snapshot)
        } finally {
            password.fill('\u0000')
        }
    }
}
