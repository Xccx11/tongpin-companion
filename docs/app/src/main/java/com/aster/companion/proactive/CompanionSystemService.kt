package com.aster.companion.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aster.R
import com.aster.companion.agent.AgentTurnCoordinator
import com.aster.companion.agent.GroupProactiveTurnCoordinator
import com.aster.companion.agent.GroupToPrivateProactiveCoordinator
import com.aster.companion.agent.MomentsAgentCoordinator
import com.aster.companion.data.CompanionDao
import com.aster.companion.domain.EventState
import com.aster.companion.system.ShizukuFixedActionRouter
import com.aster.receiver.SmsBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Foreground owner for proactive sensing and the three-minute idle check. */
@AndroidEntryPoint
class CompanionSystemService : Service() {
    @Inject lateinit var dao: CompanionDao
    @Inject lateinit var events: ProactiveEventStore
    @Inject lateinit var coordinator: AgentTurnCoordinator
    @Inject lateinit var groupCoordinator: GroupProactiveTurnCoordinator
    @Inject lateinit var groupToPrivateCoordinator: GroupToPrivateProactiveCoordinator
    @Inject lateinit var momentsAgent: MomentsAgentCoordinator
    @Inject lateinit var lifeTicker: com.aster.companion.agent.life.LifeTicker
    @Inject lateinit var modeState: SystemModeState
    @Inject lateinit var weatherEvents: WeatherEventSource
    @Inject lateinit var placeEvents: PlaceEventSource
    @Inject lateinit var mediaEvents: MediaPlaybackEventSource
    @Inject lateinit var batteryEvents: BatteryEventSource
    @Inject lateinit var calendarEvents: CalendarEventSource
    @Inject lateinit var networkEvents: NetworkEventSource
    @Inject lateinit var sensingDiagnostics: SensingDiagnosticStore
    @Inject lateinit var shizukuRouter: ShizukuFixedActionRouter
    @Inject lateinit var backgroundIdle: com.aster.companion.lite.BackgroundIdleStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var smsReceiver: SmsBroadcastReceiver? = null
    private var deviceStateReceiver: BroadcastReceiver? = null
    private var receiversRegistered = false
    private var lastForegroundPackage: String? = null
    private var lastForegroundObservedAt = 0L
    private var foregroundStartedAt = 0L
    private var lastForegroundDiagnostic: String? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            modeState.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        modeState.setEnabled(true)
        startSystemModeForeground()
        Log.i(TAG, "System mode service started")
        scope.launch { shizukuRouter.connect() }
        if (loop == null) loop = scope.launch {
            Log.i(TAG, "Proactive loop coroutine started")
            runLoop()
        }
        registerSensingReceivers()
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        loop = null
        smsReceiver?.let { runCatching { unregisterReceiver(it) } }
        smsReceiver = null
        deviceStateReceiver?.let { runCatching { unregisterReceiver(it) } }
        deviceStateReceiver = null
        receiversRegistered = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSystemModeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                SystemModeForegroundTypePolicy.resolve(
                    hasCoarseLocationPermission = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                    hasFineLocationPermission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                )
            )
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
    }

    private suspend fun runLoop() {
        var firstTick = true
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            if (firstTick) {
                firstTick = false
                Log.i(TAG, "Proactive loop first tick")
            }
            // Capture the live device state before any model-bound pending work.
            recordForegroundAppChange(now)
            // 每次最多处理这么多：空闲让路期间事件会堆着，闲下来那一拍一次全放出去
            // 就是一串主动陪伴决策（每个都是一次大 LLM 调用）。30 秒一拍 × 3 个足够排空。
            dao.pendingEvents()
                .filter { it.state == EventState.NEW || (it.deferUntil ?: Long.MAX_VALUE) <= now }
                .take(MAX_EVENTS_PER_TICK)
                .forEach { processInBackground(it.id) }

            placeEvents.poll(now)?.let { event ->
                val eventId = events.recordSystemEvent(event.source, event.summary, event.dedupeKey, now)
                sensingDiagnostics.record("place", placeEvents.diagnostic(), now)
                processInBackground(eventId)
            } ?: sensingDiagnostics.record("place", placeEvents.diagnostic(), now)

            mediaEvents.poll(now)?.let { event ->
                val eventId = events.recordSystemEvent(event.source, event.summary, event.dedupeKey, now)
                sensingDiagnostics.record("media", mediaEvents.diagnostic(), now)
                Log.i(TAG, "Media sensing state=${mediaEvents.diagnostic()}")
                processInBackground(eventId)
            } ?: run {
                sensingDiagnostics.record("media", mediaEvents.diagnostic(), now)
                Log.i(TAG, "Media sensing state=${mediaEvents.diagnostic()}")
            }

            weatherEvents.poll(now)?.let { event ->
                val eventId = events.recordSystemEvent(event.source, event.summary, event.dedupeKey, now)
                Log.i(TAG, "Weather event recorded")
                sensingDiagnostics.record("weather", "event_recorded", now)
                processInBackground(eventId)
            }
            batteryEvents.poll(now)?.let { event ->
                val eventId = events.recordSystemEvent(event.source, event.summary, event.dedupeKey, now)
                Log.i(TAG, "Battery state event recorded")
                sensingDiagnostics.record("battery", "event_recorded", now)
                processInBackground(eventId)
            }
            networkEvents.poll(now)?.let { event ->
                val eventId = events.recordSystemEvent(event.source, event.summary, event.dedupeKey, now)
                Log.i(TAG, "Network state event recorded")
                sensingDiagnostics.record("network", "event_recorded", now)
                processInBackground(eventId)
            }
            calendarEvents.poll(now)?.let { result ->
                sensingDiagnostics.record("calendar", result.status, now)
                result.events.forEach { event ->
                    val eventId = events.recordSystemEvent(event.source, event.summary, event.dedupeKey, now)
                    processInBackground(eventId)
                }
            }
            dao.redactHistoricalEvents(now - LIFE_CONTEXT_WINDOW_MS)

            // 「他说了晚安」按下的主动联系，越过一夜就自动放开 —— 不然他第二天只说一句「早」，
            // 她会因为没听到「我醒了」这类词而一直不主动找他（详见 ProactivePauseExpiry）。
            runCatching { modeState.resumeExpiredPauses(now) }

            // 空闲让路：生活推进 / 朋友圈决策都是后台 LLM 调用，用户还在聊天时先别跑，
            // 免得跟对话抢同一张卡。设置页「后台推演」可调。
            // 注意：这里**不再有"延后补跑"的待办**了 —— 世界推演已下线，剩下这两样本来每拍都会重算，
            // 跳过一拍下一拍自然会来（[com.aster.companion.lite.BackgroundDeferredWork] 因此删除）。
            val idleMs = backgroundIdle.idleMs.value
            val lastUserMessageAt = runCatching { dao.latestUserMessageAt() }.getOrNull()
            val deferBackground = com.aster.companion.lite.BackgroundIdlePolicy
                .shouldDefer(lastUserMessageAt, now, idleMs)
            if (deferBackground) {
                Log.d(TAG, "后台推演让路：用户仍在对话（空闲阈值 ${idleMs / 1000}s）")
            } else {
                // 世界引擎已下线（单人模式只保留「时间胶囊」）：不再跑世界调度 tick，也不再补跑世界推演。
                // 它曾是胶囊的第二个写者，「她一边说睡着一边活跃」就是这么来的。
                // **下面这行 lifeTicker 必须保留**：现在它是胶囊唯一的自动写者。
                //
                // 她的生活由「一天的时间线」推进：时间没走到下一段，她就不会换事做。
                //
                // 原先这里每 90 秒重问一次「你现在在干嘛」，那正是「活动 90 秒一跳、一天没有形状、
                // 凌晨三点在阳台浇花」的根源。现在换成一整天排好的时间线（TimelineSkeleton 定时间边界、
                // 模型填具体内容），只有走到下一段才会换事做。
                runCatching { lifeTicker.tick(now) }
                    .onFailure { error -> Log.w(TAG, "Life tick failed", error) }

                scope.launch {
                    runCatching { momentsAgent.pollProactivePosts(now) }
                    runCatching { momentsAgent.ensureCharacterPostReactions(now) }
                        .onFailure { error -> Log.w(TAG, "Proactive feed poll failed", error) }
                }

                // 关系/在场脉冲与群聊主动回合同样会发起 LLM 调用（属于主动陪伴），一起让路。
                scheduleRelationshipPulses(now)
                scheduleGroupPresencePulses(now)
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    /** Each relationship has a separate pulse; a busy conversation cannot starve another one. */
    private suspend fun scheduleRelationshipPulses(now: Long) {
        // 被按下主动（他说了「我去睡了」）或她自己那条「主动联系」关掉的角色：**连脉冲事件都不要产生**。
        // 这些事件的 dedupeKey 里有时间片（`presence:<id>:<now/interval>`），暂停期间会一条条攒起来；
        // 等用户重新打开主动，那些旧事件会一起被处理 —— 那就是"一开就狂发好几条"。
        // 交付端（AgentTurnCoordinator）本来就会拦，但那是"先攒后扔"，不如源头不产生。
        val proactiveEnabledIds = com.aster.companion.proactive.ProactiveCharacterSelector
            .resolveAll(
                preferredIds = modeState.proactiveCharacterIds,
                availableIds = dao.characterIds(),
                selectionConfigured = modeState.proactiveSelectionConfigured
            )
            .toSet()
        dao.allThreads()
            .asSequence()
            .filter { it.type == com.aster.companion.domain.ConversationType.PRIVATE }
            .forEach { thread ->
                val lastMessage = dao.recentMessages(thread.id, 1).firstOrNull() ?: return@forEach
                val characterId = lastMessage.characterId ?: return@forEach
                if (characterId !in proactiveEnabledIds || modeState.isProactivePaused(characterId)) return@forEach
                if (lastMessage.fromUser || now - lastMessage.createdAt < IDLE_INTERVAL_MS) return@forEach
                val eventId = events.recordSystemEvent(
                    source = UnansweredEventTarget.source(thread.id, characterId),
                    summary = "Relationship pulse: read the complete current context and freely decide whether to reach out. The user already received your prior reply; never answer their older message again.",
                    dedupeKey = "presence:${lastMessage.id}",
                    occurredAt = now
                )
                processInBackground(eventId)
                if (now - lastMessage.createdAt >= PRESENCE_PULSE_INTERVAL_MS) {
                    val presenceEventId = events.recordSystemEvent(
                        source = UnansweredEventTarget.presenceSource(thread.id, characterId),
                        summary = "Relationship presence pulse: the user has not sent another message after the character's prior reply. Read the complete current context and do not repeat any earlier answer.",
                        dedupeKey = "presence:${lastMessage.id}:${now / PRESENCE_PULSE_INTERVAL_MS}",
                        occurredAt = now
                    )
                    processInBackground(presenceEventId)
                }
            }
    }

    /** Group turns stay inside the chat: no system notification is posted for them. */
    private suspend fun scheduleGroupPresencePulses(now: Long) {
        dao.allThreads()
            .asSequence()
            .filter { it.type == com.aster.companion.domain.ConversationType.GROUP }
            .forEach { thread ->
                val last = dao.recentMessages(thread.id, 1).firstOrNull() ?: return@forEach
                scope.launch {
                    if (now - last.createdAt >= IDLE_INTERVAL_MS) {
                        runCatching { groupCoordinator.process(thread.id, now) }
                            .onFailure { error -> Log.w(TAG, "Family group proactive turn failed", error) }
                    }
                    // The private follow-up must see any group message just persisted above.
                    runCatching {
                        groupToPrivateCoordinator.process(thread.id, now)
                    }
                        .onFailure { error -> Log.w(TAG, "Group-to-private proactive follow-up failed", error) }
                }
            }
    }

    private fun registerSensingReceivers() {
        if (receiversRegistered) return
        if (smsReceiver == null) {
            smsReceiver = SmsBroadcastReceiver().also { receiver ->
                receiver.onSmsReceived = { sender, body, timestamp ->
                    scope.launch {
                        val eventId = events.recordSystemEvent("sms", "短信：$body", "$sender|$body|$timestamp", timestamp)
                        processInBackground(eventId)
                    }
                }
                registerReceiver(receiver, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
            }
        }
        if (deviceStateReceiver == null) {
            deviceStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action ?: return
                    val summary = when (action) {
                        Intent.ACTION_SCREEN_ON -> "The user's screen turned on."
                        Intent.ACTION_SCREEN_OFF -> "The user's screen turned off."
                        Intent.ACTION_USER_PRESENT -> "The user just unlocked their phone."
                        else -> return
                    }
                    val now = System.currentTimeMillis()
                    scope.launch {
                        val eventId = events.recordSystemEvent(
                            source = "device:screen",
                            summary = summary,
                            dedupeKey = "screen:$action:${now / SCREEN_EVENT_BUCKET_MS}",
                            occurredAt = now
                        )
                        sensingDiagnostics.record("screen", action.removePrefix("android.intent.action.").lowercase(), now)
                        processInBackground(eventId)
                    }
                }
            }.also { receiver ->
                registerReceiver(receiver, IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                })
            }
        }
        receiversRegistered = true
    }

    private suspend fun recordForegroundAppChange(now: Long) {
        val shizukuResult = shizukuRouter.readForegroundApp()
        val shizukuForeground = shizukuResult
            .takeIf { it.success }
            ?.summary
            ?.let(ForegroundAppParser::parse)
        val foreground = shizukuForeground ?: foregroundFromUsageStats(now)
        reportForegroundDiagnostic(
            when {
                shizukuForeground != null -> "shizuku"
                shizukuResult.success -> "shizuku_output_unrecognized"
                foreground != null -> "usage_stats"
                else -> "no_foreground_result"
            }
        )
        sensingDiagnostics.record("foreground", lastForegroundDiagnostic ?: "no_foreground_result", now)
        if (foreground != null && foreground != packageName &&
            (foreground != lastForegroundPackage || now - lastForegroundObservedAt >= FOREGROUND_REFRESH_MS)) {
            if (foreground != lastForegroundPackage) foregroundStartedAt = now
            lastForegroundPackage = foreground
            lastForegroundObservedAt = now
            Log.i(TAG, "Foreground application observed via ${if (shizukuForeground != null) "Shizuku" else "UsageStats"}")
            val eventId = this.events.recordSystemEvent(
                "device:foreground_app",
                "Current foreground app: ${foregroundLabel(foreground)} ($foreground). It has remained foreground for ${(now - foregroundStartedAt) / 60_000L} minutes.",
                "$foreground:${now / FOREGROUND_REFRESH_MS}",
                now
            )
            processInBackground(eventId)
        }
    }

    /** Keeps sensing independent from API latency. The coordinator rejects duplicate event work. */
    private fun processInBackground(eventId: String) {
        scope.launch {
            // 主动陪伴也要让路：用户还在聊天时别发起主动消息，免得跟对话抢同一张卡
            //（实测这类调用的 system 能到 37KB、单次跑到 50 秒）。
            // 直接跳过是安全的：事件仍是 NEW，下一拍 dao.pendingEvents() 会重新捞起来处理。
            val idleMs = backgroundIdle.idleMs.value
            val lastUserMessageAt = runCatching { dao.latestUserMessageAt() }.getOrNull()
            if (com.aster.companion.lite.BackgroundIdlePolicy
                    .shouldDefer(lastUserMessageAt, System.currentTimeMillis(), idleMs)
            ) {
                Log.d(TAG, "主动陪伴让路：用户仍在对话（空闲阈值 ${idleMs / 1000}s）id=$eventId")
                return@launch
            }
            runCatching { coordinator.process(eventId) }
                .onSuccess { result -> Log.i(TAG, "Proactive event completed id=$eventId result=${result::class.simpleName}") }
                .onFailure { error -> Log.w(TAG, "Proactive event processing failed", error) }
        }
    }

    private fun reportForegroundDiagnostic(value: String) {
        if (value != lastForegroundDiagnostic) {
            lastForegroundDiagnostic = value
            Log.i(TAG, "Foreground sensing path=$value")
        }
    }

    private fun foregroundFromUsageStats(now: Long): String? {
        val usage = getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { usage.queryEvents(now - 60_000L, now) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var foreground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) foreground = event.packageName
        }
        return foreground
    }

    private fun foregroundLabel(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun notification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "同频系统模式", NotificationManager.IMPORTANCE_LOW))
        val stopIntent = Intent(this, CompanionSystemService::class.java).setAction(ACTION_STOP)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("同频正在陪伴")
            .setContentText("系统感知已开启")
            .addAction(0, "停止", android.app.PendingIntent.getService(this, 1, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tongpin_system_mode"
        private const val NOTIFICATION_ID = 701
        private const val ACTION_STOP = "com.tongpin.companion.STOP_SYSTEM_MODE"
        // 自主化：群聊空闲 90 秒就触发角色间自主互动。
        private const val IDLE_INTERVAL_MS = 90 * 1000L
        /** 一拍最多处理多少个待处理事件，防止空闲让路后一次性放出一串主动陪伴决策。 */
        private const val MAX_EVENTS_PER_TICK = 3
        private const val POLL_INTERVAL_MS = 30 * 1000L
        private const val FOREGROUND_REFRESH_MS = 2 * 60 * 1000L
        private const val PRESENCE_PULSE_INTERVAL_MS = 12 * 60 * 1000L
        private const val SCREEN_EVENT_BUCKET_MS = 60 * 1000L
        private const val LIFE_CONTEXT_WINDOW_MS = 2 * 60 * 60 * 1000L
        private const val TAG = "TongpinSensing"

        fun start(context: Context) {
            SystemModeState(context).setEnabled(true)
            context.startForegroundService(Intent(context, CompanionSystemService::class.java))
        }

        fun stop(context: Context) {
            SystemModeState(context).setEnabled(false)
            context.stopService(Intent(context, CompanionSystemService::class.java))
        }
    }
}
