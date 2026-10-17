package dev.quiettasks.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import dev.quiettasks.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White, primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49), background = Color(0xFFF0F4F9), surface = Color.White,
    surfaceContainer = Color(0xFFF0F4F9), surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainerHigh = Color(0xFFE9EEF6), onSurface = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF444746), outline = Color(0xFF747775), outlineVariant = Color(0xFFC4C7C5),
    secondaryContainer = Color(0xFFD3E3FD), onSecondaryContainer = Color(0xFF041E49),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F), primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD), background = Color(0xFF111315), surface = Color(0xFF272A2E),
    surfaceDim = Color(0xFF111315), surfaceBright = Color(0xFF3C4147),
    surfaceContainerLowest = Color(0xFF111315), surfaceContainerLow = Color(0xFF191B1E),
    surfaceContainer = Color(0xFF272A2E), surfaceContainerHigh = Color(0xFF33373C),
    surfaceContainerHighest = Color(0xFF3C4147), surfaceVariant = Color(0xFF33373C), onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFC4C7C5), outline = Color(0xFF8E918F), outlineVariant = Color(0xFF444746),
    secondaryContainer = Color(0xFF004A77), onSecondaryContainer = Color(0xFFC2E7FF),
)

@Composable fun TasksTheme(mode: ThemeMode = ThemeMode.SYSTEM, deviceColors: Boolean = true, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.AMOLED || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val context = LocalContext.current
    val palette = if (deviceColors && Build.VERSION.SDK_INT >= 31) {
        (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).let {
            if (dark) {
                fun shaded(color: Color, fallback: Color) = if (color.toArgb() and 0xFFFFFF == 0) fallback else color
                val canvas = shaded(it.surfaceContainerLowest, Dark.background)
                it.copy(background = canvas, surface = shaded(it.surfaceContainerHigh, Dark.surface), surfaceDim = canvas,
                    surfaceBright = shaded(it.surfaceContainerHighest, Dark.surfaceBright), surfaceContainerLowest = canvas,
                    surfaceContainerLow = shaded(it.surfaceContainerLow, Dark.surfaceContainerLow),
                    surfaceContainer = shaded(it.surfaceContainerHigh, Dark.surfaceContainer),
                    surfaceContainerHigh = shaded(it.surfaceContainerHighest, Dark.surfaceContainerHigh),
                    surfaceContainerHighest = shaded(it.surfaceContainerHighest, Dark.surfaceContainerHighest),
                    surfaceVariant = shaded(it.surfaceVariant, Dark.surfaceVariant))
            } else it.copy(background = it.surfaceContainer, surface = it.surfaceContainerLow)
        }
    } else if (dark) Dark else Light
    val colors = if (mode == ThemeMode.AMOLED) palette.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceBright = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF050505),
        surfaceContainer = Color(0xFF0A0A0A), surfaceContainerHigh = Color(0xFF121212),
        surfaceContainerHighest = Color(0xFF1A1A1A), surfaceVariant = Color(0xFF121212), surfaceTint = Color.Black,
    ) else palette
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
    }
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark (shaded)"
    ThemeMode.AMOLED -> "AMOLED (pure black)"
}
