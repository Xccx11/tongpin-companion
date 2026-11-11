package com.aster

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把未捕获异常的栈写到手机上的文件里，方便直接取出来定位问题。
 *
 * 两条落盘路径：应用外部目录 `Android/data/<包名>/files/`，以及系统「下载」目录
 * （部分机型对 Android/data 做了限制，所以两个都写）。
 *
 * 只在进程崩溃时运行，不联网、不常驻。
 */
internal object CrashLogRecorder {

    private const val TAG = "TongpinCrash"

    /** 装到最外层：attachBaseContext 就跑，保证比任何业务代码都早。 */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            // 原始处理器继续走，系统照样记录/提示崩溃。
            previous?.uncaughtException(thread, error)
        }
    }

    /** 启动路标：万一连崩溃处理器都没跑到（例如 native 崩溃），也能看出走到了哪一步。 */
    fun mark(context: Context, step: String) {
        runCatching {
            val file = File(externalDir(context), BOOT_TRACE_FILE)
            file.appendText("${timestamp()}  $step\n")
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val text = buildString {
            appendLine("time=${timestamp()}")
            appendLine("app=com.tongpin.companion ${versionName(context)}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("thread=${thread.name}")
            appendLine()
            appendLine("--- stack ---")
            appendLine(Log.getStackTraceString(error))
            error.cause?.let { cause ->
                appendLine()
                appendLine("--- cause ---")
                appendLine(Log.getStackTraceString(cause))
            }
        }
        val name = "tongpin-crash-${System.currentTimeMillis()}.txt"
        runCatching {
            File(externalDir(context), name).writeText(text)
            Log.e(TAG, "crash log written to ${externalDir(context)}/$name")
        }
        runCatching { writeToDownloads(context, name, text) }
    }

    /** 下载目录，普通文件管理器就能看到并分享出来。 */
    private fun writeToDownloads(context: Context, name: String, text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(text.toByteArray())
        }
    }

    private fun externalDir(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private const val BOOT_TRACE_FILE = "boot-trace.txt"
}
