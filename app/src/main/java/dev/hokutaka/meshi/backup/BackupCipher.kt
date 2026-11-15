package dev.hokutaka.meshi.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned authenticated backup envelope.
 * Layout: ASCII "MESHI001" (8 bytes) | salt (16) | IV (12) | AES-GCM ciphertext+tag.
 */
object BackupCipher {
    private val magic = "MESHI001".toByteArray(Charsets.US_ASCII)
    private const val iterations = 600_000
    private const val saltLength = 16
    private const val ivLength = 12
    private const val tagBits = 128
    const val maxFileBytes = 16 * 1024 * 1024
    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 12) { "パスフレーズは12文字以上で入力してください" }
        require(plaintext.size <= maxFileBytes - 64) { "バックアップが大きすぎます" }
        val salt = ByteArray(saltLength).also(random::nextBytes)
        val iv = ByteArray(ivLength).also(random::nextBytes)
        val header = magic + salt + iv
        val keyBytes = derive(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(tagBits, iv))
            cipher.updateAAD(header)
            header + cipher.doFinal(plaintext)
        } finally {
            keyBytes.fill(0)
        }
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        require(file.size in (magic.size + saltLength + ivLength + 16)..maxFileBytes) {
            "バックアップのサイズが不正です"
        }
        require(file.copyOfRange(0, magic.size).contentEquals(magic)) {
            "対応していないバックアップ形式です"
        }
        val headerSize = magic.size + saltLength + ivLength
        val salt = file.copyOfRange(magic.size, magic.size + saltLength)
        val iv = file.copyOfRange(magic.size + saltLength, headerSize)
        val keyBytes = derive(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(tagBits, iv))
            cipher.updateAAD(file, 0, headerSize)
            cipher.doFinal(file, headerSize, file.size - headerSize)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("パスフレーズが違うか、ファイルが破損しています")
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
