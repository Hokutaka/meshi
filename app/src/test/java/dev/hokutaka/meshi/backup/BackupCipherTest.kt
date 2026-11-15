package dev.hokutaka.meshi.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCipherTest {
    private val sample = """{"format":"meshi-ledger","schemaVersion":1}""".toByteArray()

    @Test fun roundTrip() {
        val encrypted = BackupCipher.encrypt(sample, "long-test-password-123".toCharArray())
        assertArrayEquals(sample, BackupCipher.decrypt(encrypted, "long-test-password-123".toCharArray()))
        assertFalse(sample.contentEquals(encrypted))
    }

    @Test fun incorrectPasswordIsRejected() {
        val encrypted = BackupCipher.encrypt(sample, "long-test-password-123".toCharArray())
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(encrypted, "wrong-test-password-123".toCharArray())
        }
    }

    @Test fun tamperingIsRejected() {
        val encrypted = BackupCipher.encrypt(sample, "long-test-password-123".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(encrypted, "long-test-password-123".toCharArray())
        }
    }

    @Test fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.encrypt(sample, "short".toCharArray())
        }
    }

    @Test fun headerTamperingIsRejected() {
        val encrypted = BackupCipher.encrypt(sample, "long-test-password-123".toCharArray())
        encrypted[0] = 'X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(encrypted, "long-test-password-123".toCharArray())
        }
    }
}
