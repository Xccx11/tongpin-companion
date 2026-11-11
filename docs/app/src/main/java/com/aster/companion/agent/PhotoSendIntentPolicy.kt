package com.aster.companion.agent

/**
 * Lite 私聊里判断「角色这句话是在说要给我发照片吗」。
 *
 * 角色主动发图的预览卡完全依赖这个判断（Lite 聊天不带 tools），所以判定**宁松勿漏**：
 * 只要她话里出现照片类名词、拍/发/传的动作、或举起手机对着镜头这类行为，就给她一张预览卡——
 * 多弹一次用户可以取消，漏掉一次她就「说了发照片却没反应」。
 *
 * 只有两种情况不弹：她明确拒绝（不想发照片），以及话题明显是截图/邮件/链接这类非人物照。
 */
object PhotoSendIntentPolicy {

    fun matches(reply: String): Boolean {
        val text = reply.replace(WHITESPACE, "")
        if (text.length < 4) return false
        if (REFUSAL.containsMatchIn(text)) return false
        // 已经说出「照片/自拍」这类词：直接算发图意图，不再看下面的非人物照排除。
        if (STRONG_PHRASE.any { text.contains(it) }) return true
        if (SEND_BEFORE_NOUN.containsMatchIn(text)) return true
        if (NOUN_BEFORE_SEND.containsMatchIn(text)) return true
        // 照片类名词 + 任何交出/展示动作（任意语序）。
        if (PHOTO_WORDS.any { text.contains(it) } && SEND_CHARS.containsMatchIn(text)) return true
        // 到这一步都还没说出「照片/自拍」，只排掉截图、邮件、链接这类明显不是人物照的。
        if (NOT_SELF.containsMatchIn(text)) return false
        // 「拍好了/挑好了…」+ 交出动作。
        if (READY_THEN_SEND.containsMatchIn(text)) return true
        // 「发一张/拍张…」+ 给谁，或干脆省略了「照片」二字。
        if (QUANTITY_THEN_TARGET.containsMatchIn(text)) return true
        if (QUANTITY_THEN_SELF.containsMatchIn(text)) return true
        if (QUANTITY_BARE.containsMatchIn(text)) return true
        // 「已经发出去了 / 送过去了 / 查收」这类：她没提「照片」二字，但在这种私聊里就是发图。
        if (ALREADY_SENT.containsMatchIn(text)) return true
        // 拍照本体动作：举起手机、对着镜头、按快门本身就是一次出图。
        if (ACTION_HINTS.any { text.contains(it) }) return true
        return false
    }

    private val NOUN = "照片|自拍|图片|写真|生活照|相片|美照|照片儿"
    private val WHITESPACE = Regex("\\s+")

    private val PHOTO_WORDS = listOf("照片", "自拍", "图片", "写真", "生活照", "相片", "美照", "拍一张", "拍张")

    private val STRONG_PHRASE = listOf(
        "发照片", "发张照片", "发你照片", "发给你照片", "拍照片", "拍张照片",
        "给你发照片", "给你看照片", "发自拍", "拍自拍", "自拍发", "发张自拍",
        "照片发", "照片给你", "照片你看", "照片我发",
        "发个照片", "照给你", "拍给你看", "发给你看",
        "发张给你", "拍张给你", "发你看看", "给你看看我", "发张照", "照片过来"
    )

    /** 「发/拍/传/晒/分享…」到「照片/自拍…」之间最多隔 16 个字。 */
    private val SEND_BEFORE_NOUN = Regex("(发|拍|传|晒|分享|展示)[^。！？!?；;]{0,16}($NOUN)")

    /** 反序：「照片…给你/你看/发你」。 */
    private val NOUN_BEFORE_SEND = Regex(
        "($NOUN)[^。！？!?；;]{0,12}" +
            "(给你|你看|看看|发你|传你|发过去|传给你|发过来|传过来|拍给|发去|马上发|这就发|瞧瞧|瞅瞅|分享一下)"
    )

    /** 照片类名词 + 任意交出/展示动作（单字也算，宁松勿漏）。 */
    private val SEND_CHARS = Regex("(发|拍|传|给|看|瞧|晒|分享)")

    /** 「拍好了/挑好了…」+ 交出动作，即使没说出「照片」二字也是在发图。 */
    private val READY_THEN_SEND = Regex(
        "(拍好了|拍完了|拍完|挑好了|选好了|整理好了|整理好|准备好了|拍完啦)[^。！？!?]{0,8}" +
            "(给你|发你|传你|发过去|传过去|发过来|传过来|马上|一会儿)"
    )

    /** 「发一张/拍张…」+ 给谁（允许「拍了张」这种中间夹个「了/一/个」）。 */
    private val QUANTITY_THEN_TARGET = Regex(
        "(发|拍|传|晒)(了|一|个|儿)?(一张|张|幅)[^。！？!?]{0,6}(给你|你看|看看|你|过去|过来|瞧|我)"
    )

    /** 「发一张我现在的样子」这类，用「样子/本人」代替了「照片」二字。 */
    private val QUANTITY_THEN_SELF = Regex("(发|拍|传)(了|一|个|儿)?(一张|张|幅)[^。！？!?]{0,12}(样子|本人|自己)")

    /** 「发张」「拍一张」「拍了张」连目标都省了，也当成她准备拍。 */
    private val QUANTITY_BARE = Regex("(发|拍|传|晒)(了|一|个|儿)?(一张|张|幅)")

    /**
     * 已经发出去的说法：发出去了 / 发过去了 / 传过去了 / 送过去了 / 查收 / 收到了吗。
     *
     * 私聊里她说「发出去了」就是在说图，不会再指别的东西；
     * 非人物照（文件/链接/邮箱/资料）在上面已经被 NOT_SELF 排掉了。
     */
    private val ALREADY_SENT = Regex(
        "(发出去|发过去|传过去|送过去|发给你了|传给你了|发你了|传你了|给你了)" +
            "|(查收)|(收到了吗|收到没有|收到了没)"
    )

    /** 明确的拒绝，不能当成愿意发图。 */
    private val REFUSAL = Regex(
        "(不想|不愿|不用|不打算|不准备|懒得|休想|不能|不该|别)[^。！？!?]{0,3}(发|拍|传|晒|分享)($NOUN)"
    )

    /**
     * 明显不是人物照的话题。只在**没出现照片类名词**时用来排除，
     * 所以这里只留最不会误伤的几条：截图、邮件、链接。
     */
    private val NOT_SELF = Regex("(截图|截屏|屏幕截图|二维码|邮箱|邮件|链接)")

    private val ACTION_HINTS = listOf(
        "举起手机", "拿起手机", "掏出手机", "对着镜头", "对着镜子", "按快门", "摆姿势",
        "拍照", "自拍", "相机", "镜头", "拍张", "拍一张", "拍完", "拍好了", "挑好了",
        "发图", "传图", "展示自己", "给你看看我", "我拍给", "我这就拍", "我这就发"
    )
}
