package br.com.codecacto.kmplib.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Typography system baseado em Material Design 3
 *
 * Hierarquia completa de estilos de texto para aplicações.
 * Todos os tamanhos e pesos são baseados nas especificações do Material 3.
 */

/**
 * Typography padrão Material 3
 *
 * Usa FontFamily.Default (Roboto no Android, San Francisco no iOS)
 */
fun createAppTypography(
    fontFamily: FontFamily = FontFamily.Default
): Typography {
    return Typography(
        // Display - Maiores textos, usados com parcimônia
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp
        ),
        displayMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp
        ),

        // Headline - Títulos principais
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),

        // Title - Títulos de seções
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),

        // Body - Texto principal
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),

        // Label - Botões, badges, labels
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}

/**
 * Typography padrão para a biblioteca
 */
val AppTypography = createAppTypography()

/**
 * Multiplica `fontSize` e `lineHeight` de todos os estilos de uma [Typography] pelo fator
 * [fontScale] (escala de fonte global de acessibilidade), preservando famílias/pesos/espaçamentos.
 *
 * O fator é **clampado** ([clampFontScale]); `1f` devolve a mesma typography (retrocompatível —
 * comportamento atual do [AppTheme]). Regra pura, testável (commonTest).
 */
fun scaleTypography(typography: Typography, fontScale: Float): Typography {
    val scale = clampFontScale(fontScale)
    if (scale == 1f) return typography
    return typography.copy(
        displayLarge = typography.displayLarge.scaled(scale),
        displayMedium = typography.displayMedium.scaled(scale),
        displaySmall = typography.displaySmall.scaled(scale),
        headlineLarge = typography.headlineLarge.scaled(scale),
        headlineMedium = typography.headlineMedium.scaled(scale),
        headlineSmall = typography.headlineSmall.scaled(scale),
        titleLarge = typography.titleLarge.scaled(scale),
        titleMedium = typography.titleMedium.scaled(scale),
        titleSmall = typography.titleSmall.scaled(scale),
        bodyLarge = typography.bodyLarge.scaled(scale),
        bodyMedium = typography.bodyMedium.scaled(scale),
        bodySmall = typography.bodySmall.scaled(scale),
        labelLarge = typography.labelLarge.scaled(scale),
        labelMedium = typography.labelMedium.scaled(scale),
        labelSmall = typography.labelSmall.scaled(scale),
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle = copy(
    fontSize = fontSize.scaledUnit(scale),
    lineHeight = lineHeight.scaledUnit(scale),
)

private fun TextUnit.scaledUnit(scale: Float): TextUnit =
    if (isSpecified) this * scale else this
