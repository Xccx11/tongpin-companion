package com.aster.companion.agent

/**
 * 外貌档案的**非外貌样板清洗**。
 *
 * ## 为什么需要
 *
 * 外貌档案字段里经常被粘进一整段「出图提示词」——里面混着画幅（`full-body photo`）、
 * 画质（`high-definition`、`clear and detailed`）、构图（`From head to toe`）、
 * 姿态（`She is standing in a full-body pose`）和光线（`natural lighting`）。
 * 这些是「**这一张怎么拍**」，不是「**她长什么样**」。它们一旦被 [AppearanceAnchor] 原样拼到
 * 提示词最前面，就会：
 *
 * 1. **和场景打架**：实测锚点写着 `She is standing in a full-body pose, visible from head to toe`，
 *    而同一次的场景是 `is lying on a bed` —— 站立 vs 躺床，按哪一段画都是错的；
 * 2. **违反硬规则**：krea2 规则两处明令「整段不出现任何光线或阴影描写」，而 `natural lighting`
 *    就躺在锚点里。出图清洗（[SceneExposureScrubPolicy]）只作用于**模型输出**那一段——锚点是
 *    用户档案、由代码拼进去的，永远走不到清洗；
 * 3. **浪费预算**：一段 900 多字的档案里，真正描述长相的只有后面一半。
 *
 * 所以在拼接**之前**把样板剥掉，只留五官与身材。
 *
 * ## 刻意不做的事
 *
 * - **不动年龄**：`22-year-old` 属于长相，保留；
 * - 不做改写、翻译、同义替换，只做「删词 / 删子句」；
 * - 删完的拼接方式与原文一模一样（仍然是"原文换行拼最前"），所以 [AppearanceAnchor] 的
 *   幂等性与 [AppearanceAnchor.strip] 的反向剥离都成立。
 */
object AppearanceAnchorScrubPolicy {

    data class Result(val text: String, val notes: List<String>)

    /** 明确属于「这一张怎么拍」的样板词（画幅 / 构图 / 画质）。 */
    private val FRAMING_PHRASES = listOf(
        "photorealistic smartphone full-body photo", "smartphone full-body photo", "smartphone photo",
        "full-body photo", "full body photo", "full-body shot", "full body shot",
        "the whole figure fits in the frame", "the entire figure fits in the frame",
        "entire figure fits in the vertical frame", "vertical frame",
        "visible from head to toe", "from head to toe",
        "high-definition", "high definition", "ultra-detailed", "ultra detailed",
        "clear and detailed", "clear and natural", "sharp focus", "depth of field",
        "photorealistic", "masterpiece", "best quality",
        "全身照", "从头到脚", "高清", "超清"
    )

    /**
     * 姿态子句：整句删掉，因为它描述的是「这一张的姿势」，天生会和场景冲突。
     *
     * 三条例式覆盖实测形态：`She is standing in a full-body pose, …` / `standing in a pose, …` /
     * `in a full-body pose …`。`[^.]*` 允许跨逗号，所以一整个子句（含后面并列的样板）一起被吃掉。
     */
    private val POSE_CLAUSES = listOf(
        Regex(
            "(?i)\\b(?:she|he|the\\s+woman|the\\s+girl)\\s+(?:is\\s+|was\\s+)?" +
                "(?:standing|sitting|kneeling|lying|crouching|leaning|posing)\\b[^.]*?\\bpose\\b[^.]*[.;]?"
        ),
        Regex("(?i)\\b(?:standing|sitting|kneeling|lying|crouching|leaning)\\s+in\\s+an?\\s+[^.]*?\\bpose\\b[^.]*[.;]?"),
        Regex("(?i)\\bin\\s+an?\\s+full[- ]body\\s+pose\\b[^.]*[.;]?")
    )

    /** 光线 / 阴影 / 脸红词表：与出图清洗**同源**，直接复用，避免两处清单漂移。 */
    private val LIGHTING_PHRASES: List<String>
        get() = SceneExposureScrubPolicy.FORBIDDEN_PHRASES

    fun scrub(anchor: String): Result {
        val source = anchor.trim()
        if (source.isEmpty()) return Result(source, emptyList())
        val notes = mutableListOf<String>()
        var out = source

        // 1) 光线 / 阴影 / 脸红
        val noLighting = LIGHTING_PHRASES.fold(out) { acc, phrase ->
            acc.replace(Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE), " ")
        }
        if (noLighting != out) {
            notes += "剥掉光线/阴影/脸红一类词（规则明令整段不许出现）"
            out = noLighting
        }

        // 2) 画幅 / 构图 / 画质样板
        val noFraming = FRAMING_PHRASES.fold(out) { acc, phrase ->
            acc.replace(Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE), " ")
        }
        if (noFraming != out) {
            notes += "剥掉画幅/构图/画质样板（那是「怎么拍」，不是长相）"
            out = noFraming
        }

        // 3) 姿态子句
        var hadPose = false
        POSE_CLAUSES.forEach { pattern ->
            if (pattern.containsMatchIn(out)) {
                hadPose = true
                out = pattern.replace(out, " ")
            }
        }
        if (hadPose) notes += "剥掉姿态子句（会和这一张的场景打架）"

        return Result(tidy(out), notes)
    }

    /**
     * 收拾删词留下的标点与空白。
     *
     * 删词会在原地留下空洞（`,  ,  , .`），必须收敛掉，否则模型会把连续标点当成排版错误。
     * 只做「空白 + 标点」的规整，不碰任何字母或汉字。
     */
    private fun tidy(text: String): String {
        var out = text.replace(Regex("\\s+"), " ")
        // 反复收敛：删掉一个标点后可能又暴露出新的相邻标点。
        repeat(3) {
            out = out
                .replace(Regex("\\s+([,.;:!?])"), "$1")
                .replace(Regex("[,;:]+\\s*(?=[,;:.])"), "")
                .replace(Regex("([.!?])\\s*[,;:]+"), "$1")
                // 删短语会留下孤零零的冠词（`A photorealistic smartphone full-body photo, …`
                // 被删成 `A, realistic skin texture, …`），一并收掉。只在句首/句末紧邻标点时命中，
                // 所以 `A 22-year-old` / `a slender neck` 这类正常写法不受影响。
                .replace(Regex("(?i)^(?:a|an|the)\\s*(?=[,;:!?.])"), "")
                .replace(Regex("(?i)(?<=[.!?]\\s)(?:a|an|the)\\s*(?=[,;:!?.])"), "")
        }
        return out
            .replace(Regex("^[\\s,;:.!?]+"), "")
            .replace(Regex("[\\s,;:]+$"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
