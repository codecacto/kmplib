package br.com.codecacto.kmplib.ui.questionnaire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** O que o runner registra no log quando o modelo vem quebrado. */
class QuestionnaireValidationTest {

    private fun issues(q: Questionnaire) = q.validate().map { it.path to it.message }

    @Test
    fun `documento bom nao tem defeito`() {
        val q = Questionnaire(
            scale = scale(0, 3),
            blocks = listOf(block("a", question("q1"), yesNo("q2", visibleIf = cond("""{"question":"q1","gte":1}""")))),
            scores = listOf(QuestionnaireScore("s", questions = listOf("q1", "q2"), bands = listOf(band("x", min = 0.0, max = 3.0)))),
        )
        assertTrue(q.validate().isEmpty(), q.validate().toString())
    }

    @Test
    fun `ids repetidos`() {
        val q = Questionnaire(scale = scale(0, 3), blocks = listOf(block("a", question("q")), block("a", question("q"))))
        val found = issues(q)
        assertTrue(found.any { it.first == "blocks[1]" && "bloco 'a' repetido" in it.second }, found.toString())
        assertTrue(found.any { it.first == "blocks[1].questions[0]" && "pergunta 'q' repetida" in it.second }, found.toString())
    }

    @Test
    fun `pergunta que nao se desenha`() {
        val q = Questionnaire(
            blocks = listOf(
                block(
                    "a",
                    question("semRegua"),
                    question("vazia", type = QuestionnaireQuestionType.CHOICE),
                    question("dup", type = QuestionnaireQuestionType.MULTI_CHOICE, options = listOf(option("x"), option("x"))),
                    question("nova", type = QuestionnaireQuestionType.UNSUPPORTED),
                    number("n", min = 10.0, max = 1.0),
                ),
            ),
        )
        val paths = issues(q).map { it.first }
        assertEquals(
            listOf("blocks[0].questions[0]", "blocks[0].questions[1]", "blocks[0].questions[2]", "blocks[0].questions[3]", "blocks[0].questions[4]"),
            paths,
        )
    }

    @Test
    fun `condicoes e escores apontando para o que nao existe`() {
        val q = Questionnaire(
            scale = scale(0, 3),
            blocks = listOf(
                block(
                    "a",
                    question("q1", visibleIf = cond("""{"question":"fantasma","context":"sexo","all":[{"score":"nada"}],"any":[{"question":"q1"}],"not":{"question":"x"}}""")),
                    visibleIf = cond("""{"score":"s"}"""),
                ),
            ),
            scores = listOf(
                QuestionnaireScore(
                    "s",
                    questions = listOf("q1", "sumiu"),
                    products = listOf(QuestionnaireScoreProduct(emptyList()), QuestionnaireScoreProduct(listOf("ausente"))),
                    bands = listOf(band("invertida", min = 5.0, max = 1.0, condition = cond("""{"question":"nada"}"""))),
                ),
                QuestionnaireScore("s"),
            ),
        )
        val found = issues(q)
        fun has(path: String, text: String) = assertTrue(found.any { it.first == path && text in it.second }, "$path / $text em $found")
        has("blocks[0].questions[0].visibleIf", "mais de um sujeito")
        has("blocks[0].questions[0].visibleIf", "'fantasma', que não existe")
        has("blocks[0].questions[0].visibleIf.all[0]", "escore 'nada'")
        has("blocks[0].questions[0].visibleIf.any[0]", "depende de si mesma")
        has("blocks[0].questions[0].visibleIf.not", "'x', que não existe")
        has("scores[0]", "'sumiu'")
        has("scores[0].products[0]", "produto sem perguntas")
        has("scores[0].products[1]", "'ausente'")
        has("scores[0].bands[0]", "min maior que max")
        has("scores[0].bands[0].when", "'nada'")
        has("scores[1]", "escore 's' repetido")
    }
}
