package com.aster.companion.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aster.R
import com.aster.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanionNotificationPublisher @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * 主动消息通知的**节流**：一个感知事件会同时触发多个角色（每个角色一条消息 + 一条通知），
     * 用户看到的就是"一次弹好几个弹窗"。这里按时间窗合并：同一批只弹一条，
     * 消息本身照常进各自会话（只是不再逐条打扰）。
     */
    @Volatile
    private var lastProactiveNotifyAt = 0L

    fun publish(characterName: String, body: String, threadId: String) {
        val now = System.currentTimeMillis()
        if (now - lastProactiveNotifyAt < PROACTIVE_NOTIFY_WINDOW_MS) return
        lastProactiveNotifyAt = now
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "同频消息", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "角色主动发来的消息"
            }
        )
        val openThread = PendingIntent.getActivity(
            context,
            threadId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_THREAD
                putExtra(EXTRA_THREAD_ID, threadId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            threadId.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(characterName)
                .setContentText(body)
                .setContentIntent(openThread)
                .setAutoCancel(true)
                .build()
        )
    }

    fun publishMoment(characterName: String, body: String, postId: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(MOMENT_CHANNEL_ID, "动态消息", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "角色发布新动态或互动时的提醒"
            }
        )
        val openMoment = PendingIntent.getActivity(
            context,
            ("moment:" + postId).hashCode(),
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_MOMENT
                putExtra(EXTRA_MOMENT_ID, postId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            ("moment:" + postId).hashCode(),
            NotificationCompat.Builder(context, MOMENT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("$characterName 的动态")
                .setContentText(body)
                .setContentIntent(openMoment)
                .setAutoCancel(true)
                .build()
        )
    }

    fun cancel(threadId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(threadId.hashCode())
    }

    companion object {
        const val ACTION_OPEN_THREAD = "com.tongpin.companion.OPEN_THREAD"
        const val ACTION_OPEN_MOMENT = "com.tongpin.companion.OPEN_MOMENT"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_MOMENT_ID = "moment_id"
        private const val CHANNEL_ID = "tongpin_companion_messages"
        private const val MOMENT_CHANNEL_ID = "tongpin_companion_moments"

        /** 同一批主动消息只弹一条通知的时间窗（多角色并发反应时用）。 */
        private const val PROACTIVE_NOTIFY_WINDOW_MS = 8_000L
    }
}
