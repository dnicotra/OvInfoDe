package io.github.dnicotra.ovinfode.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B4F8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF8A6A00),
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF2B2000),
    background = Color(0xFFFBFCFF),
    surface = Color(0xFFFBFCFF),
    surfaceContainerLow = Color(0xFFF3F5FA),
    surfaceContainer = Color(0xFFECEFF5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3C9FF),
    onPrimary = Color(0xFF00315B),
    primaryContainer = Color(0xFF004880),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFF1C100),
    secondaryContainer = Color(0xFF5C4700),
    onSecondaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF111418),
    surface = Color(0xFF111418),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
)

/** Colours with a fixed meaning, independent of the (possibly dynamic) Material scheme. */
@Immutable
data class StatusColors(val onTime: Color, val delayed: Color)

private val LightStatus = StatusColors(onTime = Color(0xFF1B7F2A), delayed = Color(0xFFC62828))
private val DarkStatus = StatusColors(onTime = Color(0xFF7DD98A), delayed = Color(0xFFFF8A80))

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
