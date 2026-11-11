package com.aster.companion.lite

import android.content.Context
import com.aster.companion.agent.ConversationContextWindowPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 「模型上下文」两个设置项的持久化：**上下文长度**（模型能吃多少 token）与**窗口条数**（每轮最多带几条历史）。
 *
 * 为什么要有这两个开关：App 每轮都是**重新拼一个固定大小的请求**（人设 + 状态 + 最近若干条 + 当前消息），
 * 拼多大必须知道模型能吃多少。用户会在 16K / 32K / 64K / 96K / 128K 之间换模型，写死任何一个档位都不对：
 * - 写 128K：换成 16K 的本地小模型时直接超上下文（服务端从中间截，砍掉的正好是记忆）；
 * - 写 16K：换成 128K 的模型时白白浪费一大截窗口。
 *
 * 窗口条数**故意不跟随档位自动放大**：实测 128K 档把整条对话（118 条 / 2.3 万 token）每轮重发，
 * 预填充量线性上涨、越来越慢。所以它是独立的一项，默认 40 条，要更长的记忆自己调大。
 * 两个值都支持自定义（不吸附到档位），只做上下限收敛。
 */
@Singleton
class ContextWindowStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _contextTokens = MutableStateFlow(readContext())
    private val _windowMessages = MutableStateFlow(readWindow())

    val contextTokens: StateFlow<Int> = _contextTokens

    /** 每轮最多带几条真实历史（不含本轮用户消息）。 */
    val windowMessages: StateFlow<Int> = _windowMessages

    fun set(contextTokens: Int) {
        val normalized = ConversationContextWindowPolicy.normalizeContext(contextTokens)
        preferences.edit().putInt(KEY_CONTEXT, normalized).apply()
        _contextTokens.value = normalized
    }

    fun setWindowMessages(messages: Int) {
        val normalized = ConversationContextWindowPolicy.normalizeWindow(messages)
        preferences.edit().putInt(KEY_WINDOW, normalized).apply()
        _windowMessages.value = normalized
    }

    private fun readContext(): Int =
        ConversationContextWindowPolicy.normalizeContext(
            preferences.getInt(KEY_CONTEXT, ConversationContextWindowPolicy.DEFAULT_CONTEXT_TOKENS)
        )

    private fun readWindow(): Int =
        ConversationContextWindowPolicy.normalizeWindow(
            preferences.getInt(KEY_WINDOW, ConversationContextWindowPolicy.DEFAULT_WINDOW_MESSAGES)
        )

    private companion object {
        const val PREFS = "tongpin_context_window"
        const val KEY_CONTEXT = "context_tokens"
        const val KEY_WINDOW = "window_messages"
    }
}
