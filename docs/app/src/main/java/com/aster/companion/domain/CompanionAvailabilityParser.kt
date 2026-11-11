package com.aster.companion.domain

enum class CompanionAvailabilitySignal { NONE, PAUSE, PAUSE_AND_WRITE_DIARY, RESUME }

/** Recognizes only explicit user availability statements; it never infers a busy state. */
object CompanionAvailabilityParser {
    fun parse(message: String): CompanionAvailabilitySignal {
        val text = message.replace(Regex("\\s+"), "")
        if (RESUME_WORDS.any(text::contains)) return CompanionAvailabilitySignal.RESUME
        if (SLEEP_WORDS.any(text::contains)) return CompanionAvailabilitySignal.PAUSE_AND_WRITE_DIARY
        if (BUSY_WORDS.any(text::contains)) return CompanionAvailabilitySignal.PAUSE
        return CompanionAvailabilitySignal.NONE
    }

    private val RESUME_WORDS = listOf(
        "我回来了", "回来啦", "回来咯", "回来喽", "回来了", "到家了", "我到了", "到啦", "到咯",
        "我醒了", "我醒来", "醒啦", "醒咯", "睡醒了", "睡醒啦", "起床了", "起了", "没睡", "还没睡", "不睡了", "睡不着",
        "打完了", "打完啦", "游戏结束", "游戏打完", "忙完了", "忙完啦", "忙完", "忙好了", "忙好啦", "不忙了", "学完了", "学习完了",
        "下课了", "下班了", "做完了", "做完啦", "弄完了", "结束了", "有空了", "有空啦", "空下来了", "可以聊天", "继续聊", "继续说"
    )
    private val SLEEP_WORDS = listOf(
        "睡觉", "睡了", "去睡", "要睡", "该睡", "先睡", "睡会", "睡一会", "睡会儿", "去休息", "先休息", "躺会"
    )
    private val BUSY_WORDS = listOf(
        "要忙了", "去忙", "忙一会", "忙会儿", "要学习", "去学习", "学习了", "写作业", "写作业了",
        "上课", "上自习", "复习", "打游戏", "去打游戏", "开黑", "开会", "去工作", "要工作", "先工作"
    )
}
