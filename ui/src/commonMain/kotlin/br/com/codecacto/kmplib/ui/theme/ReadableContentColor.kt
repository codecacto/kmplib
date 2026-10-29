package br.com.codecacto.kmplib.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Cor de **conteúdo** (texto/ícone) para um botão ou preenchimento de cor [container] — a regra que
 * os componentes da lib usam quando o chamador troca a cor do botão sem dizer a do texto (2.333.0).
 *
 * - [container] é uma cor do TEMA (`primary`, `error`, `secondary`, `primaryContainer`…) → devolve o
 *   par `on*` do próprio tema, como faz o `contentColorFor` do Material3 — o `on*` que o app declarou
 *   (`AppBrandColors.onPrimary`) é respeitado.
 * - Qualquer outra cor → [ColorContrast.readableOn]: branco ou preto, o que der contraste AA
 *   (≥ 4,5:1), com [container] translúcido composto sobre [backdrop] (default: a superfície, que é
 *   o que fica atrás de um botão de diálogo).
 *
 * O defeito que isto fecha: até a 2.332.0 o `AppButton` e o confirmar do `AppAlertDialog` pintavam o
 * texto com `onPrimary` qualquer que fosse a cor do botão. Num app de primária clara (lima, amarelo),
 * `onPrimary` é escuro — e o "Sair" vermelho de `confirmButtonColor = colorScheme.error` saía com texto
 * PRETO sobre vermelho (OnMore, App do Personal, 29/out/2026).
 */
@Composable
@ReadOnlyComposable
fun readableContentColorFor(
    container: Color,
    backdrop: Color = MaterialTheme.colorScheme.surface,
): Color = readableContentColorFor(container, MaterialTheme.colorScheme, backdrop)

/**
 * Versão pura de [readableContentColorFor] (sem Compose runtime): par `on*` de [scheme] quando
 * [container] é uma das cores dele; senão [ColorContrast.readableOn] sobre [backdrop].
 * [Color.Unspecified] devolve `onSurface`.
 */
fun readableContentColorFor(container: Color, scheme: ColorScheme, backdrop: Color = scheme.surface): Color {
    if (container == Color.Unspecified) return scheme.onSurface
    themeContentColorFor(container, scheme)?.let { return it }
    return ColorContrast.readableOn(container, backdrop)
}

/** O par `on*` do tema para [container], ou `null` se ele não for uma cor do [scheme]. */
internal fun themeContentColorFor(container: Color, scheme: ColorScheme): Color? = when (container) {
    scheme.primary -> scheme.onPrimary
    scheme.secondary -> scheme.onSecondary
    scheme.tertiary -> scheme.onTertiary
    scheme.error -> scheme.onError
    scheme.primaryContainer -> scheme.onPrimaryContainer
    scheme.secondaryContainer -> scheme.onSecondaryContainer
    scheme.tertiaryContainer -> scheme.onTertiaryContainer
    scheme.errorContainer -> scheme.onErrorContainer
    scheme.inverseSurface -> scheme.inverseOnSurface
    scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerLow, scheme.surfaceContainerLowest,
    scheme.surfaceContainerHigh, scheme.surfaceContainerHighest, scheme.surfaceBright, scheme.surfaceDim,
    -> scheme.onSurface
    scheme.surfaceVariant -> scheme.onSurfaceVariant
    scheme.background -> scheme.onBackground
    else -> null
}
