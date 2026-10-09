package br.com.codecacto.kmplib.ui.questionnaire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O que aparece, o que falta, o que já foi respondido — a regra que a tela e o ViewModel leem. */
class QuestionnaireEvaluationTest {

    private val form = Questionnaire(
        scale = scale(1, 5),
        blocks = listOf(
            block("perfil", choice("sexo", option("F"), option("M")), number("idade", min = 0.0, max = 120.0)),
            block(
                "gineco",
                QuestionnaireQuestion(id = "dum", text = "DUM", type = QuestionnaireQuestionType.DATE),
                yesNo("gestante", visibleIf = cond("""{"question":"dum"}""")),
                visibleIf = cond("""{"question":"sexo","equals":"F"}"""),
            ),
            block(
                "humor",
                question("h1"),
                question("h2", reserved = true),
                question("h3", required = false),
            ),
            block("intimo", question("i1"), question("i2"), reserved = true),
            block("vazio", QuestionnaireQuestion(id = "x", text = "X", type = QuestionnaireQuestionType.UNSUPPORTED)),
        ),
        scores = listOf(
            QuestionnaireScore("humor", questions = listOf("h1", "h2")),
            QuestionnaireScore("todos"),
        ),
    )

    @Test
    fun `blocos visiveis - condicao e bloco sem pergunta respondivel`() {
        val masc = form.evaluate(answersOf("sexo" to "M"))
        assertEquals(listOf("perfil", "humor", "intimo"), masc.visibleBlocks.map { it.id }, "gineco oculto; 'vazio' só tem tipo desconhecido")
        val fem = form.evaluate(answersOf("sexo" to "F"))
        assertEquals(listOf("perfil", "gineco", "humor", "intimo"), fem.visibleBlocks.map { it.id })
        val gineco = form.blocks[1]
        assertEquals(listOf("dum"), fem.navigableQuestions(gineco).map { it.id }, "gestante só depois da DUM")
        val vazio = form.blocks.last()
        assertEquals(listOf("x"), fem.visibleQuestions(vazio).map { it.id }, "visível, mas não respondível")
        assertTrue(fem.navigableQuestions(vazio).isEmpty())
    }

    @Test
    fun `progresso conta visiveis respondiveis - obrigatorias e opcionais`() {
        val evaluation = form.evaluate(answersOf("sexo" to "M", "idade" to 40, "h1" to 3))
        // perfil (2) + humor (3) + intimo (2) = 7
        with(evaluation.progress) {
            assertEquals(7, total)
            assertEquals(3, answered)
            assertEquals(3, pending, "h2, i1, i2 obrigatórias em branco; h3 é opcional")
            assertEquals(3, reservedPending, "h2 e o bloco íntimo inteiro")
            assertFalse(isComplete)
            assertEquals(3f / 7f, fraction)
        }
        assertEquals(0f, Questionnaire().evaluate().progress.fraction, "sem pergunta, 0 — nunca NaN")
        assertTrue(Questionnaire().evaluate().progress.isComplete)
    }

    @Test
    fun `erros - obrigatoria - faixa numerica - opcao que nao existe mais`() {
        val evaluation = form.evaluate(answersOf("sexo" to "talvez", "idade" to 130, "h3" to 9))
        assertEquals(QuestionnaireFieldError.Required, evaluation.errorOf("sexo"), "opção que sumiu = sem resposta")
        assertEquals(QuestionnaireFieldError.OutOfRange(0.0, 120.0), evaluation.errorOf("idade"))
        assertNull(evaluation.errorOf("h3"), "opcional com ponto fora da régua: sem resposta, sem erro")
        assertNull(evaluation.answerOf("h3"))
        assertFalse(evaluation.isAnswered("idade"), "número fora da faixa não está respondido")
        assertEquals(QuestionnaireValue.Number(130.0), evaluation.answerOf("idade"), "mas o valor continua lá")
        assertEquals(listOf("sexo", "idade"), evaluation.pendingIn(form.blocks.first()).map { it.id })
    }

    @Test
    fun `data valida e data ilegivel`() {
        val ok = form.evaluate(answersOf("sexo" to "F", "dum" to "2026-09-30"))
        assertEquals(QuestionnaireValue.Text("2026-09-30"), ok.answerOf("dum"))
        assertTrue(ok.isVisible("gestante"))
        val ruim = form.evaluate(answersOf("sexo" to "F", "dum" to "30/09/2026"))
        assertNull(ruim.answerOf("dum"))
        assertEquals(QuestionnaireFieldError.Required, ruim.errorOf("dum"))
    }

    @Test
    fun `reservadas - pela pergunta e pelo bloco`() {
        val evaluation = form.evaluate(answersOf("sexo" to "M", "i1" to 2))
        assertTrue(evaluation.isReserved("h2"))
        assertTrue(evaluation.isReserved("i1"), "bloco reservado reserva as perguntas")
        assertFalse(evaluation.isReserved("h1"))
        assertFalse(evaluation.isReserved("nao-existe"))
        assertEquals(listOf("h2", "i2"), evaluation.reservedPending.map { it.id }, "i1 já respondida")
    }

    @Test
    fun `retomada abre na primeira sem resposta`() {
        assertEquals(QuestionnaireStep("perfil", "sexo"), form.evaluate().firstUnansweredStep())
        assertEquals(
            QuestionnaireStep("humor", "h3"),
            form.evaluate(answersOf("sexo" to "M", "idade" to 30, "h1" to 1, "h2" to 2)).firstUnansweredStep(),
            "opcional em branco também é onde a pessoa parou (como na weblib)",
        )
        val tudo = answersOf("sexo" to "M", "idade" to 30, "h1" to 1, "h2" to 2, "h3" to 3, "i1" to 1, "i2" to 1)
        assertEquals(QuestionnaireStep("intimo", "i1"), form.evaluate(tudo).firstUnansweredStep(), "tudo respondido: último bloco")
        assertNull(Questionnaire().evaluate().firstUnansweredStep())
    }

    @Test
    fun `escore aparece no bloco da sua ultima pergunta`() {
        val evaluation = form.evaluate(answersOf("sexo" to "M"))
        val humor = form.blocks[2]
        val intimo = form.blocks[3]
        assertEquals(listOf("humor"), evaluation.scoresOwnedBy(humor).map { it.score.id })
        assertEquals(listOf("todos"), evaluation.scoresOwnedBy(intimo).map { it.score.id }, "o de todas as réguas fecha no último bloco")
        assertTrue(evaluation.scoresOwnedBy(form.blocks.first()).isEmpty())
        assertEquals("humor", evaluation.score("humor")?.score?.id)
        assertNull(evaluation.score("nao-existe"))
    }

    @Test
    fun `consulta por id`() {
        val evaluation = form.evaluate()
        assertEquals("h1", evaluation.question("h1")?.id)
        assertEquals("humor", evaluation.blockOf("h1")?.id)
        assertNull(evaluation.question("nao-existe"))
        assertFalse(evaluation.isVisible("nao-existe"))
        assertFalse(evaluation.isVisible("dum"), "bloco gineco oculto sem sexo")
    }

    @Test
    fun `id repetido - vale a primeira`() {
        val dup = Questionnaire(
            scale = scale(1, 3),
            blocks = listOf(block("a", question("q")), block("b", question("q"), question("r"))),
        )
        val evaluation = dup.evaluate()
        assertEquals("a", evaluation.blockOf("q")?.id)
        assertEquals(listOf("r"), evaluation.navigableQuestions(dup.blocks[1]).map { it.id })
    }

    @Test
    fun `respondivel depende do tipo e da configuracao`() {
        val q = Questionnaire(scale = null)
        assertFalse(q.isAnswerable(question("s")), "régua sem scale em lugar nenhum")
        assertTrue(q.isAnswerable(question("s", scale = scale(0, 4))))
        assertFalse(q.isAnswerable(question("c", type = QuestionnaireQuestionType.CHOICE)), "escolha sem opções")
        assertTrue(q.isAnswerable(number("n")))
        assertTrue(q.isAnswerable(question("t", type = QuestionnaireQuestionType.TEXT)))
        assertFalse(q.isAnswerable(question("u", type = QuestionnaireQuestionType.UNSUPPORTED)))
    }
}
