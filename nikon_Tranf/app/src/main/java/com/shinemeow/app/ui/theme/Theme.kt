package com.shinemeow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.shinemeow.app.UiSpec

/** 应用主题：dark = 深色模式开关（设置项，默认 false = 浅色）。
 *  colorScheme 随 UiSpec 当前配色主题构造 —— 按钮/开关/复选框/滑块/TextButton
 *  等所有读 MaterialTheme.colorScheme.primary 的控件都随主题联动切换。 */
@Composable
fun ShineMeowTheme(
    dark: Boolean = false,
    content: @Composable () -> Unit
) {
    val theme = UiSpec.currentTheme          // 读主题状态：切换主题即重建配色
    val cs = remember(dark, theme) { buildColorScheme(dark) }
    MaterialTheme(
        colorScheme = cs,
        typography = Typography,
        content = content
    )
}

/** 按当前主题构造 M3 配色：
 *  品牌层（primary/secondary/error 及派生容器色）取自 UiSpec 主题表；
 *  中性层（背景/表面/描边/文字）沿用原观感，保证换主题不晃眼。 */
private fun buildColorScheme(dark: Boolean): androidx.compose.material3.ColorScheme {
    val p = UiSpec.accent(dark)
    // 主色偏亮（深色模式常见）时用其暗版做前景，保证按钮文字对比度
    val onP = if (p.luminance() > 0.35f) Color(p.red * 0.25f, p.green * 0.25f, p.blue * 0.25f)
    else Color.White
    val surf = if (dark) Color(0xFF1B2023) else Color(0xFFFFFFFF)
    val mix = if (dark) 0.22f else 0.14f    // 主色混入表面的比例：得到淡底容器色
    val keep = 1f - mix
    val cont = Color(
        red = p.red * mix + surf.red * keep,
        green = p.green * mix + surf.green * keep,
        blue = p.blue * mix + surf.blue * keep
    )
    return if (dark) darkColorScheme(
        primary = p,
        onPrimary = onP,
        primaryContainer = cont,
        onPrimaryContainer = p,
        secondary = UiSpec.NEF_PURPLE,
        onSecondary = Color.White,
        background = Color(0xFF141719),
        onBackground = Color(0xFFE4E7EA),
        surface = Color(0xFF1B2023),
        onSurface = Color(0xFFE4E7EA),
        surfaceVariant = Color(0xFF262B30),
        onSurfaceVariant = Color(0xFF9BA1A6),
        error = UiSpec.error(dark),
        onError = Color(0xFF44100E),
        outline = Color(0xFF3A4146)
    ) else lightColorScheme(
        primary = p,
        onPrimary = onP,
        primaryContainer = cont,
        onPrimaryContainer = p,
        secondary = UiSpec.NEF_PURPLE,
        onSecondary = Color.White,
        background = Color(0xFFF7F8FA),
        onBackground = Color(0xFF26292C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF333333),
        surfaceVariant = Color(0xFFF1F3F5),
        onSurfaceVariant = Color(0xFF888888),
        error = UiSpec.error(dark),
        onError = Color.White,
        outline = Color(0xFFCCCCCC)
    )
}
