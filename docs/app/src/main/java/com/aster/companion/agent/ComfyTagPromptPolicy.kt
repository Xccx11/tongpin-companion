package com.aster.companion.agent

/**
 * Lite 生图 prompt 入队闸门。
 *
 * 已放宽：允许中文、叙述句、混排 tag；只要非空且长度合理即可出图。
 * 仅拦截：空白、超长、代码围栏、明显 JSON 外壳。
 */
object ComfyTagPromptPolicy {

    data class Validation(
        val accepted: Boolean,
        val reasons: List<String> = emptyList()
    )

    /**
     * [characterName] 只用来**剥掉**名字，不再因它判不合格：简报里全是对话原文（"角色名: …"），
     * 模型写描述时常常把名字带出来，而渲染器本来就不认识人名。
     * 真正要拦的是模型退化成「I am 宫雨琴. I want to send…」这种复述指令的自言自语 —— 那不是画面描述。
     */
    fun validate(
        prompt: String,
        appearancePrompt: String = "",
        characterName: String = "",
        maxLength: Int = MAX_LENGTH
    ): Validation {
        val text = prompt.trim()
        val name = characterName.trim()
        val body = if (name.isEmpty()) {
            text
        } else {
            text.replace(name, " ").replace(Regex("\\s{2,}"), " ").trim()
        }
        val reasons = buildList {
            if (body.isEmpty()) add("empty")
            if (body.length > maxLength) add("too_long")
            if (body.contains("```")) add("markdown_fence")
            if (body.startsWith("{") || body.startsWith("[")) add("json")
            if (NARRATION.any { it.containsMatchIn(body) }) add("narration")
        }
        if (reasons.isNotEmpty()) return Validation(accepted = false, reasons = reasons)
        return Validation(accepted = true)
    }

    /** 模型退化成自言自语、复述指令的特征；正常的画面描述（无论 tag 还是叙述）都不会命中。 */
    private val NARRATION = listOf(
        Regex("\\bI am\\b", RegexOption.IGNORE_CASE),
        Regex("\\bI want to\\b", RegexOption.IGNORE_CASE),
        Regex("\\bI will send\\b", RegexOption.IGNORE_CASE),
        Regex("\\bthe user\\b", RegexOption.IGNORE_CASE),
        Regex("Preserve the real visual intent", RegexOption.IGNORE_CASE)
    )

    /** 剥掉模型爱包的 markdown 围栏（```...```）；原来只剥一个反引号，围栏根本剥不掉。 */
    internal fun stripFences(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```").removePrefix("english").removePrefix("text").removePrefix("plain")
            t = t.trimStart('\n', ' ', '\t')
            val end = t.lastIndexOf("```")
            if (end >= 0) t = t.substring(0, end)
        }
        return t.trim().removeSurrounding("\"").trim()
    }

    fun finalPromptOrNull(
        prompt: String,
        appearancePrompt: String = "",
        characterName: String = "",
        maxLength: Int = MAX_LENGTH
    ): String? {
        val text = prompt.trim()
        return text.takeIf { validate(it, appearancePrompt, characterName, maxLength).accepted }
    }

    /**
     * 出图前的**确定性清洗**：模型反复违反、而提示词规则治不住的那几条，在这里用代码兜住。
     *
     * 起因：`krea2.pass2` 三千多字、二十多个约束点，模型每次只执行前面一部分。
     * 眼睛又变回「又大又圆」、肤色层出现「毫无红晕」（**否定句**——Krea 读不到「没有」，
     * 写了反而会把要禁的东西画出来）、代称「隐私部位」与解剖词混用。
     * 这些不该指望模型自觉，用代码改可靠得多。
     *
     * 只做六件事，不改内容本身：
     *  1. 删掉**否定短语**所在的分句
     *  3. 代称（隐私部位 / 私处 / 下面 / 那处）**换成解剖词**
     *  4. 程度词（剧烈 / 猛烈 / 极度 / 极具 / 极其 / 夸张）**删掉**
     */
    internal fun finalPolish(text: String): String {
        var t = text
        // 1) 否定分句：整句删掉，只留下它末尾的分隔符，免得把前后两句粘一起。
        for (re in NEGATION_CLAUSES) {
            t = re.replace(t) { m -> m.value.takeLastWhile { c -> c in "\u3002\uff0c,;\uff1b\n" } }
        }
        // 1.5) 脸红类分句：同样整句删掉。用户明确要求禁止描写脸红，而模型屡教不改
        //（实测每张都写 flushed cheeks）——这类就靠代码兜，不再指望规则。
        for (re in RED_FACE_CLAUSES) {
            t = re.replace(t) { m -> m.value.takeLastWhile { c -> c in "\u3002\uff0c,;\uff1b\n" } }
        }
        // 2) 通用眼型 -> 狐狸眼八项模板
        // 2) 通用眼型不再替换：外貌是用户自己提供的，代码不许改它。
        // 3) 代称 -> 解剖词
        for ((re, word) in EUPHEMISMS) t = re.replace(t, word)
        // 4) 程度词 -> 删
        for (re in INTENSIFIERS) t = re.replace(t, "")
        // 5) 否定结构（不裁切 / 不遮挡 这类）：Krea 读不到「不」，会把动词当要求。
        for (re in NEGATED_VERBS) t = re.replace(t, "")
        // 6) 男性称谓归一（用户指定）：名词一律 `an Asian young man`，所有格一律 `an Asian man's`。
        //    不要 the man / a man / his / he 这类没有身份的统称——渲染器会照字面画出别的年龄和族裔。
        //    顺序不能反：先换所有格，再换主格/宾格，否则 his 会先被当成 he 换掉。
        t = MALE_POSSESSIVE.replace(t) { m -> matchCase(m.value, "an Asian man's") }
        t = MALE_SUBJECT.replace(t) { m -> matchCase(m.value, "an Asian young man") }
        return t.replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    private fun matchCase(source: String, replacement: String): String =
        if (source.firstOrNull()?.isUpperCase() == true) {
            replacement.replaceFirstChar { it.uppercase() }
        } else {
            replacement
        }

    /** 男性的所有格写法：his / the man's / a man's。 */
    private val MALE_POSSESSIVE = Regex(
        "\\b(?:his|the man['\u2019]s|a man['\u2019]s|this man['\u2019]s)\\b",
        RegexOption.IGNORE_CASE
    )

    /** 男性的主格/宾格写法：he / him / the man / a man。 */
    private val MALE_SUBJECT = Regex(
        "\\b(?:he|him|the man|a man|this man)\\b",
        RegexOption.IGNORE_CASE
    )


    /** 否定分句：整句删。Krea 读不到否定，留着等于把要禁的东西写进提示词。 */
    private val NEGATION_CLAUSES = listOf(
        Regex("[^\u3002\uff0c,;\uff1b\n]*?(?:\u6beb\u65e0|\u6ca1\u6709\u4efb\u4f55|\u5b8c\u5168\u6ca1\u6709\u4efb\u4f55|\u5e76\u6ca1\u6709|\u4e0d\u5177\u6709)[^\u3002\uff0c,;\uff1b\n]*[\u3002\uff0c,;\uff1b]?")
    )

    /**
     * 脸红类分句：整句删。用户明确要求禁止描写脸红——渲染器会把它画成脸上不自然的红斑。
     *
     * **只认和脸/脸颊连在一起的写法**：裸的 `flushed` 不能删，器官描写里有正当用法
     *（`the glans flushed dark`），误删会把该写的东西删掉。
     */
    private val RED_FACE_CLAUSES = listOf(
        // ⚠️ 名字保留（改它要动调用点），但**行为已经变了：只删脸红短语，不再连整个逗号段一起删**。
        //
        // 以前是 `[^。，,;；\n]*?` + 脸红词 + `[^。，,;；\n]*`，即删掉**整个逗号段**。
        // 而英文提示词里表情和脸红常常同处一段（`a faint smile with rosy cheeks`），
        // 于是"微笑"跟着"红晕"一起被删掉 → 交付给出图模型的提示词里一点表情都不剩 →
        // **人物渲染出来面无表情**（用户现象："人物没有表情"，且每张都这样）。
        //
        // 现在只吃掉「连接词 + 修饰词 + 脸红短语」（`with rosy cheeks` / `light blush` / `泛红`…），
        // 同一段里的表情描述原样留下。用户"禁止描写脸红"的硬规则照旧生效。
        Regex(
            "(?i)(?:\\s*,?\\s*(?:with|and)\\s+)?" +
                "(?:a |an |the |light |slight |faint |soft |deep )*" +
                "(?:blush\\w*|red cheeks|red face|rosy cheeks|crimson cheeks|" +
                "flushed cheeks|flushed face|flushed skin|face (?:was |is |looks )?flushed|" +
                "cheeks? (?:was |were |are )?flushed|\u8138\u7ea2|\u6f6e\u7ea2|\u6cdb\u7ea2|\u7f9e\u7ea2)"
        )
    )

    /** 通用眼型：CLIP / Qwen 都把它们当高频词，会盖掉角色档案里的狐狸眼。 */
    // GENERIC_EYES / FOX_EYES 已移除：外貌由用户提供，不许用代码改写。

    /** 代称 -> 解剖词。代称生图模型读不懂，写它等于没写。 */
    private val EUPHEMISMS = listOf(
        Regex("\u9690\u79c1\u90e8\u4f4d|\u79c1\u5bc6\u90e8\u4f4d|\u79c1\u5904|\u79c1\u5bc6\u5904|\u90a3\u5904|\u90a3\u4e2a\u5730\u65b9|\u4e0b\u9762") to "\u9634\u90e8"
    )

    /** 程度词：模型靠它们把画面写夸张（「剧烈晃动」「极具压迫感的量感」）。 */
    private val INTENSIFIERS = listOf(
        Regex("\u5267\u70c8\u5730?|\u731b\u70c8\u5730?|\u75af\u72c2\u5730?|\u6781\u5ea6\u5730?|\u6781\u5177|\u6781\u5176|\u6781\u4e3a|\u975e\u5e38\u5730?|\u5341\u5206\u5730?|\u5938\u5f20\u5730?")
    )

    /**
     * 否定结构：「不裁切」「不遮挡」「不露出」这类。**Krea 读不到「不」**——
     * 它读到的是「裁切」「遮挡」「露出」这些动词，**等于在要求做那件事**。
     * 所以整段删掉，该表达的意思由别的层用肯定句承担。
     */
    private val NEGATED_VERBS = listOf(
        Regex("(?:不|没|无)(?:要)?(?:裁切|遮挡|遮盖|露出|披露|包裹|改变|动作|看镜头|看相机)[^。，,;；\n]*[。，,;；]?")
    )

    /**
     * 清掉模型写进提示词里、渲染器根本读不了的东西：
     * 方括号占位符（真实数据里出现过 `[outfit from previous context]`）和中文。
     *
     * [stripCjk] 只在**英文 tag 模型**下为 true：z-image / lumina 这类走 Qwen 系编码器，
     * 中文自然语言正是它要的，剥掉中文等于把提示词删空。
     * 只用于**模型产出的场景段**，用户填的外貌锚点永远原样保留。
     *
     * [keepLineBreaks] 只在 **Krea 2（中文自然语言形态）** 下为 true：它是**按模块分段的纯文本**，
     * 换行就是分段标记，所以这里逐行清洗、行结构原样保留（连续换行归一成一个换行）；其余形态仍压成一行。
     *
     * 清洗最后都会过一遍 [finalPolish]（否定句 / 通用眼型 / 代称 / 程度词）。
     */
    fun sanitizeScene(prompt: String, stripCjk: Boolean = true, keepLineBreaks: Boolean = false): String {
        var text = prompt
            .replace(DANGLING_WORD_BEFORE_PLACEHOLDER, " ")
            .replace(BRACKET_PLACEHOLDER, " ")
        if (stripCjk) {
            text = text.replace(CJK_RUN, " ")
        }
        return if (keepLineBreaks) {
            text.split(LINE_BREAK_RUN)
                .map { line ->
                    finalPolish(line)
                        .replace(COMMA_RUN, ", ")
                        .replace(INLINE_WHITESPACE_RUN, " ")
                        .trim()
                        .trim(',')
                        .trim()
                }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        } else {
            finalPolish(
                text
                    .replace(COMMA_RUN, ", ")
                    .replace(WHITESPACE_RUN, " ")
                    .trim()
                    .trim(',')
                    .trim()
            )
        }
    }

    /**
     * 场景 tag 被写成中文时的提示。本地 SDXL 认不出中文，出图会跟画面无关。
     * 只提示、不代改。
     */
    fun sceneLanguageWarning(prompt: String): String? {
        val text = prompt.trim()
        if (text.isEmpty()) return null
        val cjk = text.count { it.code in 0x3400..0x9FFF }
        return if (cjk > text.length / 3) "场景标签写成了中文，本地 SDXL 认不出，出图会跟画面无关" else null
    }

    /** 真实数据里模型会照抄模板占位符（`[outfit from previous context]`），这种 tag 只会污染画面。 */
    private val BRACKET_PLACEHOLDER = Regex("\\[[^\\]]*\\]")
    /** 占位符前面那个动词也要一起清掉，否则会留下悬空的 `wearing,`。 */
    private val DANGLING_WORD_BEFORE_PLACEHOLDER = Regex(
        "(?i)\\b(wearing|holding|dressed in|carrying|using|with|having)\\s*\\[[^\\]]*\\]"
    )
    private val CJK_RUN = Regex("[\\u3400-\\u9FFF]+")
    private val COMMA_RUN = Regex("\\s*,\\s*(?:,\\s*)*")
    private val WHITESPACE_RUN = Regex("\\s+")
    /** 换行（`\r\n` / `\r` / `\n` 通吃），Krea 2 保留行结构时用来按行切开。 */
    private val LINE_BREAK_RUN = Regex("\\r\\n|\\r|\\n")
    /** 行内空白：不含换行，保留行结构时可放心压成单空格。 */
    private val INLINE_WHITESPACE_RUN = Regex("[ \\t\\x0B\\f]+")

    /**
     * 最终入队校验的上限，必须**略高于** `ScenePromptRefiner.MAX_CHARS`：
     * 否则第 2 轮刚被判合格、这里又把它拦下来，细化结果依旧用不上。
     */
    private const val MAX_LENGTH = 6_000
}
