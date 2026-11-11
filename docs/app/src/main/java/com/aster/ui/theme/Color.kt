package com.aster.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tongpin's graphite and mist-rose color system.
 */
object AsterDarkColors {
    val bg = Color(0xFF111012)
    val surface1 = Color(0xFF1B181B)
    val surface2 = Color(0xFF262124)
    val surface3 = Color(0xFF312A2E)
    val text = Color(0xFFF7F0F1)
    val textSubtle = Color(0xFFC9BEC0)
    val textMuted = Color(0xFF897C80)
    val primary = Color(0xFFE8A8B2)
    val accent = Color(0xFFD9BDC2)
    // iOS 风：**不用描边分层**，只留一条几乎看不见的发丝线（原来 0x382F33 对比太强，
    // 满屏 1dp 描边就是"老安卓"的来源）。
    val border = Color(0xFF221D20)
    val borderBright = Color(0xFF59494E)
}

object AsterLightColors {
    val bg = Color(0xFFFFFEFD)
    val surface1 = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFFFF6F7)
    val surface3 = Color(0xFFFBECEE)
    val text = Color(0xFF3E3739)
    val textSubtle = Color(0xFF7D7476)
    val textMuted = Color(0xFFAAA1A3)
    val primary = Color(0xFFD98D9C)
    val accent = Color(0xFFEABEC6)
    // iOS 风：发丝线，不再用明显描边（原来 0xF0E5E7 在纯白卡上就是一圈"框"）。
    val border = Color(0xFFF6EFF0)
    val borderBright = Color(0xFFE6D5D8)
}

object SemanticColors {
    val success = Color(0xFF7BAA93)
    val successDim = Color(0xFF5D8A73)
    val successBright = Color(0xFFA1C9AF)

    // Warning (amber)
    val warning = Color(0xFFF59E0B)
    val warningDim = Color(0xFFD97706)
    val warningBright = Color(0xFFFBBF24)

    // Error (rose)
    val error = Color(0xFFF43F5E)
    val errorDim = Color(0xFFE11D48)
    val errorBright = Color(0xFFFB7185)

    // Info (violet)
    val info = Color(0xFF8B5CF6)
    val infoDim = Color(0xFF7C3AED)
    val infoBright = Color(0xFFA78BFA)
}
