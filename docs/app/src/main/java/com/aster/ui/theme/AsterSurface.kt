package com.aster.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS 风设计基线：圆角 / 间距 / 阴影统一成几档。
 *
 * 分层原则：**靠极浅阴影 + 留白**，不靠描边。
 *
 * 四档圆角 + 一档间距，全部有名字（以前是 6/8/10/12/13/14/15/16/17/18/20/22/24 十四个裸数字，
 * 看着乱但说不出哪乱）：
 * - [cardRadius] 18：卡片、面板、气泡；
 * - [controlRadius] 16：按钮 / 输入框 / 小卡片 / 卡片内的图；
 * - [microRadius] 12：标签、筛选片、内嵌小图；
 * - [gap] 8：间距统一用它。
 * 只剩极个别 6/8.dp 的微元素（下拉项高亮、占位框）还写裸值 —— 再收会把它们收没了。
 */
object AsterMetrics {
    /** 卡片圆角（设置项、模型卡、胶囊展开块、消息气泡）。 */
    val cardRadius: Dp = 18.dp

    /** 消息气泡圆角。 */
    val bubbleRadius: Dp = 18.dp

    /** 按钮 / 输入框 / 下拉等控件圆角。 */
    val controlRadius: Dp = 16.dp

    /** 标签、筛选片、内嵌小图的圆角（比控件再小一档）。 */
    val microRadius: Dp = 12.dp

    /** 统一间距（8 格）。 */
    val gap: Dp = 8.dp

    /** 卡片内边距。 */
    val cardPadding: Dp = 16.dp

    /** 卡片阴影：极浅，只用来"起一层"，不做戏剧化投影。 */
    val cardElevation: Dp = 1.dp
}

/** 卡片：**阴影 + 圆角背景，不画边框**。 */
@Composable
fun Modifier.asterCard(
    colors: AsterColorScheme,
    radius: Dp = AsterMetrics.cardRadius,
    background: Color = colors.surface1,
    elevation: Dp = AsterMetrics.cardElevation
): Modifier = this
    .shadow(elevation, RoundedCornerShape(radius), clip = false)
    .background(background, RoundedCornerShape(radius))
