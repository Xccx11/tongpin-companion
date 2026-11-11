package com.aster.companion.media

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class CompanionPrivateImageStoreTest {
    @Test
    fun `generated image bytes are saved only in the app private files directory`() {
        val context = RuntimeEnvironment.getApplication()
        val bytes = byteArrayOf(1, 2, 3, 4)

        val savedUri = CompanionPrivateImageStore(context)
            .save(ByteArrayInputStream(bytes), "image/png")
            .getOrThrow()

        val privateDirectory = File(context.filesDir, "tongpin-images")
        assertTrue(savedUri.contains("tongpin-images"))
        assertTrue(privateDirectory.listFiles().orEmpty().any { it.readBytes().contentEquals(bytes) })
    }

    @Test
    fun `private image can be read back for a complete backup`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val bytes = byteArrayOf(9, 8, 7, 6)
        val store = CompanionPrivateImageStore(context)
        val savedUri = store.save(ByteArrayInputStream(bytes), "image/png").getOrThrow()

        val restored = store.read(savedUri).getOrThrow()

        assertTrue(restored.bytes.contentEquals(bytes))
        assertEquals("image/png", restored.contentType)
    }
}
