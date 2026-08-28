package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Progresso **operacional** de uma contagem "X de Y" — quantos itens de uma lista já foram
 * concluídos/conferidos/marcados.
 *
 * > **Não confundir com `UsageSnapshot`** (`monetization/entitlement`), que é de **billing**: cota
 * > comercial paga, com fonte de verdade no servidor, limite `-1` = ilimitado e paywall no
 * > esgotamento. Aqui não há cota, nem servidor, nem "esgotado": é só o andamento de uma tarefa do
 * > dia. Misturar as duas semânticas faria uma tela de operação herdar comportamento de cobrança.
 *
 * @property current itens já concluídos.
 * @property total itens esperados.
 */
data class CountProgress(
    val current: Int,
    val total: Int,
) {
    /** Fração concluída em `0f..1f` (clampada). `total <= 0` ⇒ `0f` (evita divisão por zero). */
    val fraction: Float
        get() = if (total <= 0) 0f else (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    /** Quantos faltam (nunca negativo). */
    val remaining: Int get() = (total - current).coerceAtLeast(0)

    /** `true` quando há algo a concluir e tudo foi concluído. */
    val isComplete: Boolean get() = total > 0 && current >= total

    /** `true` quando não há itens a contar (lista vazia) — a barra fica zerada. */
    val isEmpty: Boolean get() = total <= 0
}

/**
 * Textos do [ProgressCounter]/[CounterBadge] (i18n; defaults pt-BR).
 *
 * @property ofSeparator conector de "7 **de** 12" (en: `"of"`).
 */
data class ProgressCounterTexts(
    val ofSeparator: String = "de",
)

/** Texto curto do contador: `"7 de 12"`. Lógica pura — testável. */
fun progressCounterText(
    progress: CountProgress,
    texts: ProgressCounterTexts = ProgressCounterTexts(),
): String = "${progress.current} ${texts.ofSeparator} ${progress.total}"

/**
 * Frase completa anunciada pelo leitor de tela: `"7 de 12 embarcados"` — nunca só o número solto,
 * que fora de contexto não diz nada. Lógica pura — testável.
 */
fun progressCounterAccessibilityText(
    progress: CountProgress,
    label: String? = null,
    texts: ProgressCounterTexts = ProgressCounterTexts(),
): String {
    val base = progressCounterText(progress, texts)
    return if (label.isNullOrBlank()) base else "$base $label"
}

/**
 * Tom efetivo da barra: usa [completeTone] quando a contagem fecha (feedback de "acabou"), senão
 * [tone]. `completeTone = null` mantém o mesmo tom sempre. Lógica pura — testável.
 */
fun progressCounterTone(
    progress: CountProgress,
    tone: ProgressTone = ProgressTone.Primary,
    completeTone: ProgressTone? = ProgressTone.Success,
): ProgressTone = if (progress.isComplete && completeTone != null) completeTone else tone

/**
 * **Contador de progresso "X de Y" com barra fina e rótulo** — o cabeçalho de uma tela de execução
 * de lista ("7 de 12 embarcados", "3 de 8 itens conferidos", "2 de 5 etapas").
 *
 * Semântica **operacional/neutra**, deliberadamente distinta de `UsageMeter`/`UsageBadge`
 * (billing/quota paga — ver [CountProgress]). Reusa [AppProgressBar] (tokens do tema, zero cor
 * hardcoded) em vez de desenhar outra barra.
 *
 * ### Acessibilidade
 * O bloco inteiro vira **um único nó** ([clearAndSetSemantics]) que anuncia a frase completa
 * ("7 de 12 embarcados") mais o [ProgressBarRangeInfo] — em vez de o leitor ler "7 de 12" e
 * "embarcados" como fragmentos desconexos, seguidos de um percentual órfão.
 *
 * ```kotlin
 * ProgressCounter(current = state.embarcados, total = state.total, label = "embarcados")
 * ```
 *
 * @param current itens concluídos.
 * @param total itens esperados.
 * @param label rótulo do que está sendo contado (ex.: "embarcados"). Entra também no anúncio.
 * @param tone tom da barra em andamento (default [ProgressTone.Primary]).
 * @param completeTone tom ao completar (default [ProgressTone.Success]); `null` = não muda.
 * @param barHeight espessura da barra (fina por padrão — é um indicador de cabeçalho, não o herói).
 * @param texts textos i18n.
 */
@Composable
fun ProgressCounter(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    tone: ProgressTone = ProgressTone.Primary,
    completeTone: ProgressTone? = ProgressTone.Success,
    barHeight: Dp = 6.dp,
    texts: ProgressCounterTexts = ProgressCounterTexts(),
) {
    ProgressCounter(
        progress = CountProgress(current = current, total = total),
        modifier = modifier,
        label = label,
        tone = tone,
        completeTone = completeTone,
        barHeight = barHeight,
        texts = texts,
    )
}

/**
 * Overload de [ProgressCounter] que recebe o [CountProgress] já derivado — útil quando o State do
 * ViewModel já expõe o progresso pronto.
 */
@Composable
fun ProgressCounter(
    progress: CountProgress,
    modifier: Modifier = Modifier,
    label: String? = null,
    tone: ProgressTone = ProgressTone.Primary,
    completeTone: ProgressTone? = ProgressTone.Success,
    barHeight: Dp = 6.dp,
    texts: ProgressCounterTexts = ProgressCounterTexts(),
) {
    val effectiveTone = progressCounterTone(progress, tone, completeTone)
    val announcement = progressCounterAccessibilityText(progress, label, texts)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = announcement
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress.fraction,
                    range = 0f..1f,
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progressCounterText(progress, texts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AppProgressBar(
            progress = progress.fraction,
            tone = effectiveTone,
            height = barHeight,
        )
    }
}

/**
 * Versão **compacta em pill** do contador operacional — para top bar, cabeçalho de card ou linha de
 * lista, onde não cabe a barra. Par de [ProgressCounter], assim como [UsageBadge] é par de
 * `UsageMeter` (mas aqui **sem** semântica de billing).
 *
 * ```kotlin
 * AppTopBar(title = "Embarque", actions = { CounterBadge(state.embarcados, state.total) })
 * ```
 */
@Composable
fun CounterBadge(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    tone: ProgressTone = ProgressTone.Primary,
    completeTone: ProgressTone? = ProgressTone.Success,
    texts: ProgressCounterTexts = ProgressCounterTexts(),
) {
    val progress = CountProgress(current = current, total = total)
    val color = progressToneColor(progressCounterTone(progress, tone, completeTone))

    AppBadge(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = progressCounterAccessibilityText(progress, label, texts)
        },
        text = progressCounterText(progress, texts),
        style = BadgeStyle.PILL,
        backgroundColor = color.copy(alpha = 0.15f),
        textColor = color,
    )
}
