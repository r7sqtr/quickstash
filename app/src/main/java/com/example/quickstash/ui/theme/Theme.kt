package com.example.quickstash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QuickStashColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = AmberContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = SkyBlue,
    onSecondary = OnSkyBlue,
    secondaryContainer = SkyBlueContainer,
    onSecondaryContainer = OnSkyBlueContainer,
    tertiary = LightBlue,
    onTertiary = OnLightBlue,
    tertiaryContainer = LightBlueContainer,
    onTertiaryContainer = OnLightBlueContainer,
    background = Navy0,
    onBackground = TextMain,
    surface = Navy1,
    onSurface = TextMain,
    surfaceVariant = NavyVariant,
    onSurfaceVariant = TextSub,
    surfaceContainerLowest = Navy0,
    surfaceContainerLow = Navy2,
    surfaceContainer = Navy3,
    surfaceContainerHigh = Navy4,
    surfaceContainerHighest = Navy5,
    inverseSurface = TextMain,
    inverseOnSurface = Navy1,
    inversePrimary = AmberDeep,
    outline = OutlineNavy,
    outlineVariant = OutlineNavyVariant,
    scrim = Color.Black,
)

@Composable
fun QuickStashTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = QuickStashColorScheme,
        typography = Typography,
        content = content,
    )
}
