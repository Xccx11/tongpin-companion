package com.aster.companion.agent

/**
 * 把**拍摄器材**从生成结果里剔掉：手机、相机、镜头、取景框、自拍杆、屏幕。
 *
 * 为什么需要一道硬过滤：照片是"她眼睛看到的画面"，只要画面里出现手机/相机，构图就会被一个
 * 器材或举着它的手臂切开（照片割裂），这是用户明确不要的。
 * 规则文本里已经交代过，但模型会漏，所以这里再做一次确定性清理——不依赖模型听话。
 *
 * **只抹词、不删句**：自然语言里逗号是句子内部的分隔，同一句里可能既有器材又有长相
 * （"a candid photo taken with a phone of a young East Asian woman with an oval face"）。
 * 早期实现按逗号切分、把含器材词的分句**整条丢掉**，于是同句里的外貌被一起删掉——
 * 实测"22 岁左右的东亚少女 + 鹅蛋脸"整段消失，最终提示词只剩 `defined jawline …`，
 * 用户看到的就是「外貌设定被篡改」。所以现在：先把器材词/短语从句中抹掉，
 * 句子其余内容与原有标点原样保留；只有抹完之后仍然只剩器材描述的分句才会被丢弃。
 */
object SceneDeviceSuppression {

    /** 英文 tag 形态：tag 本身是离散单元，直接丢掉含器材词的 tag。 */
    fun stripTags(scene: String): String = scene
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !containsDevice(it) }
        .joinToString(", ")
        .trim()

    /** 中文自然语言形态：按句号/分号切分粒度合适，仍按分句剔除（保留旧行为）。 */
    fun stripSentences(scene: String): String {
        val clauses = scene
            .split(SENTENCE_SPLIT)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !containsDevice(it) }
        if (clauses.isEmpty()) return ""
        return clauses.joinToString("，") + "。"
    }

    /**
     * Krea 2 / 自然语言长句：**按行**清理，行结构原样保留。
     *
     * 先抹器材短语（保住同句里的外貌与场景），只有抹不干净的纯器材分句才丢弃；
     * 标点不再被改写——以前会把整行用全角逗号重拼，于是英文提示词里混出 `，`。
     */
    fun stripLines(scene: String): String = scene
        .split(LINE_SPLIT)
        .map { line ->
            val scrubbed = scrubDevices(line)
            if (!containsDevice(scrubbed)) {
                scrubbed
            } else {
                scrubbed.split(SENTENCE_SPLIT)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !containsDevice(it) }
                    .joinToString("，")
            }
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()

    fun strip(scene: String, format: ScenePromptFormat): String = when {
        format == ScenePromptFormat.ENGLISH_NATURAL ||
            format == ScenePromptFormat.CHINESE_NATURAL -> stripLines(scene)
        format.isNaturalLanguage -> stripSentences(scene)
        else -> stripTags(scene)
    }

    fun containsDevice(text: String): Boolean {
        val lower = text.lowercase()
        return DEVICE_WORDS.any { lower.contains(it) }
    }

    /**
     * 从句中抹掉器材词/短语，保留其余内容与原有标点。
     *
     * 先删"带介词/动词的完整短语"（`taken with a phone`），再删裸词（`phone`），
     * 这样 "A candid photo of a young East Asian woman…" 能完整留下来。
     */
    private fun scrubDevices(text: String): String {
        var out = text
        EN_DEVICE_PHRASES.forEach { pattern -> out = pattern.replace(out, " ") }
        CJK_DEVICE_PHRASES.forEach { pattern -> out = pattern.replace(out, "") }
        return out
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s+([,.!?;:，。！？；：])"), "$1")
            .replace(Regex("([,;])\\s*([,;])"), "$1")
            .replace(Regex("\\b(?:and|of|with|in)\\s+(?=[,.])", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimStart(',', '，', '.', ';', '；', ' ')
            .trim()
    }

    private val LINE_SPLIT = Regex("\\r\\n|\\r|\\n")
    private val SENTENCE_SPLIT = Regex("[。！？；;，,、]")

    /** 英文：先删带介词/动词的完整短语，再删裸词——顺序不能反。 */
    private val EN_DEVICE_PHRASES = listOf(
        Regex(
            "\\b(?:taken|captured|shot|filmed|recorded|photographed)\\s+(?:with|by|on|using)\\s+" +
                "(?:a|an|the)?\\s*(?:smart\\s*)?(?:phone|camera|dslr|mirrorless|device)\\b\\s*(?:of|showing|that\\s+shows)?",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "\\b(?:holding|cradling|gripping|raising|wielding|pointing)\\s+(?:a|an|the)?\\s*" +
                "(?:smart\\s*)?(?:phone|camera|device)\\b(?:\\s+(?:in|at)\\s+(?:her|his|the)\\s+hand)?",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "\\b(?:smart\\s*)?phone(?:\\s+(?:screen|snapshot|snap|selfie|shot|photo|shoot|camera))?\\b",
            RegexOption.IGNORE_CASE
        ),
        Regex("\\b(?:dslr|mirrorless|viewfinder|selfie\\s*stick|tripod|shutter|lens)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcamera(?:\\s+(?:screen|lens|in\\s+hand))?\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:lcd|phone|device)\\s+screen\\b", RegexOption.IGNORE_CASE),
        Regex("\\bscreen\\b", RegexOption.IGNORE_CASE),
    )

    private val CJK_DEVICE_PHRASES = listOf(
        Regex("(?:手机|相机|摄像机|摄影机|单反|镜头|取景框|自拍杆|三脚架|快门|屏幕)")
    )

    private val DEVICE_WORDS = listOf(
        // 英文（tag 形态与自然语言共用）
        "phone", "smartphone", "camera", "dslr", "mirrorless", "lens", "viewfinder",
        "selfie stick", "tripod", "shutter", "phone screen", "lcd screen", "screen",
        "holding device", "camera in hand", "phone in hand",
        // 中文（自然语言形态）
        "手机", "相机", "镜头", "取景框", "自拍杆", "三脚架", "快门", "屏幕", "摄像机", "摄影机"
    )
}
