package br.com.codecacto.kmplib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * Tema principal da aplicação
 *
 * Fornece cores, tipografia e shapes para todos os componentes.
 * Suporta Light e Dark mode automaticamente baseado nas configurações do sistema.
 *
 * Os parâmetros de **acessibilidade** [fontScale] e [highContrast] são **aditivos e
 * retrocompatíveis**: os defaults (`1f` / `false`) preservam exatamente o comportamento anterior —
 * nenhum consumidor existente quebra.
 *
 * @param darkTheme Se true, usa o tema escuro. Padrão: isSystemInDarkTheme()
 * @param colorPalette Paleta de cores customizada. Padrão: AppColorPalettes.Default
 * @param fontFamily FontFamily customizada. Padrão: FontFamily.Default
 * @param fontScale Escala de fonte global de acessibilidade (multiplica toda a [AppTypography] e é
 *   exposta em [LocalFontScale]). Clampada em [MIN_FONT_SCALE]..[MAX_FONT_SCALE]. Use os degraus de
 *   [AppFontScale] (`Small`/`Medium`/`Large`/`ExtraLarge`) → `fontScale = AppFontScale.Large.scale`.
 *   Padrão: `1f` (sem escala).
 * @param highContrast Quando `true`, seleciona um par de [ColorScheme] de **alto contraste** derivado
 *   da [colorPalette] — superfícies em contraste máximo (acima do AAA); os acentos preservam a paleta
 *   da marca. Ideal para baixa visão. Padrão: `false`.
 * @param content Conteúdo da aplicação
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPalette: AppColorPalette = AppColorPalettes.Default,
    fontFamily: FontFamily = FontFamily.Default,
    fontScale: Float = 1f,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        highContrast && darkTheme -> createHighContrastDarkColorScheme(colorPalette)
        highContrast -> createHighContrastLightColorScheme(colorPalette)
        darkTheme -> createDarkColorScheme(colorPalette)
        else -> createLightColorScheme(colorPalette)
    }

    val effectiveScale = clampFontScale(fontScale)
    val typography = scaleTypography(createAppTypography(fontFamily), effectiveScale)

    // Fornecer a paleta customizada e a escala de fonte via CompositionLocal
    CompositionLocalProvider(
        LocalAppColorPalette provides colorPalette,
        LocalFontScale provides effectiveScale
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

/**
 * CompositionLocal para acessar cores customizadas (success, warning, info)
 * que não fazem parte do ColorScheme padrão do Material 3
 */
val LocalAppColorPalette = staticCompositionLocalOf {
    AppColorPalettes.Default
}

/**
 * Acesso fácil às cores customizadas
 */
object AppColors {
    val current: AppColorPalette
        @Composable
        get() = LocalAppColorPalette.current
}
