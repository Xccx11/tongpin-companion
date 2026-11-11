package com.aster.companion.agent

/**
 * 提示词片段的**边界安全截断**。
 *
 * ## 为什么需要
 *
 * `LAST_SCENE`（"把上一张的画面记下来给下一张做连拍一致性"）原先用 `take(600)` 硬截，
 * 实测切出了这样一句：
 *
 * ```
 * Her previous photo showed: A photorealistic smartphone full-body photo, … She wear.
 * ```
 *
 * `She wears a cool refined no-makeup makeup look` 被拦腰砍断成 `She wear.`，残句直接进下一轮请求。
 *
 * ## 规则
 *
 * 1. 优先切在**句末**（`. ! ? 。 ！ ？`）——最自然，读起来仍然完整；
 * 2. 没有句末就切在**最后一个词末空格**；
 * 3. 都没有才硬截（中文长句无空格时会走到这里）。
 *
 * **切完不留省略号**：残留的 `...` 会被模型当成提示词内容照抄进画面描述。
 * 切点太靠前（会只剩一个词）时宁可不切，交给上一步的结果说话。
 */
object PromptExcerptPolicy {

    /**
     * 句末切点的最小保留长度。
     *
     * 取小值：`。`/`.` 是语言natural的边界，一个完整短句（如 11 字的中文句）已经比硬截好得多。
     * 又因为找的是**最后一个**句末，正常长文本的切点天然靠近上限，不会真被这条卡住。
     */
    private const val MIN_SENTENCE_KEEP = 6

    /**
     * 词末切点的最小保留长度（还要同时满足"至少留到上限的四分之一"）。
     *
     * 比句末严：词边界只是"不敢切在词中间"，若它离上限太远（丢了大部分内容）就不如硬截。
     */
    private const val MIN_WORD_KEEP = 12

    private val SENTENCE_ENDS = charArrayOf('.', '!', '?', '。', '！', '？')

    fun excerpt(text: String, limit: Int): String {
        val source = text.trim()
        if (limit <= 0) return ""
        if (source.length <= limit) return source
        val head = source.take(limit)

        val sentence = head.lastIndexOfAny(SENTENCE_ENDS)
        if (sentence >= MIN_SENTENCE_KEEP) return head.take(sentence + 1).trim()

        val space = head.lastIndexOf(' ')
        if (space >= maxOf(MIN_WORD_KEEP, limit / 4)) return head.take(space).trim()

        return head.trim()
    }
}
