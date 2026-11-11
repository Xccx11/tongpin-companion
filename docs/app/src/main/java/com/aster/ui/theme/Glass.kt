package com.aster.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 同频的材质层。规范见 DESIGN.md。
 *
 * 三条原则：
 * 1. **内容永不用玻璃** —— 聊天气泡、出图、提示词正文都是纯色（用 [contentSurface]）。
 * 2. **材质是成套的** —— 越厚 = 越不透明 **且** 模糊越大（12/20/32dp）。
 * 3. **tint 取自己的表面色**，不是纯白 —— 纯白压在雾玫瑰底上会发冷。
 *
 * ## 模糊怎么来
 *
 * Compose 自带的 `Modifier.blur` 模糊的是元素**自身**的内容，不是它背后的背景，
 * 所以真正的 backdrop blur 交给 haze。用法固定是两步：
 *
 * ```
 * val hazeState = remember { HazeState() }
 * Box {
 *     ChatContent(Modifier.hazeSource(hazeState))     // 背景：要被采样的那一层
 *     Overlay(Modifier.hazeEffect(hazeState, style))  // 前景：玻璃
 * }
 * ```
 *
 * 两条必须记住的约束：
 * - **玻璃层必须和 `hazeSource` 在同一个 Compose 窗口**。`Dialog` / `Popup` /
 *   `ModalBottomSheet` 各自开新窗口，haze 采不到背景 —— 弹层要做成同窗口的 overlay，
 *   不要用 Dialog，否则玻璃会退化成一块纯色。
 * - haze 的真模糊依赖 `RenderEffect`，**API 31 以下不生效**，会退回 `fallbackTint` 涂色。
 *   在 minSdk 26 的机器上玻璃只剩「半透明 + 描边」，这是已知降级，不是 bug。
 */
enum class GlassTier(
    /**
     * 浅色底下的不透明度。
     *
     * **这是叠在模糊之上的色层，不是模糊本身**——值越高，模糊越看不见。
     * 第一版直接抄了 DESIGN.md 的 `.55/.72/.82`，结果 `Thick` 用 .82 把模糊整个盖住，
     * 玻璃看着就是一块实色板。所以整体压下来，只保留「越厚越不透明」的秩序。
     */
    val lightAlpha: Float,
    /** 深色底下的不透明度 —— 比浅色再低一点，否则在深色里会糊成一块死黑。 */
    val darkAlpha: Float,
    /** backdrop blur 半径。 */
    val blur: Dp,
    val corner: Dp
) {
    Thin(0.34f, 0.30f, 12.dp, 18.dp),
    Regular(0.48f, 0.44f, 20.dp, 24.dp),
    Thick(0.60f, 0.56f, 32.dp, 24.dp)
}

/** 没有真模糊时（API < 31，或者调用方没给 hazeState）色层必须更实，否则背后的字会赤裸裸透出来。 */
private const val OPAQUE_TINT_ALPHA = 0.88f

/**
 * 玻璃的底色取**自己的表面色**而不是纯白：DESIGN.md §1 的三组玻璃值
 * （浅 `#FFFFFF/#FFF6F7/#FBECEE`、深 `#1B181B/#262124/#312A2E`）就是
 * `surface1 / surface2 / surface3`，深浅两侧同源。
 */
private fun tintSource(tier: GlassTier, colors: AsterColorScheme): Color = when (tier) {
    GlassTier.Thin -> colors.surface3
    GlassTier.Regular -> colors.surface2
    GlassTier.Thick -> colors.surface1
}

private fun tintAlpha(tier: GlassTier, colors: AsterColorScheme): Float =
    if (colors.isDark) tier.darkAlpha else tier.lightAlpha

/** 把 [tier] 翻成 haze 的样式。 */
private fun hazeStyle(tier: GlassTier, colors: AsterColorScheme): HazeStyle {
    val surface = tintSource(tier, colors)
    val tint = HazeTint(surface.copy(alpha = tintAlpha(tier, colors)))
    return HazeStyle(
        // haze 在绘制前会先往捕获层里铺一层底色（BlurEffect.kt 的 `drawRect(bg)`），
        // 缺了这个必填项会在**运行到绘制那一帧时**抛
        // "IllegalArgumentException: backgroundColor not specified" —— 编译期查不出来。
        // 它只是捕获层的底，不是玻璃的颜色：玻璃的深浅完全由 tint 决定。
        backgroundColor = colors.bg,
        tint = tint,
        blurRadius = tier.blur,
        // 不加噪点：DESIGN.md §4.5 禁止玻璃上出现腮红/脏斑类颗粒。
        noiseFactor = 0f,
        // API 31 以下没有真模糊，用更实的一层色顶上。
        fallbackTint = HazeTint(surface.copy(alpha = OPAQUE_TINT_ALPHA))
    )
}

/**
 * 次级/悬浮界面的表面。
 *
 * 传了 [hazeState] 就是真玻璃：背景由 `hazeSource` 采出来糊掉，这里只加 tint、描边和浮起。
 * 不传则退化成「半透明 + 描边 + 浮起」——留给没有 hazeSource 的孤立场合。
 */
@Composable
fun Modifier.glassSurface(
    tier: GlassTier,
    hazeState: HazeState? = null,
    /** 通栏表面（顶栏）传 `RectangleShape`；默认是 [tier] 自带的圆角。 */
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(tier.corner)
): Modifier {
    val colors = AsterTheme.colors
    val base = this
        .shadow(
            elevation = 10.dp,
            shape = shape,
            clip = false,
            ambientColor = colors.text.copy(alpha = 0.10f),
            spotColor = colors.text.copy(alpha = 0.12f)
        )
        .clip(shape)
    val filled = if (hazeState != null) {
        base.hazeEffect(hazeState, style = hazeStyle(tier, colors))
    } else {
        base.background(tintSource(tier, colors).copy(alpha = tintAlpha(tier, colors)))
    }
    return filled.border(width = 1.dp, color = colors.borderBright.copy(alpha = 0.7f), shape = shape)
}

/**
 * 内容表面：纯色，永远不用玻璃。正文、可编辑的文本、图片都落在这里。
 */
@Composable
fun Modifier.contentSurface(corner: Dp = 14.dp): Modifier {
    val colors = AsterTheme.colors
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(colors.surface1)
        .border(width = 1.dp, color = colors.border, shape = shape)
}

/** 胶囊：贴在内容上的轻标签（状态、进度）。 */
@Composable
fun Modifier.glassCapsule(hazeState: HazeState? = null): Modifier {
    val colors = AsterTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    val base = this
        .shadow(
            elevation = 6.dp,
            shape = shape,
            clip = false,
            ambientColor = colors.text.copy(alpha = 0.08f),
            spotColor = colors.text.copy(alpha = 0.10f)
        )
        .clip(shape)
    val filled = if (hazeState != null) {
        base.hazeEffect(hazeState, style = hazeStyle(GlassTier.Thin, colors))
    } else {
        base.background(
            tintSource(GlassTier.Thin, colors).copy(alpha = tintAlpha(GlassTier.Thin, colors))
        )
    }
    return filled
        .border(width = 1.dp, color = colors.borderBright.copy(alpha = 0.6f), shape = shape)
        .padding(horizontal = 14.dp, vertical = 8.dp)
}
