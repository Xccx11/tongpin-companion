package com.aster.companion.lite

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 「后台推演空闲设置」的持久化。默认 1 分钟：你一分钟没发消息，后台才开始跑。
 *
 * 注意 [allowedIdleMs] 必须先于 [_idleMs] 初始化 —— Kotlin 属性按声明顺序初始化，
 * read() 里会用到它；声明在后面的话构造时它是 null，会抛 NPE
 * （这个坑在 [LiteImageSizeStore] 上已经炸过一次）。
 */
@Singleton
class BackgroundIdleStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val allowedIdleMs: Set<Long> = BackgroundIdlePolicy.OPTIONS.map { it.second }.toSet()

    private val _idleMs = MutableStateFlow(read())

    val idleMs: StateFlow<Long> = _idleMs

    fun set(idleMs: Long) {
        val normalized =
            if (allowedIdleMs.contains(idleMs)) idleMs else BackgroundIdlePolicy.DEFAULT_IDLE_MS
        preferences.edit().putLong(KEY, normalized).apply()
        _idleMs.value = normalized
    }

    private fun read(): Long {
        val saved = preferences.getLong(KEY, BackgroundIdlePolicy.DEFAULT_IDLE_MS)
        return if (allowedIdleMs.contains(saved)) saved else BackgroundIdlePolicy.DEFAULT_IDLE_MS
    }

    private companion object {
        const val PREFS = "tongpin_background_idle"
        const val KEY = "idle_ms"
    }
}
