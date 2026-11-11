package com.aster.companion.lite.proactive

import com.aster.companion.agent.AgentTurnCoordinator
import com.aster.companion.agent.ImageGenerationCoordinator
import com.aster.companion.agent.OpenAiCompatibleClient
import com.aster.companion.agent.ScenePromptCoordinator
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionRepository
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.CompanionTimelineRecorder
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.CompanionAgencyPolicy
import com.aster.companion.domain.ConfirmationRequirement
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.MomentActors
import com.aster.companion.domain.UrgePolicy
import com.aster.companion.lite.LiteProductConfig
import com.aster.companion.proactive.CompanionNotificationPublisher
import com.aster.companion.proactive.ThreadActivitySignal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lite 主动消息调度（分册 §4 状态机、§5 探活与同拍、§7 调度、§9 参数）。
 *
 * 一拍里按顺序做四件事，任何一件失败都不影响其余：
 * 1. 最多 4 槽并行探活（两段式每角色占 2 槽），每拍落库受限流；
 * 2. 探活若返回 `PHOTO:` 意图 → 走 Scene 流水线写 tag → 交给图片动作链路出图（显存独占）；
 * 3. 每拍最多 1 次 L1 记忆压缩（记忆分册 §5.1）；
 * 4. 每拍最多 1 条朋友圈同拍动态（分册 §6.3，无图）。
 *
 * 让路规则：出图中整拍停、角色自己作息的睡眠区间内不打扰、全局安静时段不打扰、
 * 长空闲心跳（默认 2 小时）到期才允许对同一 idle 窗口重复探活。
 */
@Singleton
class LiteProactiveCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val cipher: CompanionSecretCipher,
    private val imageGeneration: ImageGenerationCoordinator,
    private val notificationPublisher: CompanionNotificationPublisher,
    private val timeline: CompanionTimelineRecorder,
    private val repository: CompanionRepository,
    private val scenePromptCoordinator: ScenePromptCoordinator,
    private val memoryRollup: com.aster.companion.lite.memory.MemoryRollupCoordinator,
    private val conversationStateUpdater: com.aster.companion.agent.ConversationStateUpdater,
    private val scenePhotoBus: com.aster.companion.lite.LiteScenePhotoBus,
    private val backgroundIdle: com.aster.companion.lite.BackgroundIdleStore,
    private val systemModeState: com.aster.companion.proactive.SystemModeState,
    private val toggles: LiteProactiveToggles,
    /** 日程预排：每拍顺带判一次"今天该不该重新审她的日程"（它自己会 gate，不会每拍都跑模型）。 */
    private val dayPlan: com.aster.companion.agent.DayPlanCoordinator,
    /** 世界事件播种：她"外面"偶尔发生的小事（默认关闭）。 */
    private val worldSeeder: com.aster.companion.agent.WorldSeederCoordinator,
    /** 「他正在这个会话里回复」（见 [ThreadActivitySignal]）：把原本永远为假的 `active` 闸接上真信号。 */
    private val threadActivitySignal: ThreadActivitySignal
) {

    // 设置页可以单独关掉「她主动发朋友圈」「她主动发照片」；关掉就是真的不产生这类动作。
    // 另有两项新入口：主动性节律（默认关 = 旧行为），以及「更主动一点」（默认关 = 严格档）。
    private val settings: LiteProactiveSettings
        get() = LiteProactiveSettings(
            momentsEnabled = toggles.momentsEnabled.value,
            photoEnabled = toggles.photoEnabled.value,
            urgeEnabled = toggles.urgeEnabled.value,
            contactMode = if (toggles.relaxedContact.value) {
                CompanionAgencyPolicy.ContactMode.NATURAL
            } else {
                CompanionAgencyPolicy.ContactMode.STRICT
            },
            worldSeedingEnabled = toggles.worldSeeding.value
        )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** threadId → 已经探活过的那个 idle 窗口（用当时最后一条消息的时间标识）。 */
    private val probedIdleWindow = ConcurrentHashMap<String, Long>()

    /** threadId → 上次探活时间；长空闲心跳据此放开重复探活。 */
    private val probeAtByThread = ConcurrentHashMap<String, Long>()

    /** characterId → 上次自发朋友圈动态的时间。 */
    private val momentAtByCharacter = ConcurrentHashMap<String, Long>()

    /** characterId → 上次主动出图的时间。 */
    private val photoAtByCharacter = ConcurrentHashMap<String, Long>()

    /**
     * threadId → 最近一次已知的可打扰性窗口（判断 + 账）。
     *
     * 每拍开头由 [hydrateAgencyWindows] 从库里恢复，[persistAgencyWindow] 负责写回。
     * 以前"上次开口时间 / 今天发过几条"只活在内存 map 里，进程一重启就全部归零。
     */
    private val agencyWindows = ConcurrentHashMap<String, CompanionAgencyPolicy.Window>()

    /**
     * threadId → 主动性节律状态（下次"想说话"的时刻）。
     *
     * 与 [agencyWindows] 同一路子：每拍由 [hydrateUrgeStates] 从库里恢复，
     * 开口后由 [persistUrge] 写回。未启用节律时这张表始终为空，一切照旧。
     */
    private val urgeStates = ConcurrentHashMap<String, UrgePolicy.State>()

    /** characterId → 上次真正发出主动消息的时间。 */
    private val lastProactiveAt = ConcurrentHashMap<String, Long>()

    /** characterId → (本地日期, 当日条数)。 */
    private val dailyCounter = ConcurrentHashMap<String, Pair<String, Int>>()

    /** 同角色一把锁（分册 §10）。 */
    private val characterLocks = ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var running = false

    fun start() {
        if (!LiteProductConfig.ENABLED || running) return
        running = true
        scope.launch {
            while (true) {
                runCatching { tick(System.currentTimeMillis()) }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /** 一次调度拍；测试可直接调用。 */
    suspend fun tick(now: Long, zone: ZoneId = ZoneId.systemDefault()) {
        if (!LiteProductConfig.ENABLED) return
        // 先把"账"从库里恢复回来（上次开口时间 / 今天发过几条），再据此算这一拍的可打扰性。
        // 它们以前只在内存 map 里，进程一重启冷却与每日配额就全部归零 —— 她会立刻又能刷一轮。
        hydrateAgencyWindows(now, zone)
        hydrateUrgeStates()
        // 本机此刻能不能支撑一次主动动作（模型可用 + 显存没被出图占满），一次算好传给每个会话。
        val snapshots = privateThreadSnapshots(
            now,
            zone,
            deviceAccessFor(imageGeneration.isImageLaneBusy())
        )
        if (snapshots.isEmpty()) return
        // 空闲让路：探活（主动陪伴里最吵的一条，每角色两次 LLM 调用）本来就有一道
        // idleQuietMs 安静窗口（默认 2 分钟），这里把它抬到设置页「后台推演」定的值（默认 5 分钟）；
        // 设置被关掉（0）时保留原来的 2 分钟默认，恢复旧行为。
        val configuredIdleMs = backgroundIdle.idleMs.value
        val effectiveIdleMs =
            if (configuredIdleMs > 0) configuredIdleMs else settings.idleQuietMs
        // 分册 §5：最多 4 槽并行探活（两段式每角色占 2 槽）；让路规则都在 planProbes 里。
        val selected = LiteProactivePolicy.planProbes(
            snapshots = snapshots,
            now = now,
            settings = settings.copy(idleQuietMs = effectiveIdleMs),
            imageBusy = imageGeneration.isImageLaneBusy(),
            dailyTotal = dailyTotal(now, zone),
            zone = zone
        )
        if (selected.isNotEmpty()) {
            val probes = coroutineScope {
                selected.map { snapshot ->
                    async {
                        com.aster.companion.lite.LiteBackgroundLlmGate.withPermit { probe(snapshot, now, zone) }
                    }
                }.mapNotNull { it.await() }
            }
            // 分册 §5.4：即使多槽都返回正文，每拍真正落库也有上限，多出来的直接丢弃。
            probes.take(LiteProactivePolicy.persistBudget(probes.size, settings)).forEach { outcome ->
                when (val reply = outcome.reply) {
                    is ProbeReply.Text -> persist(outcome.snapshot, reply.body, now, zone)
                    is ProbeReply.Photo -> runCatching { shareProactivePhoto(outcome.snapshot, reply.intent, now) }
                }
            }
        }
        // 用户仍在聊（最近 idleQuiet 内有消息）时，不跑记忆压缩/朋友圈文案，避免和聊天抢槽。
        val userLikelyChatting = snapshots.any { now - it.lastMessageAt < settings.idleQuietMs }
        if (!userLikelyChatting) {
            // 记忆分册 §5.1：每拍最多 1 次 L1 压缩，用剩下的槽；出图时 rollUp 自己会让路。
            snapshots.maxByOrNull { it.lastMessageAt }?.let { freshest ->
                runCatching {
                    com.aster.companion.lite.LiteBackgroundLlmGate.withPermit {
                        memoryRollup.rollUp(freshest.threadId, now)
                    }
                }
            }
            // 分册 §6.3：每拍最多 1 条朋友圈同拍（无图；配图仍由用户走 Scene 流水线）。
            runCatching { shareMomentIfDue(snapshots, now, zone) }
        }
    }

    private sealed interface ProbeReply {
        data class Text(val body: String) : ProbeReply
        data class Photo(val intent: String) : ProbeReply
    }

    private data class Probe(val snapshot: LiteProactiveThreadSnapshot, val reply: ProbeReply)

    /**
     * 从库里恢复所有会话的可打扰性窗口（判断 + 账）。
     *
     * 每拍开头跑一次：一次按 scope 的查询，不是逐会话查。
     * 内存比库里新时以内存为准——这一拍刚记过账、落库可能还没回来。
     */
    private suspend fun hydrateAgencyWindows(now: Long, zone: ZoneId) {
        val today = dateKey(now, zone)
        val rows = runCatching { dao.worldStatesByScope(CompanionAgencyPolicy.SCOPE) }
            .getOrDefault(emptyList())
        rows.forEach { row ->
            val threadId = row.threadId ?: return@forEach
            val characterId = row.characterId ?: return@forEach
            val stored = CompanionAgencyPolicy.Window.fromJson(row.content)
            val window = stored.copy(contactLog = mergeContactLog(stored.contactLog, threadId))
            agencyWindows[threadId] = window
            // 联系日志是"账"的唯一权威；下面两张表只是给 planProbes / dailyTotal 读的内存镜像。
            lastProactiveAt[characterId] = maxOf(
                lastProactiveAt[characterId] ?: 0L,
                CompanionAgencyPolicy.lastContactAt(window.contactLog, characterId)
            )
            val fromLog = CompanionAgencyPolicy.countContactsInWindow(window.contactLog, characterId, now)
            dailyCounter[characterId] = today to maxOf(
                dailyCounter[characterId]?.takeIf { it.first == today }?.second ?: 0,
                fromLog
            )
        }
    }

    /**
     * 把库里的联系日志与内存里的合并。
     *
     * 两边都可能各有对方没有的记录（这一拍刚记的还没落库 / 上一次落库后进程还没重启完），
     * 所以取并集再按时间保留最近若干条，而不是简单覆盖。
     */
    private fun mergeContactLog(
        stored: List<CompanionAgencyPolicy.ContactLogEntry>,
        threadId: String
    ): List<CompanionAgencyPolicy.ContactLogEntry> {
        val memory = agencyWindows[threadId]?.contactLog.orEmpty()
        if (memory.isEmpty()) return stored
        return (stored + memory)
            .distinctBy { it.participantId to it.at }
            .sortedBy { it.at }
            .takeLast(CompanionAgencyPolicy.CONTACT_LOG_LIMIT)
    }

    /**
     * 算一次可打扰性窗口：**事实**从库里读，**账**取自 [agencyWindows]（每拍已恢复过）。
     *
     * 判据本身在 [CompanionAgencyPolicy] 里，是纯函数；这里只负责把事实查齐。
     */
    private suspend fun agencyWindowFor(
        threadId: String,
        characterId: String,
        conversationType: ConversationType,
        now: Long,
        zone: ZoneId,
        living: com.aster.companion.data.CharacterLivingStateEntity?,
        sleeping: Boolean,
        paused: Boolean,
        deviceAccess: CompanionAgencyPolicy.DeviceAccess,
        sourceEntryIds: List<String>
    ): CompanionAgencyPolicy.Window = CompanionAgencyPolicy.evaluate(
        CompanionAgencyPolicy.Input(
            now = now,
            sleeping = sleeping,
            sleepUntil = living?.sleepUntil ?: 0L,
            busyUntil = planReviewAt(characterId),
            locationId = living?.locationId,
            conversationType = conversationType,
            quietHours = LiteProactivePolicy.inQuietHours(now, settings, zone),
            paused = paused,
            userReplying = threadActivitySignal.isReplying(threadId),
            deviceAccess = deviceAccess,
            sourceEntryIds = sourceEntryIds
        ),
        previous = agencyWindows[threadId] ?: CompanionAgencyPolicy.Window()
    )

    /** 联系温度配置：间隔与配额沿用既有设置，只有档位是新加的口子。 */
    private fun contactPolicyConfig(): CompanionAgencyPolicy.ContactPolicyConfig =
        CompanionAgencyPolicy.ContactPolicyConfig(
            mode = settings.contactMode,
            minimumIntervalMs = settings.perCharacterCooldownMs,
            dailyCap = settings.dailyGlobalLimit
        )

    /**
     * 本机此刻能支撑一次主动动作吗。
     *
     * 同频没有上游那种"设备权限"概念（它跑在手机上、模型在 PC），所以映射成**模型可用性**：
     * 连对话模型都没配好 → 不可用；显存被出图占着 → 受限；否则可用。
     */
    private suspend fun deviceAccessFor(imageBusy: Boolean): CompanionAgencyPolicy.DeviceAccess {
        val profile = runCatching { dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL) }.getOrNull()
        val ready = profile != null && profile.enabled && !profile.encryptedApiKey.isNullOrBlank()
        return when {
            !ready -> CompanionAgencyPolicy.DeviceAccess.UNAVAILABLE
            imageBusy -> CompanionAgencyPolicy.DeviceAccess.LIMITED
            else -> CompanionAgencyPolicy.DeviceAccess.AVAILABLE
        }
    }

    // ---- 主动性节律（Urge）----

    /** 节律配置；未启用返回 null（调用方据此完全不干预，旧行为原样保留）。 */
    private fun urgeConfig(): UrgePolicy.Config? =
        if (settings.urgeEnabled) {
            // 与「更主动一点」方向一致（用户要求）：那个开关打开时，节律也提到更积极的一档。
            //
            // 以前 `relaxedContact` 只映射到 `contactMode`，**完全接不到 `urgeFrequency`**——
            // 节律开启时"她什么时候想说话"由 MEDIUM 档决定，冷期的等待甚至可能比原来的固定冷却更长，
            // 于是打开「更主动一点」感觉没生效、甚至更安静。两个开关本该同向，现在真的同向了。
            // 取**更积极的那个**：用户显式选了 HIGH 就还是 HIGH，不会被这里降级。
            val frequency = if (
                settings.contactMode == CompanionAgencyPolicy.ContactMode.NATURAL &&
                settings.urgeFrequency.ordinal < UrgePolicy.Frequency.HIGH.ordinal
            ) {
                UrgePolicy.Frequency.HIGH
            } else {
                settings.urgeFrequency
            }
            UrgePolicy.Config(enabled = true, frequency = frequency)
        } else {
            null
        }

    /**
     * 取这一拍要用的节律状态；**没排过期就先排一次**。
     *
     * 首次排程很关键：`nextAt = 0` 会被 [UrgePolicy.isDue] 判成"随时可以"，
     * 不排的话刚打开开关等于没接上。排完立刻落库，重启不丢。
     */
    private suspend fun urgeStateFor(
        threadId: String,
        characterId: String,
        messageTimes: List<Long>,
        now: Long
    ): UrgePolicy.State? {
        val config = urgeConfig() ?: return null
        val current = urgeStates[threadId] ?: UrgePolicy.State()
        if (current.nextAt > 0L) return current
        val scheduled = UrgePolicy.schedule(current, now, messageTimes, config)
        urgeStates[threadId] = scheduled
        persistUrge(threadId, characterId, scheduled, now)
        return scheduled
    }

    /** 从库里恢复节律状态（一次按 scope 的查询，不是逐会话查）。 */
    private suspend fun hydrateUrgeStates() {
        val rows = runCatching { dao.worldStatesByScope(UrgePolicy.SCOPE) }.getOrDefault(emptyList())
        rows.forEach { row ->
            val threadId = row.threadId ?: return@forEach
            urgeStates[threadId] = UrgePolicy.State.fromJson(row.content)
        }
    }

    private suspend fun persistUrge(
        threadId: String,
        characterId: String,
        state: UrgePolicy.State,
        now: Long
    ) {
        runCatching {
            dao.upsertWorldState(
                WorldStateEntity(
                    id = UrgePolicy.rowId(threadId),
                    characterId = characterId,
                    threadId = threadId,
                    scope = UrgePolicy.SCOPE,
                    content = state.toJson(),
                    updatedAt = now
                )
            )
        }
    }

    /** 她手头这件事**她自己说的**下次可问时间（`LIFE_PLAN` 的 reviewAt；没有计划则 0）。 */
    private suspend fun planReviewAt(characterId: String): Long = runCatching {
        dao.worldStates(characterId, "LIFE_PLAN").maxByOrNull { it.updatedAt }
            ?.content?.substringBefore('|')?.toLongOrNull()
    }.getOrNull() ?: 0L

    /**
     * 记一笔"她开口了"，并把窗口落库。
     *
     * 这是 `LIFE_PLAN`（`life-plan:<characterId>`）那套做法的同一路子：把"下次什么时候再动"
     * 这类跨进程状态放进 world state 的独立 scope，于是**不需要 DB 迁移**，重启也不会丢。
     */
    private suspend fun persistAgencyWindow(snapshot: LiteProactiveThreadSnapshot, at: Long) {
        runCatching {
            val prior = agencyWindows[snapshot.threadId] ?: CompanionAgencyPolicy.Window()
            val bumped = CompanionAgencyPolicy.recordSpoke(
                prior,
                CompanionAgencyPolicy.ContactLogEntry(
                    participantId = snapshot.characterId,
                    at = at,
                    origin = CompanionAgencyPolicy.ContactOrigin.LIFE_EVENT.name,
                    reason = "生活剧本探活"
                )
            )
            agencyWindows[snapshot.threadId] = bumped
            dao.upsertWorldState(
                WorldStateEntity(
                    id = CompanionAgencyPolicy.windowId(snapshot.threadId),
                    characterId = snapshot.characterId,
                    threadId = snapshot.threadId,
                    scope = CompanionAgencyPolicy.SCOPE,
                    content = bumped.toJson(),
                    updatedAt = at
                )
            )
        }
    }

    private suspend fun privateThreadSnapshots(
        now: Long,
        zone: ZoneId,
        deviceAccess: CompanionAgencyPolicy.DeviceAccess
    ): List<LiteProactiveThreadSnapshot> {
        val today = dateKey(now, zone)
        // 用户按角色关掉「主动联系」时，只让名单里的角色参与探活（名单没配过 = 所有角色都可以）。
        val proactiveEnabledIds = com.aster.companion.proactive.ProactiveCharacterSelector
            .resolveAll(
                preferredIds = systemModeState.proactiveCharacterIds,
                availableIds = dao.characterIds(),
                selectionConfigured = systemModeState.proactiveSelectionConfigured
            )
            .toSet()
        val snapshots = dao.allThreads()
            .filter { it.type == ConversationType.PRIVATE }
            .mapNotNull { thread ->
                val characterId = dao.participantIds(thread.id).firstOrNull() ?: return@mapNotNull null
                val recent = dao.recentMessages(thread.id, AGENCY_SOURCE_MESSAGES)
                val lastMessageAt = recent.firstOrNull()?.createdAt ?: thread.updatedAt
                val sourceEntryIds = recent.map { it.id }
                // 他说过「我去睡了」这类话 → 她的主动联系被按下（越过一夜会自动放开）。
                val paused = systemModeState.isProactivePaused(characterId)
                val living = dao.livingState(characterId)
                val sleeping = living?.sleepUntil?.let { now < it } == true
                val window = agencyWindowFor(
                    threadId = thread.id,
                    characterId = characterId,
                    conversationType = thread.type,
                    now = now,
                    zone = zone,
                    living = living,
                    sleeping = sleeping,
                    paused = paused,
                    deviceAccess = deviceAccess,
                    sourceEntryIds = sourceEntryIds
                )
                // 这一拍的候选是"生活剧本想主动找他说句话"（普通内容、普通来源）。
                // 环境硬门 + 场合 + 24 小时配额 + 最小间隔 + 意愿，全部在这一次裁决里过一遍。
                val decision = CompanionAgencyPolicy.decide(
                    window = window,
                    draft = CompanionAgencyPolicy.ContactDraft(
                        participantId = characterId,
                        origin = CompanionAgencyPolicy.ContactOrigin.LIFE_EVENT,
                        motive = "生活剧本产生的主动联系",
                        sourceEntryIds = sourceEntryIds
                    ),
                    config = contactPolicyConfig(),
                    now = now
                )
                // 主动性节律：她"想不想"开口（与上面"能不能"是**与**关系）。未启用时为 null。
                val urge = urgeStateFor(thread.id, characterId, recent.map { it.createdAt }, now)
                // 日程预排：顺带判一次"今天该不该重新审她的日程"。同一天只审一次、失败有退避，
                // 所以这一句绝大多数时候只是读一行 world state 就返回。
                // 真要跑模型时**必须**走后台闸 + 独立协程：不然它会跟聊天抢本机那几张并发槽，
                // 用户的话就会变慢——这条纪律与主动消息后的状态更新一致。
                scope.launch {
                    runCatching {
                        com.aster.companion.lite.LiteBackgroundLlmGate.withPermit {
                            dayPlan.reviewIfDue(
                                characterId = characterId,
                                threadId = thread.id,
                                recent = recent,
                                now = now,
                                zone = zone
                            )
                        }
                    }
                }
                // 世界事件播种（**默认关闭**）：她"外面"偶尔发生点小事。与日程复审同理——
                // 它是后台模型调用，必须走后台闸 + 独立协程。
                if (settings.worldSeedingEnabled) {
                    scope.launch {
                        runCatching {
                            com.aster.companion.lite.LiteBackgroundLlmGate.withPermit {
                                worldSeeder.seedIfDue(
                                    characterId = characterId,
                                    threadId = thread.id,
                                    now = now,
                                    dayKey = dateKey(now, zone)
                                )
                            }
                        }
                    }
                }
                LiteProactiveThreadSnapshot(
                    threadId = thread.id,
                    characterId = characterId,
                    lastMessageAt = lastMessageAt,
                    lastProactiveAt = lastProactiveAt[characterId] ?: 0L,
                    todayProactiveCount = dailyCounter[characterId]
                        ?.takeIf { it.first == today }
                        ?.second
                        ?: 0,
                    // 「他正在这个会话里回复」。以前这里写死 false —— 一道**永远为假**的闸，
                    // 看着像保护，实际什么都没挡（她的回复生成可以跑到几十秒，足够和空闲窗口重叠）。
                    active = threadActivitySignal.isReplying(thread.id),
                    probedForCurrentIdle = probedIdleWindow[thread.id] == lastMessageAt,
                    lastProbeAt = probeAtByThread[thread.id] ?: 0L,
                    sleeping = sleeping,
                    paused = paused,
                    // 会话管理里按角色关掉的「主动联系」，这条路以前也没看。
                    proactiveEnabled = characterId in proactiveEnabledIds,
                    // 可打扰性结论：环境层（睡着 / 手头忙 / 安静时段 / 他正在回复 / 设备不可用）
                    // 与候选层（场合 / 24 小时配额 / 最小间隔 / 意愿）都过，才允许开口。
                    agencyBlocked = !decision.allowed,
                    urgeBlocked = urge != null && !UrgePolicy.isDue(urge, now)
                )
            }
        return LiteProactivePolicy.filterEligible(snapshots)
    }

    /** 单槽探活：只产出结果，不落库；SILENT 或失败返回 null。 */
    private suspend fun probe(snapshot: LiteProactiveThreadSnapshot, now: Long, zone: ZoneId): Probe? {
        val lock = characterLocks.getOrPut(snapshot.characterId) { Mutex() }
        if (lock.isLocked) return null
        return lock.withLock {
            // 探活过就记账，避免同一个 idle 窗口被反复打扰（心跳到期除外）。
            probedIdleWindow[snapshot.threadId] = snapshot.lastMessageAt
            probeAtByThread[snapshot.threadId] = now
            val character = dao.character(snapshot.characterId) ?: return@withLock null
            val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL) ?: return@withLock null
            if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return@withLock null
            val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
            val photoAllowed = !imageGeneration.isImageLaneBusy() && LiteProactiveReplyPolicy.canSendPhoto(
                lastPhotoAt = photoAtByCharacter[snapshot.characterId] ?: 0L,
                now = now,
                settings = settings
            )
            val living = dao.livingState(snapshot.characterId)
            val input = LiteProactivePromptBuilder.Input(
                characterName = character.name,
                personalityPrompt = character.personalityPrompt,
                relationshipPrompt = character.relationshipPrompt,
                recent = recentBrief(snapshot.threadId),
                timeOfDay = timeOfDay(now, zone),
                minutesSinceLastChat = ((now - snapshot.lastMessageAt) / 60_000L).toInt().coerceAtLeast(0),
                situation = listOfNotNull(
                    living?.currentActivity?.takeIf { it.isNotBlank() },
                    living?.mood?.takeIf { it.isNotBlank() }?.let { "心情 $it" }
                ).joinToString(" · "),
                photoAllowed = photoAllowed
            )
            val reply = runCatching { generateReply(profile, apiKey, input) }.getOrNull() ?: return@withLock null
            Probe(snapshot, reply)
        }
    }

    private suspend fun persist(snapshot: LiteProactiveThreadSnapshot, body: String, now: Long, zone: ZoneId) {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            threadId = snapshot.threadId,
            characterId = snapshot.characterId,
            body = body,
            imageUri = null,
            fromUser = false,
            createdAt = System.currentTimeMillis()
        )
        dao.insertMessage(message)
        timeline.recordMessage(message)
        dao.touchThread(snapshot.threadId, message.createdAt)
        lastProactiveAt[snapshot.characterId] = message.createdAt
        val today = dateKey(now, zone)
        dailyCounter[snapshot.characterId] =
            today to ((dailyCounter[snapshot.characterId]?.takeIf { it.first == today }?.second ?: 0) + 1)
        // 把这笔账落库（可打扰性窗口）：以前内存 map 一重启就归零，她会立刻又能刷一轮。
        persistAgencyWindow(snapshot, message.createdAt)
        // 节律：她这次真的开口了 → 记下时刻，并按最新活跃度重排下一次"想说话"的时机。
        urgeConfig()?.let { config ->
            val times = runCatching {
                dao.recentMessages(snapshot.threadId, URGE_SAMPLE_MESSAGES).map { it.createdAt }
            }.getOrDefault(emptyList())
            val next = UrgePolicy.schedule(
                UrgePolicy.recordSpoke(urgeStates[snapshot.threadId] ?: UrgePolicy.State(), message.createdAt),
                message.createdAt,
                times,
                config
            )
            urgeStates[snapshot.threadId] = next
            persistUrge(snapshot.threadId, snapshot.characterId, next, message.createdAt)
        }
        dao.character(snapshot.characterId)?.let { character ->
            notificationPublisher.publish(character.name, body, snapshot.threadId)
        }
        // 她主动说的话也是"你们之间发生过的事"。
        // 以前这条路径只落库 + 推进消息水位，**从不触发状态更新**（全仓 `updateAfterTurn` 只有
        // ConversationTurnCoordinator 一处调用），于是主动消息要等用户下一次先开口才会并进
        // 「当前状态」——表现就是"她记得有这回事，但细节对不上"。
        // 用独立协程起，不阻塞本拍其余的落库与通知；后台 LLM 闸 + 每会话 Mutex 都在，
        // 攒不够 MIN_NEW_MESSAGES 条时这一步只读一次水位、不发请求。
        scope.launch {
            runCatching {
                com.aster.companion.lite.LiteBackgroundLlmGate.withPermit {
                    conversationStateUpdater.updateAfterTurn(snapshot.threadId)
                }
            }
        }
    }

    /**
     * 主动生图（分册 §3 二期的可选行为）：角色的中文意图 → Scene 流水线出英文 tag 串
     * → 交给图片动作链路按 768 出图；出图期间本拍的其它主动已经让路。
     */
    private suspend fun shareProactivePhoto(snapshot: LiteProactiveThreadSnapshot, intent: String, now: Long) {
        if (!settings.photoEnabled) return
        if (imageGeneration.isImageLaneBusy()) return
        val draft = runCatching {
            scenePromptCoordinator.buildTagPrompt(
                characterId = snapshot.characterId,
                intent = intent,
                recent = recentBrief(snapshot.threadId),
                useBackgroundGate = true,
                viewpoint = true,
                // 她探活时已经自己决定了要拍什么，这里只把她的决定转成 tag，并补一行「为什么」。
                wantReason = true,
                shot = com.aster.companion.agent.CharacterPhotoShotPolicy.PhotoShot(shot = intent),
                // ⚠️ **必须显式传**。以前这里省略了它，于是靠编译器合成的 `$default` 桥补默认值
                // `null`——而那条桥在这条签名（suspend + `Result` 返回 + 有默认参数）下会把参数
                // 传错，导致**她主动发照片时直接 NPE 闪退**（栈里出现 `$default` 与
                // `ScenePromptInputs…component1` 就是它）。显式传 null 与旧语义完全一致，
                // 但不再生成桥：函数体自己会回退到 `inputsFor(characterId)`。
                inputs = null
            )
        }.getOrNull() ?: return
        photoAtByCharacter[snapshot.characterId] = now
        lastProactiveAt[snapshot.characterId] = now
        // 主动生图也走预览确认，避免用户不知道会生成什么。
        scenePhotoBus.publish(
            com.aster.companion.lite.ScenePhotoRequest(
                threadId = snapshot.threadId,
                characterId = snapshot.characterId,
                intent = intent,
                prompt = draft.prompt,
                shot = draft.shot,
                reason = draft.reason,
                stage = com.aster.companion.lite.ScenePhotoStage.READY,
                attempts = draft.attempts
            )
        )
    }

    /**
     * 朋友圈同拍（分册 §6.3）：与探活同拍但最多 1 条，冷却 6 小时；
     * 只写中文短动态，不生成图——用户想要配图时走 Scene 流水线。
     */
    private suspend fun shareMomentIfDue(snapshots: List<LiteProactiveThreadSnapshot>, now: Long, zone: ZoneId) {
        if (!settings.momentsEnabled) return
        if (imageGeneration.isImageLaneBusy()) return
        val candidate = snapshots
            .filterNot { it.sleeping }
            .sortedByDescending { it.lastMessageAt }
            .firstOrNull { snapshot ->
                val lastAt = momentAtByCharacter[snapshot.characterId] ?: 0L
                val ok = LiteProactivePolicy.canShareMoment(
                    lastMomentAt = lastAt,
                    now = now,
                    settings = settings,
                    imageBusy = false,
                    zone = zone
                )
                android.util.Log.d(
                    "TongpinMoments",
                    "LITE moment check char=${snapshot.characterId.take(8)} lastAt=$lastAt " +
                        "sinceMin=${if (lastAt > 0) (now - lastAt) / 60_000L else -1} " +
                        "cooldownMin=${settings.momentCooldownMs / 60_000L} can=${ok}"
                )
                ok
            } ?: return
        val lock = characterLocks.getOrPut(candidate.characterId) { Mutex() }
        if (lock.isLocked) return
        lock.withLock {
            val character = dao.character(candidate.characterId) ?: return@withLock
            val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL) ?: return@withLock
            if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return@withLock
            val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
            val living = dao.livingState(candidate.characterId)
            val input = LiteProactivePromptBuilder.Input(
                characterName = character.name,
                personalityPrompt = character.personalityPrompt,
                relationshipPrompt = character.relationshipPrompt,
                recent = recentBrief(candidate.threadId),
                timeOfDay = timeOfDay(now, zone),
                minutesSinceLastChat = ((now - candidate.lastMessageAt) / 60_000L).toInt().coerceAtLeast(0),
                situation = listOfNotNull(
                    living?.currentActivity?.takeIf { it.isNotBlank() },
                    living?.mood?.takeIf { it.isNotBlank() }?.let { "心情 $it" }
                ).joinToString(" · ")
            )
            val raw = com.aster.companion.lite.LiteBackgroundLlmGate.withPermit {
                client.chat(
                    profile = profile,
                    apiKey = apiKey,
                    systemPrompt = LiteProactivePromptBuilder.momentSystem(input),
                    userPrompt = LiteProactivePromptBuilder.momentUser(input)
                ).getOrNull()
            } ?: return@withLock
            val body = when (val verdict = LiteProactiveReplyPolicy.parse(raw, settings)) {
                is LiteProactiveReplyPolicy.Verdict.Speak -> verdict.text
                else -> return@withLock
            }
            runCatching {
                repository.createCharacterMoment(
                    characterId = character.id,
                    body = body,
                    source = MomentActors.SOURCE_PROACTIVE,
                    status = MomentActors.STATUS_PUBLISHED
                )
            }.onSuccess {
                momentAtByCharacter[character.id] = System.currentTimeMillis()
                android.util.Log.i("TongpinMoments", "LITE moment POSTED char=${character.id.take(8)} body=${body.take(30)}")
            }.onFailure {
                android.util.Log.w("TongpinMoments", "LITE moment failed", it)
            }
        }
    }

    /** 两段式：先要一行内心，再据此决定说、发照片还是 SILENT。任何一段失败都当作本次安静。 */
    private suspend fun generateReply(
        profile: com.aster.companion.data.ModelProfileEntity,
        apiKey: String,
        input: LiteProactivePromptBuilder.Input
    ): ProbeReply? {
        val inner = if (settings.twoStage) {
            client.chat(
                profile = profile,
                apiKey = apiKey,
                systemPrompt = LiteProactivePromptBuilder.innerSystem(input),
                userPrompt = "只输出那一行 ≤15 字中文。"
            ).getOrNull()?.trim()?.lines()?.firstOrNull { it.isNotBlank() }?.take(30).orEmpty()
        } else ""
        val system = LiteProactivePromptBuilder.system(input)
        val user = LiteProactivePromptBuilder.user(input.copy(inner = inner))
        val raw = client.chat(profile, apiKey, system, user).getOrElse { return null }
        return when (val verdict = LiteProactiveReplyPolicy.parse(raw, settings)) {
            is LiteProactiveReplyPolicy.Verdict.Speak -> ProbeReply.Text(verdict.text)
            is LiteProactiveReplyPolicy.Verdict.Photo ->
                if (input.photoAllowed) ProbeReply.Photo(verdict.intent) else null
            else -> null
        }
    }

    private suspend fun recentBrief(threadId: String): List<String> = dao.recentMessages(threadId, RECENT_LIMIT)
        .asReversed()
        .mapNotNull { message ->
            val body = message.body.trim().replace(WHITESPACE, " ")
            if (body.isEmpty() || body.startsWith("stream:")) return@mapNotNull null
            if (message.fromUser) "User: ${body.take(40)}"
            else "${dao.character(message.characterId.orEmpty())?.name ?: "角色"}: ${body.take(40)}"
        }

    private fun dailyTotal(now: Long, zone: ZoneId): Int {
        val today = dateKey(now, zone)
        return dailyCounter.values.count { it.first == today }
    }

    private fun dateKey(now: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()

    private fun timeOfDay(now: Long, zone: ZoneId): String {
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        return when {
            hour in 5..8 -> "清晨"
            hour in 9..11 -> "上午"
            hour in 12..13 -> "中午"
            hour in 14..17 -> "下午"
            hour in 18..22 -> "晚上"
            else -> "深夜"
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 15_000L

        /** 取最近几条消息当可打扰性窗口的"来源证据"（可回溯"为什么是这个判断"）。 */
        const val AGENCY_SOURCE_MESSAGES = 3

        /**
         * 算活跃度密度时看多少条消息。
         *
         * 密度只看最近 15 分钟，但取样要多取一些：窗口内消息很密时，
         * 只取 3 条会把"这 15 分钟里其实聊了七八轮"看漏。
         */
        const val URGE_SAMPLE_MESSAGES = 30
        const val RECENT_LIMIT = 4
        val WHITESPACE = Regex("\\s+")
    }
}
