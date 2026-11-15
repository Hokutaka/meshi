package dev.hokutaka.meshi.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates a random SQLCipher passphrase and stores it encrypted by Android Keystore.
 * This protects data at rest; biometric app unlocking is a separate feature.
 */
class DatabaseKeyManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("meshi_db_key", Context.MODE_PRIVATE)
    private val alias = "meshi_db_wrap_v1"

    fun loadOrCreate(): ByteArray {
        val storedNonce = prefs.getString("nonce", null)
        val storedCiphertext = prefs.getString("ciphertext", null)
        if ((storedNonce == null) != (storedCiphertext == null)) {
            error("Incomplete database key metadata; refusing to overwrite.")
        }

        val key = keystoreKey()
        if (storedNonce != null && storedCiphertext != null) {
            val decryptor = Cipher.getInstance("AES/GCM/NoPadding")
            decryptor.init(Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(128, Base64.decode(storedNonce, Base64.NO_WRAP)))
            return decryptor.doFinal(Base64.decode(storedCiphertext, Base64.NO_WRAP))
        }

        // An existing encrypted DB without its wrapped key is not recoverable here.
        if (context.getDatabasePath("meshi.db").exists()) {
            error("Database key unavailable; refusing to replace existing data.")
        }

        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        val encryptor = Cipher.getInstance("AES/GCM/NoPadding")
        encryptor.init(Cipher.ENCRYPT_MODE, key)
        val wrapped = encryptor.doFinal(passphrase)
        check(prefs.edit()
            .putString("nonce", Base64.encodeToString(encryptor.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .commit()) { "Unable to store encrypted DB key" }
        return passphrase
    }

    private fun keystoreKey(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keystore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
