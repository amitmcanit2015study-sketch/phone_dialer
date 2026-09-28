package com.amitbharat.phonedialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.amitbharat.phonedialer.utils.ThemeMode

val PrimaryIndigo = Color(0xFF3B50DF)
val PrimaryIndigoDark = Color(0xFF818CF8)
val AccentGreen = Color(0xFF22C55E)
val AccentRed = Color(0xFFEF4444)
val SurfaceDark = Color(0xFF1E222B)
val BackgroundDark = Color(0xFF11141A)
val SurfaceVariantDark = Color(0xFF282D37)

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
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    background = BackgroundDark,
    onBackground = Color(0xFFF8FAFC),
    surface = SurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val AmoledColorScheme = darkColorScheme(
    primary = PrimaryIndigoDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E1B4B),
    onPrimaryContainer = Color(0xFFE0E7FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF12141A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C2029),
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
