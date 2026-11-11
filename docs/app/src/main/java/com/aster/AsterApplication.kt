package com.aster

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AsterApplication : Application() {

    // The provider is declared in the app manifest. Keep a direct type reference so D8 retains it.
    @Suppress("unused")
    private val shizukuProviderRuntimeAnchor = rikka.shizuku.ShizukuProvider::class

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // 最早期装上崩溃捕获，保证之后任何一步崩了都能留下栈。
        CrashLogRecorder.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogRecorder.mark(this, "Application.onCreate: begin")
        System.loadLibrary("sqlcipher")
        CrashLogRecorder.mark(this, "Application.onCreate: ready")
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "aster_service"
        const val NOTIFICATION_ID = 1
    }
}
