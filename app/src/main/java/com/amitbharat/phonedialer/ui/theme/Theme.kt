package com.amitbharat.phonedialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.amitbharat.phonedialer.utils.ThemeMode

val PrimaryIndigo = Color(0xFF3B50DF)
val PrimaryIndigoDark = Color(0xFFBCC2FF)
val AccentGreen = Color(0xFF22C55E)
val AccentRed = Color(0xFFEF4444)
val SurfaceDark = Color(0xFF1A1B1F)
val BackgroundDark = Color(0xFF111216)
val SurfaceVariantDark = Color(0xFF25272F)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF000B5C),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigoDark,
    onPrimary = Color(0xFF00189B),
    primaryContainer = Color(0xFF1F35C7),
    onPrimaryContainer = Color(0xFFDFE0FF),
    background = BackgroundDark,
    onBackground = Color(0xFFE4E1E6),
    surface = SurfaceDark,
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFC7C5D0)
)

private val AmoledColorScheme = darkColorScheme(
    primary = PrimaryIndigoDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1F35C7),
    onPrimaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0A0A0A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF18181B),
    onSurfaceVariant = Color(0xFFA1A1AA)
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
