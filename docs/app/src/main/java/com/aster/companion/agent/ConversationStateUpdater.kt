package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.data.ThreadSummaryEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.EmotionalOffsetPolicy
import com.aster.companion.update.RemotePromptStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 会话状态更新器：把"更早之前发生过什么"从**散文摘要**换成**结构化状态**，并且**增量**更新。
 *
 * 三条与旧做法（`ConversationSummarizer`）的关键区别：
 *
 * 1. **增量而不是每 40 条重压**：每轮结束后把「上一状态 + 这几条新对话」喂给模型，产出新状态。
 *    旧做法是"旧摘要（已经是有损的）+ 新 40 条（每条还被 take(120) 砍过）→ 再压 300 字"，
 *    每压一次丢一层，压几轮开头就磨没了。
 * 2. **独立极简 system，不含角色卡**：一个 RP 微调过的模型拿到人设就会顺着"演"下去，
 *    产出带对白和星号动作的"摘要"；这里只给它"你是状态记录器"。
 * 3. **输出必须过校验才落库**：不合格就**保留旧状态、水位不推进**，下轮自动重来；
 *    连续失败到 [ALERT_FAILURES] 次会在设置页给出提示（以前是 `runCatching` 静默吞掉）。
 *
 * 并发/幂等：水位是"已并入状态的最大消息时间"（[ThreadSummaryEntity.updatedAt]），
 * 只有成功才推进，所以失败不会丢消息，重跑也不会重复吞消息。
 */
@Singleton
class ConversationStateUpdater @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val secretCipher: com.aster.companion.data.CompanionSecretCipher,
    private val chatDisplaySettings: com.aster.companion.ui.ChatDisplaySettings
) {
    companion object {
        /**
         * 攒够这么多条新消息才更新一次（4 条 = 两轮完整问答）。
         *
         * 每轮都更新输入很便宜（状态 600 字 + 两条消息），但它是**同一条模型的又一次调用**，
         * 会和她的对话、生活推演、出图抢同一张卡，所以留一点间隔。
         */
        const val MIN_NEW_MESSAGES = 4

        /** 增量更新一次最多吃几条新消息（剩下的下轮继续，水位保证不丢）。 */
        const val MAX_BATCH_MESSAGES = 8

        /** 状态为空（首次 / 旧散文摘要作废）时，用最近这么多条重建一次。 */
        const val REBUILD_BACKLOG_MESSAGES = 40

        const val MAX_ATTEMPTS = 3

        /** 记录器要的是"照抄事实"，温度压到 0.2 抑制发挥。 */
        const val TEMPERATURE = 0.2

        /** 输出上限：状态本身 ≤600 字，400 token 绰绰有余，也防它写长文。 */
        const val MAX_TOKENS = 400

        /** 连续失败到这个次数就在设置页提示用户（记忆不再无声丢失）。 */
        const val ALERT_FAILURES = 5

        /** 单条消息进记录器的截断长度。 */
        private const val MESSAGE_CHARS = 400

        private val RETRY_DELAYS_MS = longArrayOf(1_500L, 4_000L)

        /**
         * 情绪底色存放的 world state scope。
         *
         * 存在已有的 KV 表里而不是新开一张表：这一层是"语气"，不是必须随备份走的核心记忆，
         * 落在 world state 上可以**完全免掉一次 DB 迁移**（表结构、schema、备份/换机都不用动）。
         */
        const val OFFSET_SCOPE = "EMOTIONAL_OFFSET"

        /** 生成底色描写的远程提示词 key（改措辞不用重装 App）。 */
        const val OFFSET_PROMPT_KEY = "chat.emotionalOffset"

        /** 统计"最近有多活跃"时最多看多少条消息。 */
        private const val DENSITY_SAMPLE_MESSAGES = 60

        /**
         * 底色生成用的温度。
         *
         * 比状态记录器（0.2）高一点：记录器要"照抄事实"，而底色要的是一句有点味道的语气描写。
         */
        private const val OFFSET_TEMPERATURE = 0.3

        /** 写进评分轨迹的相位标签。 */
        private const val PHASE_USER_MESSAGE = "user-message"

        /** 生成底色描写时最多带多少字对话进去。 */
        private const val OFFSET_TRANSCRIPT_CHARS = 1_200

        /**
         * 底色描写的内置兜底提示词（[OFFSET_PROMPT_KEY] 取不到时用）。
         *
         * 它要的是"语气状态"，不是对白/剧情/动作描写——否则 RP 微调过的模型会顺手演下去，
         * 和状态记录器当初踩过的坑一模一样。
         */
        private val DEFAULT_OFFSET_PROMPT = """
            你是语气记录器。根据「最近对话」，写一句角色**此刻说话时的情绪底色**。

            硬性规则：
            1. 只输出一句中文短句（不超过 40 字）：不要引号、不要解释、不要列表、不要代码块标记。
            2. 写的是"语气状态"，不是对白、不是剧情续写、不是动作描写。
               例如：语气发紧，话变短了／松下来了，愿意多说两句／带着点哄人的味道。
            3. 只依据给出的对话，不要编造没有发生的事。
            4. 不要出现"氛围""情绪""强度"这类词，直接写她说话的样子。
        """.trimIndent()
    }

    /** 每个会话一把锁（见 [updateAfterTurn]）。 */
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /** 供对话注入的状态文本；空串 = 还没有可用状态（新会话或旧摘要已作废）。 */
    suspend fun stateFor(threadId: String): String {
        val stored = runCatching { dao.threadSummary(threadId)?.summary }.getOrNull()
        return if (ConversationStateFormat.isUsable(stored)) stored!!.trim() else ""
    }

    /**
     * 一轮对话结束后调用（后台协程）。成功返回新状态文本，失败返回异常且**不改动已有状态**。
     *
     * 同一会话用互斥锁串起来：用户连发两条会同时起两个后台任务，
     * 两个都读到同一个水位就会重复并入同一批消息（写入互相覆盖）。锁住之后第二个会看到新水位。
     */
    suspend fun updateAfterTurn(threadId: String): Result<String> =
        locks.getOrPut(threadId) { Mutex() }.withLock { updateLocked(threadId) }

    private suspend fun updateLocked(threadId: String): Result<String> {
        val entity = runCatching { dao.threadSummary(threadId) }.getOrNull()
        val current = entity?.summary?.takeIf { ConversationStateFormat.isUsable(it) }?.trim().orEmpty()

        // 状态可用 → 只处理"上次成功之后"的新消息；状态为空（首次或旧数据）→ 用最近一批重建。
        val cutoff = if (current.isEmpty()) 0L else (entity?.updatedAt ?: 0L)
        val pending = runCatching {
            dao.recentMessages(threadId, 500)
                .asReversed()
                .filter { !it.id.startsWith("stream:") }
                .filter { it.createdAt > cutoff }
        }.getOrDefault(emptyList())
        if (pending.size < MIN_NEW_MESSAGES) return Result.success(current)

        val batch = if (current.isEmpty()) {
            pending.takeLast(REBUILD_BACKLOG_MESSAGES)
        } else {
            pending.take(MAX_BATCH_MESSAGES)
        }
        if (batch.isEmpty()) return Result.success(current)

        val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return Result.failure(IllegalStateException("对话模型不可用，状态未更新"))
        val apiKey = runCatching { secretCipher.decrypt(requireNotNull(profile.encryptedApiKey)) }.getOrElse {
            return Result.failure(IllegalStateException("对话模型密钥读取失败"))
        }

        // 角色名先查好（joinToString 的 lambda 里不能调 suspend 的 dao）。
        val speakerNames = batch.mapNotNull { it.characterId }.distinct().associateWith { id ->
            runCatching { dao.character(id)?.name }.getOrNull().orEmpty().ifBlank { "角色" }
        }
        val transcript = batch.joinToString("\n") { message ->
            val speaker = if (message.fromUser) "用户" else (speakerNames[message.characterId.orEmpty()] ?: "角色")
            "$speaker：${message.body.trim().replace(Regex("\\s+"), " ").take(MESSAGE_CHARS)}"
        }
        val userPrompt = buildString {
            appendLine("【当前状态】")
            appendLine(current.ifBlank { "（还没有记录，这是第一次）" })
            appendLine()
            appendLine("【新增对话】")
            append(transcript)
        }

        var lastReason = "未知错误"
        for (attempt in 1..MAX_ATTEMPTS) {
            val raw = client.chat(
                profile = profile,
                apiKey = apiKey,
                systemPrompt = ConversationStateFormat.SYSTEM_PROMPT,
                userPrompt = userPrompt,
                temperature = TEMPERATURE,
                maxTokens = MAX_TOKENS
            )
            val validation = raw.fold(
                onSuccess = { ConversationStateFormat.validate(it) },
                onFailure = {
                    ConversationStateFormat.Validation(
                        ok = false,
                        text = ConversationStateFormat.EMPTY,
                        reason = it.message?.take(80) ?: "调用失败"
                    )
                }
            )
            if (validation.ok) {
                dao.upsertThreadSummary(
                    ThreadSummaryEntity(
                        threadId = threadId,
                        role = entity?.role.orEmpty(),
                        summary = validation.text,
                        summarizedUpToMessageId = batch.last().id,
                        updatedAt = batch.last().createdAt,
                        revision = (entity?.revision ?: 0) + 1,
                        failureCount = 0,
                        lastError = null,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                )
                // 情绪底色（氛围惯性）：吃下记录器给出的位移，攒够时去要一句"此刻语气"的描写。
                // 它是语气、不是记忆——所以整段 runCatching 掉，失败绝不影响状态更新本身。
                runCatching {
                    updateEmotionalOffset(
                        threadId = threadId,
                        change = validation.shift,
                        transcript = transcript,
                        profile = profile,
                        apiKey = apiKey
                    )
                }
                return Result.success(validation.text)
            }
            lastReason = validation.reason ?: "输出不合格"
            if (attempt <= RETRY_DELAYS_MS.size) delay(RETRY_DELAYS_MS[attempt - 1])
        }

        // 失败：保留旧状态与水位，只累计失败次数和原因（下轮自动重试）。
        runCatching {
            dao.upsertThreadSummary(
                ThreadSummaryEntity(
                    threadId = threadId,
                    role = entity?.role.orEmpty(),
                    summary = entity?.summary.orEmpty(),
                    summarizedUpToMessageId = entity?.summarizedUpToMessageId,
                    updatedAt = entity?.updatedAt ?: 0L,
                    revision = entity?.revision ?: 0,
                    failureCount = (entity?.failureCount ?: 0) + 1,
                    lastError = lastReason,
                    lastAttemptAt = System.currentTimeMillis()
                )
            )
        }
        return Result.failure(IllegalStateException(lastReason))
    }

    /** 把一段对话文本总结成角色状态上下文（不入库），供状态卡等地方使用。 */
    suspend fun summarizeTranscript(transcript: String, currentActivity: String?): String {
        if (transcript.isBlank()) return ""
        val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return ""
        val stateHint = currentActivity?.takeIf(String::isNotBlank)?.let { "她此刻正在：$it。" } ?: ""
        return client.chat(
            profile = profile,
            apiKey = secretCipher.decrypt(requireNotNull(profile.encryptedApiKey)),
            systemPrompt = "把下面这段角色对话总结成 120 字以内的中文要点。$stateHint 总结要结合：用户说了什么（状态/需求/情绪）、你们聊了什么、有什么约定或进行中的事——这些是角色现在应该知道并融入状态的。只输出总结本身。",
            userPrompt = transcript,
            temperature = TEMPERATURE
        ).getOrNull()?.trim()?.takeIf { it.length >= 15 } ?: ""
    }

    /**
     * 供对话 system 注入的情绪底色块；没有可用底色时返回空串。
     *
     * 注入的是**独立一块**，不会并进「当前状态」或「此刻：」——底色是语气，而「此刻」是她的
     * 生活状态（受"对话彻底静默 30 分钟才允许后台改"的约束），两者必须能分别关掉。
     */
    suspend fun emotionalOffsetFor(threadId: String, now: Long = System.currentTimeMillis()): String {
        if (!chatDisplaySettings.preferencesFlow.value.emotionalOffsetEnabled) return ""
        val content = storedOffsetContent(threadId) ?: return ""
        return EmotionalOffsetPolicy.render(EmotionalOffsetPolicy.State.fromJson(content), now)
    }

    private suspend fun storedOffsetContent(threadId: String): String? = runCatching {
        dao.worldStatesByThread(threadId, OFFSET_SCOPE).maxByOrNull { it.updatedAt }?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * 吃下这一轮的位移，并在攒够时去要一句底色描写。
     *
     * 任何一步失败都只是"这一轮底色不更新"：生成失败会保留位移、只推后重试
     * （见 [EmotionalOffsetPolicy.onGenerationFailed]），状态更新本身不受影响。
     */
    private suspend fun updateEmotionalOffset(
        threadId: String,
        change: Int?,
        transcript: String,
        profile: ModelProfileEntity,
        apiKey: String
    ) {
        val now = System.currentTimeMillis()
        if (!chatDisplaySettings.preferencesFlow.value.emotionalOffsetEnabled) return
        val before = EmotionalOffsetPolicy.State.fromJson(storedOffsetContent(threadId))
        // 活跃度口径要对齐上游：最近一小时内的**有效回合数**。这里用"她说了几句话"来数，
        // 而不是消息总条数——用户连发五条，不该让阈值降得比真聊了五轮更快。
        val recentTurns = runCatching {
            dao.recentMessages(threadId, DENSITY_SAMPLE_MESSAGES)
                .count { !it.fromUser && it.createdAt >= now - EmotionalOffsetPolicy.DENSITY_WINDOW_MS }
        }.getOrDefault(0)
        // 这一轮的来源（私聊就是对方角色），用于按来源分桶累计。
        val sourceId = runCatching { dao.participantIds(threadId).firstOrNull() }.getOrNull()

        val state = EmotionalOffsetPolicy.observe(
            state = before,
            change = change,
            sourceId = sourceId,
            phase = PHASE_USER_MESSAGE,
            now = now,
            recentTurns = recentTurns
        )
        val trigger = EmotionalOffsetPolicy.triggerFor(state, now, recentTurns)
        // 既没有新位移、也没攒到该生成：没有任何变化要写，连一行更新都省掉。
        if (trigger == null && state == before) return

        val next = if (trigger == null) {
            state
        } else {
            runCatching { generateOffsetDescription(profile, apiKey, trigger, transcript) }.fold(
                // 返回空 = 侧端没产出；onGenerated 会按失败处理（只推退避时间，不动累计值）。
                onSuccess = { description ->
                    EmotionalOffsetPolicy.onGenerated(state, trigger, description.orEmpty(), now)
                },
                // 调用本身抛异常：同样只推退避时间。
                onFailure = { EmotionalOffsetPolicy.onGenerationFailed(state, now) }
            )
        }
        persistOffset(threadId, next, now)
    }

    private suspend fun persistOffset(threadId: String, state: EmotionalOffsetPolicy.State, now: Long) {
        val characterId = runCatching { dao.participantIds(threadId).firstOrNull() }.getOrNull()
        runCatching {
            dao.upsertWorldState(
                WorldStateEntity(
                    id = "emotional-offset:$threadId",
                    characterId = characterId,
                    threadId = threadId,
                    scope = OFFSET_SCOPE,
                    content = state.toJson(),
                    updatedAt = now
                )
            )
        }
    }

    /**
     * 要一句"她此刻语气"的描写。
     *
     * **只允许模型返回描写**：方向由宿主的累计值决定、强度由宿主公式算，两者都取自 [trigger]，
     * 侧端返回什么都不影响它们（见 [EmotionalOffsetPolicy.onGenerated]）。
     */
    private suspend fun generateOffsetDescription(
        profile: ModelProfileEntity,
        apiKey: String,
        trigger: EmotionalOffsetPolicy.Trigger,
        transcript: String
    ): String? {
        val system = RemotePromptStore.get(OFFSET_PROMPT_KEY) ?: DEFAULT_OFFSET_PROMPT
        val userPrompt = buildString {
            appendLine(
                "这一段对话的氛围正在往「${EmotionalOffsetPolicy.directionWord(trigger.direction)}」的方向走" +
                    "（累计位移 ${trigger.value}，阈值 ${trigger.threshold}，" +
                    "强度 ${EmotionalOffsetPolicy.formatIntensity(trigger.intensity)}/${EmotionalOffsetPolicy.MAX_INTENSITY}）。"
            )
            appendLine("最近对话：")
            append(transcript.take(OFFSET_TRANSCRIPT_CHARS))
        }
        return client.chat(
            profile = profile,
            apiKey = apiKey,
            systemPrompt = system,
            userPrompt = userPrompt,
            temperature = OFFSET_TEMPERATURE
        ).getOrNull()
            ?.let(EmotionalOffsetPolicy::cleanDescription)
            ?.takeIf { it.isNotBlank() }
    }
}
