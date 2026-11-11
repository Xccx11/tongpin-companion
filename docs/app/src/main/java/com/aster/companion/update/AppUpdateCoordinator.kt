package com.aster.companion.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request

/** 更新流程的可见状态（设置页按它显示按钮/进度/结果）。 */
sealed interface UpdateState {
    /** 还没查过，显示「检查更新」。 */
    data object Idle : UpdateState
    data object Checking : UpdateState
    /** 服务器上有更新（versionCode 比本机大）；promptCount 是这次一起同步到的远程提示词条数。 */
    data class Available(
        val versionName: String,
        val versionCode: Int,
        val sizeBytes: Long,
        val promptCount: Int = 0
    ) : UpdateState
    data class UpToDate(val promptCount: Int = 0) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * App 自更新：从**用户自己填的服务地址**（桥接/网关）拉版本、下安装包、调起系统安装。
 *
 * 为什么走网关而不是第三方平台：这个 App 只在自建网关上分发，
 * 而且手机本身经常连不上 adb，经 ngrok 的这条通道反而最稳。
 *
 * 网关侧对应：
 * - `GET /app/version.json`：{versionCode, versionName, sizeBytes, apkUrl}
 * - `GET /app/latest.apk`：安装包本体
 */
@Singleton
class AppUpdateCoordinator @Inject constructor(
    private val http: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    data class Release(
        val versionCode: Int,
        val versionName: String,
        val sizeBytes: Long,
        val apkUrl: String
    )

    fun currentVersionCode(): Int = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }.getOrDefault(0)

    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** 拉一次版本信息。地址没填、连不上、返回不是 JSON 都走 failure。 */
    suspend fun check(baseUrl: String): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val base = origin(baseUrl) ?: error("还没填服务地址")
            val request = Request.Builder()
                .url("$base/app/version.json")
                .header(NGROK_HEADER, "1")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { "网关返回 ${response.code}" }
                val obj = Json.parseToJsonElement(body).jsonObject
                Release(
                    versionCode = obj["versionCode"]?.jsonPrimitive?.int ?: error("版本信息里没有 versionCode"),
                    versionName = obj["versionName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    sizeBytes = obj["sizeBytes"]?.jsonPrimitive?.long ?: 0L,
                    apkUrl = obj["apkUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: "/app/latest.apk"
                )
            }
        }
    }

    /** 下载安装包到缓存目录，边下边报进度（0-100）。 */
    suspend fun download(
        baseUrl: String,
        release: Release,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val base = origin(baseUrl) ?: error("还没填服务地址")
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "tongpin-${release.versionName}.apk")
            if (target.exists()) target.delete()
            val request = Request.Builder()
                .url(base + release.apkUrl)
                .header(NGROK_HEADER, "1")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "下载失败：网关返回 ${response.code}" }
                val body = response.body ?: error("下载失败：响应为空")
                val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPercent = -1
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            read += count
                            if (total > 0) {
                                val percent = ((read * 100) / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
            }
            check(target.length() > 0) { "下载到的安装包是空的" }
            target
        }
    }

    /**
     * 调起系统安装界面。返回 false 表示还没拿到「安装未知应用」权限
     * （调用方应引导用户去授权页）。
     */
    fun install(apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return false
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /** 打开系统里「安装未知应用」的授权页。 */
    fun openInstallPermissionSettings() {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** 从用户填的地址里取出 scheme://host[:port]，忽略 /v1 这类路径。 */
    private fun origin(raw: String): String? {
        val text = raw.trim().trimEnd('/')
        if (text.isEmpty()) return null
        val withScheme = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host ?: return@runCatching text
            val port = if (uri.port > 0) ":${uri.port}" else ""
            "${uri.scheme ?: "https"}://$host$port"
        }.getOrNull()
    }

    private companion object {
        /** ngrok 免费版对浏览器请求会插提示页，带这个头直接跳过。 */
        const val NGROK_HEADER = "ngrok-skip-browser-warning"
    }
}
