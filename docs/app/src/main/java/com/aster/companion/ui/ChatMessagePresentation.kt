package com.aster.companion.ui

import com.aster.companion.data.MessageEntity

data class DisplayMessage(
    val id: String,
    val message: MessageEntity
)

data class MessageTextPart(
    val value: String,
    val isStageDirection: Boolean
)

/**
 * 一个气泡指向的原始消息。
 *
 * [segmentIndex] 只在「拆段显示」把一条消息拆成多个气泡时才不为空 —— 一个气泡**不等于**一条消息。
 */
data class DisplayMessageRef(
    val messageId: String,
    val segmentIndex: Int?,
    val segmentCount: Int
)

/** Pure display transformations. The original database message remains the model context. */
object ChatMessagePresentation {
    fun displayMessages(messages: List<MessageEntity>, splitMultilineResponses: Boolean): List<DisplayMessage> =
        messages.flatMap { message ->
            val paragraphs = if (isSplittable(message, splitMultilineResponses)) splitParagraphs(message.body) else emptyList()
            if (paragraphs.size < 2) {
                listOf(DisplayMessage(message.id, message))
            } else {
                paragraphs.mapIndexed { index, paragraph ->
                    DisplayMessage("${message.id}:$index", message.copy(body = paragraph))
                }
            }
        }

    /**
     * 反解一个气泡：它属于哪条原始消息、是那条消息拆出来的第几段。
     *
     * 同一个原始消息 id 在 [displayMessages] 里出现多次时，它出现的次序就是段序号；
     * 只出现一次说明没拆段（`segmentIndex` 为 null，删除就是删整条消息）。
     * 不要用「截掉最后一个冒号」的办法解析——消息 id 本身可能带冒号（`image-message:`、`stream:`）。
     */
    fun refOf(displayMessages: List<DisplayMessage>, displayId: String): DisplayMessageRef? {
        val display = displayMessages.firstOrNull { it.id == displayId } ?: return null
        val siblings = displayMessages.filter { it.message.id == display.message.id }
        val segmentIndex = if (siblings.size > 1) siblings.indexOf(display) else null
        return DisplayMessageRef(display.message.id, segmentIndex, siblings.size)
    }

    /**
     * 删掉多段回复里的第 [index] 段，返回剩下的正文；一段都不剩时返回 null（调用方据此删掉整条消息）。
     *
     * 段落切分规则必须和 [displayMessages] 完全一致，否则删掉的那段和用户长按的那段对不上。
     */
    fun removeSegment(body: String, index: Int): String? {
        val paragraphs = splitParagraphs(body)
        if (index !in paragraphs.indices) return null
        val remaining = paragraphs.filterIndexed { position, _ -> position != index }
        return remaining.joinToString("\n").takeIf(String::isNotBlank)
    }

    fun parts(text: String): List<MessageTextPart> {
        val matcher = STAGE_DIRECTION.findAll(text).iterator()
        val output = mutableListOf<MessageTextPart>()
        var cursor = 0
        while (matcher.hasNext()) {
            val match = matcher.next()
            if (match.range.first > cursor) output += MessageTextPart(text.substring(cursor, match.range.first), false)
            output += MessageTextPart(match.value, true)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) output += MessageTextPart(text.substring(cursor), false)
        return output.ifEmpty { listOf(MessageTextPart(text, false)) }
    }

    private val STAGE_DIRECTION = Regex("<[^<>\\n]{1,240}>|（[^（）\\n]{1,240}）|\\([^()\\n]{1,240}\\)")

    private fun isSplittable(message: MessageEntity, splitMultilineResponses: Boolean): Boolean =
        splitMultilineResponses && !message.fromUser && message.imageUri == null

    private fun splitParagraphs(body: String): List<String> =
        body.normalizedLineBreaks().lines().map(String::trim).filter(String::isNotBlank)

    private fun String.normalizedLineBreaks(): String = replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\\r", "\n")
        .replace("\\u2028", "\n")
        .replace("\\u2029", "\n")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u0085', '\n')
        .replace('\u2028', '\n')
        .replace('\u2029', '\n')
}
