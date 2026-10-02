package br.com.codecacto.kmplib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.platform.motion.rememberReduceMotion

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
 * Também é aqui que as `Modifier.testTag()` do app passam a ser visíveis para automação de UI de
 * caixa-preta (Maestro/Appium), via [WithTestTagsAsResourceId] — uma vez, na raiz, para todo app da
 * fábrica. Nada a configurar; ver o KDoc daquela função para o porquê.
 *
 * @param darkTheme Se true, usa o tema escuro. Padrão: isSystemInDarkTheme()
 * @param colorPalette Paleta de cores customizada. Padrão: AppColorPalettes.Default
 * @param fontFamily FontFamily customizada. Padrão: FontFamily.Default. Com [displayFontFamily], vale
 *   só para o texto corrido e os rótulos (titleMedium/titleSmall, body, label).
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
 * @param displayFontFamily família dos TÍTULOS — display, headline e titleLarge (o papel *brand* da
 *   escala do Material 3; ver [createAppTypography]). Padrão: a própria [fontFamily], ou seja, quem
 *   não passa nada continua com uma família só. Desde 2.228.0.
 * @param systemBars `true` (padrão) = o tema escolhe a cor dos **ícones das barras do sistema**
 *   (relógio, bateria, botões de navegação) pelo fundo da paleta EFETIVA — o que o app de fato
 *   desenha, e não o modo do aparelho. Ver [SystemBarsAppearance]. `false` só para o app que
 *   controla as barras por conta própria. Desde 2.241.0.
 * @param content Conteúdo da aplicação
 *
 * O tema também provê `LocalReduceMotion` (`kmplib-platform`, 2.228.0) com o valor vivo da
 * preferência "reduzir movimento" do sistema.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPalette: AppColorPalette = AppColorPalettes.Default,
    fontFamily: FontFamily = FontFamily.Default,
    fontScale: Float = 1f,
    highContrast: Boolean = false,
    displayFontFamily: FontFamily = fontFamily,
    systemBars: Boolean = true,
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
    val reduceMotion by rememberReduceMotion()
    val typography = scaleTypography(createAppTypography(fontFamily, displayFontFamily), effectiveScale)

    // Fornecer a paleta customizada, a escala de fonte e o alto contraste via CompositionLocal
    CompositionLocalProvider(
        LocalAppColorPalette provides colorPalette,
        LocalFontScale provides effectiveScale,
        LocalHighContrast provides highContrast,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
        ) {
            // Ícones das barras do sistema pela cor que o tema REALMENTE pinta atrás delas. O
            // `enableEdgeToEdge()` da Activity decide pelo modo do aparelho, uma vez, no `onCreate`
            // — e o tema pode discordar dele (`darkTheme` fixo, paleta de superfícies própria,
            // troca de modo com o app aberto). Fica na BASE da pilha: a tela que pinta outro fundo
            // chama `SystemBarsAppearance` e vence enquanto estiver em cena.
            if (systemBars) SystemBarsAppearance(colorScheme.background)
            // Publica as `Modifier.testTag()` da árvore como `resource-id` da plataforma, para a
            // automação de UI (Maestro/Appium) poder se ancorar por id em vez de por texto de tela.
            // Uma vez, na raiz — ver `WithTestTagsAsResourceId`. No iOS é no-op.
            WithTestTagsAsResourceId(content)
        }
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
