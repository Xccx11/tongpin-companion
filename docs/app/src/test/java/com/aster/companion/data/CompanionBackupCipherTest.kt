package com.aster.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CompanionBackupCipherTest {
    @Test
    fun `encrypts and restores a backup with its password`() {
        val cipher = CompanionBackupCipher()
        val encrypted = cipher.encrypt("private companion data".toByteArray(), "correct horse battery staple")

        assertEquals("private companion data", cipher.decrypt(encrypted, "correct horse battery staple").toString(Charsets.UTF_8))
    }

    @Test
    fun `rejects an incorrect backup password`() {
        val cipher = CompanionBackupCipher()
        val encrypted = cipher.encrypt("private companion data".toByteArray(), "correct horse battery staple")

        assertThrows(Exception::class.java) { cipher.decrypt(encrypted, "wrong password") }
    }
}
