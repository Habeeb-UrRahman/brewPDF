package com.pdfmerger.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrewAmber,
    onPrimary = EspressoBlack,
    primaryContainer = BrewAmberContainer,
    onPrimaryContainer = BrewAmberLight,
    secondary = BrewTerracotta,
    onSecondary = EspressoBlack,
    secondaryContainer = Color(0xFF3D2018),
    onSecondaryContainer = BrewTerracottaLight,
    tertiary = BrewSage,
    onTertiary = EspressoBlack,
    tertiaryContainer = Color(0xFF1A3326),
    onTertiaryContainer = BrewSageLight,
    background = EspressoBlack,
    onBackground = WarmGray95,
    surface = EspressoBlack,
    onSurface = WarmGray95,
    surfaceVariant = EspressoContainer,
    onSurfaceVariant = WarmGray80,
    surfaceContainerLowest = EspressoBlack,
    surfaceContainerLow = Color(0xFF141210),
    surfaceContainer = EspressoContainer,
    surfaceContainerHigh = EspressoContainerHigh,
    surfaceContainerHighest = EspressoContainerHighest,
    outline = WarmGray60,
    outlineVariant = WarmGray40,
    error = Color(0xFFCF6679),
    errorContainer = Color(0xFF3D1C1C),
    onError = EspressoBlack,
    onErrorContainer = Color(0xFFFFB4AB),
)

private val LightColorScheme = lightColorScheme(
    primary = BrewAmberDark,
    onPrimary = Color.White,
    primaryContainer = BrewAmberContainerLight,
    onPrimaryContainer = BrewAmberDark,
    secondary = BrewTerracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE9E7),
    onSecondaryContainer = BrewTerracottaDark,
    tertiary = BrewSageDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2E9),
    onTertiaryContainer = BrewSageDark,
    background = CreamWhite,
    onBackground = Color(0xFF1C1912),
    surface = CreamSurface,
    onSurface = Color(0xFF1C1912),
    surfaceVariant = CreamContainer,
    onSurfaceVariant = Color(0xFF504839),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = CreamWhite,
    surfaceContainer = CreamContainer,
    surfaceContainerHigh = CreamContainerHigh,
    surfaceContainerHighest = Color(0xFFE5DDD0),
    outline = Color(0xFFCCC0B0),
    outlineVariant = Color(0xFFDDD4C6),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onError = Color.White,
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
fun PdfMergerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Tint system bars to match the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BrewTypography,
        content = content
    )
}
