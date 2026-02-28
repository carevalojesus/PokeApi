package com.carevalojesus.pokeapi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SenatiSkyBlue,
    onPrimary = SenatiBlack,
    primaryContainer = SenatiBlueDark,
    onPrimaryContainer = SenatiSkyBlueLight,
    secondary = SenatiBlueLight,
    onSecondary = SenatiWhite,
    secondaryContainer = SenatiBlue,
    onSecondaryContainer = SenatiSkyBlueLight,
    tertiary = SenatiSkyBlueLight,
    onTertiary = SenatiBlack,
    background = SenatiDarkGray,
    onBackground = SenatiWhite,
    surface = SenatiBlack,
    onSurface = SenatiWhite,
    surfaceVariant = SenatiBlackLight,
    onSurfaceVariant = SenatiMediumGray,
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = SenatiBlackLight
)

private val LightColorScheme = lightColorScheme(
    primary = SenatiBlue,
    onPrimary = SenatiWhite,
    primaryContainer = SenatiSkyBlueLight.copy(alpha = 0.3f),
    onPrimaryContainer = SenatiBlueDark,
    secondary = SenatiSkyBlueDark,
    onSecondary = SenatiWhite,
    secondaryContainer = SenatiSkyBlueLight.copy(alpha = 0.25f),
    onSecondaryContainer = SenatiBlueDark,
    tertiary = SenatiSkyBlue,
    onTertiary = SenatiWhite,
    background = SenatiLightGray,
    onBackground = SenatiBlack,
    surface = SenatiWhite,
    onSurface = SenatiBlack,
    surfaceVariant = SenatiMediumGray,
    onSurfaceVariant = SenatiBlackLight,
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = SenatiMediumGray
)

@Composable
fun PokeApiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
