package com.example.quickstash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.quickstash.R

val LineSeedJp = FontFamily(
    Font(R.font.line_seed_jp_rg, FontWeight.Normal),
    Font(R.font.line_seed_jp_bd, FontWeight.Medium),
    Font(R.font.line_seed_jp_bd, FontWeight.Bold),
)

private val Base = Typography()

val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = LineSeedJp),
    displayMedium = Base.displayMedium.copy(fontFamily = LineSeedJp),
    displaySmall = Base.displaySmall.copy(fontFamily = LineSeedJp),
    headlineLarge = Base.headlineLarge.copy(fontFamily = LineSeedJp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = LineSeedJp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = LineSeedJp),
    titleLarge = Base.titleLarge.copy(fontFamily = LineSeedJp),
    titleMedium = Base.titleMedium.copy(fontFamily = LineSeedJp),
    titleSmall = Base.titleSmall.copy(fontFamily = LineSeedJp),
    bodyLarge = Base.bodyLarge.copy(fontFamily = LineSeedJp),
    bodyMedium = Base.bodyMedium.copy(fontFamily = LineSeedJp),
    bodySmall = Base.bodySmall.copy(fontFamily = LineSeedJp),
    labelLarge = Base.labelLarge.copy(fontFamily = LineSeedJp),
    labelMedium = Base.labelMedium.copy(fontFamily = LineSeedJp),
    labelSmall = Base.labelSmall.copy(fontFamily = LineSeedJp),
)
