package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Textos do [LikertScaleField] (i18n; defaults pt-BR).
 *
 * As **âncoras** (`Nunca` / `Sempre`) e os **rótulos por ponto** (`Às vezes`…) NÃO ficam aqui: eles
 * são conteúdo do instrumento, vêm do banco do app e mudam por protocolo — a lib recebe, não traduz.
 * O que mora aqui é só o vocabulário do próprio componente, que é o mesmo em qualquer questionário.
 *
 * @param option palavra usada na descrição de cada alvo lida pelo leitor de tela ("**opção** 3 de 5").
 * @param of separador de posição ("opção 3 **de** 5") — mesmo papel do `ofSeparator` do
 *   [ProgressCounter], de propósito.
 * @param unanswered estado anunciado quando nada foi selecionado. É o que torna audível a diferença
 *   entre "ainda não respondi" e "respondi" — num formulário de 28 perguntas, quem usa leitor de
 *   tela não tem outra forma de saber onde parou.
 * @param invalidScale mensagem exibida quando a escala recebida é impossível de renderizar (ver
 *   [likertPoints]). Aparecer é melhor que sumir: escala vazia na tela vira "a pergunta não carregou"
 *   e ninguém descobre que o cadastro é que está errado.
 */
data class LikertScaleTexts(
    val option: String = "opção",
    val of: String = "de",
    val unanswered: String = "Não respondida",
    val invalidScale: String = "Escala inválida",
)

/** Valores padrão do [LikertScaleField]. */
object LikertScaleDefaults {
    /**
     * Alvo mínimo de toque de cada opção — **48dp**.
     *
     * É o mínimo do Material, e é **piso**, não sugestão: quando os pontos não cabem na largura
     * disponível, o componente **quebra em linhas** em vez de encolher o alvo (ver
     * [likertColumnCount]). Escala Likert com alvo de 20dp é o defeito clássico de questionário em
     * telefone — e o público que mais responde questionário longo é justamente o que erra o toque.
     */
    val MinTouchTarget: Dp = 48.dp

    /** Espaço entre as opções (e entre as linhas, quando há quebra). */
    val Spacing: Dp = 8.dp

    /** Espessura da borda da opção **selecionada** — o dobro da normal (redundância não-cromática). */
    val SelectedBorderWidth: Dp = 2.dp

    /** Espessura da borda das demais opções. */
    val BorderWidth: Dp = 1.dp

    /**
     * Espaço reservado em volta de cada opção para o **anel de foco**.
     *
     * É reservado sempre (não só quando há foco) para que a fileira não "pule" ao andar com o
     * teclado. Reservar significa que ele **sai da largura útil do alvo** — daí
     * [likertSlotMinSize]: sem entrar na conta, um alvo de 48dp vira 40dp de área tocável e a
     * garantia deste componente deixa de valer justamente onde ninguém olha.
     */
    val FocusRingInset: Dp = 4.dp

    /** Opacidade do preenchimento da opção selecionada. */
    const val SelectedContainerAlpha: Float = 0.12f

    /** Opacidade do conteúdo desabilitado. */
    const val DisabledContentAlpha: Float = 0.38f

    /**
     * Teto de pontos da escala — **15**.
     *
     * Não é limitação técnica: é a fronteira entre "escala Likert" e "outra coisa". O maior
     * instrumento usado na prática é o 0–10 (NPS/EVA, 11 pontos); acima disso o desenho correto é
     * um slider ou um campo numérico, não uma fileira de alvos. Sem teto, um `max` corrompido
     * (`1..500`) renderizaria 500 alvos e travaria a tela em vez de acusar o dado ruim.
     */
    const val MaxPoints: Int = 15
}

/**
 * Pontos da escala, do menor ao maior — **a fonte única** de quantos alvos existem.
 *
 * Devolve **lista vazia** quando a escala é impossível: [max] menor que [min], escala de um ponto só
 * (não é escolha, é afirmação) ou mais que [LikertScaleDefaults.MaxPoints] pontos. O componente
 * exibe [LikertScaleTexts.invalidScale] nesse caso — nunca desenha nada e some.
 *
 * A escala é **parametrizada de propósito**: `1..5`, `1..7`, `0..10` e `-2..2` (concordo/discordo com
 * ponto neutro em zero) são todos instrumentos reais, e a amplitude vem do cadastro do protocolo, não
 * do código da tela.
 */
fun likertPoints(min: Int, max: Int): List<Int> {
    if (max <= min) return emptyList()
    val count = max - min + 1
    if (count > LikertScaleDefaults.MaxPoints) return emptyList()
    return (min..max).toList()
}

/**
 * Largura/altura que uma opção realmente ocupa na fileira: o alvo tocável **mais** o espaço do anel
 * de foco dos dois lados. É este valor, não o alvo cru, que deve ir para [likertColumnCount] — a
 * conta feita com o alvo cru devolve uma coluna a mais do que cabe e o alvo encolhe em silêncio.
 */
fun likertSlotMinSize(minTouchTarget: Dp = LikertScaleDefaults.MinTouchTarget): Dp =
    minTouchTarget + LikertScaleDefaults.FocusRingInset * 2

/**
 * Quantas opções cabem **por linha** sem encolher o alvo abaixo de [minTouchTarget].
 *
 * Esta é a regra de responsividade do componente, e ela é explícita porque o comportamento errado é
 * silencioso: com `weight(1f)` numa linha única, cinco alvos numa tela de 320dp viram cinco alvos de
 * 20dp — passa em build, passa em review, e só falha no dedo de quem responde.
 *
 * 1. Calcula quantos alvos de [minTouchTarget] cabem em [availableWidth] com [spacing] entre eles.
 * 2. **Equilibra as linhas**: 10 pontos que caberiam 6 por linha viram **5 + 5**, não 6 + 4 — fila
 *    desigual sugere agrupamento que não existe na escala.
 * 3. Nunca devolve menos que 1: numa largura absurda, o resultado é uma opção por linha (alvo
 *    preservado, layout feio) em vez de alvo microscópico (layout bonito, produto quebrado).
 */
fun likertColumnCount(
    availableWidth: Dp,
    pointCount: Int,
    minTouchTarget: Dp = likertSlotMinSize(),
    spacing: Dp = LikertScaleDefaults.Spacing,
): Int {
    if (pointCount <= 0) return 0
    val slot = minTouchTarget.value + spacing.value
    if (slot <= 0f) return pointCount
    val fits = ((availableWidth.value + spacing.value) / slot).toInt()
    val columns = fits.coerceIn(1, pointCount)
    val rows = ceilDiv(pointCount, columns)
    return ceilDiv(pointCount, rows)
}

/**
 * Faixas de índices por linha, dadas as [columns] de [likertColumnCount]. A última linha pode ser
 * mais curta; o componente a completa com espaço vazio para que as colunas permaneçam alinhadas
 * (opções de larguras diferentes na mesma escala confundem a leitura de "todos os pontos são iguais").
 */
fun likertRowRanges(pointCount: Int, columns: Int): List<IntRange> {
    if (pointCount <= 0 || columns <= 0) return emptyList()
    return (0 until pointCount step columns).map { start ->
        start until minOf(start + columns, pointCount)
    }
}

private fun ceilDiv(a: Int, b: Int): Int = if (b <= 0) a else (a + b - 1) / b

/**
 * Estado visual de uma opção. Existe como enum (e não como par de booleanos espalhado pela UI) para
 * que a **redundância não-cromática** seja testável: quem muda a cor tem de responder também pela
 * espessura da borda e pelo peso do número.
 */
enum class LikertOptionState {
    /** Escolhida pela pessoa. */
    Selected,

    /** Escolhida, mas o campo está bloqueado (envio em curso, formulário só-leitura). */
    SelectedDisabled,

    /** Disponível, sem escolha. */
    Unselected,

    /** Disponível e o campo está sinalizado como pendente/erro. */
    UnselectedError,

    /** Indisponível. */
    Disabled,
}

/** Estado visual da opção. Lógica pura — testável. */
fun likertOptionState(
    selected: Boolean,
    enabled: Boolean,
    isError: Boolean,
): LikertOptionState = when {
    !enabled && selected -> LikertOptionState.SelectedDisabled
    !enabled -> LikertOptionState.Disabled
    selected -> LikertOptionState.Selected
    isError -> LikertOptionState.UnselectedError
    else -> LikertOptionState.Unselected
}

/** `true` quando o estado representa uma opção escolhida. */
val LikertOptionState.isSelected: Boolean
    get() = this == LikertOptionState.Selected || this == LikertOptionState.SelectedDisabled

/**
 * Espessura da borda por estado — **o dobro no selecionado**.
 *
 * É metade da redundância exigida pela WCAG 1.4.1 (a outra metade é [likertOptionBold]): num app cuja
 * paleta é do cliente, e para quem enxerga cor de forma atípica, a diferença de tom entre selecionado
 * e não selecionado pode simplesmente não existir.
 */
fun likertOptionBorderWidth(state: LikertOptionState): Dp =
    if (state.isSelected) LikertScaleDefaults.SelectedBorderWidth else LikertScaleDefaults.BorderWidth

/** `true` quando o número deve sair em negrito — a segunda pista não-cromática da seleção. */
fun likertOptionBold(state: LikertOptionState): Boolean = state.isSelected

/**
 * Rótulo textual de um ponto da escala (`"Às vezes"` para o 3 de uma escala 1..5), ou `null` quando o
 * instrumento não nomeia os pontos. `optionLabels` é indexada a partir de [min], não de zero.
 */
fun likertOptionLabel(value: Int, min: Int, optionLabels: List<String>): String? =
    optionLabels.getOrNull(value - min)?.takeIf { it.isNotBlank() }

/**
 * O que o leitor de tela anuncia em cada alvo — *"Às vezes, opção 3 de 5"*.
 *
 * É a razão de este componente existir em vez de um [SegmentedControl]: sem isto, quem usa leitor de
 * tela ouve *"botão 3"* vinte e oito vezes e não tem como saber o que significa o 3. O rótulo
 * completo **não cabe** embaixo do número numa tela de 390dp, e abreviá-lo mentiria sobre a escala —
 * então ele vive aqui.
 */
fun likertOptionDescription(
    value: Int,
    min: Int,
    max: Int,
    optionLabels: List<String> = emptyList(),
    texts: LikertScaleTexts = LikertScaleTexts(),
): String {
    val position = value - min + 1
    val total = max - min + 1
    val positionPart = "${texts.option} $position ${texts.of} $total"
    val label = likertOptionLabel(value, min, optionLabels)
    return if (label == null) positionPart.replaceFirstChar { it.uppercaseChar() }
    else "$label, $positionPart"
}

/**
 * Sufixos de `testTag` do [LikertScaleField], para automação de UI.
 *
 * **Não há id fixo aqui, e isso é decisão.** Uma avaliação exibe 28 escalas na mesma rolagem; um id
 * constante (`likert-opcao-3`) apareceria 28 vezes e o teste tocaria na primeira que encontrasse —
 * respondendo a pergunta errada e ficando verde. Por isso o id é **por pergunta**: o app passa
 * `testTag = "q12"` e a lib planta `q12`, `q12-opcao-3`, `q12-erro`.
 */
object LikertScaleTestTags {
    /** Id de uma opção, dado o prefixo da pergunta e o valor do ponto. */
    fun option(prefix: String, value: Int): String = "$prefix-opcao-$value"

    /** Id da mensagem de erro da pergunta. */
    fun error(prefix: String): String = "$prefix-erro"
}
