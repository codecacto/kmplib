package br.com.codecacto.kmplib.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Sistema de cores baseado em Material Design 3
 *
 * Suporta Light e Dark mode com paletas personalizáveis.
 */

/**
 * Paleta de cores personalizável
 *
 * @param primary Cor primária (botões, destaques principais)
 * @param secondary Cor secundária (destaques complementares)
 * @param tertiary Cor terciária (elementos adicionais)
 * @param error Cor de erro (avisos, erros de validação)
 * @param success Cor de sucesso (feedback positivo)
 * @param warning Cor de aviso (alertas)
 * @param info Cor informativa (informações neutras)
 */
data class AppColorPalette(
    val primary: Color,
    val secondary: Color = primary,
    val tertiary: Color = primary,
    val error: Color = Color(0xFFDC3545),
    val success: Color = Color(0xFF10B981),
    val warning: Color = Color(0xFFF59E0B),
    val info: Color = Color(0xFF3B82F6)
)

/**
 * Cria um ColorScheme Light baseado na paleta customizada
 */
fun createLightColorScheme(
    palette: AppColorPalette
): ColorScheme {
    return lightColorScheme(
        primary = palette.primary,
        onPrimary = Color.White,
        primaryContainer = palette.primary.copy(alpha = 0.1f),
        onPrimaryContainer = palette.primary,

        secondary = palette.secondary,
        onSecondary = Color.White,
        secondaryContainer = palette.secondary.copy(alpha = 0.1f),
        onSecondaryContainer = palette.secondary,

        tertiary = palette.tertiary,
        onTertiary = Color.White,
        tertiaryContainer = palette.tertiary.copy(alpha = 0.1f),
        onTertiaryContainer = palette.tertiary,

        error = palette.error,
        onError = Color.White,
        errorContainer = palette.error.copy(alpha = 0.1f),
        onErrorContainer = palette.error,

        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1C1C1C),

        surface = Color.White,
        onSurface = Color(0xFF1C1C1C),
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF616161),

        outline = Color(0xFFE0E0E0),
        outlineVariant = Color(0xFFF5F5F5),

        scrim = Color.Black.copy(alpha = 0.32f),

        inverseSurface = Color(0xFF1C1C1C),
        inverseOnSurface = Color(0xFFFAFAFA),
        inversePrimary = palette.primary.copy(alpha = 0.8f),

        surfaceTint = palette.primary
    )
}

/**
 * Cria um ColorScheme Dark baseado na paleta customizada
 */
fun createDarkColorScheme(
    palette: AppColorPalette
): ColorScheme {
    return darkColorScheme(
        primary = palette.primary.copy(alpha = 0.9f),
        onPrimary = Color(0xFF1C1C1C),
        primaryContainer = palette.primary.copy(alpha = 0.2f),
        onPrimaryContainer = palette.primary.copy(alpha = 0.9f),

        secondary = palette.secondary.copy(alpha = 0.9f),
        onSecondary = Color(0xFF1C1C1C),
        secondaryContainer = palette.secondary.copy(alpha = 0.2f),
        onSecondaryContainer = palette.secondary.copy(alpha = 0.9f),

        tertiary = palette.tertiary.copy(alpha = 0.9f),
        onTertiary = Color(0xFF1C1C1C),
        tertiaryContainer = palette.tertiary.copy(alpha = 0.2f),
        onTertiaryContainer = palette.tertiary.copy(alpha = 0.9f),

        error = palette.error.copy(alpha = 0.9f),
        onError = Color(0xFF1C1C1C),
        errorContainer = palette.error.copy(alpha = 0.2f),
        onErrorContainer = palette.error.copy(alpha = 0.9f),

        background = Color(0xFF121212),
        onBackground = Color(0xFFE0E0E0),

        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFE0E0E0),
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB0B0B0),

        outline = Color(0xFF424242),
        outlineVariant = Color(0xFF2C2C2C),

        scrim = Color.Black.copy(alpha = 0.6f),

        inverseSurface = Color(0xFFE0E0E0),
        inverseOnSurface = Color(0xFF1C1C1C),
        inversePrimary = palette.primary,

        surfaceTint = palette.primary.copy(alpha = 0.9f)
    )
}

/**
 * Paletas de cores prontas
 */
object AppColorPalettes {
    /**
     * Paleta padrão roxa (Material Design)
     */
    val Default = AppColorPalette(
        primary = Color(0xFF6C63FF)
    )

    /**
     * Paleta laranja (Locadora)
     */
    val Orange = AppColorPalette(
        primary = Color(0xFFF97316)
    )

    /**
     * Paleta verde esmeralda (Advogado)
     */
    val Green = AppColorPalette(
        primary = Color(0xFF10B981)
    )

    /**
     * Paleta azul
     */
    val Blue = AppColorPalette(
        primary = Color(0xFF3B82F6)
    )

    /**
     * Paleta rosa
     */
    val Pink = AppColorPalette(
        primary = Color(0xFFEC4899)
    )

    /**
     * Paleta vermelha
     */
    val Red = AppColorPalette(
        primary = Color(0xFFDC3545)
    )
}
