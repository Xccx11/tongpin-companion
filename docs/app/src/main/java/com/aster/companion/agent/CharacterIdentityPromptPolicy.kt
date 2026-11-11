package com.aster.companion.agent

/**
 * 外貌锚点的职责边界：**只保留「她长什么样」**。
 *
 * 用户的原话是「生图应该是角色决定穿什么、做什么、什么场景，而不是单单只看我给的提示词」。
 * 但外貌提示词里通常同时也写了服装、姿势、表情、光线、地点——这些是**当刻**的东西，
 * 每张图都拼上去就会把画面钉死成同一个样子。所以出图时只取长相部分：
 *
 * - 保留：年龄观感、脸型、五官、眼型瞳色、妆容、发型发色、肤色、身材、发型饰品，以及画风/质量词（造型一致性靠它）；
 * - 剔除：服装/内衣/袜子/鞋、姿势、表情与情绪、光线、地点/背景、镜头与景别。
 *
 * 只用于**出图时**；数据库里用户填的原文一个字不改，卡片上也提示剔掉了什么。
 */
object CharacterIdentityPromptPolicy {

    /** 只取长相部分；全部被剔掉时退回原文，避免把锚点清空。 */
    fun identityOnly(appearance: String): String {
        val parts = appearance.split(COMMA).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return appearance.trim()
        val kept = parts.filterNot { isTransient(it) }
        return if (kept.isEmpty()) appearance.trim() else kept.joinToString(", ")
    }

    /** 剔掉的是不是「当刻」的词（服装/姿势/表情/光线/地点/镜头）。 */
    /** 外貌里的分隔符：半/全角逗号、顿号、分号、换行 —— 中文外貌几乎都用顿号，不认它就会整段误剔。 */
    private val COMMA = Regex("[,，、;；\\n]")

    fun isTransient(tag: String): Boolean {
        val trimmed = tag.trim()
        // 外貌常常是用户写的一整段英文，**句子之间是句号、不是逗号**，于是一个逗号段里可能塞着好几句话。
        // 那种长段一律当作用户自己写的外貌描述原样保留：按子串去剔会把脸型、下颌线这些一起删掉
        //（实测 "a photorealistic portrait of an 18-year-old bratty …" 一段里，`bra` 命中 "bratty"，
        // 整段被删，连句中的 oval face / jawline / chin 一起丢）。只对短 tag 判定。
        if (trimmed.length > TRANSIENT_TAG_MAX_CHARS) return false
        val lower = trimmed.lowercase()
        if (TRANSIENT_CJK_WORDS.any { lower.contains(it) }) return true
        return TRANSIENT_EN_PATTERN.containsMatchIn(lower)
    }

    /** 剔掉过东西就返回一句提示，交给卡片显示；没剔就返回 null。 */
    fun identityNote(appearance: String): String? {
        val removed = appearance.split(COMMA).map { it.trim() }
            .filter { it.isNotEmpty() && isTransient(it) }
        if (removed.isEmpty()) return null
        val sample = removed.take(3).joinToString("、") { it.take(18) }
        return "外貌里原本写了「$sample」这类当刻内容，出图只取了长相；穿什么、做什么、在哪由她此刻决定"
    }

    /**
     * 这些词代表「当刻」的东西。注意**不要**把身材类词（breast/hip/waist 之外的身体描述）算进来，
     * 它们是长相的一部分；也不要动画风与质量词。
     */
    /** 超过这个长度就当是整句外貌描述：不做当刻判定，整段保留。 */
    private const val TRANSIENT_TAG_MAX_CHARS = 60

    private val TRANSIENT_WORDS = listOf(
        // 服装 / 内衣 / 袜子 / 鞋
        "dress", "skirt", "sweater", "shirt", "t-shirt", "tshirt", "blouse", "hoodie", "jacket",
        "coat", "cardigan", "cheongsam", "qipao", "kimono", "yukata", "uniform", "suit", "vest",
        "jeans", "shorts", "pants", "trousers", "leggings", "camisole", "apron", "nightgown",
        "lingerie", "bra", "brassiere", "panties", "underwear", "thong", "garter", "bikini",
        "stockings", "pantyhose", "thighhighs", "thigh-high", "socks", "boots", "shoes", "heels",
        "sneakers", "sandals", "slippers", "gloves", "scarf", "hat", "cap", "beret", "belt",
        "suspender", "swimsuit", "towel", "robe", "pajamas", "pyjamas", "outfit", "wearing",
        // 姿势 / 动作
        "sitting", "standing", "lying", "kneeling", "crouching", "walking", "running", "leaning",
        "crossed legs", "spreading", "raising", "holding", "hands on", "arms", "turned away",
        // 表情 / 情绪
        "expression", "smile", "smiling", "frown", "blush", "crying", "tears", "shy", "aloof",
        "cold expression", "serious", "angry", "happy", "sad", "surprised", "eyes closed",
        "looking at viewer", "looking away", "eye contact",
        // 光线 / 环境 / 地点
        "lighting", "backlight", "sunlight", "candlelight", "moonlight", "neon light", "warm light",
        "soft light", "dim light", "shadow", "indoor", "outdoor",
        "bedroom", "bathroom", "kitchen", "living room", "classroom", "office", "balcony", "garden",
        "park", "street", "beach", "room", "window", "background", "night", "daytime", "noon",
        "evening", "sunset", "rain", "snow",
        // 镜头 / 景别 / 构图
        "close-up", "close up", "full body", "upper body", "cowboy shot", "portrait", "from above",
        "from below", "from side", "from behind", "pov", "selfie", "depth of field", "bokeh",
        "composition", "framing", "angle", "centered",
        // 中文：同一批概念（外貌用中文写时也要能剔）
        "长裙", "短裙", "裙子", "连衣裙", "旗袍", "和服", "制服", "上衣", "衬衫", "毛衣", "外套",
        "内衣", "胸罩", "内裤", "丝袜", "连裤袜", "过膝袜", "袜子", "鞋", "靴", "高跟", "拖鞋",
        "躺着", "躺在", "坐着", "坐在", "站着", "站在", "跪着", "跪在", "靠着", "靠在", "趴在",
        "蹲着", "姿势", "表情", "微笑", "害羞", "冷淡", "回头", "侧身", "俯视", "仰视",
        "光线", "灯光", "暖光", "室内", "室外", "卧室", "浴室", "厨房", "客厅", "窗外", "窗边", "窗前",
        "背景", "夜景", "台灯", "灯光下",
        "特写", "近景", "全身", "半身", "构图", "镜头", "自拍"
    )

    /** 英文当刻词合成一条正则做**整词**匹配：子串匹配会把 "bra" 命中 "bratty"、"hat" 命中 "that"。 */
    private val TRANSIENT_EN_PATTERN: Regex by lazy {
        Regex(
            "(?<![a-z0-9])(?:" +
                TRANSIENT_WORDS.filter { word -> word.all { it.code < 128 } }
                    .sortedByDescending(String::length)
                    .joinToString("|") { Regex.escape(it) } +
                ")(?![a-z0-9])"
        )
    }

    private val TRANSIENT_CJK_WORDS = TRANSIENT_WORDS.filter { word -> word.any { it.code >= 128 } }
}
