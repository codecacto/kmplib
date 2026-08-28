package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

/**
 * Status visual (genérico) de um [TimelineItem]. **NÃO acopla domínio** — a kmplib só conhece a
 * intenção de cor; o app mapeia seus próprios estados (ex.: dose TOMADA/PENDENTE/ATRASADA/AGENDADA,
 * etapa de obra, marco de projeto) para um destes tons. As cores vêm SEMPRE da camada de tema
 * ([MaterialTheme.colorScheme] / [AppColors]) — nunca `Color(...)` hardcoded.
 *
 * Para um estado que não se encaixe nos tons abaixo, use [TimelineStatus.None] e forneça
 * [TimelineItem.indicatorColor]/[TimelineItem.badge] explícitos.
 *
 * @see TimelineItem
 * @see TimelineList
 */
enum class TimelineStatus {
    /** Sem tom semântico — usa a cor `outline`/onSurfaceVariant do tema. Default. */
    None,

    /** Concluído/feito (ex.: dose TOMADA, etapa concluída) → token `success`. */
    Done,

    /** Pendente/aguardando ação → token `primary` (neutro de destaque). */
    Pending,

    /** Atrasado/crítico (ex.: dose ATRASADA) → token `error`. */
    Late,

    /** Agendado/futuro (ex.: dose AGENDADA) → token `info`. */
    Scheduled,
}

/**
 * Um marco (item) da linha do tempo. Modelo **genérico** — sem nenhum campo de domínio
 * específico (vacina, obra, etc.). O app preenche os textos já formatados e mapeia seu estado para
 * um [TimelineStatus] (ou fornece cor/badge explícitos).
 *
 * @param id identificador estável do item (chave da `LazyColumn` + payload do `onItemClick`).
 * @param title título principal do marco (ex.: "BCG", "Fundação"). Obrigatório.
 * @param dateLabel rótulo de data/momento, já formatado pelo app (ex.: "Ao nascer", "12/03/2022",
 *   "em 12 dias"). Exibido em destaque à esquerda (coluna de data). Opcional.
 * @param subtitle linha secundária (ex.: "1ª dose", "Pasto 3 · 42 animais"). Opcional.
 * @param status tom semântico do marco; controla a cor default do indicador e do badge.
 * @param badgeLabel texto do badge de status (ex.: "tomada", "atrasada"). Quando `null`, nenhum
 *   badge é exibido. A cor do badge deriva de [status] (ou de [badgeColor]/[indicatorColor]).
 * @param badgeColor sobrescreve a cor do badge (use tokens do tema). Default derivado de [status].
 * @param indicatorColor sobrescreve a cor do ponto/indicador do item. Default derivado de [status].
 * @param muted quando `true`, o item é exibido esmaecido (linha "legado/não-agendável" — ex.:
 *   Aftosa no calendário sanitário do Rebanho). Reduz a opacidade e desabilita o clique.
 * @param enabled quando `false`, o clique é suprimido (mas a opacidade é mantida; use [muted] para
 *   esmaecer também).
 */
data class TimelineItem(
    val id: String,
    val title: String,
    val dateLabel: String? = null,
    val subtitle: String? = null,
    val status: TimelineStatus = TimelineStatus.None,
    val badgeLabel: String? = null,
    val badgeColor: Color? = null,
    val indicatorColor: Color? = null,
    val muted: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * Linha do tempo / cronograma vertical **genérico** (componente-coração de calendários e
 * históricos do ecossistema — ex.: calendário vacinal das 4 variações do MinhasVacinas, timeline de
 * status de cobrança, etapas de obra). **Não acoplado a nenhum domínio:** recebe uma lista de
 * [TimelineItem] e renderiza marcos verticais com:
 *  - **conector visual** (linha do tempo) ligando os indicadores dos itens;
 *  - **indicador** (ponto) por item, colorido pelo [TimelineItem.status]/cor explícita;
 *  - **badge de status** opcional (compõe o [StatusBadge] existente da lib — não duplica);
 *  - coluna de **data/rótulo**, **título** e **subtítulo**;
 *  - **clique por item** ([onItemClick]) e **slot trailing** customizável.
 *
 * As cores vêm SEMPRE da camada de tema (tokens de [AppTheme]/[MaterialTheme.colorScheme] +
 * semânticas [AppColors]) — nunca `Color(...)` hardcoded — e acompanham paleta/dark mode do app.
 *
 * ### Responsividade
 * Usa [LocalIsCompact] (kmplib): em telas expandidas (tablet/desktop) aumenta os espaçamentos e a
 * largura da coluna de data. O app não precisa decidir — basta envolver a árvore com
 * `ProvideIsCompact` (como já recomendado pela plataforma).
 *
 * ### Rolagem
 * Por padrão a lista é rolável internamente (`LazyColumn`). Se a timeline já estiver dentro de uma
 * coluna rolável do app (evitando aninhar dois scrolls verticais), passe `scrollable = false`.
 *
 * Exemplo (calendário vacinal — o app mapeia o status da dose para [TimelineStatus]):
 * ```kotlin
 * TimelineList(
 *     items = doses.map { d ->
 *         TimelineItem(
 *             id = d.id,
 *             dateLabel = d.faixaEtaria,           // "Ao nascer", "2 meses"
 *             title = d.vacina,                    // "BCG"
 *             subtitle = d.doseLabel,              // "1ª dose"
 *             status = when (d.status) {
 *                 Status.TOMADA    -> TimelineStatus.Done
 *                 Status.PENDENTE  -> TimelineStatus.Pending
 *                 Status.ATRASADA  -> TimelineStatus.Late
 *                 Status.AGENDADA  -> TimelineStatus.Scheduled
 *             },
 *             badgeLabel = d.statusTexto,          // "tomada", "atrasada"
 *             muted = d.legado,                    // Aftosa: "Legado — não agendável"
 *         )
 *     },
 *     onItemClick = { id -> viewModel.onDoseClick(id) },
 * )
 * ```
 *
 * @param items marcos da linha do tempo, em ordem de exibição (topo → base).
 * @param modifier modificador externo.
 * @param onItemClick callback ao tocar um item (recebe [TimelineItem.id]). `null` = itens não
 *   clicáveis. Itens com [TimelineItem.enabled] = false ou [TimelineItem.muted] = true não disparam.
 * @param scrollable se `true` (default), a lista rola internamente via `LazyColumn`; se `false`,
 *   renderiza uma `Column` (use quando já houver scroll vertical do app por volta).
 * @param contentPadding padding em torno do conteúdo da lista (default `16.dp` verticais).
 * @param trailingContent slot opcional à direita de cada item (ex.: ícone de ação, valor). Recebe o
 *   [TimelineItem] correspondente.
 */
@Composable
fun TimelineList(
    items: List<TimelineItem>,
    modifier: Modifier = Modifier,
    onItemClick: ((String) -> Unit)? = null,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(vertical = 16.dp),
    trailingContent: (@Composable (TimelineItem) -> Unit)? = null,
) {
    if (scrollable) {
        LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = contentPadding) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                TimelineRow(
                    item = item,
                    isFirst = index == 0,
                    isLast = index == items.lastIndex,
                    onItemClick = onItemClick,
                    trailingContent = trailingContent,
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(contentPadding)) {
            items.forEachIndexed { index, item ->
                TimelineRow(
                    item = item,
                    isFirst = index == 0,
                    isLast = index == items.lastIndex,
                    onItemClick = onItemClick,
                    trailingContent = trailingContent,
                )
            }
        }
    }
}

/** Uma linha (marco) da timeline: coluna de data | conector+indicador | conteúdo | trailing. */
@Composable
private fun TimelineRow(
    item: TimelineItem,
    isFirst: Boolean,
    isLast: Boolean,
    onItemClick: ((String) -> Unit)?,
    trailingContent: (@Composable (TimelineItem) -> Unit)?,
) {
    val isCompact = LocalIsCompact.current
    val dateColWidth: Dp = if (isCompact) 64.dp else 96.dp
    val rowVerticalPadding: Dp = if (isCompact) 8.dp else 12.dp
    val indicatorSize = 14.dp
    val connectorColor = MaterialTheme.colorScheme.outlineVariant

    val indicatorColor = item.indicatorColor ?: statusColor(item.status)
    val clickable = onItemClick != null && item.enabled && !item.muted
    val contentAlpha = if (item.muted) 0.45f else 1f

    val rowModifier = Modifier
        .fillMaxWidth()
        // O fio é PINTADO atrás do item (ver [timelineConnector]): até a 2.120.0 eram dois `Box` de
        // altura fixa (40dp), que não acompanhavam a altura real da linha — marco com título de duas
        // ou três linhas deixava um buraco visível no meio da linha do tempo.
        .timelineConnector(
            color = connectorColor,
            centerX = dateColWidth + indicatorSize / 2,
            centerY = rowVerticalPadding + indicatorSize / 2,
            strokeWidth = 2.dp,
            drawAbove = !isFirst,
            drawBelow = !isLast,
        )
        .then(
            if (clickable && onItemClick != null) {
                Modifier.clickable { onItemClick.invoke(item.id) }
            } else {
                Modifier
            },
        )
        .defaultMinSize(minHeight = 48.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.Top,
    ) {
        // Coluna de data/rótulo (esquerda).
        Box(
            modifier = Modifier
                .width(dateColWidth)
                .padding(top = rowVerticalPadding, end = 8.dp)
                .alpha(contentAlpha),
        ) {
            item.dateLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Indicador (ponto) do marco — o fio passa por trás dele.
        Box(
            modifier = Modifier
                .width(indicatorSize)
                .padding(top = rowVerticalPadding),
        ) {
            Box(
                modifier = Modifier
                    .size(indicatorSize)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = contentAlpha)),
            )
        }

        // Conteúdo (título + subtítulo + badge) à direita do conector.
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = rowVerticalPadding, bottom = rowVerticalPadding)
                .alpha(contentAlpha),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                item.badgeLabel?.let { label ->
                    Spacer(Modifier.width(8.dp))
                    val badgeBg = (item.badgeColor ?: indicatorColor).copy(alpha = 0.15f)
                    val badgeFg = item.badgeColor ?: indicatorColor
                    StatusBadge(
                        text = label,
                        textColor = badgeFg,
                        backgroundColor = badgeBg,
                    )
                }
            }
            item.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Slot trailing opcional (ex.: ícone de ação).
        trailingContent?.let { trailing ->
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, top = rowVerticalPadding)
                    .alpha(contentAlpha),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    trailing(item)
                }
            }
        }
    }
}

/** Resolve o tom semântico de um marco para um token de cor da camada de tema. */
@Composable
private fun statusColor(status: TimelineStatus): Color = when (status) {
    TimelineStatus.None -> MaterialTheme.colorScheme.onSurfaceVariant
    TimelineStatus.Done -> br.com.codecacto.kmplib.ui.theme.AppColors.current.success
    TimelineStatus.Pending -> MaterialTheme.colorScheme.primary
    TimelineStatus.Late -> MaterialTheme.colorScheme.error
    TimelineStatus.Scheduled -> br.com.codecacto.kmplib.ui.theme.AppColors.current.info
}
