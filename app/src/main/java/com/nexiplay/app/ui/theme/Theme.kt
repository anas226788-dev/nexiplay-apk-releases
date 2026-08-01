package com.nexiplay.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.nexiplay.app.R

// ── Inter Font Family ──
val InterFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_black, FontWeight.Black),
)

// ── Theme State (for Day/Night toggle) ──
object ThemeState {
    var isDarkMode by mutableStateOf(true)
}

// ── Dark Color Scheme ──
private val DarkColorScheme = darkColorScheme(
    primary = NexiRed,
    onPrimary = TextWhite,
    secondary = NexiRedLight,
    tertiary = CoinGold,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextGray,
    outline = DarkBorder,
    error = ErrorRed,
)

// ── Light Color Scheme ──
private val LightColorScheme = lightColorScheme(
    primary = NexiRed,
    onPrimary = TextWhite,
    secondary = NexiRedLight,
    tertiary = CoinGold,
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = LightCard,
    onBackground = TextBlack,
    onSurface = TextBlack,
    onSurfaceVariant = TextLightGray,
    outline = LightBorder,
    error = ErrorRed,
)

// ── Typography with Inter Font ──
private val NexiTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
    ),
)

@Composable
fun NexiPlayTheme(
    darkTheme: Boolean = ThemeState.isDarkMode,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (darkTheme) {
                window.statusBarColor = DarkBg.toArgb()
                window.navigationBarColor = DarkSurface.toArgb()
            } else {
                window.statusBarColor = LightBg.toArgb()
                window.navigationBarColor = LightSurface.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NexiTypography,
        content = content,
    )
}

// ── Theme-aware color helpers ──
val isDark: Boolean
    @Composable
    get() = ThemeState.isDarkMode

@Composable fun themeBg() = if (isDark) DarkBg else LightBg
@Composable fun themeSurface() = if (isDark) DarkSurface else LightSurface
@Composable fun themeCard() = if (isDark) DarkCard else LightCard
@Composable fun themeBorder() = if (isDark) DarkBorder else LightBorder
@Composable fun themeTextPrimary() = if (isDark) TextWhite else TextBlack
@Composable fun themeTextSecondary() = if (isDark) TextGray else TextLightGray
@Composable fun themeTextTertiary() = if (isDark) TextDarkGray else TextLightGray
