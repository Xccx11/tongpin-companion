package com.aster.companion.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/** Password-encrypted portable backup format. It deliberately has no dependency on device Keystore keys. */
class CompanionBackupCipher @Inject constructor() {
    fun encrypt(plainText: ByteArray, password: String): ByteArray {
        require(password.length >= MIN_PASSWORD_LENGTH) { "Backup password must be at least $MIN_PASSWORD_LENGTH characters" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val encrypted = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        }.doFinal(plainText)
        return MAGIC + salt + iv + encrypted
    }

    fun decrypt(payload: ByteArray, password: String): ByteArray {
        require(payload.size > MAGIC.size + SALT_BYTES + IV_BYTES) { "Invalid backup file" }
        require(payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Unsupported backup file" }
        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_BYTES
        val cipherStart = ivStart + IV_BYTES
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, deriveKey(password, payload.copyOfRange(saltStart, ivStart)), GCMParameterSpec(TAG_BITS, payload.copyOfRange(ivStart, cipherStart)))
        }.doFinal(payload.copyOfRange(cipherStart, payload.size))
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS))
            .encoded
        return SecretKeySpec(derived, "AES")
    }

    private companion object {
        val MAGIC = byteArrayOf(0x54, 0x50, 0x42, 0x4B, 0x31)
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val ITERATIONS = 210_000
        const val MIN_PASSWORD_LENGTH = 8
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
