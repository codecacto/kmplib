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
 * @param darkTheme Se true, usa o tema escuro. Padrão: isSystemInDarkTheme()
 * @param colorPalette Paleta de cores customizada. Padrão: AppColorPalettes.Default
 * @param fontFamily FontFamily customizada. Padrão: FontFamily.Default
 * @param content Conteúdo da aplicação
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPalette: AppColorPalette = AppColorPalettes.Default,
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        createDarkColorScheme(colorPalette)
    } else {
        createLightColorScheme(colorPalette)
    }

    val typography = createAppTypography(fontFamily)

    // Fornecer a paleta customizada via CompositionLocal
    CompositionLocalProvider(
        LocalAppColorPalette provides colorPalette
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
