package com.aster.companion.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aster.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class ImageGenerationService : Service() {
    @Inject lateinit var actionCoordinator: CompanionActionCoordinator
    @Inject lateinit var imageWorkGate: com.aster.companion.agent.ImageWorkGate
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeActionIds = ConcurrentHashMap.newKeySet<String>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val actionId = intent?.getStringExtra(EXTRA_ACTION_ID) ?: return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, notification())
        // 同一个动作已经在跑：直接忽略，不再起第二遍。
        if (!activeActionIds.add(actionId)) return START_NOT_STICKY
        scope.launch {
            // 挂上"正在出图"的闸：这期间主动引擎/感知事件不再去调对话模型
            //（网关得在出图/聊天两种模式间装卸模型，一块显卡装不下两边，被插队就会把图拖慢甚至打断）。
            imageWorkGate.begin()
            try {
                // **只跑一次**：以前这里套了 while + delay，失败/超时后会隔 2 秒再跑一遍，
                // 每轮都是一次真实的出图提交 —— 日志里"同一提示词每 40～80 秒又发一次"就是它。
                // 失败就是失败：等用户手动重试，绝不自己再提交。
                actionCoordinator.runImageGeneration(actionId)
            } catch (error: Throwable) {
                android.util.Log.e("ImageGenerationService", "image generation failed", error)
                runCatching { actionCoordinator.markImageGenerationFailed(actionId) }
            } finally {
                imageWorkGate.end()
                activeActionIds.remove(actionId)
                stopSelf(startId)
            }
        }
        // 不做 REDELIVER：进程被杀后不要自动把同一个出图动作再跑一遍。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): android.app.Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "同频照片生成", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("同频正在生成照片")
            .setContentText("完成后会直接发到对应对话")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tongpin_image_generation"
        private const val NOTIFICATION_ID = 702
        private const val EXTRA_ACTION_ID = "action_id"
        private const val RETRY_CHECK_INTERVAL_MS = 5_000L

        fun start(context: Context, actionId: String): Boolean = runCatching {
            context.startForegroundService(Intent(context, ImageGenerationService::class.java).putExtra(EXTRA_ACTION_ID, actionId))
        }.isSuccess
    }
}
