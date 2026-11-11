package com.aster.companion.proactive

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Persistent owner-controlled master switch for all proactive sensing. */
@Singleton
class SystemModeState @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("tongpin_system_mode", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    private val _proactiveCharacterIds = MutableStateFlow(preferences.getStringSet(KEY_PROACTIVE_CHARACTER_IDS, emptySet()).orEmpty())
    private val _proactiveSelectionConfigured = MutableStateFlow(preferences.getBoolean(KEY_PROACTIVE_SELECTION_CONFIGURED, false))
    private val _pausedCharacterIds = MutableStateFlow(preferences.getStringSet(KEY_PAUSED_CHARACTER_IDS, emptySet()).orEmpty())

    val enabled: Boolean get() = _enabled.value
    val enabledFlow: StateFlow<Boolean> = _enabled
    /** An empty set intentionally means every private character may decide for incoming events. */
    val proactiveCharacterIds: Set<String> get() = _proactiveCharacterIds.value
    val proactiveCharacterIdsFlow: StateFlow<Set<String>> = _proactiveCharacterIds
    val proactiveSelectionConfigured: Boolean get() = _proactiveSelectionConfigured.value
    val proactiveSelectionConfiguredFlow: StateFlow<Boolean> = _proactiveSelectionConfigured
    val pausedCharacterIds: Set<String> get() = _pausedCharacterIds.value
    val pausedCharacterIdsFlow: StateFlow<Set<String>> = _pausedCharacterIds

    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    /** Enables companion sensing on first use; a user-initiated stop remains respected. */
    fun enableOnFirstUse() {
        if (!preferences.contains(KEY_ENABLED)) setEnabled(true)
    }

    fun setProactiveCharacterIds(characterIds: Set<String>) {
        preferences.edit().putStringSet(KEY_PROACTIVE_CHARACTER_IDS, characterIds).apply()
        _proactiveCharacterIds.value = characterIds
        preferences.edit().putBoolean(KEY_PROACTIVE_SELECTION_CONFIGURED, true).apply()
        _proactiveSelectionConfigured.value = true
    }

    /**
     * 按下主动联系（用户说「我去睡了」这类）。
     *
     * **同时记下按下的时间**：这个暂停原本只靠「我醒了 / 有空了 / 忙完了」那串词才会解除，
     * 用户第二天只说一句「早」的话她会**一直**不主动找他 —— 看起来就像坏了。
     * 有了时间戳，[resumeExpiredPauses] 就能在一夜过去之后自动放开。
     */
    fun pauseProactiveFor(characterIds: Collection<String>, now: Long = System.currentTimeMillis()) {
        val ids = characterIds.distinct()
        updatePausedCharacters { it + ids }
        preferences.edit().apply {
            ids.forEach { putLong(pausedAtKey(it), now) }
        }.apply()
        Log.i(TAG, "Proactive contact paused for ${ids.size} character(s)")
    }

    fun resumeProactiveFor(characterIds: Collection<String>) {
        val ids = characterIds.distinct()
        updatePausedCharacters { it - ids.toSet() }
        preferences.edit().apply {
            ids.forEach { remove(pausedAtKey(it)) }
        }.apply()
        Log.i(TAG, "Proactive contact resumed for ${ids.size} character(s)")
    }

    /** 这个角色是什么时候被按下主动的（0 = 没有暂停记录）。 */
    fun pausedAt(characterId: String): Long = preferences.getLong(pausedAtKey(characterId), 0L)

    /**
     * 放开「已经过去太久」的暂停：越过一夜（[ProactivePauseExpiry.MAX_PAUSE_MS]）就当她早就醒了。
     * 返回这次放开的角色 id。
     */
    fun resumeExpiredPauses(now: Long = System.currentTimeMillis()): Set<String> {
        val expired = _pausedCharacterIds.value
            .filter { id -> ProactivePauseExpiry.isExpired(pausedAt(id), now) }
            .toSet()
        if (expired.isNotEmpty()) resumeProactiveFor(expired)
        return expired
    }

    fun isProactivePaused(characterId: String): Boolean = characterId in _pausedCharacterIds.value

    fun transferSnapshot(): SystemModeTransferSnapshot = SystemModeTransferSnapshot(
        enabled = enabled,
        proactiveCharacterIds = proactiveCharacterIds,
        selectionConfigured = proactiveSelectionConfigured,
        pausedCharacterIds = pausedCharacterIds
    )

    fun restoreTransferSnapshot(snapshot: SystemModeTransferSnapshot) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, snapshot.enabled)
            .putStringSet(KEY_PROACTIVE_CHARACTER_IDS, snapshot.proactiveCharacterIds)
            .putBoolean(KEY_PROACTIVE_SELECTION_CONFIGURED, snapshot.selectionConfigured)
            .putStringSet(KEY_PAUSED_CHARACTER_IDS, snapshot.pausedCharacterIds)
            .apply()
        _enabled.value = snapshot.enabled
        _proactiveCharacterIds.value = snapshot.proactiveCharacterIds
        _proactiveSelectionConfigured.value = snapshot.selectionConfigured
        _pausedCharacterIds.value = snapshot.pausedCharacterIds
    }

    private fun updatePausedCharacters(transform: (Set<String>) -> Set<String>) {
        val updated = transform(_pausedCharacterIds.value)
        preferences.edit().putStringSet(KEY_PAUSED_CHARACTER_IDS, updated).apply()
        _pausedCharacterIds.value = updated
    }

    private fun pausedAtKey(characterId: String) = "$KEY_PAUSED_AT_PREFIX$characterId"

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PROACTIVE_CHARACTER_IDS = "proactive_character_ids"
        const val KEY_PROACTIVE_SELECTION_CONFIGURED = "proactive_selection_configured"
        const val KEY_PAUSED_CHARACTER_IDS = "paused_character_ids"
        const val KEY_PAUSED_AT_PREFIX = "paused_at_"
        const val TAG = "TongpinAvailability"
    }
}

/**
 * 「主动联系被按下」这件事什么时候该自动放开。
 *
 * 暂停的语义是"他现在不方便，别打扰"，而不是"永远别理他"。一夜过去（默认 8 小时）就当他早就醒了：
 * 否则用户说完晚安、第二天只说一句「早」，她会因为没听到「我醒了」这类词而永远不主动找他。
 */
object ProactivePauseExpiry {
    /** 默认放开时长：够一夜睡眠，又不至于拖到第二天下午。 */
    const val MAX_PAUSE_MS = 8 * 60 * 60 * 1000L

    /** [pausedAt] 为 0（没有记录）时不放开 —— 老数据保持原来的行为，等用户自己说「我醒了」。 */
    fun isExpired(pausedAt: Long, now: Long, maxPauseMs: Long = MAX_PAUSE_MS): Boolean =
        pausedAt > 0L && now - pausedAt >= maxPauseMs
}

data class SystemModeTransferSnapshot(
    val enabled: Boolean,
    val proactiveCharacterIds: Set<String>,
    val selectionConfigured: Boolean,
    val pausedCharacterIds: Set<String>
)

/** Resolves a persisted proactive companion without ever routing a proactive message to a group. */
object ProactiveCharacterSelector {
    fun resolveAll(preferredIds: Set<String>, availableIds: List<String>, selectionConfigured: Boolean = preferredIds.isNotEmpty()): List<String> =
        if (!selectionConfigured) availableIds else availableIds.filter { it in preferredIds }
}
