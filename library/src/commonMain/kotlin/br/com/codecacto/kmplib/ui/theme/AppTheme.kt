package br.com.codecacto.kmplib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import br.com.codecacto.kmplib.core.util.AppLogger

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
 *   da [colorPalette] — superfícies em contraste máximo (acima do AAA) e uma **primária quase-preta**
 *   (light) / **quase-branca** (dark) para elementos preenchidos, tornando a mudança inconfundível
 *   (ex.: `CommunicationTile` `Normal` sai de colorido para quase-preto+texto branco). Também expõe
 *   [LocalHighContrast] para componentes reforçarem a UI (bordas grossas). Ideal para baixa visão.
 *   Padrão: `false`.
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
    // Memoiza o esquema por (palette, darkTheme, highContrast): evita recomputar as derivações de
    // superfície/contraste a cada recomposição e garante que a validação de contraste seja logada
    // apenas UMA vez por combinação (não a cada frame).
    val colorScheme = remember(colorPalette, darkTheme, highContrast) {
        val scheme = when {
            highContrast && darkTheme -> createHighContrastDarkColorScheme(colorPalette)
            highContrast -> createHighContrastLightColorScheme(colorPalette)
            darkTheme -> createDarkColorScheme(colorPalette)
            else -> createLightColorScheme(colorPalette)
        }
        // Contraste é responsabilidade da lib: as cores `on*` derivadas já saem com contraste
        // garantido; aqui só alertamos (nunca bloqueamos) sobre superfícies passadas À MÃO que
        // ficaram abaixo do alvo WCAG — para o desenvolvedor corrigir a paleta.
        if (!highContrast) {
            val surfaces = if (darkTheme) colorPalette.darkSurfaces else colorPalette.lightSurfaces
            surfaces?.let { surfaceContrastWarnings(it) }
                ?.forEach { AppLogger.w("AppTheme", "Contraste de superfície abaixo do alvo — $it") }
        }
        scheme
    }

    val effectiveScale = clampFontScale(fontScale)
    val typography = scaleTypography(createAppTypography(fontFamily), effectiveScale)

    // Fornecer a paleta customizada, a escala de fonte e o alto contraste via CompositionLocal
    CompositionLocalProvider(
        LocalAppColorPalette provides colorPalette,
        LocalFontScale provides effectiveScale,
        LocalHighContrast provides highContrast
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
 * CompositionLocal que expõe se o tema atual está em **alto contraste** (setado por
 * `AppTheme(highContrast = ...)`; default `false`). Componentes acessíveis (ex.: `CommunicationTile`)
 * leem este valor para reforçar a UI — por exemplo, desenhar bordas grossas de separação. Segue o
 * mesmo padrão de [LocalFontScale].
 */
val LocalHighContrast = staticCompositionLocalOf { false }

/**
 * Acesso fácil às cores customizadas
 */
object AppColors {
    val current: AppColorPalette
        @Composable
        get() = LocalAppColorPalette.current
}
