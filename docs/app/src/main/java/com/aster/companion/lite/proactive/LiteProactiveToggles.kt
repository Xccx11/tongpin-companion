package com.aster.companion.lite.proactive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 「她可以主动做什么」的开关。
 *
 * - [momentsEnabled]：她自己发朋友圈（默认开）；
 * - [photoEnabled]：她主动给你发照片（会占显存、跟聊天抢同一张卡，所以给用户一个单独关掉的口子，默认开）；
 * - [urgeEnabled]：按她自己的节奏找你（**默认关**：不开就是原来的固定冷却，行为不变）；
 * - [relaxedContact]：更主动一点（意愿门槛降低、最小间隔缩到 30 分钟；**默认关** = 严格档）。
 *
 * 前两个以前写死在 [LiteProactiveSettings] 的默认参数里，用户没有入口——关不掉。
 * 后两个是「幕间能力」里 [com.aster.companion.domain.UrgePolicy] 与
 * [com.aster.companion.domain.CompanionAgencyPolicy.ContactMode] 的用户入口：
 * **没有这两个开关，那两块就只是默认值，用户碰不到。**
 */
@Singleton
class LiteProactiveToggles @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _momentsEnabled = MutableStateFlow(preferences.getBoolean(KEY_MOMENTS, true))
    private val _photoEnabled = MutableStateFlow(preferences.getBoolean(KEY_PHOTO, true))
    private val _urgeEnabled = MutableStateFlow(preferences.getBoolean(KEY_URGE, false))
    private val _relaxedContact = MutableStateFlow(preferences.getBoolean(KEY_RELAXED, false))
    private val _worldSeeding = MutableStateFlow(preferences.getBoolean(KEY_WORLD_SEEDING, false))

    val momentsEnabled: StateFlow<Boolean> = _momentsEnabled
    val photoEnabled: StateFlow<Boolean> = _photoEnabled
    val urgeEnabled: StateFlow<Boolean> = _urgeEnabled
    val relaxedContact: StateFlow<Boolean> = _relaxedContact
    val worldSeeding: StateFlow<Boolean> = _worldSeeding

    fun setMomentsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_MOMENTS, enabled).apply()
        _momentsEnabled.value = enabled
    }

    fun setPhotoEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PHOTO, enabled).apply()
        _photoEnabled.value = enabled
    }

    fun setUrgeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_URGE, enabled).apply()
        _urgeEnabled.value = enabled
    }

    fun setRelaxedContact(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RELAXED, enabled).apply()
        _relaxedContact.value = enabled
    }

    fun setWorldSeeding(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WORLD_SEEDING, enabled).apply()
        _worldSeeding.value = enabled
    }

    companion object {
        private const val PREFS = "tongpin_proactive_toggles"
        private const val KEY_MOMENTS = "moments_enabled"
        private const val KEY_PHOTO = "photo_enabled"
        private const val KEY_URGE = "urge_enabled"
        private const val KEY_RELAXED = "relaxed_contact"
        private const val KEY_WORLD_SEEDING = "world_seeding"
    }
}
