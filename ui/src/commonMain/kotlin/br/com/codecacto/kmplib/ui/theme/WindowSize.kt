package br.com.codecacto.kmplib.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Classe de janela — **três estados, não dois** (GAP-TABLET-01).
 *
 * ## Por que [LocalIsCompact] não bastava
 *
 * O booleano de 600dp responde uma pergunta só: "é telefone retrato?". Com ele, a única coisa que um
 * app consegue fazer num tablet é **a mesma árvore de composição, mais larga** — trocar `padding` e
 * número de colunas de uma grade. É exatamente o "responsivo mal feito" que um layout de tablet
 * precisa deixar de ser.
 *
 * Um tablet em **retrato** (~800dp) e um em **paisagem** (~1280dp) não querem o mesmo desenho: no
 * primeiro cabe um painel largo com navegação lateral estreita; no segundo cabem dois painéis lado a
 * lado, com o menu sempre visível. No booleano os dois caem no mesmo `false`.
 *
 * ## Os cortes
 *
 * | Classe | Largura | O que é, na prática |
 * |---|---|---|
 * | [COMPACTA] | `< 600dp` | telefone em retrato |
 * | [MEDIA] | `600–839dp` | tablet em retrato, telefone dobrável aberto, telefone em paisagem |
 * | [EXPANDIDA] | `≥ 840dp` | tablet em paisagem, desktop |
 *
 * São os mesmos limiares do `WindowWidthSizeClass` do Material 3 — mas **sem** a dependência
 * `material3-window-size-class`, que é Android-only. Aqui é `BoxWithConstraints` puro de foundation:
 * funciona em Android, iOS e Desktop sem `expect/actual`, que é o motivo de o `LocalIsCompact` ter
 * nascido assim em primeiro lugar.
 *
 * ## Compatibilidade
 *
 * [ProvideIsCompact] continua existindo e passa a **derivar** desta classe. Nenhum consumidor atual
 * quebra: `LocalIsCompact` é `true` exatamente quando a classe é [COMPACTA].
 */
enum class WindowSizeClass {
    /** Telefone em retrato. Uma coluna, bottom bar, tudo em pilha. */
    COMPACTA,

    /** Tablet em retrato e dobráveis. Navigation rail, conteúdo largo, ainda uma coluna principal. */
    MEDIA,

    /** Tablet em paisagem e desktop. Rail + drawer permanente, dois painéis. */
    EXPANDIDA,
    ;

    /** `true` em [MEDIA] e [EXPANDIDA] — quando a navegação sai da barra inferior. */
    val temNavegacaoLateral: Boolean get() = this != COMPACTA

    /** `true` só em [EXPANDIDA] — quando cabem dois painéis lado a lado de verdade. */
    val temDoisPaineis: Boolean get() = this == EXPANDIDA
}

/** Limiar entre [WindowSizeClass.COMPACTA] e [WindowSizeClass.MEDIA]. */
val MediumWidthThreshold: Dp = 600.dp

/** Limiar entre [WindowSizeClass.MEDIA] e [WindowSizeClass.EXPANDIDA]. */
val ExpandedWidthThreshold: Dp = 840.dp

/**
 * A classe de janela corrente.
 *
 * Default [WindowSizeClass.COMPACTA] — o cenário mais restritivo e o do telefone, garantindo layout
 * seguro caso algum consumidor leia o local sem o provider.
 */
val LocalWindowSizeClass = compositionLocalOf { WindowSizeClass.COMPACTA }

/**
 * Regra pura de classificação. Fica fora do `@Composable` para ser **testável** sem árvore de
 * composição — é o mesmo motivo pelo qual [gridColumns] existe separado.
 */
fun windowSizeClassFor(
    largura: Dp,
    limiarMedio: Dp = MediumWidthThreshold,
    limiarExpandido: Dp = ExpandedWidthThreshold,
): WindowSizeClass = when {
    largura < limiarMedio -> WindowSizeClass.COMPACTA
    largura < limiarExpandido -> WindowSizeClass.MEDIA
    else -> WindowSizeClass.EXPANDIDA
}

/**
 * Provê [LocalWindowSizeClass] **e** [LocalIsCompact], medindo a largura via [BoxWithConstraints].
 *
 * Chamado uma vez no `App.kt`, dentro do `AppTheme`. Substitui o [ProvideIsCompact] — que continua
 * funcionando, mas oferece menos informação.
 *
 * ```kotlin
 * AppTheme(colorPalette = ...) {
 *     ProvideWindowSizeClass {
 *         AppNavHost()
 *     }
 * }
 * ```
 */
@Composable
fun ProvideWindowSizeClass(
    limiarMedio: Dp = MediumWidthThreshold,
    limiarExpandido: Dp = ExpandedWidthThreshold,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        val classe = windowSizeClassFor(maxWidth, limiarMedio, limiarExpandido)
        CompositionLocalProvider(
            LocalWindowSizeClass provides classe,
            // Derivado, não medido de novo: dois cálculos da mesma coisa é como eles divergem
            // quando alguém muda um limiar e esquece o outro.
            LocalIsCompact provides (classe == WindowSizeClass.COMPACTA),
        ) {
            content()
        }
    }
}

/**
 * Número de colunas conforme a classe de janela. Regra pura, testável.
 *
 * Existe ao lado do [gridColumns] booleano — e não no lugar dele — porque o antigo continua correto
 * para quem só quer "uma ou duas colunas". Este responde a pergunta de três estados.
 */
fun gridColumnsFor(
    classe: WindowSizeClass,
    compacta: Int = 1,
    media: Int = 2,
    expandida: Int = 3,
): Int = when (classe) {
    WindowSizeClass.COMPACTA -> compacta
    WindowSizeClass.MEDIA -> media
    WindowSizeClass.EXPANDIDA -> expandida
}

/**
 * Largura máxima de uma coluna de LEITURA, por classe.
 *
 * Texto corrido esticado em 1280dp é ilegível — o olho perde a linha na volta. Este é o valor que
 * evita a "fita estreita no meio da tela" **e** o parágrafo de 200 caracteres: o container usa a
 * largura toda, e só o texto tem teto.
 */
fun leituraMaxWidth(classe: WindowSizeClass): Dp = when (classe) {
    WindowSizeClass.COMPACTA -> Dp.Unspecified
    WindowSizeClass.MEDIA -> 640.dp
    WindowSizeClass.EXPANDIDA -> 720.dp
}
