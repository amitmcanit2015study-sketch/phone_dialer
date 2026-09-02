package com.amitbharat.phonedialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.amitbharat.phonedialer.utils.ThemeMode

val PrimaryBlue = Color(0xFF006495)
val PrimaryBlueDark = Color(0xFF8FCDFF)
val AccentGreen = Color(0xFF22C55E)
val AccentRed = Color(0xFFEF4444)
val SurfaceDark = Color(0xFF0F172A)
val BackgroundDark = Color(0xFF0B0F19)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E30),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color(0xFF003450),
    primaryContainer = Color(0xFF004B72),
    onPrimaryContainer = Color(0xFFCBE6FF),
    background = BackgroundDark,
    onBackground = Color(0xFFF1F5F9),
    surface = SurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val AmoledColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004B72),
    onPrimaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0A0A0A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8)
)

@Composable
fun PhoneDialerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
