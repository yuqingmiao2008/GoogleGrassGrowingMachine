package io.github.yuqingmiao2008.grassmachine.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

/**
 * 配色方案。
 * themeMode: system / light / dark / black（OLED 纯黑）
 */
private val Green = Color(0xFF2E7D32)
private val GreenLight = Color(0xFF81C784)
private val Lime = Color(0xFFB2FF59)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0B8),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF52634F),
    secondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFF38656A),
    background = Color(0xFFFBFDF7),
    surface = Color(0xFFFBFDF7),
    surfaceVariant = Color(0xFFDEE5D8),
    outline = Color(0xFF74796D),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF00390E),
    primaryContainer = Color(0xFF00531A),
    onPrimaryContainer = Color(0xFFB7F0B8),
    secondary = Color(0xFFBACCB4),
    secondaryContainer = Color(0xFF3A4B38),
    tertiary = Color(0xFFA0CFD4),
    background = Color(0xFF10140F),
    surface = Color(0xFF10140F),
    surfaceVariant = Color(0xFF42493E),
    outline = Color(0xFF8C9385),
)

/** OLED 纯黑：所有容器色压到 #000000，减少像素发光 */
private val BlackColors = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF123015),
    onPrimaryContainer = Color(0xFFCBFFA0),
    secondary = Color(0xFFBACCB4),
    secondaryContainer = Color(0xFF1A221A),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD4),
    tertiaryContainer = Color(0xFF0D2224),
    background = Color.Black,
    onBackground = Color(0xFFE6E9E1),
    surface = Color.Black,
    onSurface = Color(0xFFE6E9E1),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFBFC5B8),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF171717),
    outline = Color(0xFF4A4F46),
    outlineVariant = Color(0xFF23261F),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF2A0A08),
)

@Composable
fun GgmTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = false,
    fontSp: Int = 14,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "light" -> false
        "dark", "black" -> true
        else -> systemDark
    }
    val context = LocalContext.current
    val colors = when {
        themeMode == "black" -> BlackColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val baseDensity = LocalDensity.current
    val scale = (fontSp / 14f).coerceIn(0.75f, 2.0f)

    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * scale)
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
