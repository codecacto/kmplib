@file:Suppress("DEPRECATION")

package br.com.codecacto.kmplib.ui.questionnaire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A linguagem de condição — a mesma do `visibleIf` e do `when` das faixas. */
class QuestionnaireConditionTest {

    private val form = Questionnaire(
        scale = scale(0, 3),
        blocks = listOf(
            block(
                "b",
                choice("fuma", option("sim"), option("nao"), option("ex")),
                number("macos", min = 0.0, max = 10.0),
                question("likert"),
                question("comorb", type = QuestionnaireQuestionType.MULTI_CHOICE, options = listOf(option("has"), option("dm"), option("asma"))),
                question("obs", type = QuestionnaireQuestionType.TEXT, required = false),
            ),
        ),
        scores = listOf(QuestionnaireScore("s", questions = listOf("likert"))),
    )

    private fun holds(json: String, answers: Map<String, QuestionnaireValue> = emptyMap(), context: Map<String, QuestionnaireValue> = emptyMap()) =
        form.evaluate(answers, context).holds(cond(json))

    @Test
    fun `equals e notEquals`() {
        val a = answersOf("fuma" to "sim")
        assertTrue(holds("""{"question":"fuma","equals":"sim"}""", a))
        assertFalse(holds("""{"question":"fuma","equals":"nao"}""", a))
        assertTrue(holds("""{"question":"fuma","notEquals":"nao"}""", a))
        assertFalse(holds("""{"question":"fuma","notEquals":"sim"}""", a))
    }

    @Test
    fun `sem resposta toda comparacao e falsa - inclusive notEquals`() {
        assertFalse(holds("""{"question":"fuma","equals":"sim"}"""))
        assertFalse(holds("""{"question":"fuma","notEquals":"sim"}"""), "notEquals é 'respondeu diferente'")
        assertFalse(holds("""{"question":"macos","gte":0}"""))
        assertFalse(holds("""{"question":"comorb","contains":"has"}"""))
        assertFalse(holds("""{"question":"fuma","in":["sim","ex"]}"""))
        assertTrue(holds("""{"not":{"question":"fuma","equals":"sim"}}"""), "o `not` de uma falsa é verdadeiro")
    }

    @Test
    fun `answered e sujeito sem operador`() {
        val a = answersOf("fuma" to "ex")
        assertTrue(holds("""{"question":"fuma"}""", a), "sujeito sem operador = respondida")
        assertFalse(holds("""{"question":"fuma"}"""))
        assertTrue(holds("""{"question":"fuma","answered":true}""", a))
        assertTrue(holds("""{"question":"fuma","answered":false}"""))
        assertFalse(holds("""{"question":"fuma","answered":false}""", a))
        assertFalse(holds("""{"question":"fuma","answered":false,"equals":"ex"}"""), "answered:false com comparação, sem resposta = falso")
    }

    @Test
    fun `texto em branco nao e resposta`() {
        assertFalse(holds("""{"question":"obs"}""", answersOf("obs" to "   ")))
        assertTrue(holds("""{"question":"obs"}""", answersOf("obs" to " a ")))
    }

    @Test
    fun `comparacoes numericas`() {
        val a = answersOf("macos" to 5)
        assertTrue(holds("""{"question":"macos","gt":4}""", a))
        assertFalse(holds("""{"question":"macos","gt":5}""", a))
        assertTrue(holds("""{"question":"macos","gte":5}""", a))
        assertTrue(holds("""{"question":"macos","lt":6}""", a))
        assertFalse(holds("""{"question":"macos","lt":5}""", a))
        assertTrue(holds("""{"question":"macos","lte":5}""", a))
        assertTrue(holds("""{"question":"macos","gte":1,"lte":5}""", a), "operadores juntos = E (faixa)")
        assertFalse(holds("""{"question":"macos","gte":6,"lte":9}""", a))
        assertTrue(holds("""{"question":"macos","equals":5}""", a))
        assertTrue(holds("""{"question":"macos","equals":"5"}""", a), "texto numérico casa com número")
        assertTrue(holds("""{"question":"macos","equals":5.0}""", a))
        assertFalse(holds("""{"question":"fuma","gt":0}""", answersOf("fuma" to "sim")), "texto não numérico nunca é maior")
    }

    @Test
    fun `numero fora da faixa ainda vale para condicao`() {
        assertTrue(holds("""{"question":"macos","gt":10}""", answersOf("macos" to 40)))
    }

    @Test
    fun `in e contains em multipla`() {
        val a = answersOf("comorb" to listOf("dm", "asma"))
        assertTrue(holds("""{"question":"comorb","contains":"dm"}""", a))
        assertFalse(holds("""{"question":"comorb","contains":"has"}""", a))
        assertTrue(holds("""{"question":"comorb","in":["has","asma"]}""", a), "alguma marcada está na lista")
        assertFalse(holds("""{"question":"comorb","in":["has"]}""", a))
        assertFalse(holds("""{"question":"comorb","equals":"dm"}""", a), "equals em múltipla = exatamente uma")
        assertTrue(holds("""{"question":"comorb","equals":"dm"}""", answersOf("comorb" to listOf("dm"))))
        assertTrue(holds("""{"question":"fuma","in":["sim","ex"]}""", answersOf("fuma" to "ex")))
        assertTrue(holds("""{"question":"fuma","contains":"ex"}""", answersOf("fuma" to "ex")), "contains em escalar = equals")
    }

    @Test
    fun `opcao inexistente nao e resposta`() {
        assertFalse(holds("""{"question":"fuma"}""", answersOf("fuma" to "talvez")))
        assertFalse(holds("""{"question":"comorb"}""", answersOf("comorb" to listOf("cancer"))))
        assertTrue(
            holds("""{"question":"comorb","equals":"dm"}""", answersOf("comorb" to listOf("dm", "cancer"))),
            "a opção que não existe some, a que existe fica",
        )
    }

    @Test
    fun `contexto informado pelo app`() {
        val ctx = answersOf("sexo" to "F", "idade" to 52, "gestante" to false)
        assertTrue(holds("""{"context":"sexo","equals":"F"}""", context = ctx))
        assertTrue(holds("""{"context":"idade","gte":50}""", context = ctx))
        assertTrue(holds("""{"context":"gestante","equals":false}""", context = ctx), "booleano do contexto")
        assertFalse(holds("""{"context":"peso","gt":0}""", context = ctx), "chave ausente = sem resposta")
    }

    @Test
    fun `all any not combinam`() {
        val ctx = answersOf("sexo" to "M", "idade" to 45)
        assertTrue(holds("""{"all":[{"context":"sexo","equals":"M"},{"context":"idade","gte":40}]}""", context = ctx))
        assertFalse(holds("""{"all":[{"context":"sexo","equals":"M"},{"context":"idade","gte":50}]}""", context = ctx))
        assertTrue(holds("""{"any":[{"context":"sexo","equals":"F"},{"context":"idade","gte":40}]}""", context = ctx))
        assertFalse(holds("""{"any":[]}""", context = ctx), "any de nada é falso")
        assertTrue(holds("""{"all":[]}""", context = ctx), "all de nada é verdadeiro")
        assertTrue(holds("""{"not":{"context":"sexo","equals":"F"}}""", context = ctx))
        assertTrue(holds("{}"), "condição vazia vale sempre")
        assertFalse(holds("""{"context":"sexo","equals":"M","not":{"context":"idade","gte":40}}""", context = ctx), "sujeito E combinação")
    }

    @Test
    fun `escore como sujeito so quando completo`() {
        assertFalse(holds("""{"score":"s","gte":0}"""), "incompleto não tem valor")
        assertTrue(holds("""{"score":"s","gte":2}""", answersOf("likert" to 2)))
        assertFalse(holds("""{"score":"s","gte":3}""", answersOf("likert" to 2)))
        assertFalse(holds("""{"score":"nao-existe"}"""), "escore inexistente = sem valor")
    }

    @Test
    fun `resposta de pergunta oculta nao existe - cascata`() {
        val cascade = Questionnaire(
            blocks = listOf(
                block(
                    "b",
                    yesNo("fuma"),
                    number("macos", visibleIf = cond("""{"question":"fuma","equals":"sim"}""")),
                    yesNo("tentou-parar", visibleIf = cond("""{"question":"macos","gte":1}""")),
                ),
            ),
        )
        val answers = answersOf("fuma" to "sim", "macos" to 10, "tentou-parar" to "sim")
        val visible = cascade.evaluate(answers)
        assertTrue(visible.isVisible("macos"))
        assertTrue(visible.isVisible("tentou-parar"))

        val hidden = cascade.evaluate(answers + answersOf("fuma" to "nao"))
        assertFalse(hidden.isVisible("macos"))
        assertFalse(hidden.isVisible("tentou-parar"), "a resposta 10 de 'macos' oculto não sustenta a 3ª pergunta")
        assertEquals(listOf("macos", "tentou-parar"), hidden.hiddenAnsweredIds)
        assertEquals(mapOf("fuma" to QuestionnaireValue.Text("nao")), hidden.effectiveAnswers)
    }

    @Test
    fun `ciclo nao derruba - a referencia circular e sem resposta`() {
        val cyclic = Questionnaire(
            blocks = listOf(
                block(
                    "b",
                    yesNo("a", visibleIf = cond("""{"question":"b","equals":"sim"}""")),
                    yesNo("b", visibleIf = cond("""{"question":"a","equals":"sim"}""")),
                    yesNo("c", visibleIf = cond("""{"question":"c"}""")),
                    yesNo("d"),
                ),
            ),
        )
        val evaluation = cyclic.evaluate(answersOf("a" to "sim", "b" to "sim", "c" to "sim"))
        assertFalse(evaluation.isVisible("c"), "depende de si mesma: oculta")
        assertTrue(evaluation.isVisible("d"))
        // a ↔ b: cada uma vê a outra "sem resposta" no ciclo — as duas ficam ocultas, sem laço.
        assertEquals(listOf("d"), evaluation.navigableQuestions(cyclic.blocks.single()).map { it.id })
    }

    @Test
    fun `escore que esconde a propria pergunta nao entra em laco`() {
        val selfScore = Questionnaire(
            scale = scale(0, 3),
            blocks = listOf(block("b", question("x", visibleIf = cond("""{"score":"s","lt":2}""")), question("y"))),
            scores = listOf(QuestionnaireScore("s", questions = listOf("x", "y"))),
        )
        val evaluation = selfScore.evaluate(answersOf("x" to 1, "y" to 1))
        // O resultado exato depende da ordem de avaliação; o que importa é terminar e ser estável.
        assertEquals(evaluation.isVisible("x"), selfScore.evaluate(answersOf("x" to 1, "y" to 1)).isVisible("x"))
    }
}
