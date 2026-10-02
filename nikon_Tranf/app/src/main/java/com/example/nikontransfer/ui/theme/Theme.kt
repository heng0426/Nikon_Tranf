package com.example.nikontransfer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 浅色配色：白底 + 品牌青主色（当前默认外观，保持既有观感） */
private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = TealOnPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = PurpleBadge,
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF26292C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF333333),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF888888),
    error = Color(0xFFB71C1C),
    onError = Color.White,
    outline = Color(0xFFCCCCCC)
)

/** 深色配色：深灰底 + 提亮青主色 */
private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = PurpleBadgeDark,
    onSecondary = Color(0xFF31113B),
    background = Color(0xFF141719),
    onBackground = Color(0xFFE4E7EA),
    surface = Color(0xFF1B2023),
    onSurface = Color(0xFFE4E7EA),
    surfaceVariant = Color(0xFF262B30),
    onSurfaceVariant = Color(0xFF9BA1A6),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF44100E),
    outline = Color(0xFF3A4146)
)

/** 应用主题：dark = 深色模式开关（设置项，默认 false = 浅色） */
@Composable
fun NikonTransferTheme(
    dark: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
