package com.aster.companion.agent

/**
 * 景别兜底：**用户要「从头看到脚」，所以默认一律全身**。
 *
 * 为什么需要代码兜底：实测最近 6 张的景别分别是
 * `close-up` / `medium shot` / `medium full-body` / `medium full-body` / `medium full-body` / `medium shot`
 * —— **一张 full body 都没有**。`medium` 就是中景（半身），出图模型读到 `medium` 就裁成半身；
 * `medium full-body` 这种自相矛盾的混搭，模型只会取 `medium`。规则文本压不住，所以这里确定性地改。
 *
 * 做法：
 * 1. 拍眼前的东西（[CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_SCENE]）不是人像 —— 一个字不动；
 * 2. 上下文明确只要一个局部（"只拍她的手"）—— 原样返回，尊重她的选择；
 * 3. 其余情况：把**半身类**景别词（medium 系列 / close-up / upper body / waist up / …）抹掉，
 *    没有全身说法的再补上；全身统一写成同一句，免得各写各的。
 */
object SceneFraming {

    /** 全身的标准说法 —— 与规则文本里那句保持一致。 */
    private const val FULL_BODY_EN =
        "full body, from head to toe, feet included, the whole figure fits in the frame"
    private const val FULL_BODY_CN =
        "全身，从头到脚都在画面里，脚没有被切掉，整个人都在画框里"

    /** 正文是否在描述脸/表情 —— 只有它确实写了脸，"脸没被裁"这条才有意义。 */
    private val MENTIONS_FACE = Regex(
        "(?i)\\b(face|facial|expression|eyes?|eyelids?|lips?|gaze|cheeks?|brows?|temples?)\\b" +
            "|\u8138|\u773c|\u5634\u5507|\u795e\u60c5|\u8868\u60c5"
    )

    /**
     * 明确**把头部排除在外**的框：上边界落在胸口及以下。
     *
     * 只认「先有一个构图词（framed / shot / view / close-up / crop），后面 80 字内跟着
     * `to <部位>`」这种**上边界**写法，避免误伤
     * `the sheet pulled up to her chest` 这类与构图无关的句子。
     */
    private val CROPS_HEAD = Regex(
        "(?i)(?:framed|frame|shot|view|close[- ]?up|crop(?:ped)?)[^.]{0,80}?" +
            "\\b(?:up\\s+)?to\\s+(?:her\\s+|the\\s+)?(?:chest|breasts?|waist|hips?|thighs?|knees?|lap)\\b"
    )

    /** 前置的那句。写得像画面描述、而不是给模型的指令。 */
    private const val FACE_IN_FRAME =
        "Her whole face is fully in frame and clearly visible, facial expression readable, face not cropped."

    /** 悬空的「介词 + 冠词」：器材词被删掉之后留下的残渣。 */
    private val DANGLING_PREPOSITION_ARTICLE = Regex(
        "(?i)\\b(?:at|into|toward|towards|to|facing|near|behind|on|in|of|with)\\s+the\\s*(?=[,.!?;:]|$)"
    )

    /** 悬空的冠词（前面没有介词的情形）。 */
    private val DANGLING_ARTICLE = Regex("(?i)\\bthe\\s*(?=[,.!?;:]|$)")

    /**
     * **抹掉器材词之后不许留下断句。**
     *
     * 真机实测（00158 的提示词）出现了三处：
     * `looking down at the,` / `looking straight into the with …` / `turned toward the,`。
     * 原因：器材词清洗（`SceneDeviceSuppression`）把 `camera` **单独替换成空串**，冠词留在原地。
     *
     * 危害不只是语法难看：渲染器看到 `looking straight into the` 会**自己把 the 补成相机**，
     * 于是"别看镜头"反而变成"一直看镜头"（用户现象：角色老看镜头）。
     */
    private fun stripDanglingArticles(raw: String): String = raw
        .replace(DANGLING_PREPOSITION_ARTICLE, " ")
        .replace(DANGLING_ARTICLE, " ")
        .replace(Regex("\\s{2,}"), " ")
        .replace(Regex("\\s+([,.])"), "$1")
        .trim()

    fun ensure(
        scene: String,
        shotType: CharacterPhotoShotPolicy.PhotoShotType,
        format: ScenePromptFormat = ScenePromptFormat.ENGLISH_TAGS
    ): String {
        // **先把断句修掉**（用户要求，2026-10-11）。放在最前面，所以下面每个分支都受益。
        val text = stripDanglingArticles(scene.trim())
        if (text.isEmpty()) return text
        // 拍的是眼前的东西、不是人像，谈景别没有意义。
        if (shotType == CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_SCENE) return text
        // 上下文明确只要一个局部时，不强行改成全身。
        if (LOCAL_ONLY.any { text.contains(it, ignoreCase = true) }) return text

        // 先把**半身类景别词抹掉** —— 它们是"只出半身"的直接原因。
        var out = text
        // **构图由模型自己决定**（用户要求，2026-10-11）：以前这里会把模型写的半身/特写类
        // 景别词逐个删掉，于是模型的构图选择从没机会存在。现在一个字都不删。
        // HALF_BODY_WORDS.forEach { word -> out = out.replace(word, "", ignoreCase = true) }
        out = out
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s+([,.])"), "$1")
            .replace(Regex("([,.])\\s*([,.])+"), "$1")
            .replace(Regex("\\(\\s*,?\\s*\\)"), "")
            .replace(Regex("\\[\\s*,?\\s*\\]"), "")
            .replace(Regex("\\b(?:from|at|in|of|with)\\s+([,.])"), "$1")
            .replace(Regex(",\\s*,"), ",")
            .trim()
            .trimStart(',', '.', ';', ' ')
            .trim()

        val chinese = format.wantsChineseInstructions
        // 补哪一种语言，看**这一形态最终提示词用什么语言**：不能用 allowsChinese
        //（它的意思是「编码器认不认中文」，krea2 同样为 true，但它的提示词是英文）。
        val already = if (chinese) {
            out.contains("全身") || out.contains("从头到脚")
        } else {
            Regex("full[\\s-]?body|head to toe|whole figure|feet included", RegexOption.IGNORE_CASE)
                .containsMatchIn(out)
        }
        if (already) return out

        // **不再追加"全身兜底"**（用户要求，2026-10-11）。
        //
        // 以前这里无条件补一句 `full body, from head to toe, feet included…`，与场景正文里
        // 模型自己写的景别（例如"大腿到头顶"）**直接矛盾**。真机实测：出图模型只认正文那句，
        // 而脸的像素被压到画面 ~1/8 → **提示词里表情写得很足，图里却看不出来**。
        // 现在模型写什么构图就是什么构图，代码不插手。
        // val body = if (chinese) FULL_BODY_CN else FULL_BODY_EN
        // val tail = out.trimEnd('。', '.', '，', ',', ';', '；', ' ')
        // return tail + (if (chinese) "，" else ", ") + body + (if (chinese) "。" else ".")
        // **脸不许被裁掉**（用户要求，2026-10-11）。
        //
        // 实测 bug：场景模型会写出「from her thighs to her chest」这种把头部排除在外的框，
        // 而**同一段里却在细写**「eyes half-lidded and glossy / loose strands sticking to her
        // damp temples」——自相矛盾，渲染器只能二选一：要么裁掉脸（表情永远看不见），
        // 要么硬塞进脸（几十像素、还容易画歪）。两张连续出图都是这个毛病。
        //
        // 这里**不强制全身、也不改模型写的景别**（用户明确要"让模型自己决定构图"），
        // 只在两个条件同时成立时动手：① 正文确实写了脸/表情 ② 出现了把上边界截在
        // 胸口及以下的框。做法是把"脸完整在画面里、没被裁"**前置到最前**——
        // 早出现的 token 权重更高，这是既不重写模型句子、又能压过后面那句裁切框的办法。
        if (MENTIONS_FACE.containsMatchIn(out) && CROPS_HEAD.containsMatchIn(out)) {
            out = FACE_IN_FRAME + " " + out
        }
        return out
    }

    /**
     * 「上下文明确只要局部」的判据 —— 命中就一个字不动。
     * 刻意收得很窄：只有明确排除全身的说法才算，免得把模型自己乱写的 close-up 当成了用户意图。
     */
    private val LOCAL_ONLY = listOf(
        "只拍", "特写", "局部特写", "只要那个局部", "不拍全身", "不出现全身",
        "only her hand", "only her hands", "only her eyes", "only her legs", "only her feet",
        "close-up of her hand", "close-up of her hands", "close-up of her eyes",
    )

    /**
     * 半身类景别词 —— 一律抹掉，它们会让出图模型把画面裁到腰或胸。
     * 长的写在前面，免得 `medium` 先把 `medium full-body` 切碎、留下半截 `full-body`。
     */
    private val HALF_BODY_WORDS = listOf(
        "medium full-body", "medium full body", "medium close-up", "medium closeup",
        "medium shot", "medium-shot", "medium",
        "close-up", "closeup", "close up",
        "upper body", "waist up", "half body", "half-body", "bust shot", "headshot",
        "head shot", "cropped view", "cropped",
        "半身", "中景", "近景", "特写",
    )
}
