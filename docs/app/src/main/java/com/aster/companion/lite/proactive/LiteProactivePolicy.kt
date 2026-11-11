package com.aster.companion.lite.proactive

import java.time.Instant
import java.time.ZoneId

/** 主动消息的可调参数（分册《主动消息计划书》§9 参数表默认值）。 */
data class LiteProactiveSettings(
    /** 最后一条消息之后静默多久算 Idle。 */
    val idleQuietMs: Long = 2 * 60_000L,
    /** Idle 之后再等多久才到「余韵」时点。 */
    val afterglowDelayMs: Long = 3 * 60_000L,
    /** 单角色冷却。 */
    val perCharacterCooldownMs: Long = 20 * 60_000L,
    /**
     * 联系温度档位（见 [com.aster.companion.domain.CompanionAgencyPolicy.ContactMode]）。
     *
     * 默认 `STRICT` = 与改动前完全一致；`NATURAL` / `BALANCED` 会放宽意愿阈值与最小间隔，
     * 但环境硬门（睡着 / 设备不可用 / 私人内容+非私密场合）在三档下都不放宽。
     */
    val contactMode: com.aster.companion.domain.CompanionAgencyPolicy.ContactMode =
        com.aster.companion.domain.CompanionAgencyPolicy.ContactMode.STRICT,
    /**
     * 主动性节律（见 [com.aster.companion.domain.UrgePolicy]）。
     *
     * **默认关闭 = 完全保留旧行为**（"距上次开口满固定冷却就允许探一次"）。打开之后，
     * "多久之后可以考虑开口"改由活跃度密度 + 随机决定；这道节律只改**想不想**，
     * **不能**放宽任何硬门（睡着 / 设备 / 场合 / 24 小时配额 / 最小间隔）。
     */
    val urgeEnabled: Boolean = false,
    /** 节律档位：她本来有多主动。 */
    val urgeFrequency: com.aster.companion.domain.UrgePolicy.Frequency =
        com.aster.companion.domain.UrgePolicy.Frequency.MEDIUM,
    /**
     * 世界事件播种（[com.aster.companion.domain.WorldSeedingPolicy]）。
     *
     * **默认关闭**：它要定期跑模型。打开之后她"外面"会偶尔发生点小事（修路、降温、邻居动静），
     * 她会自然地知道、也可以提；但**怎么反应由她决定**，不必每件都提。
     */
    val worldSeedingEnabled: Boolean = false,
    /** 每拍真正落库/通知的上限。 */
    val perTickLimit: Int = 2,
    /** 全局每日主动上限。 */
    val dailyGlobalLimit: Int = 24,
    /** 单条正文长度上限（字）。 */
    val maxMessageChars: Int = 60,
    /** 超过这个长度视为模型跑飞，直接丢弃。 */
    val hardMaxMessageChars: Int = 120,
    /** 安静时段（本地时间分钟数），默认 23:30–07:30。 */
    val quietHoursStartMinute: Int = 23 * 60 + 30,
    val quietHoursEndMinute: Int = 7 * 60 + 30,
    /** 每拍最多并行探活的槽数。 */
    val maxParallelProbes: Int = 4,
    /** 两段式（内心 → 正文）时每角色占 2 槽。 */
    val twoStage: Boolean = true,
    /** 长空闲稀疏心跳（分册 §9：默认 2 小时；设为 0 关闭）。 */
    val idleHeartbeatMs: Long = 2 * 60 * 60_000L,
    /** 同拍朋友圈动态的冷却：同一角色两次自发动态的最小间隔（分册 §6.3）。 */
    val momentCooldownMs: Long = 6 * 60 * 60_000L,
    /** 是否允许同拍朋友圈动态。 */
    val momentsEnabled: Boolean = true,
    /** 是否允许主动给用户发照片（分册 §3 二期可选；出图独占显存，所以默认克制）。 */
    val photoEnabled: Boolean = true,
    /** 主动发照片的冷却：同一角色两次主动出图的最小间隔。 */
    val photoCooldownMs: Long = 3 * 60 * 60_000L
)

/** 一个私聊线程参与主动判定时的必需状态。 */
data class LiteProactiveThreadSnapshot(
    val threadId: String,
    val characterId: String,
    /** 该线程最后一条消息的时间。 */
    val lastMessageAt: Long,
    /** 该角色上次主动发出的时间，0 表示从未。 */
    val lastProactiveAt: Long = 0L,
    /** 该角色今天已经主动发过几条。 */
    val todayProactiveCount: Int = 0,
    /** 用户当前正在这个线程里活动（有输入或正在回复）。 */
    val active: Boolean = false,
    /** 这个 idle 窗口是否已经探活过。 */
    val probedForCurrentIdle: Boolean = false,
    /** 该线程上次探活的时间；长空闲心跳据此放开同一 idle 窗口的重复探活。 */
    val lastProbeAt: Long = 0L,
    /** 该角色此刻在自己作息的睡眠区间内（按角色作息的安静时段，分册 §9）。 */
    val sleeping: Boolean = false,
    /**
     * 他明确说过「我去睡了 / 我要忙了」这类话，她的主动联系被按下（见 [com.aster.companion.proactive.SystemModeState]）。
     * 这种角色这一拍**完全不参与**：不探活、不主动发照片、不主动发动态。
     */
    val paused: Boolean = false,
    /**
     * 她自己那条「主动联系」开关还开着吗（会话管理里可以按角色关掉）。
     * 关掉 = 这一拍同样完全不参与，用户看到的就是"关了就是关了"。
     */
    val proactiveEnabled: Boolean = true,
    /**
     * 可打扰性裁决给出的结论：`true` = 这一拍不该打扰。
     *
     * 默认 `false` = 不额外限制，所以**既有调用方与既有测试的行为完全不变**；
     * 调度器会按 [com.aster.companion.domain.CompanionAgencyPolicy] 的"环境层 + 候选层"
     * 两段判定填入真实值。
     */
    val agencyBlocked: Boolean = false,
    /**
     * 主动性节律的结论：`true` = 她这会儿还不想开口。
     *
     * 与 [agencyBlocked] 是**与**关系：想说话也得先过得了那几道硬门。
     * 默认 `false` = 不额外限制（节律未启用时调度器就填这个值）。
     */
    val urgeBlocked: Boolean = false
)

/**
 * 主动消息的纯规则层（分册 §4 状态机、§5.3 让路、§5.4 限流）。
 * 这里不碰数据库、不碰网络，只回答「现在能不能、该不该让某个角色开口」。
 */
object LiteProactivePolicy {

    /**
     * 这一拍**完全不该参与**的角色：他说了「我去睡了」把她按下了（[LiteProactiveThreadSnapshot.paused]），
     * 或者她自己那条「主动联系」被关掉了（[LiteProactiveThreadSnapshot.proactiveEnabled]）。
     *
     * 回归背景：这两个开关原本只有"事件驱动"那条路（`AgentTurnCoordinator`）在看，
     * **Lite 主动陪伴这条路一个都没看** —— 于是说完晚安她照样来找你、
     * 在会话管理里关掉「主动联系」也照样来找你。
     * 暂停越过一夜会自动放开（[com.aster.companion.proactive.ProactivePauseExpiry]），不会永久静音。
     */
    fun filterEligible(snapshots: List<LiteProactiveThreadSnapshot>): List<LiteProactiveThreadSnapshot> =
        snapshots.filter { it.proactiveEnabled && !it.paused }

    /** 最后一条消息后静默够久才算 Idle；返回 Idle 时刻。 */
    fun idleAtOrNull(lastMessageAt: Long, now: Long, settings: LiteProactiveSettings): Long? {
        if (lastMessageAt <= 0L) return null
        val idleAt = lastMessageAt + settings.idleQuietMs
        return idleAt.takeIf { now >= idleAt }
    }

    /** 余韵时点 = Idle 时刻 + 5 分钟。 */
    fun afterglowAtOrNull(lastMessageAt: Long, now: Long, settings: LiteProactiveSettings): Long? =
        idleAtOrNull(lastMessageAt, now, settings)?.let { idleAt ->
            (idleAt + settings.afterglowDelayMs).takeIf { now >= it }
        }

    fun inQuietHours(now: Long, settings: LiteProactiveSettings, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val minute = Instant.ofEpochMilli(now).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
        val start = settings.quietHoursStartMinute
        val end = settings.quietHoursEndMinute
        return if (start <= end) minute in start until end else minute >= start || minute < end
    }

    fun isCoolingDown(snapshot: LiteProactiveThreadSnapshot, now: Long, settings: LiteProactiveSettings): Boolean =
        snapshot.lastProactiveAt > 0L && now - snapshot.lastProactiveAt < settings.perCharacterCooldownMs

    fun isWithinDailyLimit(snapshot: LiteProactiveThreadSnapshot, settings: LiteProactiveSettings): Boolean =
        snapshot.todayProactiveCount < settings.dailyGlobalLimit

    /**
     * 单个线程此刻能不能探活。
     * [imageBusy] 为真（出图中）时整拍让路（分册 §5.3）。
     */
    fun canProbe(
        snapshot: LiteProactiveThreadSnapshot,
        now: Long,
        settings: LiteProactiveSettings,
        imageBusy: Boolean,
        dailyTotal: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (imageBusy) return false
        if (snapshot.active) return false
        // 可打扰性：环境层（睡着 / 手头忙 / 安静时段 / 他正在回复 / 设备不可用）与候选层
        // （场合、24 小时配额、最小间隔、意愿）两段判定的结论。判据收拢在 CompanionAgencyPolicy。
        if (snapshot.agencyBlocked) return false
        // 主动性节律：她"还想再等等"。这一条只延后、不放宽上面那些硬门。
        if (snapshot.urgeBlocked) return false
        // 他说过「我去睡了 / 我要忙了」→ 主动联系被按下；或者用户把她的「主动联系」关了：都不探活。
        // （调用方也会用 [filterEligible] 先把这些角色滤掉，这里是第二道闸，免得将来新加的入口漏掉。）
        if (snapshot.paused || !snapshot.proactiveEnabled) return false
        // 角色自己作息的睡眠区间内不打扰（分册 §9：按角色作息，比全局安静时段更细）。
        if (snapshot.sleeping) return false
        // 同一个 idle 窗口默认只探一次；但长空闲心跳到期后允许再探一次（分册 §9）。
        if (snapshot.probedForCurrentIdle && !heartbeatDue(snapshot, now, settings)) return false
        if (afterglowAtOrNull(snapshot.lastMessageAt, now, settings) == null) return false
        if (isCoolingDown(snapshot, now, settings)) return false
        if (!isWithinDailyLimit(snapshot, settings)) return false
        if (dailyTotal >= settings.dailyGlobalLimit) return false
        if (inQuietHours(now, settings, zone)) return false
        return true
    }

    /**
     * 长空闲心跳是否到期：同一 idle 窗口里距上次探活已超过心跳间隔。
     * [LiteProactiveSettings.idleHeartbeatMs] 设为 0 即关闭心跳（回到「一个 idle 只探一次」）。
     */
    fun heartbeatDue(snapshot: LiteProactiveThreadSnapshot, now: Long, settings: LiteProactiveSettings): Boolean {
        if (settings.idleHeartbeatMs <= 0L) return false
        if (snapshot.lastProbeAt <= 0L) return false
        return now - snapshot.lastProbeAt >= settings.idleHeartbeatMs
    }

    /**
     * 同拍朋友圈动态是否可行（分册 §6.3）：总开关关掉、出图中、安静时段、冷却未过都不发。
     */
    fun canShareMoment(
        lastMomentAt: Long,
        now: Long,
        settings: LiteProactiveSettings,
        imageBusy: Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (!settings.momentsEnabled) return false
        if (imageBusy) return false
        if (inQuietHours(now, settings, zone)) return false
        if (lastMomentAt > 0L && now - lastMomentAt < settings.momentCooldownMs) return false
        return true
    }

    /**
     * 每拍候选排序（分册 §5.1）：刚 Idle 的线程优先，其次是冷却早、距上次主动久、今天发得少的。
     */
    fun rankCandidates(
        snapshots: List<LiteProactiveThreadSnapshot>,
        now: Long,
        settings: LiteProactiveSettings
    ): List<LiteProactiveThreadSnapshot> = snapshots.sortedWith(
        compareByDescending<LiteProactiveThreadSnapshot> { it.lastMessageAt }
            .thenBy { it.lastProactiveAt }
            .thenBy { it.todayProactiveCount }
    )

    /** 本拍实际派发的角色数：两段式时每角色占 2 槽。 */
    fun parallelSlots(settings: LiteProactiveSettings): Int =
        if (settings.twoStage) maxOf(1, settings.maxParallelProbes / 2) else settings.maxParallelProbes

    /**
     * 本拍要探活的候选：先按让路规则过滤，再排序，最后截到槽位上限（分册 §5）。
     * 决策留在纯函数里，调度器只负责执行。
     */
    fun planProbes(
        snapshots: List<LiteProactiveThreadSnapshot>,
        now: Long,
        settings: LiteProactiveSettings,
        imageBusy: Boolean,
        dailyTotal: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<LiteProactiveThreadSnapshot> = rankCandidates(
        snapshots.filter { snapshot ->
            canProbe(snapshot, now, settings, imageBusy, dailyTotal, zone)
        },
        now,
        settings
    ).take(parallelSlots(settings))

    /** 探活结果里最多落库几条（分册 §5.4）：多出来的直接丢弃，不刷屏。 */
    fun persistBudget(probeCount: Int, settings: LiteProactiveSettings): Int =
        probeCount.coerceAtMost(settings.perTickLimit).coerceAtLeast(0)
}
