package com.aster.companion.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 可打扰性（Agency Window）：回答两个不同层次的问题。
 *
 * - **[evaluate]：环境层**——"此刻这个环境的客观条件是什么"（她手头忙不忙、什么场合、设备能不能用），
 *   产出可落库的 [Window]；
 * - **[decide]：候选层**——"这一个具体的联系候选该不该发"（来源是什么、内容是普通还是私人、
 *   意愿够不够、距上次多久、今天发了几条），产出 [Decision]。
 *
 * 分成两层不是洁癖：环境层可以缓存/落库（一段时间内不变），候选层每次都要重算
 * （同一个环境里，一条"承诺"可以突破，一条"随口想聊聊"不行）。
 *
 * ## 为什么要落库
 *
 * 主动联系的"账"（上次什么时候开的口、今天已经发了几条）原本只活在内存 map 里，
 * **进程一重启就归零**——于是重启后冷却和每日配额全部失效，她可以立刻再刷一轮。
 * 这里把账并进 [Window.contactLog]（最近 [CONTACT_LOG_LIMIT] 条），重启即恢复。
 *
 * ## 硬门矩阵（环境）
 *
 * - 设备 `UNAVAILABLE` / `LIMITED` → 不立即发送；
 * - 日程 / 手头 `OVERLOADED` → 不立即发送；
 * - **私人内容 + 场合不私密** → 不立即发送；
 * - 普通 `OCCUPIED` → 只有**承诺**或**实际进展**可以突破；
 * - 普通联系受最小间隔限制，而**承诺可绕过间隔**；
 * - 意愿值（若候选带）仍须达阈值。
 *
 * ## 联系温度三档（[ContactMode]）
 *
 * | 档位 | 意愿阈值 | 最小间隔 | 说明 |
 * |---|---|---|---|
 * | `STRICT` | 配置值（默认 0.65） | 配置值（默认 60 分钟） | 现状，最克制 |
 * | `NATURAL` | 取更小者（默认 0.25） | 取更小者（默认 30 分钟） | 追加"想念 / 好奇 / 想分享"为合法动机 |
 * | `BALANCED` | 同 NATURAL | 同 NATURAL | 教义同 NATURAL，但要求节制 |
 *
 * 三档**共有**的保护一律不放宽：环境硬门、突发间隔优先、每参与者 24 小时上限。
 *
 * ## 注意（上游同款脆弱点）
 *
 * [Window.contactLog] 只保留最近 [CONTACT_LOG_LIMIT] 条，而 24 小时配额是**从这份日志数出来的**。
 * 同一参与者 24 小时内活动极频繁时，旧条目会被挤出窗口，配额判定会**偏松**。这是刻意保留的
 * 存储上限，不是 bug；真要收紧就得改成独立计数器（那时必须走一次 DB 迁移）。
 */
object CompanionAgencyPolicy {

    /** 联系审计日志保留条数。 */
    const val CONTACT_LOG_LIMIT = 20

    /** 每日配额的计数窗口（滑动 24 小时）。 */
    const val CONTACT_WINDOW_MS = 86_400_000L

    /** 判断不出来时的最短重查间隔。 */
    const val MIN_RECHECK_MS = 5 * 60_000L

    /** 窗口默认有效期（上游 maxWindowMinutes 默认 240 分钟）。 */
    const val DEFAULT_MAX_WINDOW_MINUTES = 240L

    /** 落库时用的 scope 名。 */
    const val SCOPE = "AGENCY_WINDOW"

    /** world state 主键（`LIFE_PLAN` 用的是 `life-plan:<characterId>`，这里按会话存）。 */
    fun windowId(threadId: String): String = "agency-window:$threadId"

    /** 手头占用度。 */
    enum class ActivityLoad {
        /** 手上没事，愿意说话。 */
        FREE,

        /** 手头有件事在做（有她自报的时长），顶多一句。 */
        OCCUPIED,

        /** 完全不该打扰：睡着 / 被按下 / 安静时段 / 用户正在这个会话里回复。 */
        OVERLOADED
    }

    /**
     * 她此刻的场合。
     *
     * 只从**已有事实**推：群会话一律算共同场合；地点 id 以 `home` 开头算自己家；
     * `outside.*` 算公开场合；推不出来时保守取 [PRIVATE]（私聊语境）。
     */
    enum class PrivacySetting { PRIVATE, SHARED, PUBLIC }

    /** 这台设备此刻能不能支撑一次主动动作。 */
    enum class DeviceAccess {
        AVAILABLE,

        /** 部分占用（例如显存被出图占满）：能说话，但不适合再发起重动作。 */
        LIMITED,

        /** 连对话模型都不可用：什么都别发。 */
        UNAVAILABLE
    }

    /** 联系温度档位。 */
    enum class ContactMode { STRICT, NATURAL, BALANCED }

    /**
     * 这条联系候选是从哪来的。
     *
     * 来源决定它能不能突破某些门：**承诺**可以绕过最小间隔，`PROMISE` 与 `PRACTICAL_UPDATE`
     * 可以在她手头有事时仍然发出。
     */
    enum class ContactOrigin {
        /** 生活剧本自己产生的理由（"她想起一件事"）。 */
        LIFE_EVENT,

        /** 之前答应过对方的事到点了。 */
        PROMISE,

        /** 有实际进展要同步（事情办完了、东西到了）。 */
        PRACTICAL_UPDATE,

        /** 关系层面的后续跟进。 */
        RELATIONSHIP_FOLLOW_UP
    }

    /** 内容性质：普通闲聊 vs 私人内容（私人内容不能在非私密场合发）。 */
    enum class Disclosure { ORDINARY, PERSONAL }

    /** 裁决结果。 */
    enum class Outcome {
        /** 现在就可以发。 */
        SEND_NOW,

        /** 收起来，到 [Decision.notBefore] 之后再重查。 */
        RECHECK_LATER,

        /** 这条就放弃（过期、超配额、意愿不足、场合不允许）。 */
        LET_GO
    }

    /** 一条联系审计记录。**不进模型上下文**，只用于计数与排查。 */
    @Serializable
    data class ContactLogEntry(
        val participantId: String,
        val at: Long,
        val origin: String = "",
        val reason: String = ""
    )

    /**
     * 环境层判断的完整结果，也是**落库的那个东西**。
     *
     * 所有字段都有默认值：字段以后增加时旧数据也读得进来。
     */
    @Serializable
    data class Window(
        val activityLoad: ActivityLoad = ActivityLoad.FREE,
        val privacy: PrivacySetting = PrivacySetting.PRIVATE,
        val deviceAccess: DeviceAccess = DeviceAccess.AVAILABLE,
        /** 下次适合开口的时刻；null = 环境上现在就可以。 */
        val nextOpportunityAt: Long? = null,
        /** 这个判断的有效期。 */
        val validUntil: Long = 0L,
        /** 判断依据（人话，用于排查与状态卡）。 */
        val basis: String = "",
        /** 支撑这个判断的原始消息 id（可回溯"为什么是这个状态"）。 */
        val sourceEntryIds: List<String> = emptyList(),
        /** 最近的联系审计（最多 [CONTACT_LOG_LIMIT] 条）。 */
        val contactLog: List<ContactLogEntry> = emptyList(),
        val updatedAt: Long = 0L
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun fromJson(raw: String?): Window {
                if (raw.isNullOrBlank()) return Window()
                return runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(Window())
            }
        }
    }

    /** 联系温度的配置面（对齐上游 AgencyConfig 的那几项）。 */
    data class ContactPolicyConfig(
        val mode: ContactMode = ContactMode.STRICT,
        /** 严格档的最小间隔。 */
        val minimumIntervalMs: Long = 60 * 60_000L,
        /** natural / balanced 档的最小间隔（**仅当小于严格档时才生效**）。 */
        val naturalMinimumIntervalMs: Long = 30 * 60_000L,
        /** 严格档的意愿阈值。 */
        val willingnessThreshold: Double = 0.65,
        /** natural / balanced 档的意愿阈值（取更小者）。 */
        val naturalWillingnessThreshold: Double = 0.25,
        /** 每参与者 24 小时上限；0 = 不限。 */
        val dailyCap: Int = 3
    ) {
        /** 当前档位真正生效的最小间隔。 */
        val effectiveIntervalMs: Long
            get() = when (mode) {
                ContactMode.STRICT -> minimumIntervalMs
                ContactMode.NATURAL, ContactMode.BALANCED ->
                    minOf(minimumIntervalMs, naturalMinimumIntervalMs)
            }

        /** 当前档位真正生效的意愿阈值。 */
        val effectiveWillingnessThreshold: Double
            get() = when (mode) {
                ContactMode.STRICT -> willingnessThreshold
                ContactMode.NATURAL, ContactMode.BALANCED ->
                    minOf(willingnessThreshold, naturalWillingnessThreshold)
            }
    }

    /** 一个具体的联系候选。 */
    data class ContactDraft(
        val participantId: String,
        val origin: ContactOrigin,
        val motive: String = "",
        val disclosure: Disclosure = Disclosure.ORDINARY,
        val sourceEntryIds: List<String> = emptyList(),
        /** 意愿值 0..1；null = 这条来源不产生意愿（例如承诺），跳过意愿闸。 */
        val willingness: Double? = null,
        /** 最早可以发的时刻（"到点了才提醒"）。 */
        val notBefore: Long? = null,
        /** 过了这个时刻这条候选就作废。 */
        val expiresAt: Long? = null
    )

    /** 裁决结果。[allowed] 是给调用方的唯一开关。 */
    data class Decision(
        val outcome: Outcome,
        val reason: String,
        val notBefore: Long? = null
    ) {
        val allowed: Boolean get() = outcome == Outcome.SEND_NOW
    }

    /** 环境层的事实（只是事实，不含账）。 */
    data class Input(
        val now: Long,
        /** 她此刻在自己作息的睡眠区间内。 */
        val sleeping: Boolean = false,
        /** 睡到几点（0 = 没在睡）。 */
        val sleepUntil: Long = 0L,
        /**
         * 她手头这件事**她自己说的**下次可问时间（`LIFE_PLAN` 的 reviewAt；0 = 没有计划）。
         * 它没到点 = 她正忙着手上的事。
         */
        val busyUntil: Long = 0L,
        val locationId: String? = null,
        val conversationType: ConversationType? = null,
        /** 全局安静时段。 */
        val quietHours: Boolean = false,
        /** 他说过「我去睡了 / 我要忙了」，主动联系被按下。 */
        val paused: Boolean = false,
        /** 用户此刻正在这个会话里回复。 */
        val userReplying: Boolean = false,
        val deviceAccess: DeviceAccess = DeviceAccess.AVAILABLE,
        /** 支撑这次判断的原始消息 id。 */
        val sourceEntryIds: List<String> = emptyList(),
        /** 窗口有效期（分钟）。 */
        val maxWindowMinutes: Long = DEFAULT_MAX_WINDOW_MINUTES
    )

    /** 场合推断：群 = 共同场合；`home*` = 自己家；`outside.*` = 公开。 */
    fun privacyOf(locationId: String?, conversationType: ConversationType?): PrivacySetting = when {
        conversationType == ConversationType.GROUP -> PrivacySetting.SHARED
        locationId.isNullOrBlank() -> PrivacySetting.PRIVATE
        locationId.startsWith("home") -> PrivacySetting.PRIVATE
        locationId.startsWith("outside") -> PrivacySetting.PUBLIC
        else -> PrivacySetting.PRIVATE
    }

    /** 手头占用度：睡着 / 被按下 / 安静时段 / 用户正在回复，都算完全不该打扰。 */
    fun loadOf(input: Input): ActivityLoad = when {
        input.sleeping -> ActivityLoad.OVERLOADED
        input.paused -> ActivityLoad.OVERLOADED
        input.userReplying -> ActivityLoad.OVERLOADED
        input.quietHours -> ActivityLoad.OVERLOADED
        input.now < input.busyUntil -> ActivityLoad.OCCUPIED
        else -> ActivityLoad.FREE
    }

    /**
     * 算一个环境窗口。[previous] 是上次落库的那一份（取里面的联系日志）。
     */
    fun evaluate(input: Input, previous: Window = Window()): Window {
        val load = loadOf(input)
        val privacy = privacyOf(input.locationId, input.conversationType)
        val device = input.deviceAccess
        val now = input.now

        val blockedByEnvironment = load == ActivityLoad.OVERLOADED || device != DeviceAccess.AVAILABLE
        val nextOpportunityAt = when {
            !blockedByEnvironment -> null
            // 睡着 / 被按下 → 给出一个明确的"最早什么时候"
            input.sleeping && input.sleepUntil > now -> input.sleepUntil
            else -> now + MIN_RECHECK_MS
        }

        return Window(
            activityLoad = load,
            privacy = privacy,
            deviceAccess = device,
            nextOpportunityAt = nextOpportunityAt,
            validUntil = now + input.maxWindowMinutes.coerceAtLeast(1L) * 60_000L,
            basis = buildBasis(input, load, privacy, device),
            sourceEntryIds = input.sourceEntryIds,
            contactLog = previous.contactLog,
            updatedAt = now
        )
    }

    /** 数出某个参与者在窗口内的联系次数（[Window.contactLog] 是唯一来源）。 */
    fun countContactsInWindow(
        log: List<ContactLogEntry>,
        participantId: String,
        now: Long,
        windowMs: Long = CONTACT_WINDOW_MS
    ): Int = log.count { it.participantId == participantId && now - it.at < windowMs }

    /** 某个参与者上次开口的时刻（0 = 从没）。 */
    fun lastContactAt(log: List<ContactLogEntry>, participantId: String): Long =
        log.filter { it.participantId == participantId }.maxOfOrNull { it.at } ?: 0L

    /**
     * 候选层裁决：这一个具体候选现在能不能发。
     *
     * 判定顺序刻意从"最不可协商"到"最可协商"：过期 → 环境硬门 → 场合 → 配额 → 间隔 → 意愿。
     */
    fun decide(
        window: Window,
        draft: ContactDraft,
        config: ContactPolicyConfig = ContactPolicyConfig(),
        now: Long
    ): Decision {
        // 1) 过期：这条候选已经没意义了。
        val expiresAt = draft.expiresAt
        if (expiresAt != null && now > expiresAt) {
            return Decision(Outcome.LET_GO, "候选已过期")
        }
        // 2) 还没到点：等她/它到点再来。
        val notBefore = draft.notBefore
        if (notBefore != null && now < notBefore) {
            return Decision(Outcome.RECHECK_LATER, "还没到该开口的时候", notBefore)
        }
        // 3) 环境硬门：这三样在任何档位、任何来源下都不放宽。
        if (window.activityLoad == ActivityLoad.OVERLOADED) {
            return Decision(Outcome.RECHECK_LATER, "环境不该打扰：${window.basis}", window.nextOpportunityAt)
        }
        if (window.deviceAccess == DeviceAccess.UNAVAILABLE) {
            return Decision(Outcome.RECHECK_LATER, "设备不可用（模型跑不起来）", window.nextOpportunityAt)
        }
        if (window.deviceAccess == DeviceAccess.LIMITED) {
            return Decision(Outcome.RECHECK_LATER, "设备受限（显存被占），不适合再发起", window.nextOpportunityAt)
        }
        // 4) 私人内容不能在非私密场合发。
        if (draft.disclosure == Disclosure.PERSONAL && window.privacy != PrivacySetting.PRIVATE) {
            return Decision(Outcome.LET_GO, "私人内容不能在${privacyWord(window.privacy)}发")
        }
        // 5) 每参与者 24 小时上限。
        val used = countContactsInWindow(window.contactLog, draft.participantId, now)
        if (config.dailyCap > 0 && used >= config.dailyCap) {
            return Decision(Outcome.LET_GO, "24 小时内已经主动过 $used 次，到上限")
        }
        // 6) 间隔：承诺可以绕过，其余必须等够。
        val last = lastContactAt(window.contactLog, draft.participantId)
        val interval = config.effectiveIntervalMs
        val earliest = if (last > 0L) last + interval else 0L
        if (draft.origin != ContactOrigin.PROMISE && now < earliest) {
            val waitMinutes = (earliest - now) / 60_000L
            return Decision(Outcome.RECHECK_LATER, "距上次开口不够（还差 $waitMinutes 分钟）", earliest)
        }
        // 7) 手头有事时，只有承诺与实际进展能突破。
        if (window.activityLoad == ActivityLoad.OCCUPIED &&
            draft.origin != ContactOrigin.PROMISE &&
            draft.origin != ContactOrigin.PRACTICAL_UPDATE
        ) {
            return Decision(Outcome.RECHECK_LATER, "她手头有事，这类联系先不打扰", window.nextOpportunityAt)
        }
        // 8) 意愿闸：只有候选带了意愿值才判（承诺类不产生意愿）。
        val willingness = draft.willingness
        if (willingness != null && willingness < config.effectiveWillingnessThreshold) {
            return Decision(Outcome.LET_GO, "意愿 $willingness 低于阈值 ${config.effectiveWillingnessThreshold}")
        }
        return Decision(Outcome.SEND_NOW, "可以开口")
    }

    /**
     * 记账：她真的开口了。日志保留最近 [CONTACT_LOG_LIMIT] 条。
     */
    fun recordSpoke(window: Window, entry: ContactLogEntry): Window = window.copy(
        contactLog = (window.contactLog + entry).takeLast(CONTACT_LOG_LIMIT),
        updatedAt = entry.at
    )

    private fun privacyWord(privacy: PrivacySetting): String = when (privacy) {
        PrivacySetting.PRIVATE -> "自己家"
        PrivacySetting.SHARED -> "共同场合"
        PrivacySetting.PUBLIC -> "公开场合"
    }

    private fun buildBasis(
        input: Input,
        load: ActivityLoad,
        privacy: PrivacySetting,
        device: DeviceAccess
    ): String = buildString {
        append(
            when (load) {
                ActivityLoad.FREE -> "她手上没事"
                ActivityLoad.OCCUPIED -> "她手头的事要做到 ${input.busyUntil}"
                ActivityLoad.OVERLOADED -> when {
                    input.sleeping -> "她在睡"
                    input.paused -> "他把她按下了"
                    input.userReplying -> "他正在这个会话里回复"
                    else -> "全局安静时段"
                }
            }
        )
        append("；场合=")
        append(privacyWord(privacy))
        if (device != DeviceAccess.AVAILABLE) {
            append("；设备=")
            append(if (device == DeviceAccess.LIMITED) "受限" else "不可用")
        }
    }
}
