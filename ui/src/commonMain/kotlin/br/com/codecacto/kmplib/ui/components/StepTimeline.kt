package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

/**
 * Estado de uma etapa de [StepTimeline]. É o vocabulário **de andamento** (um processo que caminha),
 * distinto do [TimelineStatus] de [TimelineList], que é o tom de um marco de histórico.
 *
 * O app mapeia o seu domínio para um destes quatro — a lib não conhece "chamado", "pedido" nem
 * "protocolo".
 */
enum class StepState {
    /** Já aconteceu (marcador preenchido com "✓"). */
    Done,

    /** Acontecendo agora — a etapa em que o processo está parado neste momento (marcador em destaque). */
    Current,

    /** Ainda não aconteceu (marcador vazado, texto secundário). */
    Pending,

    /** Não vai acontecer: cancelada, recusada, encerrada sem conclusão (marcador com "✕", texto riscado). */
    Canceled,
}

/**
 * Uma etapa da linha do tempo de andamento.
 *
 * @param id identificador estável (chave da `LazyColumn` e payload do `onStepClick`).
 * @param title o que aconteceu, do ponto de vista de quem lê (ex.: "Enviado à prefeitura",
 *   "Saiu para entrega"). Obrigatório.
 * @param subtitle detalhe opcional em uma linha (ex.: "10 confirmações", "Secretaria de Obras").
 * @param timeLabel momento/legenda **já formatado pelo app** (ex.: "hoje 09:12", "18/08 10:35",
 *   "previsto 10:20", "aguardando"). A lib não formata data — quem sabe o fuso e o idioma é o app.
 * @param state estado da etapa; controla marcador, ícone default e ênfase do texto.
 * @param icon ícone dentro do marcador. `null` (default) usa o ícone do [state]
 *   ([StepTimelineDefaults.iconFor]) — informe para dar identidade à etapa (moto, sacola, bandeira).
 * @param enabled quando `false`, a etapa não responde ao toque (mas continua legível).
 */
data class TimelineStep(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val timeLabel: String? = null,
    val state: StepState = StepState.Pending,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

/**
 * Textos i18n do [StepTimeline] — o que o **leitor de tela** anuncia como estado da etapa
 * (`stateDescription`). Defaults em pt-BR; um app em outro idioma passa os seus.
 *
 * Não são textos visíveis: na tela o estado aparece como marcador + ícone + ênfase.
 */
data class StepTimelineTexts(
    val done: String = "Concluída",
    val current: String = "Em andamento",
    val pending: String = "Pendente",
    val canceled: String = "Cancelada",
)

/** Medidas, ícones e opacidades padrão do [StepTimeline]. */
object StepTimelineDefaults {
    /** Diâmetro do marcador (círculo). */
    val IndicatorSize: Dp = 28.dp

    /**
     * Diâmetro do **slot** do marcador — maior que o círculo para caber o halo da etapa atual sem
     * deslocar o trilho. É constante em todos os estados, senão o fio entortaria de item para item.
     */
    val IndicatorSlotSize: Dp = 36.dp

    /** Tamanho do ícone dentro do marcador. */
    val IconSize: Dp = 16.dp

    /** Espessura do fio que liga os marcadores. */
    val ConnectorWidth: Dp = 2.dp

    /** Espessura da borda do marcador vazado (etapa pendente). */
    val PendingBorderWidth: Dp = 2.dp

    /**
     * Altura mínima da etapa. **48dp** é o alvo de toque mínimo do Material/WCAG — a etapa é
     * clicável em vários usos (abrir o detalhe do chamado), e um alvo menor que isso não se acerta.
     */
    val MinStepHeight: Dp = 48.dp

    /** Opacidade do halo em volta do marcador da etapa atual. */
    const val CurrentHaloAlpha: Float = 0.18f

    /** Opacidade do conteúdo de uma etapa desabilitada. */
    const val DisabledContentAlpha: Float = 0.45f

    /** Máximo de linhas do título (texto longo quebra; a partir daí, reticências). */
    const val TitleMaxLines: Int = 3

    /** Máximo de linhas do subtítulo. */
    const val SubtitleMaxLines: Int = 3

    /**
     * Ícone default de cada estado. Comunicar o estado **também por forma**, e não só por cor, é
     * requisito WCAG 1.4.1 — daí "✓" para concluída e "✕" para cancelada.
     */
    fun iconFor(state: StepState): ImageVector = when (state) {
        StepState.Done -> Icons.Filled.Check
        StepState.Current -> Icons.Filled.Schedule
        StepState.Pending -> Icons.Outlined.RadioButtonUnchecked
        StepState.Canceled -> Icons.Filled.Close
    }
}

/**
 * Tom semântico de cada estado — resolvido pela **fonte única** [statusToneColor], a mesma que
 * [StatusBadge], [ChecklistItem] e [AppBanner] usam. Lógica pura (sem Compose) — testável.
 */
fun stepStateTone(state: StepState): StatusTone = when (state) {
    StepState.Done -> StatusTone.SUCCESS
    StepState.Current -> StatusTone.WARNING
    StepState.Pending -> StatusTone.NEUTRAL
    StepState.Canceled -> StatusTone.DANGER
}

/**
 * `true` quando o marcador é **preenchido** (círculo sólido); `false` quando é **vazado** (só
 * contorno). O que ainda não aconteceu fica vazado — é o que separa passado de futuro na tela sem
 * depender de cor. Lógica pura — testável.
 */
fun stepIndicatorIsFilled(state: StepState): Boolean = state != StepState.Pending

/** `true` quando o título é riscado (etapa que não vai acontecer). Lógica pura — testável. */
fun stepTitleIsStruckThrough(state: StepState): Boolean = state == StepState.Canceled

/**
 * `true` quando o título recebe **ênfase** (peso maior): a etapa atual é o ponto em que a leitura
 * deve cair primeiro. Lógica pura — testável.
 */
fun stepTitleIsEmphasized(state: StepState): Boolean = state == StepState.Current

/**
 * Descrição de estado anunciada pelo leitor de tela (`stateDescription`) — o que substitui a
 * informação que, para quem enxerga, está na cor e na forma do marcador. Lógica pura — testável.
 */
fun stepStateDescription(state: StepState, texts: StepTimelineTexts = StepTimelineTexts()): String =
    when (state) {
        StepState.Done -> texts.done
        StepState.Current -> texts.current
        StepState.Pending -> texts.pending
        StepState.Canceled -> texts.canceled
    }

/**
 * `true` quando a etapa responde ao toque: só se houver handler **e** a etapa estiver habilitada.
 * Lógica pura — testável.
 */
fun stepIsClickable(step: TimelineStep, hasClickHandler: Boolean): Boolean =
    hasClickHandler && step.enabled

/**
 * **Linha do tempo vertical de andamento** — as etapas de um processo, com marcadores circulares
 * ligados por um fio, cada uma com título, legenda de horário e estado
 * (concluída / atual / pendente / cancelada).
 *
 * É o desenho de "ANDAMENTO" / "Status do pedido": chamado à prefeitura (publicado → confirmações →
 * visualizado → fechado), pedido de delivery (recebido → em preparo → saiu para entrega → entregue),
 * etapas de um contrato ou de uma reserva. **Domínio-agnóstico:** recebe textos já formatados e um
 * [StepState] por etapa.
 *
 * ### Quando usar este e quando usar [TimelineList]
 * - **[StepTimeline]** — *processo que caminha*: poucas etapas, **conhecidas de antemão**, incluindo
 *   as que ainda não aconteceram ("previsto 10:20"). O interesse é *em que ponto estamos*.
 * - **[TimelineList]** — *histórico*: marcos já ocorridos, com **coluna de data à esquerda** e selo
 *   de status. O interesse é *o que aconteceu e quando*.
 *
 * ### Cores
 * Tudo vem de token de tema ([statusToneColor] → [br.com.codecacto.kmplib.ui.theme.AppColors] /
 * `MaterialTheme.colorScheme`); **nenhum `Color(0x…)`**. O ícone dentro do marcador preenchido é
 * escolhido por **contraste WCAG** ([ColorContrast.pickOnColor]) contra a cor do próprio marcador —
 * então um app de paleta clara não recebe "branco sobre amarelo".
 *
 * ### Acessibilidade
 * - Cada etapa é **um único nó semântico** (`mergeDescendants`), anunciado como
 *   "título, subtítulo, horário" + `stateDescription` do estado ([StepTimelineTexts]).
 * - Estado comunicado por **forma e ícone**, não só por cor (WCAG 1.4.1): preenchido × vazado,
 *   "✓" × "✕", riscado na cancelada.
 * - Etapa clicável tem alvo de toque de no mínimo [StepTimelineDefaults.MinStepHeight] (48dp) e
 *   [Role.Button].
 *
 * ### Lista longa e texto longo
 * Rola internamente (`LazyColumn`) por default — passe `scrollable = false` quando a timeline já
 * estiver dentro de uma coluna rolável do app (dois scrolls verticais aninhados brigam). O fio é
 * pintado no `drawBehind` do item ([timelineConnector]), então acompanha etapa de duas ou três
 * linhas sem deixar buraco.
 *
 * ```kotlin
 * StepTimeline(
 *     steps = listOf(
 *         TimelineStep("pub", "Publicado pelo morador", timeLabel = "hoje 09:12", state = StepState.Done),
 *         TimelineStep("env", "10 confirmações · enviado à prefeitura", timeLabel = "hoje 09:40", state = StepState.Done),
 *         TimelineStep("vis", "Prefeitura visualizou", timeLabel = "hoje 10:05", state = StepState.Current),
 *         TimelineStep("fim", "Prefeitura fecha o caso", timeLabel = "aguardando", state = StepState.Pending),
 *     ),
 * )
 * ```
 *
 * @param steps etapas em ordem de exibição (topo → base). Lista vazia não desenha nada.
 * @param modifier modificador externo.
 * @param onStepClick toque em uma etapa (recebe [TimelineStep.id]). `null` (default) = etapas não
 *   clicáveis, que é o caso comum (a timeline informa, não navega).
 * @param scrollable `true` (default) rola internamente; `false` renderiza uma `Column`.
 * @param contentPadding padding em volta do conteúdo.
 * @param texts textos i18n do estado anunciado pelo leitor de tela.
 * @param trailingContent slot opcional à direita da etapa (ex.: valor, ícone de ação).
 */
@Composable
fun StepTimeline(
    steps: List<TimelineStep>,
    modifier: Modifier = Modifier,
    onStepClick: ((String) -> Unit)? = null,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    texts: StepTimelineTexts = StepTimelineTexts(),
    trailingContent: (@Composable (TimelineStep) -> Unit)? = null,
) {
    if (scrollable) {
        LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = contentPadding) {
            itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                StepTimelineRow(
                    step = step,
                    drawAbove = timelineDrawsSegmentAbove(index),
                    drawBelow = timelineDrawsSegmentBelow(index, steps.size),
                    onStepClick = onStepClick,
                    texts = texts,
                    trailingContent = trailingContent,
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(contentPadding)) {
            steps.forEachIndexed { index, step ->
                StepTimelineRow(
                    step = step,
                    drawAbove = timelineDrawsSegmentAbove(index),
                    drawBelow = timelineDrawsSegmentBelow(index, steps.size),
                    onStepClick = onStepClick,
                    texts = texts,
                    trailingContent = trailingContent,
                )
            }
        }
    }
}

/** Uma etapa: marcador (com fio atrás) | título + subtítulo + horário | trailing. */
@Composable
private fun StepTimelineRow(
    step: TimelineStep,
    drawAbove: Boolean,
    drawBelow: Boolean,
    onStepClick: ((String) -> Unit)?,
    texts: StepTimelineTexts,
    trailingContent: (@Composable (TimelineStep) -> Unit)?,
) {
    val isCompact = LocalIsCompact.current
    val verticalPadding: Dp = if (isCompact) 6.dp else 10.dp
    val slot = StepTimelineDefaults.IndicatorSlotSize
    val clickable = stepIsClickable(step, onStepClick != null)
    val tone = statusToneColor(stepStateTone(step.state))
    val stateDesc = stepStateDescription(step.state, texts)

    val rowModifier = Modifier
        .fillMaxWidth()
        .timelineConnector(
            color = MaterialTheme.colorScheme.outlineVariant,
            centerX = slot / 2,
            centerY = verticalPadding + slot / 2,
            strokeWidth = StepTimelineDefaults.ConnectorWidth,
            drawAbove = drawAbove,
            drawBelow = drawBelow,
        )
        .then(
            if (clickable && onStepClick != null) {
                Modifier.clickable(role = Role.Button) { onStepClick(step.id) }
            } else {
                Modifier
            },
        )
        .defaultMinSize(minHeight = StepTimelineDefaults.MinStepHeight)
        .padding(vertical = verticalPadding)
        .semantics(mergeDescendants = true) { stateDescription = stateDesc }

    Row(modifier = rowModifier, verticalAlignment = Alignment.Top) {
        StepIndicator(step = step, tone = tone)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (stepTitleIsEmphasized(step.state)) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Medium
                },
                textDecoration = if (stepTitleIsStruckThrough(step.state)) {
                    TextDecoration.LineThrough
                } else {
                    null
                },
                color = stepTitleColor(step),
                maxLines = StepTimelineDefaults.TitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            step.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = StepTimelineDefaults.SubtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            step.timeLabel?.let { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        trailingContent?.let { trailing ->
            Box(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                trailing(step)
            }
        }
    }
}

/** Cor do título por estado — token do tema, com o futuro em tom secundário. */
@Composable
private fun stepTitleColor(step: TimelineStep): Color {
    val base = when (step.state) {
        StepState.Done, StepState.Current -> MaterialTheme.colorScheme.onSurface
        StepState.Pending, StepState.Canceled -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    return if (step.enabled) {
        base
    } else {
        base.copy(alpha = StepTimelineDefaults.DisabledContentAlpha)
    }
}

/** Marcador circular: preenchido (passado/atual/cancelada) ou vazado (pendente), com halo no atual. */
@Composable
private fun StepIndicator(step: TimelineStep, tone: Color) {
    val filled = stepIndicatorIsFilled(step.state)
    val icon = step.icon ?: StepTimelineDefaults.iconFor(step.state)
    val surface = MaterialTheme.colorScheme.surface
    // Contraste garantido: a paleta do app decide o tom; o ícone dentro dele é escolhido por WCAG.
    val onTone = ColorContrast.pickOnColor(tone)

    Box(
        modifier = Modifier
            .size(StepTimelineDefaults.IndicatorSlotSize)
            .then(
                if (step.state == StepState.Current) {
                    Modifier
                        .clip(CircleShape)
                        .background(tone.copy(alpha = StepTimelineDefaults.CurrentHaloAlpha))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(StepTimelineDefaults.IndicatorSize)
                .clip(CircleShape)
                .background(if (filled) tone else surface)
                .then(
                    if (filled) {
                        Modifier
                    } else {
                        Modifier.border(
                            width = StepTimelineDefaults.PendingBorderWidth,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                // Decorativo: o estado é anunciado pelo `stateDescription` da etapa inteira.
                contentDescription = null,
                tint = if (filled) onTone else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(StepTimelineDefaults.IconSize),
            )
        }
    }
}
