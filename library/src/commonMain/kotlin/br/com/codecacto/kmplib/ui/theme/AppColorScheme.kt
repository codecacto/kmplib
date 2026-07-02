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
 * Cria um ColorScheme Light de **alto contraste** (acessibilidade / baixa visão) derivado da paleta.
 *
 * Diferente do tema claro normal (que já é quase branco/preto — logo trocar por branco/preto puro é
 * imperceptível), este esquema torna a diferença **dramática e legítima** (WCAG AAA nas superfícies):
 * - `background`/`surface` = `paper` (branco), texto `ink` (preto), razão ~21:1;
 * - **`primary` = quase-preto** ([HC_PRIMARY], **não** a cor da marca): superfícies/elementos
 *   preenchidos com `primary` (ex.: `CommunicationTile` no tom `Normal`) passam de "colorido" (tema
 *   normal) para "quase-preto com texto branco" — diferença **inconfundível** ao ligar o alto contraste;
 * - `error` = vermelho escuro forte ([HC_ERROR]) com `onError` branco;
 * - tom Quick (`secondaryContainer`/`onSecondaryContainer`) = container cinza claro
 *   ([HC_TONAL_CONTAINER]) + texto `ink` (~16:1), não some no fundo branco;
 * - **bordas fortes** (`outline` = `ink`, preto) para separar elementos sem depender só de cor.
 *
 * Este é o **par de contraste** selecionado por `AppTheme(highContrast = true)`.
 */
fun createHighContrastLightColorScheme(
    palette: AppColorPalette
): ColorScheme {
    val ink = Color(0xFF000000)
    val paper = Color(0xFFFFFFFF)
    return lightColorScheme(
        primary = HC_PRIMARY,
        onPrimary = paper,
        primaryContainer = paper,
        onPrimaryContainer = ink,

        secondary = ink,
        onSecondary = paper,
        secondaryContainer = HC_TONAL_CONTAINER,
        onSecondaryContainer = ink,

        tertiary = ink,
        onTertiary = paper,
        tertiaryContainer = HC_TONAL_CONTAINER,
        onTertiaryContainer = ink,

        error = HC_ERROR,
        onError = paper,
        errorContainer = paper,
        onErrorContainer = HC_ERROR,

        background = paper,
        onBackground = ink,

        surface = paper,
        onSurface = ink,
        surfaceVariant = Color(0xFFF2F2F2),
        onSurfaceVariant = ink,

        outline = ink,
        outlineVariant = Color(0xFF3A3A3A),

        scrim = ink,

        inverseSurface = ink,
        inverseOnSurface = paper,
        inversePrimary = paper,

        surfaceTint = HC_PRIMARY
    )
}

/** Primária de alto contraste (light): quase-preto — máximo peso com texto branco (WCAG AAA). */
private val HC_PRIMARY = Color(0xFF0A0A0A)

/** Vermelho escuro forte de alto contraste (urgência), com texto branco (WCAG AAA). */
private val HC_ERROR = Color(0xFF8A0000)

/** Container tonal do tom Quick no alto contraste (light): cinza claro distinto do fundo branco. */
private val HC_TONAL_CONTAINER = Color(0xFFE6E6E6)

/**
 * Cria um ColorScheme Dark de **alto contraste** derivado da paleta, coerente com o par light:
 * superfícies pretas puras com texto branco (razão ~21:1, acima do AAA) e contornos brancos fortes;
 * **`primary` = branco** ([HC_PRIMARY_DARK]) com `onPrimary` preto — elementos preenchidos com
 * `primary` (ex.: `CommunicationTile` `Normal`) viram "branco com texto preto", máximo peso; `error`
 * = vermelho claro forte ([HC_ERROR_DARK]); tom Quick = container cinza escuro
 * ([HC_TONAL_CONTAINER_DARK]) + texto branco. Selecionado por
 * `AppTheme(highContrast = true, darkTheme = true)`.
 */
fun createHighContrastDarkColorScheme(
    palette: AppColorPalette
): ColorScheme {
    val ink = Color(0xFF000000)
    val paper = Color(0xFFFFFFFF)
    return darkColorScheme(
        primary = HC_PRIMARY_DARK,
        onPrimary = ink,
        primaryContainer = ink,
        onPrimaryContainer = paper,

        secondary = paper,
        onSecondary = ink,
        secondaryContainer = HC_TONAL_CONTAINER_DARK,
        onSecondaryContainer = paper,

        tertiary = paper,
        onTertiary = ink,
        tertiaryContainer = HC_TONAL_CONTAINER_DARK,
        onTertiaryContainer = paper,

        error = HC_ERROR_DARK,
        onError = ink,
        errorContainer = ink,
        onErrorContainer = HC_ERROR_DARK,

        background = ink,
        onBackground = paper,

        surface = ink,
        onSurface = paper,
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = paper,

        outline = paper,
        outlineVariant = Color(0xFFCFCFCF),

        scrim = ink,

        inverseSurface = paper,
        inverseOnSurface = ink,
        inversePrimary = HC_PRIMARY_DARK,

        surfaceTint = HC_PRIMARY_DARK
    )
}

/** Primária de alto contraste (dark): branco — máximo peso com texto preto (WCAG AAA). */
private val HC_PRIMARY_DARK = Color(0xFFFFFFFF)

/** Vermelho claro forte de alto contraste (dark), com texto preto (WCAG AAA sobre fundo preto). */
private val HC_ERROR_DARK = Color(0xFFFF5A5A)

/** Container tonal do tom Quick no alto contraste (dark): cinza escuro distinto do fundo preto. */
private val HC_TONAL_CONTAINER_DARK = Color(0xFF2A2A2A)

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

    /**
     * Paleta **Teal acessível** (CAA / apps de acessibilidade — ex.: Minha Voz).
     *
     * - `primary` teal médio-escuro (`#0F766E`, teal-700): preenchimento sólido com **texto branco
     *   legível** (contraste ~5:1) — serve ao `CommunicationTile` no tom `Normal`.
     * - `secondary` teal um passo mais claro (`#0D9488`, teal-600): tom harmônico e distinto usado
     *   como `secondaryContainer`/`onSecondaryContainer` do tom `Quick` (respostas rápidas).
     * - `error` vermelho forte (`#DC2626`) com texto branco, para o tom `Alert` (urgentes).
     */
    val Teal = AppColorPalette(
        primary = Color(0xFF0F766E),
        secondary = Color(0xFF0D9488),
        error = Color(0xFFDC2626)
    )
}
