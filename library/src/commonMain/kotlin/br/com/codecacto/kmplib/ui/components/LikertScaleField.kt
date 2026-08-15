package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * **Campo de escala Likert** — a fileira de pontos numerados com âncoras nas pontas que todo
 * questionário do ecossistema usa (`Nunca` … `Sempre`, `Discordo` … `Concordo`, 0–10 de NPS).
 *
 * Um instrumento típico repete este campo **dezenas de vezes na mesma sessão**, e é essa repetição
 * que decide se a pessoa termina de responder ou abandona no meio. Por isso ele é da fundação e não
 * de cada app.
 *
 * ### Por que não o [SegmentedControl]
 * O `SegmentedControl` é o componente errado aqui, e de quatro formas ao mesmo tempo: é visualmente
 * **unido** (sugere um seletor de modo, não uma régua de intensidade), não tem **âncoras**, não tem
 * estado **"não respondida"** (`selectedIndex: Int` obriga a inventar um selecionado) e sua semântica
 * é de **botão**, não de radiogroup — o leitor de tela anuncia *"botão 3"*, que não significa nada.
 *
 * ### Escala parametrizada — nada de 1..5 fixo
 * [min] e [max] vêm do cadastro do instrumento, não do código da tela: `1..5`, `1..7`, `0..10` e
 * `-2..2` (concordo/discordo com neutro em zero) são todos escalas reais. [optionLabels] nomeia cada
 * ponto **na ordem, a partir de [min]** e é opcional; [startAnchor]/[endAnchor] são a legenda das
 * pontas e **também vêm do cadastro** — literais "nunca"/"sempre" no código quebram no primeiro
 * protocolo diferente. Escala impossível não some da tela: vira [LikertScaleTexts.invalidScale] com
 * aviso no log (ver [likertPoints]).
 *
 * ### Acessibilidade é o motivo do componente, não um extra
 * - **Radiogroup de verdade:** `Modifier.selectableGroup()` no grupo e `Role.RadioButton` em cada
 *   alvo, com `selected` real — teclado e D-pad andam pelos pontos como num grupo de rádio.
 * - **Cada alvo se apresenta**: *"Às vezes, opção 3 de 5"* (ver [likertOptionDescription]). O rótulo
 *   completo não cabe embaixo do número em 390dp e abreviá-lo mentiria sobre a escala — então ele
 *   vive na descrição.
 * - **Sem resposta é um estado audível:** o grupo anuncia [LikertScaleTexts.unanswered] enquanto
 *   [value] for `null`.
 * - **Alvo ≥ [minTouchTarget]** (48dp), com quebra em linhas quando não cabe — ver *Responsividade*.
 * - **Estado nunca só por cor** (WCAG 1.4.1): o selecionado tem borda do **dobro** da espessura e
 *   número em **negrito**, além do tom ([likertOptionBorderWidth], [likertOptionBold]).
 * - **Foco visível**: anel na cor primária com afastamento, desenhado pelo próprio componente.
 * - **Tipografia escalável**: todo texto sai de `MaterialTheme.typography`, então acompanha o
 *   `AppTheme(fontScale = ...)`; a altura é **mínima**, nunca fixa, e cresce com o texto.
 *
 * ### Responsividade — a regra está escrita, não implícita
 * Numa linha única com peso igual, cinco alvos numa tela estreita viram cinco alvos de 20dp: passa em
 * build e falha no dedo. Aqui a largura disponível é medida e o componente decide **quantas opções
 * por linha** cabem preservando [minTouchTarget], quebrando em linhas equilibradas quando preciso
 * (10 pontos viram 5 + 5, não 6 + 4). A regra é pura e testável: [likertColumnCount] /
 * [likertRowRanges]. **O alvo nunca encolhe** — no pior caso o componente empilha uma opção por
 * linha. Os números **não** são abreviados nem substituídos: são a resposta que vai para o
 * instrumento.
 *
 * ### O que NÃO está aqui, de propósito
 * O **card** em volta (borda, sombra, fundo) é do app: uma tela põe a pergunta dentro de um `Card`,
 * outra dentro de uma lista, e embutir a moldura obrigaria todas a aceitarem a mesma. [isError]
 * sinaliza o campo (bordas e mensagem); a moldura de erro do card, se houver, é do app.
 *
 * ```kotlin
 * LikertScaleField(
 *     label = pergunta.enunciado,
 *     value = respostas[pergunta.id],
 *     onValueChange = { vm.dispatch(Action.Responder(pergunta.id, it)) },
 *     min = escala.min,
 *     max = escala.max,
 *     optionLabels = escala.rotulos,
 *     startAnchor = escala.rotulos.first(),
 *     endAnchor = escala.rotulos.last(),
 *     isError = pergunta.id in state.pendentes,
 *     errorMessage = "Responda para avançar",
 *     testTag = "q${pergunta.ordem}",
 * )
 * ```
 *
 * @param value ponto escolhido, ou `null` quando ainda **não respondida** (o componente é stateless).
 * @param onValueChange ponto tocado. Escolher não é ação destrutiva: não peça confirmação, e trocar
 *   a resposta é simplesmente tocar em outro ponto.
 * @param min/[max] extremos da escala, inclusivos.
 * @param optionLabels rótulo de cada ponto na ordem, a partir de [min]. Vazio = só os números.
 * @param startAnchor/[endAnchor] legendas das pontas. Em branco, a linha de âncoras não é desenhada.
 * @param label enunciado da pergunta. `null` quando a tela já o exibe por fora.
 * @param enabled `false` durante o envio ou em modo só-leitura.
 * @param isError sinaliza o campo como pendente/incorreto (bordas em `error` + [errorMessage]).
 * @param errorMessage mensagem exibida sob o campo quando [isError]. Diga o que fazer, não "campo
 *   inválido".
 * @param minTouchTarget alvo mínimo de cada opção — **piso**, ver *Responsividade*.
 * @param spacing espaço entre opções e entre linhas.
 * @param texts vocabulário do componente (i18n). Âncoras e rótulos NÃO ficam aqui: são conteúdo.
 * @param testTag prefixo dos ids de automação desta pergunta (ver [LikertScaleTestTags]). `null` não
 *   planta id nenhum — id repetido em 28 perguntas faz o teste responder a errada e passar verde.
 * @param contentDescriptionFor descrição anunciada por alvo. O default já monta
 *   *"&lt;rótulo&gt;, opção N de T"*; sobrescreva só quando o instrumento exigir outra frase.
 */
@Composable
fun LikertScaleField(
    value: Int?,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 5,
    optionLabels: List<String> = emptyList(),
    startAnchor: String = "",
    endAnchor: String = "",
    label: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    minTouchTarget: Dp = LikertScaleDefaults.MinTouchTarget,
    spacing: Dp = LikertScaleDefaults.Spacing,
    texts: LikertScaleTexts = LikertScaleTexts(),
    testTag: String? = null,
    contentDescriptionFor: (Int) -> String = {
        likertOptionDescription(it, min, max, optionLabels, texts)
    },
) {
    val points = remember(min, max) { likertPoints(min, max) }

    if (points.isEmpty()) {
        LaunchedEffect(min, max) {
            AppLogger.w(
                "LikertScaleField",
                "Escala impossível de renderizar (min=$min, max=$max, teto=" +
                    "${LikertScaleDefaults.MaxPoints} pontos) — confira o cadastro do instrumento.",
            )
        }
        Text(
            text = texts.invalidScale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.then(
                if (testTag != null) Modifier.testTag(LikertScaleTestTags.error(testTag)) else Modifier
            ),
        )
        return
    }

    val rootModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Column(
        modifier = rootModifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        BoxWithConstraints {
            // O slot inclui o espaço do anel de foco: medir só o alvo devolveria uma coluna a mais
            // do que cabe, e o alvo encolheria abaixo do piso sem ninguém perceber.
            val columns = likertColumnCount(
                availableWidth = maxWidth,
                pointCount = points.size,
                minTouchTarget = likertSlotMinSize(minTouchTarget),
                spacing = spacing,
            )
            val rows = likertRowRanges(points.size, columns)
            val unanswered = value == null

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .semantics {
                        // Sem isto, "ainda não respondi" e "respondi" soam iguais no leitor de tela —
                        // e num instrumento de 28 perguntas não há como saber onde se parou.
                        if (unanswered) stateDescription = texts.unanswered
                    },
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rows.forEach { range ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        range.forEach { index ->
                            val point = points[index]
                            LikertOption(
                                point = point,
                                state = likertOptionState(
                                    selected = value == point,
                                    enabled = enabled,
                                    isError = isError,
                                ),
                                description = contentDescriptionFor(point),
                                onClick = { onValueChange(point) },
                                minTouchTarget = minTouchTarget,
                                testTag = testTag?.let { LikertScaleTestTags.option(it, point) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Completa a última linha para manter as colunas alinhadas — opções de
                        // larguras diferentes na mesma escala sugerem pontos de "pesos" diferentes.
                        repeat(columns - (range.last - range.first + 1)) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (startAnchor.isNotBlank() || endAnchor.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = startAnchor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = endAnchor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }

        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = if (testTag != null) {
                    Modifier.testTag(LikertScaleTestTags.error(testTag))
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * Um alvo da escala. Privado de propósito: fora do grupo `selectableGroup()` ele perderia a semântica
 * de radiogroup, que é a razão de o componente existir.
 */
@Composable
private fun LikertOption(
    point: Int,
    state: LikertOptionState,
    description: String,
    onClick: () -> Unit,
    minTouchTarget: Dp,
    testTag: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val disabledAlpha = LikertScaleDefaults.DisabledContentAlpha

    val container: Color = when (state) {
        LikertOptionState.Selected, LikertOptionState.SelectedDisabled ->
            scheme.primary.copy(alpha = LikertScaleDefaults.SelectedContainerAlpha)
        LikertOptionState.Disabled -> scheme.surfaceVariant
        else -> scheme.surface
    }
    val borderColor: Color = when (state) {
        LikertOptionState.Selected -> scheme.primary
        LikertOptionState.SelectedDisabled -> scheme.primary.copy(alpha = disabledAlpha)
        LikertOptionState.UnselectedError -> scheme.error
        LikertOptionState.Disabled -> scheme.outlineVariant
        LikertOptionState.Unselected -> scheme.outline
    }
    val contentColor: Color = when (state) {
        LikertOptionState.Selected -> scheme.primary
        LikertOptionState.SelectedDisabled -> scheme.primary.copy(alpha = disabledAlpha)
        LikertOptionState.Disabled -> scheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        else -> scheme.onSurface
    }

    val enabled = state != LikertOptionState.Disabled && state != LikertOptionState.SelectedDisabled
    val selected = state.isSelected
    val shape = MaterialTheme.shapes.small
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // Anel de foco com afastamento: desenhado FORA da borda da opção, para não se confundir com a
    // borda dupla da seleção (que é uma informação diferente).
    val focusRing = if (focused) {
        Modifier
            .border(
                width = LikertScaleDefaults.SelectedBorderWidth,
                color = scheme.primary,
                shape = shape,
            )
            .padding(LikertScaleDefaults.FocusRingInset)
    } else {
        Modifier.padding(LikertScaleDefaults.SelectedBorderWidth + 2.dp)
    }

    Box(modifier = modifier.then(focusRing)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minWidth = minTouchTarget, minHeight = minTouchTarget)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) { contentDescription = description }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            shape = shape,
            color = container,
            contentColor = contentColor,
            border = BorderStroke(likertOptionBorderWidth(state), borderColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minTouchTarget)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = point.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (likertOptionBold(state)) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun LikertScaleFieldPreview() {
    AppTheme {
        LikertScaleField(
            label = "Com que frequência você consegue retomar o foco depois de uma interrupção?",
            value = 3,
            onValueChange = {},
            optionLabels = listOf("Nunca", "Raramente", "Às vezes", "Frequentemente", "Sempre"),
            startAnchor = "Nunca",
            endAnchor = "Sempre",
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun LikertScaleFieldUnansweredErrorPreview() {
    AppTheme {
        LikertScaleField(
            label = "Você percebe sinais de cansaço antes que eles atrapalhem o dia?",
            value = null,
            onValueChange = {},
            startAnchor = "Discordo",
            endAnchor = "Concordo",
            isError = true,
            errorMessage = "Responda para avançar",
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun LikertScaleFieldElevenPointsPreview() {
    AppTheme {
        LikertScaleField(
            label = "De 0 a 10, quanto você recomendaria o acompanhamento?",
            value = 8,
            onValueChange = {},
            min = 0,
            max = 10,
            startAnchor = "Nada provável",
            endAnchor = "Muito provável",
        )
    }
}
