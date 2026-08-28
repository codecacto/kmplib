package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes das regras puras do [LikertScaleField] — as três coisas que, erradas, fazem um questionário
 * ser abandonado no meio: a escala renderizada, o alvo de toque em tela estreita e o que o leitor de
 * tela anuncia em cada ponto.
 */
class LikertScaleTest {

    // ---------------------------------------------------------------------------------------
    // Escala parametrizada
    // ---------------------------------------------------------------------------------------

    @Test
    fun `escala padrao de cinco pontos`() {
        assertEquals(listOf(1, 2, 3, 4, 5), likertPoints(min = 1, max = 5))
    }

    @Test
    fun `escala nao e 1 a 5 fixo`() {
        // O motor é multi-protocolo: a amplitude vem do cadastro do instrumento.
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), likertPoints(1, 7))
        assertEquals(11, likertPoints(0, 10).size, "0..10 (NPS/EVA) tem 11 pontos")
        assertEquals(listOf(-2, -1, 0, 1, 2), likertPoints(-2, 2), "escala com neutro em zero")
    }

    @Test
    fun `escala impossivel devolve vazio em vez de renderizar lixo`() {
        assertTrue(likertPoints(min = 5, max = 1).isEmpty(), "invertida")
        assertTrue(likertPoints(min = 3, max = 3).isEmpty(), "um ponto só não é escolha")
        assertTrue(likertPoints(min = 1, max = 500).isEmpty(), "acima do teto de pontos")
    }

    @Test
    fun `teto de pontos e inclusivo`() {
        val teto = LikertScaleDefaults.MaxPoints
        assertEquals(teto, likertPoints(1, teto).size)
        assertTrue(likertPoints(1, teto + 1).isEmpty())
    }

    // ---------------------------------------------------------------------------------------
    // Responsividade — o alvo NUNCA encolhe
    // ---------------------------------------------------------------------------------------

    @Test
    fun `cinco pontos cabem em uma linha num telefone comum`() {
        // 390dp de tela menos as margens de 20dp => ~350dp úteis.
        assertEquals(5, likertColumnCount(350.dp, pointCount = 5))
    }

    @Test
    fun `escala longa quebra em linhas equilibradas`() {
        // 10 pontos caberiam 6 por linha, mas 6+4 sugere um agrupamento que a escala não tem.
        assertEquals(5, likertColumnCount(350.dp, pointCount = 10))
        assertEquals(listOf(0 until 5, 5 until 10), likertRowRanges(10, 5))
    }

    @Test
    fun `escala de onze pontos se distribui sem perder ponto`() {
        val columns = likertColumnCount(350.dp, pointCount = 11)
        val rows = likertRowRanges(11, columns)
        assertTrue(rows.size > 1, "11 pontos não cabem numa linha de telefone")
        assertEquals(11, rows.sumOf { it.last - it.first + 1 }, "nenhum ponto pode sumir")
        assertTrue(rows.all { it.last - it.first + 1 <= columns }, "nenhuma linha estoura as colunas")
    }

    @Test
    fun `tela estreita quebra em vez de encolher o alvo`() {
        // Este é o defeito que o componente existe para impedir: cinco alvos de 20dp numa linha só.
        val columns = likertColumnCount(200.dp, pointCount = 5)
        assertTrue(columns < 5, "deveria quebrar, não espremer — veio $columns")
        val larguraPorSlot = (200f - (columns - 1) * LikertScaleDefaults.Spacing.value) / columns
        assertTrue(
            larguraPorSlot >= likertSlotMinSize().value,
            "slot de ${larguraPorSlot}dp está abaixo do mínimo de ${likertSlotMinSize()}",
        )
    }

    @Test
    fun `o anel de foco entra na conta da largura`() {
        // Medir só o alvo devolveria uma coluna a mais do que cabe, e o alvo encolheria em silêncio.
        assertTrue(likertSlotMinSize() > LikertScaleDefaults.MinTouchTarget)
        assertEquals(
            LikertScaleDefaults.MinTouchTarget + LikertScaleDefaults.FocusRingInset * 2,
            likertSlotMinSize(),
        )
        val comAnel = likertColumnCount(320.dp, 7, minTouchTarget = likertSlotMinSize())
        val semAnel = likertColumnCount(320.dp, 7, minTouchTarget = LikertScaleDefaults.MinTouchTarget)
        assertTrue(comAnel <= semAnel, "ignorar o anel superestima quantas opções cabem")
    }

    @Test
    fun `largura absurda ainda devolve uma opcao por linha`() {
        assertEquals(1, likertColumnCount(30.dp, pointCount = 5))
        assertEquals(5, likertRowRanges(5, 1).size)
    }

    @Test
    fun `sem pontos nao ha colunas nem linhas`() {
        assertEquals(0, likertColumnCount(350.dp, pointCount = 0))
        assertTrue(likertRowRanges(0, 5).isEmpty())
        assertTrue(likertRowRanges(5, 0).isEmpty())
    }

    @Test
    fun `alvo minimo e o piso do Material`() {
        assertEquals(48.dp, LikertScaleDefaults.MinTouchTarget)
    }

    // ---------------------------------------------------------------------------------------
    // Estado — e a redundância não-cromática
    // ---------------------------------------------------------------------------------------

    @Test
    fun `estado combina selecao habilitacao e erro`() {
        assertEquals(
            LikertOptionState.Selected,
            likertOptionState(selected = true, enabled = true, isError = false),
        )
        assertEquals(
            LikertOptionState.Unselected,
            likertOptionState(selected = false, enabled = true, isError = false),
        )
        assertEquals(
            LikertOptionState.UnselectedError,
            likertOptionState(selected = false, enabled = true, isError = true),
        )
        assertEquals(
            LikertOptionState.Disabled,
            likertOptionState(selected = false, enabled = false, isError = false),
        )
    }

    @Test
    fun `resposta ja dada continua visivel durante o envio`() {
        // Enquanto o lote é enviado o campo fica desabilitado — mas apagar a marcação faria a pessoa
        // achar que a resposta se perdeu e responder de novo.
        val estado = likertOptionState(selected = true, enabled = false, isError = false)
        assertEquals(LikertOptionState.SelectedDisabled, estado)
        assertTrue(estado.isSelected)
    }

    @Test
    fun `selecao nunca depende so da cor`() {
        val selecionado = LikertOptionState.Selected
        val naoSelecionado = LikertOptionState.Unselected
        assertTrue(
            likertOptionBorderWidth(selecionado) > likertOptionBorderWidth(naoSelecionado),
            "a borda do selecionado tem de ser mais grossa (WCAG 1.4.1)",
        )
        assertTrue(likertOptionBold(selecionado), "o número do selecionado sai em negrito")
        assertFalse(likertOptionBold(naoSelecionado))
    }

    @Test
    fun `erro nao apaga a selecao ja feita`() {
        // Campo marcado como pendente por outra razão não pode "desmarcar" visualmente a resposta.
        assertEquals(
            LikertOptionState.Selected,
            likertOptionState(selected = true, enabled = true, isError = true),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Acessibilidade — o que o leitor de tela fala
    // ---------------------------------------------------------------------------------------

    @Test
    fun `cada alvo anuncia o rotulo e a posicao`() {
        val rotulos = listOf("Nunca", "Raramente", "Às vezes", "Frequentemente", "Sempre")
        assertEquals(
            "Às vezes, opção 3 de 5",
            likertOptionDescription(value = 3, min = 1, max = 5, optionLabels = rotulos),
        )
    }

    @Test
    fun `sem rotulos o alvo ainda diz a posicao`() {
        // Nunca "botão 3": sem posição, o número sozinho não significa nada para quem não vê a régua.
        assertEquals("Opção 4 de 5", likertOptionDescription(value = 4, min = 1, max = 5))
    }

    @Test
    fun `posicao e relativa ao minimo da escala`() {
        // Numa escala 0..10, o ponto 0 é a PRIMEIRA opção, não a "opção 0".
        assertEquals("Opção 1 de 11", likertOptionDescription(value = 0, min = 0, max = 10))
        assertEquals("Opção 11 de 11", likertOptionDescription(value = 10, min = 0, max = 10))
        assertEquals("Opção 1 de 5", likertOptionDescription(value = -2, min = -2, max = 2))
    }

    @Test
    fun `rotulos sao indexados a partir do minimo`() {
        val rotulos = listOf("Detrator", "Neutro", "Promotor")
        assertEquals("Detrator", likertOptionLabel(value = 0, min = 0, optionLabels = rotulos))
        assertEquals("Promotor", likertOptionLabel(value = 2, min = 0, optionLabels = rotulos))
        assertEquals(null, likertOptionLabel(value = 9, min = 0, optionLabels = rotulos))
    }

    @Test
    fun `rotulo em branco nao vira descricao vazia`() {
        val rotulos = listOf("Nunca", "  ", "Sempre")
        assertEquals(null, likertOptionLabel(value = 2, min = 1, optionLabels = rotulos))
        assertEquals(
            "Opção 2 de 3",
            likertOptionDescription(value = 2, min = 1, max = 3, optionLabels = rotulos),
        )
    }

    @Test
    fun `descricao respeita os textos traduzidos`() {
        val textos = LikertScaleTexts(option = "option", of = "of")
        assertEquals(
            "Never, option 1 of 5",
            likertOptionDescription(1, 1, 5, listOf("Never"), textos),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Automação
    // ---------------------------------------------------------------------------------------

    @Test
    fun `ids de automacao sao por pergunta`() {
        // Id fixo apareceria 28 vezes na mesma rolagem e o teste responderia a pergunta errada.
        assertEquals("q12-opcao-3", LikertScaleTestTags.option("q12", 3))
        assertEquals("q13-opcao-3", LikertScaleTestTags.option("q13", 3))
        assertEquals("q12-erro", LikertScaleTestTags.error("q12"))
    }
}
