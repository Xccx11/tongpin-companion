package com.aster.companion.media

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class PrivateImageData(val bytes: ByteArray, val contentType: String)

/** Stores generated images privately so short-lived provider URLs never become chat history. */
@Singleton
class CompanionPrivateImageStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun persist(source: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (source.startsWith("data:image/", ignoreCase = true)) {
                val header = source.substringBefore(',')
                val encoded = source.substringAfter("base64,", "")
                require(encoded.isNotBlank()) { "Image data is empty" }
                save(Base64.decode(encoded, Base64.DEFAULT).inputStream(), header.substringAfter(':').substringBefore(';')).getOrThrow()
            } else if (source.startsWith("content:", ignoreCase = true) || source.startsWith("file:", ignoreCase = true)) {
                val uri = Uri.parse(source)
                val input = when (uri.scheme) {
                    "content" -> requireNotNull(context.contentResolver.openInputStream(uri)) { "Image data is unavailable" }
                    "file" -> fileForUri(uri).inputStream()
                    else -> error("Unsupported local image URI")
                }
                input.use { save(it, context.contentResolver.getType(uri)).getOrThrow() }
            } else {
                persistRemote(source)
            }
        }
    }

    private suspend fun persistRemote(source: String): String {
        var lastError: Throwable? = null
        for (attempt in 1..RemoteImageDownloadPolicy.MAX_ATTEMPTS) {
            try {
                val connection = URL(source).openConnection().apply {
                    connectTimeout = NETWORK_CONNECT_TIMEOUT_MS
                    readTimeout = NETWORK_READ_TIMEOUT_MS
                }
                return connection.getInputStream().use { stream ->
                    save(stream, connection.contentType).getOrThrow()
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                if (!RemoteImageDownloadPolicy.shouldRetry(attempt)) break
                delay(RemoteImageDownloadPolicy.delayMillis(attempt))
            }
        }
        throw lastError ?: IllegalStateException("Unable to download image")
    }

    suspend fun read(source: String): Result<PrivateImageData> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(source)
            val (bytes, contentType) = when {
                source.startsWith("data:image/", ignoreCase = true) -> {
                    val header = source.substringBefore(',')
                    val encoded = source.substringAfter("base64,", "")
                    require(encoded.isNotBlank()) { "Image data is empty" }
                    Base64.decode(encoded, Base64.DEFAULT) to header.substringAfter(':').substringBefore(';')
                }
                uri.scheme == "content" -> {
                    val data = requireNotNull(context.contentResolver.openInputStream(uri)) { "Image data is unavailable" }.use { it.readBytes() }
                    data to (context.contentResolver.getType(uri) ?: "image/jpeg")
                }
                uri.scheme == "file" -> {
                    val file = fileForUri(uri)
                    val data = file.readBytes()
                    data to (context.contentResolver.getType(uri) ?: extensionContentType(file.path))
                }
                else -> error("Only local image resources can be included in a backup")
            }
            require(bytes.size <= MAX_BACKUP_IMAGE_BYTES) { "Image is too large for backup" }
            PrivateImageData(bytes, contentType)
        }
    }

    fun save(input: InputStream, contentType: String?): Result<String> = runCatching {
        val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        require(directory.isDirectory) { "Unable to create private image directory" }
        val target = File(directory, "${UUID.randomUUID()}${extensionFor(contentType)}")
        val staging = File(directory, ".${target.name}.part")
        try {
            input.use { source ->
                staging.outputStream().use { destination -> source.copyTo(destination) }
            }
            require(staging.renameTo(target)) { "Unable to finalize private image" }
            Uri.fromFile(target).toString()
        } finally {
            staging.delete()
        }
    }

    private fun extensionFor(contentType: String?): String = when {
        contentType?.contains("png", ignoreCase = true) == true -> ".png"
        contentType?.contains("webp", ignoreCase = true) == true -> ".webp"
        contentType?.contains("gif", ignoreCase = true) == true -> ".gif"
        else -> ".jpg"
    }

    private fun extensionContentType(path: String): String = when {
        path.endsWith(".png", true) -> "image/png"
        path.endsWith(".webp", true) -> "image/webp"
        path.endsWith(".gif", true) -> "image/gif"
        else -> "image/jpeg"
    }

    private fun fileForUri(uri: Uri): File {
        val encodedPath = uri.toString().removePrefix("file://")
        val decodedPath = Uri.decode(encodedPath)
        if (decodedPath.length >= 2 && decodedPath[1] == ':') return File(decodedPath)
        return runCatching { File(URI(uri.toString())) }
            .getOrElse { File(requireNotNull(uri.path)) }
    }

    private companion object {
        const val DIRECTORY_NAME = "tongpin-images"
        const val NETWORK_CONNECT_TIMEOUT_MS = 8_000
        const val NETWORK_READ_TIMEOUT_MS = 20_000
        const val MAX_BACKUP_IMAGE_BYTES = 20 * 1024 * 1024
    }
}
