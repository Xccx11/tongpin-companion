package com.aster.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 字号档位（**值与现状完全一致**，这次只是给它们起名字）。
 *
 * 之前 MainActivity 里有 240 处 `fontSize = ??.sp`，用到 16 种字号（9~25sp），
 * 同一层级的文字在不同地方写 11/12/13 —— 想统一时无从下手。
 * 这里把**出现频率最高的六档**命名，剩下的个位数场景（16sp 以上）保持裸值，
 * 因为那些是页面标题/大号数字，逐个改反而更容易出错。
 *
 * 想整体调大/调小一档，改这里的值即可（所有引用点跟着变）。
 */
object AsterType {
    /** 9~10sp：角标、时间戳、极小的辅助文字。 */
    val micro: TextUnit = 10.sp

    /** 11sp：说明文字、次要信息（出现最多的一档）。 */
    val caption: TextUnit = 11.sp

    /** 12sp：标签、列表副标题。 */
    val label: TextUnit = 12.sp

    /** 13sp：正文（次要段落、卡片正文）。 */
    val body: TextUnit = 13.sp

    /** 14sp：正文强调 / 列表主文字。 */
    val bodyLarge: TextUnit = 14.sp

    /** 15sp：小节标题。 */
    val title: TextUnit = 15.sp
}
