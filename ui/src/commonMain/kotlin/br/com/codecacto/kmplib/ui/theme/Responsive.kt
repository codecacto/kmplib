package br.com.codecacto.kmplib.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Contrato de responsividade compartilhado da plataforma (GAP-SAL-07).
 *
 * Fornece um único ponto de cálculo de breakpoint para todos os apps, evitando que cada um
 * espalhe `BoxWithConstraints`/`calculateWindowSizeClass()` pelas telas. As telas leem
 * [LocalIsCompact] para decidir o layout (1 coluna vs grade, padding, drawer modal vs permanente,
 * ações por ícone vs texto+ícone etc.).
 *
 * Critério: `compact = largura < 600dp` (telefone retrato). `expanded = largura >= 600dp`
 * (tablet/landscape/desktop) — mesmo limiar do `WindowWidthSizeClass.Compact` do Material3, mas
 * sem a dependência `material3-window-size-class`: usa `BoxWithConstraints` puro de foundation,
 * portanto funciona em Android, iOS e Desktop (KMP) sem expect/actual.
 *
 * Origem: contornos locais em Salmos (`core/ui/Responsive.kt`), Emprestei e StatusHub
 * (`core/util/ScreenSize.kt`).
 */

/** Limiar de largura abaixo do qual o layout é compacto (telefone retrato). */
val CompactWidthThreshold: Dp = 600.dp

/**
 * `true` = layout compacto (telefone retrato); `false` = expandido (tablet/landscape/desktop).
 *
 * Default `true` (compacto) — o cenário mais restritivo e o do telefone, garantindo layout seguro
 * caso algum consumidor leia o local sem o provider. Sem [ProvideIsCompact] na árvore, o valor é
 * sempre `true`.
 */
val LocalIsCompact = compositionLocalOf { true }

/**
 * Provê [LocalIsCompact] medindo a largura disponível via [BoxWithConstraints].
 *
 * Deve envolver toda a árvore de telas (normalmente chamado uma vez no `App.kt`, logo dentro do
 * [AppTheme]). O cálculo do breakpoint vive SÓ aqui.
 *
 * ```kotlin
 * AppTheme(colorPalette = ...) {
 *     ProvideIsCompact {
 *         AppNavHost()
 *     }
 * }
 * ```
 *
 * @param threshold Limiar de largura do breakpoint. Padrão: [CompactWidthThreshold] (600dp).
 * @param content Conteúdo da aplicação.
 */
@Composable
fun ProvideIsCompact(
    threshold: Dp = CompactWidthThreshold,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        val isCompact = maxWidth < threshold
        CompositionLocalProvider(LocalIsCompact provides isCompact) {
            content()
        }
    }
}

/**
 * Número de colunas da grade conforme o breakpoint. Regra pura, testável.
 *
 * @param isCompact resultado de [LocalIsCompact].
 * @param compact colunas no modo compacto (default 1).
 * @param expanded colunas no modo expandido (default 2).
 */
fun gridColumns(isCompact: Boolean, compact: Int = 1, expanded: Int = 2): Int =
    if (isCompact) compact else expanded
