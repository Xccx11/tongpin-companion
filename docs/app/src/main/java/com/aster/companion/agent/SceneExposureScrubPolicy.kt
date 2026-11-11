package com.aster.companion.agent

/**
 * 出图提示词的**确定性体检 + 修补**（A 方案，零额外模型调用）。
 *
 * 为什么需要：规则文本写在 krea2 里，模型会漏。实测最近一份提示词同时出现
 * "hand is buried inside her pussy" 和 "she wears a tight midi skirt, her inner garments…" ——
 * 渲染模型按"穿着"那一半画，于是相关部位一个都看不见。这里做四件事：
 *
 * 1. **抹禁令词**：光线/光源/明暗/阴影/blush（用户硬规则，以前完全没人清）；
 * 2. **解冲突**：出现该行为/该动作词时，删掉与之矛盾的**遮挡衣物**描述（裙子/裤子/内裤仍在），
 *    免得模型两边取一半；同时把含糊的器官说法（opening / 那里 / 下面 / it）换成具体词；
 * 3. **归一景别**：同时出现"全身从头到脚"与"只对脸/躯干对焦"时，该行为画面以**近景**为准，
 *    删掉"全身、从头到脚"这类写法；
 * 4. **记录体检结论**（[Result.notes]），便于界面/日志核对"这次为什么和规则不一样"。
 *
 * 刻意**不做**的事：不凭空编造暴露状态、不补器官细节 —— 那属于模型该写的内容（B 方案的活）。
 */
object SceneExposureScrubPolicy {

    data class Result(val text: String, val notes: List<String>)

    /** 该行为/插入类动作词（中英）。出现它才启用"解冲突"和"器官具体化"。 */
    private val ACT_WORDS = listOf(
        "penetrat", "inside her pussy", "inside her", "buried inside", "thrust", "insert",
        "enters her", "intercourse", "cock", "dick", "penis", "cum", "semen",
        "插入", "插进", "进入她", "抽插", "性交", "阴茎", "肉棒", "射精", "体液", "内射"
    )

    /** 遮挡性衣物（与"手已插入/器官已露"冲突）。 */
    private val COVERING_CLOTHES = listOf(
        "midi skirt", "skirt", "jeans", "trousers", "pants", "leggings", "panties", "underwear", "briefs", "thong",
        "inner garments", "inner garment", "undergarments",
        "短裙", "长裙", "裙子", "牛仔裤", "裤子", "长裤", "内裤", "底裤", "丁字裤"
    )

    /** 中日韩字符：这些字符不算 `\w`，凡是用 `\b` 的替换对它们都失效，必须走字面替换。 */
    private val CJK = Regex("[\\u3400-\\u9FFF]")

    /** 含糊的器官说法 → 具体词（规则要求：不许用"那里/下面/opening"含糊带过）。 */
    private val VAGUE_GENITAL = listOf(
        Regex("\\bher opening\\b", RegexOption.IGNORE_CASE) to "her pussy",
        Regex("\\bthe opening\\b", RegexOption.IGNORE_CASE) to "her pussy",
        Regex("\\bher entrance\\b", RegexOption.IGNORE_CASE) to "her pussy",
        Regex("\\bdown there\\b", RegexOption.IGNORE_CASE) to "her pussy",
        Regex("那里") to "湿润外翻的小穴",
        Regex("下面") to "小穴"
    )

    /** 禁令词（光线/阴影/blush）：整短语抹掉。[AppearanceAnchorScrubPolicy] 复用同一份清单，避免两处漂移。 */
    internal val FORBIDDEN_PHRASES = listOf(
        "natural lighting", "soft lighting", "warm lighting", "dim lighting", "cinematic lighting",
        "dramatic lighting", "backlighting", "rim light", "light source", "soft glow", "warm glow",
        "dimly lit", "brightly lit", "well lit", "soft shadows", "delicate shadows", "harsh shadows",
        "depth of field", "light blush", "blush", "flushed cheeks", "rosy cheeks", "红晕", "潮红", "脸红", "光线", "灯光", "阴影", "明暗"
    )

    /** "全身从头到脚"这类写法（与近景冲突时删掉）。 */
    private val FULL_BODY_PHRASES = listOf(
        "full body shot from head to toe", "from head to toe", "full-body shot", "full body shot",
        "showing her entire figure", "the whole figure fits in the frame", "visible from head to toe",
        "全身", "从头到脚"
    )

    fun scrub(scene: String, format: ScenePromptFormat): Result {
        if (scene.isBlank()) return Result(scene, emptyList())
        val notes = mutableListOf<String>()
        var out = scene

        // 1) 禁令词：抹短语（不删句，避免丢同句里的外貌/场景）
        val withoutForbidden = FORBIDDEN_PHRASES.fold(out) { acc, phrase ->
            acc.replace(Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE), " ")
        }
        if (withoutForbidden != out) { notes += "抹掉光线/阴影/blush 一类禁令词"; out = withoutForbidden }

        // 2) 该行为场景：解冲突 + 器官具体化
        if (ACT_WORDS.any { out.lowercase().contains(it) }) {
            var changedClothes = false
            COVERING_CLOTHES.forEach { word ->
                if (CJK.containsMatchIn(word)) {
                    // 中日韩字符不算 \w，`\b` 在中文里不成立 —— 中文走字面替换。
                    if (out.contains(word)) {
                        changedClothes = true
                        out = out.replace(word, "")
                    }
                } else {
                    val pattern = Regex("\\b(?:her|his|the)\\s+$word\\b", RegexOption.IGNORE_CASE)
                    if (pattern.containsMatchIn(out)) { changedClothes = true; out = pattern.replace(out, " ") }
                    val bare = Regex("\\b$word\\b", RegexOption.IGNORE_CASE)
                    if (bare.containsMatchIn(out)) { changedClothes = true; out = bare.replace(out, " ") }
                }
            }
            if (changedClothes) notes += "该行为画面里还有遮挡衣物（裙子/内裤）→ 已删除，免得模型按穿着画"

            val beforeVague = out
            VAGUE_GENITAL.forEach { (pattern, replacement) -> out = pattern.replace(out, replacement) }
            if (out != beforeVague) notes += "含糊的器官说法换成具体词（规则要求带状态、不许 opening/下面）"
        }

        // 3) 景别归一：同时有"全身"和"对焦在脸/躯干"时，按近景处理
        val lower = out.lowercase()
        val hasFullBody = FULL_BODY_PHRASES.any { lower.contains(it) }
        val hasCloseFocus = lower.contains("sharp focus on") || lower.contains("close-up") || lower.contains("close up") ||
            lower.contains("特写") || lower.contains("近景")
        if (hasFullBody && hasCloseFocus) {
            FULL_BODY_PHRASES.forEach { phrase ->
                out = out.replace(Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE), " ")
            }
            notes += "景别冲突（全身 + 只对脸部/躯干对焦）→ 按近景处理"
        }

        out = out.replace(Regex("\\s{2,}"), " ").replace(Regex("\\s+([,.])"), "$1").trim()
        return Result(out, notes)
    }
}
