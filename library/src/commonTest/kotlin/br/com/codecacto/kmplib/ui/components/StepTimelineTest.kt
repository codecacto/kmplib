package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes das regras puras do [StepTimeline] — o que decide se a linha do tempo comunica o
 * andamento certo: tom por estado, forma do marcador, ênfase, estado anunciado pelo leitor de tela,
 * clicabilidade e continuidade do fio.
 */
class StepTimelineTest {

    // --- tom semântico -------------------------------------------------------------------------

    @Test
    fun `cada estado tem o tom semantico esperado`() {
        assertEquals(StatusTone.SUCCESS, stepStateTone(StepState.Done))
        assertEquals(StatusTone.WARNING, stepStateTone(StepState.Current))
        assertEquals(StatusTone.NEUTRAL, stepStateTone(StepState.Pending))
        assertEquals(StatusTone.DANGER, stepStateTone(StepState.Canceled))
    }

    @Test
    fun `todo estado mapeia para um tom - nenhum cai em cor inventada`() {
        StepState.entries.forEach { state ->
            // `stepStateTone` é exaustivo: se um estado novo entrar sem mapeamento, não compila.
            assertTrue(stepStateTone(state) in StatusTone.entries)
        }
    }

    @Test
    fun `estados distintos nao compartilham o mesmo tom`() {
        val tons = StepState.entries.map { stepStateTone(it) }
        assertEquals(tons.size, tons.toSet().size, "dois estados com o mesmo tom seriam indistinguíveis")
    }

    // --- forma do marcador (WCAG 1.4.1: estado não pode ficar só na cor) -----------------------

    @Test
    fun `so a etapa pendente tem marcador vazado`() {
        assertFalse(stepIndicatorIsFilled(StepState.Pending))
        assertTrue(stepIndicatorIsFilled(StepState.Done))
        assertTrue(stepIndicatorIsFilled(StepState.Current))
        assertTrue(stepIndicatorIsFilled(StepState.Canceled))
    }

    @Test
    fun `so a etapa cancelada risca o titulo`() {
        assertTrue(stepTitleIsStruckThrough(StepState.Canceled))
        StepState.entries
            .filter { it != StepState.Canceled }
            .forEach { assertFalse(stepTitleIsStruckThrough(it), "$it não deveria riscar") }
    }

    @Test
    fun `so a etapa atual recebe enfase de peso`() {
        assertTrue(stepTitleIsEmphasized(StepState.Current))
        StepState.entries
            .filter { it != StepState.Current }
            .forEach { assertFalse(stepTitleIsEmphasized(it), "$it não deveria ter ênfase") }
    }

    @Test
    fun `cada estado tem ao menos um icone default proprio de forma`() {
        val icones = StepState.entries.map { StepTimelineDefaults.iconFor(it) }
        assertEquals(icones.size, icones.toSet().size, "ícones repetidos apagam a diferença de estado")
    }

    // --- acessibilidade ------------------------------------------------------------------------

    @Test
    fun `descricao de estado tem default pt-BR`() {
        val texts = StepTimelineTexts()
        assertEquals("Concluída", stepStateDescription(StepState.Done, texts))
        assertEquals("Em andamento", stepStateDescription(StepState.Current, texts))
        assertEquals("Pendente", stepStateDescription(StepState.Pending, texts))
        assertEquals("Cancelada", stepStateDescription(StepState.Canceled, texts))
    }

    @Test
    fun `descricao de estado usa o vocabulario do app quando informado`() {
        val texts = StepTimelineTexts(
            done = "Done",
            current = "In progress",
            pending = "Waiting",
            canceled = "Cancelled",
        )
        assertEquals("In progress", stepStateDescription(StepState.Current, texts))
        assertEquals("Waiting", stepStateDescription(StepState.Pending, texts))
    }

    @Test
    fun `nenhum estado fica sem descricao anunciada`() {
        StepState.entries.forEach { state ->
            assertTrue(stepStateDescription(state).isNotBlank(), "$state sem descrição")
        }
    }

    @Test
    fun `icone do marcador cheio mantem contraste grafico WCAG em qualquer paleta`() {
        // A cor do marcador é do TEMA do app; o ícone dentro dele é escolhido por contraste.
        val tonsPlausiveis = listOf(
            Color(0xFF1B5E20), // verde escuro
            Color(0xFFA5D6A7), // verde claro
            Color(0xFFFFC107), // âmbar (o caso que "branco sobre amarelo" quebraria)
            Color(0xFFB00020), // vermelho
            Color(0xFF0A0A0A), // quase preto
            Color(0xFFFAFAFA), // quase branco
        )
        tonsPlausiveis.forEach { tone ->
            val icone = ColorContrast.pickOnColor(tone)
            assertTrue(
                ColorContrast.meetsGraphicContrast(icone, tone),
                "ícone sem contraste gráfico (≥3:1) sobre $tone",
            )
        }
    }

    // --- clique --------------------------------------------------------------------------------

    @Test
    fun `etapa so e clicavel com handler e habilitada`() {
        val habilitada = TimelineStep(id = "a", title = "Publicado")
        val desabilitada = habilitada.copy(id = "b", enabled = false)

        assertTrue(stepIsClickable(habilitada, hasClickHandler = true))
        assertFalse(stepIsClickable(habilitada, hasClickHandler = false))
        assertFalse(stepIsClickable(desabilitada, hasClickHandler = true))
        assertFalse(stepIsClickable(desabilitada, hasClickHandler = false))
    }

    @Test
    fun `alvo de toque minimo respeita os 48dp do Material`() {
        assertTrue(StepTimelineDefaults.MinStepHeight.value >= 48f)
    }

    @Test
    fun `o slot do marcador cabe o circulo - o fio nao entorta entre estados`() {
        assertTrue(
            StepTimelineDefaults.IndicatorSlotSize.value > StepTimelineDefaults.IndicatorSize.value,
            "o halo da etapa atual precisa caber sem empurrar o trilho",
        )
        assertTrue(StepTimelineDefaults.IconSize.value < StepTimelineDefaults.IndicatorSize.value)
    }

    // --- continuidade do fio -------------------------------------------------------------------

    @Test
    fun `o fio nao comeca antes do primeiro nem passa do ultimo`() {
        val total = 4
        assertFalse(timelineDrawsSegmentAbove(0))
        assertTrue(timelineDrawsSegmentBelow(0, total))

        (1 until total - 1).forEach { i ->
            assertTrue(timelineDrawsSegmentAbove(i), "meio $i sem segmento acima")
            assertTrue(timelineDrawsSegmentBelow(i, total), "meio $i sem segmento abaixo")
        }

        assertTrue(timelineDrawsSegmentAbove(total - 1))
        assertFalse(timelineDrawsSegmentBelow(total - 1, total))
    }

    @Test
    fun `etapa unica nao desenha fio nenhum`() {
        assertFalse(timelineDrawsSegmentAbove(0))
        assertFalse(timelineDrawsSegmentBelow(0, 1))
    }

    @Test
    fun `lista vazia nao desenha fio`() {
        assertFalse(timelineDrawsSegmentBelow(0, 0))
    }

    @Test
    fun `o fio e continuo - todo par vizinho tem os dois lados do encontro`() {
        val total = 5
        (0 until total - 1).forEach { i ->
            assertTrue(
                timelineDrawsSegmentBelow(i, total) && timelineDrawsSegmentAbove(i + 1),
                "buraco entre a etapa $i e a ${i + 1}",
            )
        }
    }

    // --- modelo --------------------------------------------------------------------------------

    @Test
    fun `etapa nasce pendente e sem icone proprio`() {
        val step = TimelineStep(id = "x", title = "Prefeitura fecha o caso")
        assertEquals(StepState.Pending, step.state)
        assertEquals(null, step.icon)
        assertEquals(null, step.timeLabel)
        assertTrue(step.enabled)
        // Sem ícone próprio, o marcador cai no ícone do estado.
        assertEquals(StepTimelineDefaults.iconFor(StepState.Pending), step.icon ?: StepTimelineDefaults.iconFor(step.state))
    }

    @Test
    fun `id e a chave estavel da lista - nao ha id repetido no andamento de exemplo`() {
        val steps = listOf(
            TimelineStep("pub", "Publicado pelo morador", timeLabel = "hoje 09:12", state = StepState.Done),
            TimelineStep("env", "Enviado à prefeitura", timeLabel = "hoje 09:40", state = StepState.Done),
            TimelineStep("vis", "Prefeitura visualizou", timeLabel = "hoje 10:05", state = StepState.Current),
            TimelineStep("fim", "Prefeitura fecha o caso", timeLabel = "aguardando"),
        )
        assertEquals(steps.size, steps.map { it.id }.toSet().size)
    }
}
