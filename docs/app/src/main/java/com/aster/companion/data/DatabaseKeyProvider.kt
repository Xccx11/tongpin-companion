package com.aster.companion.data

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

class DatabaseKeyProvider(context: Context, private val preferenceName: String = DEFAULT_KEY_PREFERENCE) {
    private val preferences = context.getSharedPreferences("tongpin_secure", Context.MODE_PRIVATE)

    fun passphrase(): ByteArray {
        val stored = preferences.getString(preferenceName, null)
        return if (stored == null) {
            ByteArray(32).also(SecureRandom()::nextBytes).also { save(it) }
        } else decrypt(stored)
    }

    private fun save(raw: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = cipher.iv + cipher.doFinal(raw)
        preferences.edit().putString(preferenceName, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    private fun decrypt(encoded: String): ByteArray {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size))
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tongpin_companion_db"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val DEFAULT_KEY_PREFERENCE = "encrypted_companion_db_key"

        fun hasStoredPassphrase(context: Context): Boolean =
            context.getSharedPreferences("tongpin_secure", Context.MODE_PRIVATE)
                .contains(DEFAULT_KEY_PREFERENCE)
    }
}
