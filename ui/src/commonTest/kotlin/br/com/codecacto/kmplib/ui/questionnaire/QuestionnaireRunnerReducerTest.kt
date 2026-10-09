package br.com.codecacto.kmplib.ui.questionnaire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A regra de navegação e de resposta do runner — pura, sem tela. */
class QuestionnaireRunnerReducerTest {

    private val form = Questionnaire(
        scale = scale(0, 3),
        blocks = listOf(
            block("a", question("a1"), question("a2"), question("a3", required = false)),
            block(
                "b",
                yesNo("fuma"),
                number("macos", min = 0.0, max = 60.0, visibleIf = cond("""{"question":"fuma","equals":"sim"}""")),
                question("obs", type = QuestionnaireQuestionType.TEXT, required = false),
            ),
            block("c", question("c1"), question("c2", type = QuestionnaireQuestionType.MULTI_CHOICE, options = listOf(option("x"), option("y")))),
        ),
    )

    private fun start(answers: Map<String, QuestionnaireValue> = emptyMap(), respondent: String? = null, exitEnabled: Boolean = false) =
        QuestionnaireRunnerState.start(form, answers = answers, respondent = respondent, exitEnabled = exitEnabled)

    private fun QuestionnaireRunnerState.answering(vararg pairs: Pair<String, Any>): QuestionnaireRunnerState =
        answersOf(*pairs).entries.fold(this) { s, (id, v) -> s.reduce(QuestionnaireRunnerAction.Answer(id, v)).state }

    // ------------------------------------------------------------------------------------------
    // Início e posição
    // ------------------------------------------------------------------------------------------

    @Test
    fun `comeca na primeira sem resposta`() {
        assertEquals("a" to "a1", start().let { it.blockId to it.questionId })
        val resumed = start(answersOf("a1" to 1, "a2" to 2, "a3" to 0, "fuma" to "nao"))
        assertEquals("b" to "obs", resumed.blockId to resumed.questionId, "retoma onde parou")
        val fresh = QuestionnaireRunnerState.start(form, answers = answersOf("a1" to 1), resume = false)
        assertEquals("a" to "a1", fresh.blockId to fresh.questionId, "sem retomada: o começo")
        val empty = QuestionnaireRunnerState.start(Questionnaire())
        assertNull(empty.blockId)
        assertNull(empty.position())
    }

    @Test
    fun `posicao resolve bloco e pergunta que deixaram de aparecer`() {
        val state = start().answering("fuma" to "sim").copy(blockId = "b", questionId = "macos")
        assertEquals("macos", state.position()?.question?.id)
        val hidden = state.answering("fuma" to "nao")
        assertEquals("obs", hidden.position()?.question?.id, "macos sumiu: a vizinha depois dela")
        val unknownBlock = start().copy(blockId = "nao-existe")
        assertEquals("a", unknownBlock.position()?.block?.id)
        val noQuestion = start(answersOf("a1" to 1)).copy(blockId = "a", questionId = null)
        assertEquals("a2", noQuestion.position()?.question?.id, "sem pergunta escolhida: a primeira sem resposta")
        with(start().position()!!) {
            assertTrue(isFirstBlock)
            assertFalse(isLastBlock)
            assertTrue(isFirstQuestion)
            assertFalse(isLastQuestion)
            assertEquals(3, blockCount)
            assertEquals(3, questionCount)
        }
    }

    @Test
    fun `bloco oculto no meio cai no seguinte`() {
        val gated = Questionnaire(
            scale = scale(0, 3),
            blocks = listOf(
                block("x", question("x1")),
                block("y", question("y1"), visibleIf = cond("""{"context":"mostrar","equals":true}""")),
                block("z", question("z1")),
            ),
        )
        val state = QuestionnaireRunnerState(gated, context = answersOf("mostrar" to true), blockId = "y")
        assertEquals("y", state.position()?.block?.id)
        assertEquals("z", state.copy(context = emptyMap()).position()?.block?.id)
        val last = QuestionnaireRunnerState(gated, blockId = "z").copy(questionnaire = gated.copy(blocks = gated.blocks.take(2)))
        assertEquals("x", last.position()?.block?.id, "bloco removido do documento: começo")
    }

    // ------------------------------------------------------------------------------------------
    // Responder
    // ------------------------------------------------------------------------------------------

    @Test
    fun `responder guarda e emite o item da fila`() {
        val update = start().act(QuestionnaireRunnerAction.Answer("a1", QuestionnaireValue.of(2)))
        assertEquals(QuestionnaireValue.Number(2.0), update.state.answers["a1"])
        assertEquals(QuestionnaireRunnerEvent.Answered(QuestionnaireAnswerItem("a1", QuestionnaireValue.Number(2.0))), update.event)
        val again = update.state.act(QuestionnaireRunnerAction.Answer("a1", QuestionnaireValue.of(2)))
        assertNull(again.event, "mesmo valor: nada a salvar")
    }

    @Test
    fun `resposta invalida ou de pergunta oculta e ignorada`() {
        val state = start()
        assertSame(state, state.act(QuestionnaireRunnerAction.Answer("a1", QuestionnaireValue.of(9))).state, "fora da régua")
        assertSame(state, state.act(QuestionnaireRunnerAction.Answer("fuma", QuestionnaireValue.of("talvez"))).state, "opção inexistente")
        assertSame(state, state.act(QuestionnaireRunnerAction.Answer("macos", QuestionnaireValue.of(3))).state, "oculta")
        assertSame(state, state.act(QuestionnaireRunnerAction.Answer("nao-existe", QuestionnaireValue.of(1))).state)
        assertSame(state, state.act(QuestionnaireRunnerAction.Answer("obs", QuestionnaireValue.of(3))).state, "texto só aceita texto")
        assertSame(state, state.act(QuestionnaireRunnerAction.EditNumber("a1", "2")).state, "EditNumber só em número")
    }

    @Test
    fun `texto guarda o digitado e o item vai normalizado`() {
        val typed = start().act(QuestionnaireRunnerAction.Answer("obs", QuestionnaireValue.of("  ")))
        assertEquals(QuestionnaireValue.Text("  "), typed.state.answers["obs"], "o eco do campo precisa do texto exato")
        assertNull(typed.event, "em branco não é resposta: nada mudou para o servidor")
        val written = typed.state.act(QuestionnaireRunnerAction.Answer("obs", QuestionnaireValue.of("  dor")))
        assertEquals(QuestionnaireAnswerItem("obs", QuestionnaireValue.Text("  dor")), (written.event as QuestionnaireRunnerEvent.Answered).item)
        val cleared = written.state.act(QuestionnaireRunnerAction.Answer("obs", QuestionnaireValue.of("")))
        assertFalse("obs" in cleared.state.answers)
        assertEquals(QuestionnaireAnswerItem("obs", null), (cleared.event as QuestionnaireRunnerEvent.Answered).item, "apagar vai à fila como null")
    }

    @Test
    fun `multipla - ordem das opcoes e vazio apaga`() {
        val state = start().act(QuestionnaireRunnerAction.Answer("c2", QuestionnaireValue.of(listOf("y", "x")))).state
        assertEquals(QuestionnaireValue.Choices(listOf("x", "y")), state.answers["c2"])
        val cleared = state.act(QuestionnaireRunnerAction.Answer("c2", QuestionnaireValue.Choices(emptyList())))
        assertFalse("c2" in cleared.state.answers)
        assertEquals(QuestionnaireAnswerItem("c2", null), (cleared.event as QuestionnaireRunnerEvent.Answered).item)
    }

    @Test
    fun `numero - rascunho cru e valor com virgula`() {
        val base = start().answering("fuma" to "sim")
        val partial = base.act(QuestionnaireRunnerAction.EditNumber("macos", "12,"))
        assertEquals("12,", partial.state.numberDrafts["macos"])
        assertEquals(QuestionnaireValue.Number(12.0), partial.state.answers["macos"])
        assertEquals(QuestionnaireAnswerItem("macos", QuestionnaireValue.Number(12.0)), (partial.event as QuestionnaireRunnerEvent.Answered).item)
        val decimal = partial.state.act(QuestionnaireRunnerAction.EditNumber("macos", "12,5"))
        assertEquals(QuestionnaireValue.Number(12.5), decimal.state.answers["macos"])
        val sameValue = decimal.state.act(QuestionnaireRunnerAction.EditNumber("macos", "12,50"))
        assertNull(sameValue.event, "mesmo número: nada a salvar")
        assertEquals("12,50", sameValue.state.numberDrafts["macos"])
        val erased = sameValue.state.act(QuestionnaireRunnerAction.EditNumber("macos", ""))
        assertFalse("macos" in erased.state.answers)
        assertFalse("macos" in erased.state.numberDrafts)
        assertEquals(QuestionnaireAnswerItem("macos", null), (erased.event as QuestionnaireRunnerEvent.Answered).item)
        val junk = sameValue.state.act(QuestionnaireRunnerAction.EditNumber("macos", ","))
        assertFalse("macos" in junk.state.answers, "texto que não é número = sem resposta")
        assertEquals(",", junk.state.numberDrafts["macos"])
        val viaAnswer = sameValue.state.act(QuestionnaireRunnerAction.Answer("macos", QuestionnaireValue.of(7)))
        assertFalse("macos" in viaAnswer.state.numberDrafts, "valor de fora descarta o rascunho")
    }

    @Test
    fun `quem respondeu - carimbo local do respondente`() {
        val state = start(respondent = "recepcao").copy(filledBy = mapOf("a2" to "paciente"))
        val answered = state.act(QuestionnaireRunnerAction.Answer("a1", QuestionnaireValue.of(1))).state
        assertEquals("recepcao", answered.filledBy["a1"])
        assertEquals("paciente", answered.filledBy["a2"], "o resto não muda")
        val sameValue = state.copy(answers = answersOf("a2" to 1)).act(QuestionnaireRunnerAction.Answer("a2", QuestionnaireValue.of(1))).state
        assertEquals("paciente", sameValue.filledBy["a2"], "tocar no mesmo valor não toma a resposta")
        val changed = state.copy(answers = answersOf("a2" to 1)).act(QuestionnaireRunnerAction.Answer("a2", QuestionnaireValue.of(3))).state
        assertEquals("recepcao", changed.filledBy["a2"])
        val anonymous = start().act(QuestionnaireRunnerAction.Answer("a1", QuestionnaireValue.of(1))).state
        assertNull(anonymous.filledBy["a1"], "sem respondente, sem carimbo")
        val erased = answered.act(QuestionnaireRunnerAction.Answer("obs", QuestionnaireValue.of("x"))).state
            .act(QuestionnaireRunnerAction.Answer("obs", null)).state
        assertNull(erased.filledBy["obs"])
    }

    @Test
    fun `editar limpa o erro so daquela pergunta`() {
        val flagged = start().act(QuestionnaireRunnerAction.Next(oneByOne = false)).state
        assertEquals(setOf("a1", "a2"), flagged.flagged)
        val oneFixed = flagged.act(QuestionnaireRunnerAction.Answer("a1", QuestionnaireValue.of(0))).state
        assertEquals(setOf("a2"), oneFixed.flagged, "a outra continua marcada até ser respondida")
    }

    // ------------------------------------------------------------------------------------------
    // Navegação — agrupado
    // ------------------------------------------------------------------------------------------

    @Test
    fun `agrupado - bloco incompleto marca todas e foca a primeira`() {
        val update = start().act(QuestionnaireRunnerAction.Next(oneByOne = false))
        assertNull(update.event)
        assertEquals("a", update.state.blockId)
        assertEquals(setOf("a1", "a2"), update.state.flagged, "a3 é opcional")
        assertEquals(QuestionnaireFocusRequest("a1", 1), update.state.focusRequest)
        val again = update.state.act(QuestionnaireRunnerAction.Next(oneByOne = false))
        assertEquals(2L, again.state.focusRequest?.serial, "dois pedidos seguidos são distintos")
    }

    @Test
    fun `agrupado - bloco completo vai ao seguinte na primeira sem resposta`() {
        val state = start().answering("a1" to 1, "a2" to 2, "fuma" to "nao")
        val update = state.act(QuestionnaireRunnerAction.Next(oneByOne = false))
        assertEquals("b", update.state.blockId)
        assertEquals("obs", update.state.questionId)
        assertTrue(update.state.flagged.isEmpty())
        assertNull(update.state.focusRequest)
    }

    @Test
    fun `numero fora da faixa barra o avanco`() {
        val state = start().answering("a1" to 1, "a2" to 2, "fuma" to "sim").copy(blockId = "b")
            .act(QuestionnaireRunnerAction.EditNumber("macos", "90")).state
        val update = state.act(QuestionnaireRunnerAction.Next(oneByOne = false))
        assertEquals(setOf("macos"), update.state.flagged)
        assertEquals("b", update.state.blockId)
    }

    @Test
    fun `concluir emite as respostas que valem e descarta as ocultas`() {
        val state = start(answersOf("a1" to 1, "a2" to 2, "fuma" to "sim", "macos" to 20, "c1" to 3, "c2" to listOf("x")))
            .answering("fuma" to "nao")
            .copy(blockId = "c", numberDrafts = mapOf("macos" to "20"), filledBy = mapOf("macos" to "paciente"))
        val update = state.act(QuestionnaireRunnerAction.Next(oneByOne = false))
        val finished = assertIs<QuestionnaireRunnerEvent.Finished>(update.event)
        assertEquals(listOf("macos"), finished.cleared)
        assertEquals(
            answersOf("a1" to 1, "a2" to 2, "fuma" to "nao", "c1" to 3, "c2" to listOf("x")),
            finished.answers,
        )
        assertFalse("macos" in update.state.answers)
        assertFalse("macos" in update.state.numberDrafts)
        assertFalse("macos" in update.state.filledBy)
    }

    @Test
    fun `concluir com pendencia num bloco anterior leva ate ela`() {
        // Retomada no último bloco com o primeiro incompleto (a2 em branco).
        val state = start(answersOf("a1" to 1, "fuma" to "nao", "c1" to 2, "c2" to listOf("y"))).copy(blockId = "c")
        val update = state.act(QuestionnaireRunnerAction.Next(oneByOne = false))
        assertNull(update.event)
        assertEquals("a", update.state.blockId)
        assertEquals("a2", update.state.questionId)
        assertEquals(setOf("a2"), update.state.flagged)
        assertEquals("a2", update.state.focusRequest?.questionId)
    }

    @Test
    fun `questionario sem bloco visivel conclui direto`() {
        val update = QuestionnaireRunnerState.start(Questionnaire()).act(QuestionnaireRunnerAction.Next(oneByOne = false))
        assertEquals(QuestionnaireRunnerEvent.Finished(emptyMap(), emptyList()), update.event)
    }

    // ------------------------------------------------------------------------------------------
    // Navegação — pergunta a pergunta
    // ------------------------------------------------------------------------------------------

    @Test
    fun `uma por vez - pergunta atual em branco marca so ela`() {
        val update = start().act(QuestionnaireRunnerAction.Next(oneByOne = true))
        assertEquals(setOf("a1"), update.state.flagged)
        assertEquals("a1", update.state.questionId)
        assertEquals("a1", update.state.focusRequest?.questionId)
    }

    @Test
    fun `uma por vez - avanca pergunta e depois bloco`() {
        var state = start().answering("a1" to 1)
        state = state.act(QuestionnaireRunnerAction.Next(oneByOne = true)).state
        assertEquals("a2", state.questionId)
        state = state.answering("a2" to 0).act(QuestionnaireRunnerAction.Next(oneByOne = true)).state
        assertEquals("a3", state.questionId, "a opcional também é um passo")
        state = state.act(QuestionnaireRunnerAction.Next(oneByOne = true)).state
        assertEquals("b" to "fuma", state.blockId to state.questionId, "a3 em branco é opcional: segue de bloco")
    }

    @Test
    fun `uma por vez - fim do bloco confere o bloco inteiro`() {
        // Chegou na a3 pulando a2 (GoTo): no fim do bloco, volta para a a2.
        val state = start().answering("a1" to 1).act(QuestionnaireRunnerAction.GoTo("a3")).state
        val update = state.act(QuestionnaireRunnerAction.Next(oneByOne = true))
        assertEquals("a2", update.state.questionId)
        assertEquals(setOf("a2"), update.state.flagged)
    }

    @Test
    fun `avanco automatico so da atual - so para frente - nunca na ultima`() {
        val state = start().answering("a1" to 2)
        val advanced = state.act(QuestionnaireRunnerAction.AutoAdvance("a1")).state
        assertEquals("a2", advanced.questionId)
        assertSame(advanced, advanced.act(QuestionnaireRunnerAction.AutoAdvance("a1")).state, "atrasado: a pergunta já não é a atual")
        assertSame(advanced, advanced.act(QuestionnaireRunnerAction.AutoAdvance("a2")).state, "a2 sem resposta")
        val atLast = start().answering("a1" to 1, "a2" to 1, "a3" to 1).copy(blockId = "a", questionId = "a3")
        assertSame(atLast, atLast.act(QuestionnaireRunnerAction.AutoAdvance("a3")).state, "última do bloco: concluir é da pessoa")
    }

    @Test
    fun `condicional aparece logo depois de responder`() {
        val state = start().answering("a1" to 1, "a2" to 1, "a3" to 1).act(QuestionnaireRunnerAction.Next(oneByOne = false)).state
        assertEquals("b" to "fuma", state.blockId to state.questionId)
        val next = state.answering("fuma" to "sim").act(QuestionnaireRunnerAction.AutoAdvance("fuma")).state
        assertEquals("macos", next.questionId, "a pergunta que a resposta fez aparecer é a próxima")
    }

    // ------------------------------------------------------------------------------------------
    // Voltar
    // ------------------------------------------------------------------------------------------

    @Test
    fun `voltar - pergunta anterior e a ultima do bloco anterior`() {
        val inB = start(answersOf("a1" to 1, "a2" to 1, "a3" to 1)).copy(blockId = "b", questionId = "obs")
        assertEquals("fuma", inB.act(QuestionnaireRunnerAction.Back(oneByOne = true)).state.questionId)
        val atFirstOfB = inB.copy(questionId = "fuma")
        val back = atFirstOfB.act(QuestionnaireRunnerAction.Back(oneByOne = true)).state
        assertEquals("a" to "a3", back.blockId to back.questionId, "voltar é a pergunta de antes, não a primeira do bloco")
        val grouped = inB.act(QuestionnaireRunnerAction.Back(oneByOne = false)).state
        assertEquals("a", grouped.blockId)
    }

    @Test
    fun `voltar no primeiro passo sai so com exitEnabled`() {
        val closed = start()
        assertSame(closed, closed.act(QuestionnaireRunnerAction.Back(oneByOne = false)).state)
        assertNull(closed.act(QuestionnaireRunnerAction.Back(oneByOne = false)).event)
        val open = start(exitEnabled = true)
        assertEquals(QuestionnaireRunnerEvent.Exited, open.act(QuestionnaireRunnerAction.Back(oneByOne = true)).event)
        assertEquals(QuestionnaireRunnerEvent.Exited, QuestionnaireRunnerState.start(Questionnaire(), exitEnabled = true).act(QuestionnaireRunnerAction.Back(false)).event)
    }

    // ------------------------------------------------------------------------------------------
    // Ir até, foco, ocupado
    // ------------------------------------------------------------------------------------------

    @Test
    fun `ir ate uma pergunta com foco nela`() {
        val update = start().act(QuestionnaireRunnerAction.GoTo("c1"))
        assertEquals("c" to "c1", update.state.blockId to update.state.questionId)
        assertEquals("c1", update.state.focusRequest?.questionId)
        assertSame(update.state, update.state.act(QuestionnaireRunnerAction.GoTo("macos")).state, "oculta: não vai")
        assertSame(update.state, update.state.act(QuestionnaireRunnerAction.GoTo("nao-existe")).state)
    }

    @Test
    fun `foco atendido sai do estado so com o serial certo`() {
        val requested = start().act(QuestionnaireRunnerAction.GoTo("a2")).state
        val request = assertNotNull(requested.focusRequest)
        assertSame(requested, requested.act(QuestionnaireRunnerAction.FocusHandled(request.serial + 1)).state)
        assertNull(requested.act(QuestionnaireRunnerAction.FocusHandled(request.serial)).state.focusRequest)
    }

    @Test
    fun `ocupado ignora tudo - dois toques nao concluem duas vezes`() {
        val busy = start(answersOf("a1" to 1, "a2" to 1, "fuma" to "nao", "c1" to 1, "c2" to listOf("x"))).copy(blockId = "c", busy = true)
        listOf(
            QuestionnaireRunnerAction.Next(oneByOne = false),
            QuestionnaireRunnerAction.Back(oneByOne = false),
            QuestionnaireRunnerAction.Answer("c1", QuestionnaireValue.of(2)),
            QuestionnaireRunnerAction.EditNumber("macos", "3"),
            QuestionnaireRunnerAction.AutoAdvance("c1"),
            QuestionnaireRunnerAction.GoTo("a1"),
        ).forEach { action ->
            val update = busy.act(action)
            assertSame(busy, update.state, "$action")
            assertNull(update.event, "$action")
        }
        val withFocus = busy.copy(focusRequest = QuestionnaireFocusRequest("c1", 3))
        assertNull(withFocus.act(QuestionnaireRunnerAction.FocusHandled(3)).state.focusRequest, "o foco ainda é atendido")
    }

    @Test
    fun `ritmo pelo tamanho da tela`() {
        assertFalse(QuestionnairePace.Auto.isOneByOne(isCompact = true))
        assertTrue(QuestionnairePace.Auto.isOneByOne(isCompact = false))
        assertTrue(QuestionnairePace.OneByOne.isOneByOne(isCompact = true))
        assertFalse(QuestionnairePace.Grouped.isOneByOne(isCompact = false))
    }
}
