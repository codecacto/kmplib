@file:Suppress("DEPRECATION")

package br.com.codecacto.kmplib.ui.questionnaire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pontuação — e a prova de que o modelo genérico cobre as 12 escalas pedidas pelo Vitalis
 * (PHQ-2, GAD-2, Epworth, STOP-Bang, IPAQ curto, AUDIT-C, Bristol, ADAM, AMS, IIEF-5, IPSS, MRS).
 *
 * O conteúdo clínico mora SÓ aqui, como dado de teste: a lib não sabe o que é uma escala, só soma,
 * multiplica e escolhe a faixa. Estes casos são o vetor de conformidade que a weblib e o servidor
 * devem reproduzir — o mesmo documento tem de dar o mesmo escore nas três pontas.
 */
class QuestionnaireScoringTest {

    private fun scoreOf(q: Questionnaire, id: String, answers: Map<String, QuestionnaireValue>, context: Map<String, QuestionnaireValue> = emptyMap()) =
        q.evaluate(answers, context).score(id)!!

    private fun likertItems(prefix: String, count: Int) = (1..count).map { question("$prefix$it") }

    // ------------------------------------------------------------------------------------------
    // Regras gerais
    // ------------------------------------------------------------------------------------------

    @Test
    fun `soma implicita - o instrumento de uma regua so`() {
        val q = Questionnaire(scale = scale(1, 5), blocks = listOf(block("b", *likertItems("q", 3).toTypedArray())), scores = listOf(QuestionnaireScore("t")))
        val result = scoreOf(q, "t", answersOf("q1" to 1, "q2" to 4, "q3" to 5))
        assertEquals(10.0, result.value)
        assertTrue(result.isComplete)
        assertEquals(3, result.total)
    }

    @Test
    fun `incompleto tem valor parcial e nunca faixa`() {
        val q = Questionnaire(
            scale = scale(0, 3),
            blocks = listOf(block("b", *likertItems("q", 2).toTypedArray())),
            scores = listOf(QuestionnaireScore("s", bands = listOf(band("qualquer")))),
        )
        val partial = scoreOf(q, "s", answersOf("q1" to 3))
        assertFalse(partial.isComplete)
        assertEquals(3.0, partial.value)
        assertEquals(1, partial.answered)
        assertEquals(2, partial.total)
        assertNull(partial.band)
    }

    @Test
    fun `pergunta ausente do documento deixa o escore pendente - a reservada que o servidor tirou`() {
        // Documento da recepção: o servidor removeu q2 (reservada). O escore continua pedindo q2.
        val q = Questionnaire(
            scale = scale(0, 3),
            blocks = listOf(block("b", question("q1"))),
            scores = listOf(QuestionnaireScore("s", questions = listOf("q1", "q2"), bands = listOf(band("x")))),
        )
        val result = scoreOf(q, "s", answersOf("q1" to 2))
        assertFalse(result.isComplete)
        assertEquals(2, result.total)
        assertNull(result.band)
    }

    @Test
    fun `oculta por condicao nao se aplica - escore completo sem ela`() {
        val q = Questionnaire(
            blocks = listOf(block("b", yesNo("a"), yesNo("b", visibleIf = cond("""{"question":"a","equals":"sim"}""")))),
            scores = listOf(QuestionnaireScore("s", questions = listOf("a", "b"))),
        )
        val result = scoreOf(q, "s", answersOf("a" to "nao", "b" to "sim"))
        assertTrue(result.isComplete)
        assertEquals(0.0, result.value, "a resposta de 'b' oculto não soma")
    }

    @Test
    fun `texto e data listados nao somam nem travam o escore`() {
        val q = Questionnaire(
            blocks = listOf(block("b", yesNo("a"), question("t", type = QuestionnaireQuestionType.TEXT))),
            scores = listOf(QuestionnaireScore("s", questions = listOf("a", "t"))),
        )
        val result = scoreOf(q, "s", answersOf("a" to "sim"))
        assertTrue(result.isComplete)
        assertEquals(1, result.total)
        assertEquals(1.0, result.value)
    }

    @Test
    fun `numero fora da faixa nao entra na soma`() {
        val q = Questionnaire(blocks = listOf(block("b", number("d", 0.0, 7.0))), scores = listOf(QuestionnaireScore("s", questions = listOf("d"))))
        assertFalse(scoreOf(q, "s", answersOf("d" to 9)).isComplete)
        assertEquals(7.0, scoreOf(q, "s", answersOf("d" to 7)).value)
    }

    @Test
    fun `multipla soma os pontos marcados e opcao sem pontos vale zero`() {
        val q = Questionnaire(
            blocks = listOf(
                block(
                    "b",
                    question("m", type = QuestionnaireQuestionType.MULTI_CHOICE, options = listOf(option("a", 2.0), option("b", 3.0), option("c"))),
                    choice("c", option("x"), option("y", 4.0)),
                ),
            ),
            scores = listOf(QuestionnaireScore("s", questions = listOf("m", "c"))),
        )
        assertEquals(5.0, scoreOf(q, "s", answersOf("m" to listOf("a", "b", "c"), "c" to "x")).value)
        assertEquals(6.0, scoreOf(q, "s", answersOf("m" to listOf("a"), "c" to "y")).value)
    }

    @Test
    fun `faixas - primeira que casa - pontas inclusivas`() {
        val q = Questionnaire(
            scale = scale(0, 10),
            blocks = listOf(block("b", question("q"))),
            scores = listOf(
                QuestionnaireScore(
                    "s",
                    bands = listOf(band("baixo", max = 3.0), band("medio", min = 3.0, max = 6.0), band("alto", min = 7.0)),
                ),
            ),
        )
        assertEquals("baixo", scoreOf(q, "s", answersOf("q" to 3)).band?.label, "3 casa nas duas: vale a primeira")
        assertEquals("medio", scoreOf(q, "s", answersOf("q" to 6)).band?.label)
        assertEquals("alto", scoreOf(q, "s", answersOf("q" to 10)).band?.label)
    }

    // ------------------------------------------------------------------------------------------
    // As 12 escalas
    // ------------------------------------------------------------------------------------------

    private val phqLabels = arrayOf("Nenhuma vez", "Vários dias", "Mais da metade dos dias", "Quase todos os dias")
    private val screeningBands = listOf(
        band("Negativo", max = 2.0, tone = QuestionnaireTone.SUCCESS),
        band("Positivo", min = 3.0, tone = QuestionnaireTone.WARNING),
    )

    @Test
    fun `PHQ-2 e GAD-2`() {
        val q = Questionnaire(
            scale = scale(0, 3, *phqLabels),
            blocks = listOf(
                block("phq", question("phq1"), question("phq2")),
                block("gad", question("gad1"), question("gad2")),
            ),
            scores = listOf(
                QuestionnaireScore("phq2", label = "PHQ-2", questions = listOf("phq1", "phq2"), bands = screeningBands),
                QuestionnaireScore("gad2", label = "GAD-2", questions = listOf("gad1", "gad2"), bands = screeningBands),
            ),
        )
        assertEquals("Negativo", scoreOf(q, "phq2", answersOf("phq1" to 1, "phq2" to 1)).band?.label)
        val positive = scoreOf(q, "phq2", answersOf("phq1" to 2, "phq2" to 1))
        assertEquals(3.0, positive.value)
        assertEquals(QuestionnaireTone.WARNING, positive.band?.tone)
        assertEquals("Positivo", scoreOf(q, "gad2", answersOf("gad1" to 0, "gad2" to 3)).band?.label)
        assertEquals(6.0, scoreOf(q, "gad2", answersOf("gad1" to 3, "gad2" to 3)).value)
    }

    @Test
    fun `Epworth - mais de 10 e sonolencia excessiva`() {
        val q = Questionnaire(
            scale = scale(0, 3, "Nunca cochilaria", "Pequena chance", "Chance moderada", "Grande chance"),
            blocks = listOf(block("sono", *likertItems("ess", 8).toTypedArray())),
            scores = listOf(
                QuestionnaireScore(
                    "ess",
                    bands = listOf(band("Normal", max = 10.0), band("Sonolência excessiva", min = 11.0, tone = QuestionnaireTone.WARNING)),
                ),
            ),
        )
        val ten = answersOf("ess1" to 3, "ess2" to 3, "ess3" to 2, "ess4" to 1, "ess5" to 1, "ess6" to 0, "ess7" to 0, "ess8" to 0)
        assertEquals("Normal", scoreOf(q, "ess", ten).band?.label)
        assertEquals("Sonolência excessiva", scoreOf(q, "ess", ten + answersOf("ess8" to 1)).band?.label)
    }

    @Test
    fun `STOP-Bang - sim vale 1`() {
        val items = listOf("ronco", "cansaco", "apneia", "pressao", "imc", "idade", "pescoco", "sexo")
        val q = Questionnaire(
            blocks = listOf(block("stop", *items.map { yesNo(it) }.toTypedArray())),
            scores = listOf(
                QuestionnaireScore(
                    "stopbang",
                    bands = listOf(band("Baixo", max = 2.0), band("Intermediário", min = 3.0, max = 4.0), band("Alto", min = 5.0, tone = QuestionnaireTone.DANGER)),
                ),
            ),
        )
        fun yes(n: Int) = items.mapIndexed { i, id -> id to if (i < n) "sim" else "nao" }.toTypedArray()
        assertEquals("Baixo", scoreOf(q, "stopbang", answersOf(*yes(2))).band?.label)
        assertEquals("Intermediário", scoreOf(q, "stopbang", answersOf(*yes(3))).band?.label)
        assertEquals("Alto", scoreOf(q, "stopbang", answersOf(*yes(5))).band?.label)
    }

    @Test
    fun `AUDIT-C - faixa por sexo e itens que somem com nunca`() {
        val q = QuestionnaireJson.decode(
            """
            {"blocks":[{"id":"alcool","title":"Álcool","questions":[
              {"id":"a1","text":"Frequência","type":"choice","options":[
                {"value":"nunca","score":0},{"value":"mensal","score":1},{"value":"2-4-mes","score":2},
                {"value":"2-3-semana","score":3},{"value":"4-semana","score":4}]},
              {"id":"a2","text":"Doses num dia típico","type":"choice","visibleIf":{"question":"a1","notEquals":"nunca"},"options":[
                {"value":"1-2","score":0},{"value":"3-4","score":1},{"value":"5-6","score":2},{"value":"7-9","score":3},{"value":"10+","score":4}]},
              {"id":"a3","text":"6 doses ou mais","type":"choice","visibleIf":{"question":"a1","notEquals":"nunca"},"options":[
                {"value":"nunca","score":0},{"value":"menos-mensal","score":1},{"value":"mensal","score":2},{"value":"semanal","score":3},{"value":"diario","score":4}]}
            ]}],
             "scores":[{"id":"auditc","label":"AUDIT-C","questions":["a1","a2","a3"],"bands":[
               {"label":"Uso de risco","min":4,"tone":"warning","when":{"context":"sexo","equals":"M"}},
               {"label":"Uso de risco","min":3,"tone":"warning","when":{"context":"sexo","equals":"F"}},
               {"label":"Sem indicação de risco","tone":"success"}]}]}
            """.trimIndent(),
        )
        val never = scoreOf(q, "auditc", answersOf("a1" to "nunca"), answersOf("sexo" to "F"))
        assertTrue(never.isComplete, "'nunca' esconde os itens 2 e 3 — o escore fecha em 0")
        assertEquals(0.0, never.value)
        assertEquals("Sem indicação de risco", never.band?.label)

        val three = answersOf("a1" to "2-4-mes", "a2" to "3-4", "a3" to "nunca")
        assertEquals(3.0, scoreOf(q, "auditc", three, answersOf("sexo" to "F")).value)
        assertEquals("Uso de risco", scoreOf(q, "auditc", three, answersOf("sexo" to "F")).band?.label)
        assertEquals("Sem indicação de risco", scoreOf(q, "auditc", three, answersOf("sexo" to "M")).band?.label)
        val four = three + answersOf("a3" to "menos-mensal")
        assertEquals("Uso de risco", scoreOf(q, "auditc", four, answersOf("sexo" to "M")).band?.label)
    }

    @Test
    fun `Bristol - escolha de 7 tipos com o tipo como ponto`() {
        val q = QuestionnaireJson.decode(
            """
            {"blocks":[{"id":"intestino","title":"Intestino","questions":[
              {"id":"bristol","text":"Forma das fezes","type":"choice","options":[
                {"value":1,"score":1},{"value":2,"score":2},{"value":3,"score":3},{"value":4,"score":4},
                {"value":5,"score":5},{"value":6,"score":6},{"value":7,"score":7}]}]}],
             "scores":[{"id":"bristol","questions":["bristol"],"bands":[
               {"label":"Tendência à constipação","max":2},{"label":"Normal","min":3,"max":4},{"label":"Tendência à diarreia","min":5}]}]}
            """.trimIndent(),
        )
        assertEquals("Normal", scoreOf(q, "bristol", answersOf("bristol" to "4")).band?.label)
        assertEquals("Tendência à constipação", scoreOf(q, "bristol", answersOf("bristol" to 1)).band?.label, "resposta numérica casa com opção '1'")
        assertEquals("Tendência à diarreia", scoreOf(q, "bristol", answersOf("bristol" to "7")).band?.label)
    }

    @Test
    fun `ADAM - libido ou erecao pesam 3 e as demais 1`() {
        val q = Questionnaire(
            blocks = listOf(block("adam", *(1..10).map { yesNo("adam$it", yesScore = if (it == 1 || it == 7) 3.0 else 1.0) }.toTypedArray())),
            scores = listOf(QuestionnaireScore("adam", bands = listOf(band("Positivo", min = 3.0), band("Negativo")))),
        )
        fun answers(vararg yes: Int) = answersOf(*(1..10).map { "adam$it" to if (it in yes) "sim" else "nao" }.toTypedArray())
        assertEquals("Positivo", scoreOf(q, "adam", answers(1)).band?.label, "só a 1 (libido)")
        assertEquals("Positivo", scoreOf(q, "adam", answers(7)).band?.label, "só a 7 (ereção)")
        assertEquals("Negativo", scoreOf(q, "adam", answers(2, 3)).band?.label)
        assertEquals("Positivo", scoreOf(q, "adam", answers(2, 3, 4)).band?.label, "3 outras")
    }

    @Test
    fun `AMS - total com faixas e subescalas`() {
        val items = likertItems("ams", 17)
        val q = Questionnaire(
            scale = scale(1, 5, "Nenhum", "Leve", "Moderado", "Grave", "Extremamente grave"),
            blocks = listOf(block("ams", *items.toTypedArray(), visibleIf = cond("""{"context":"sexo","equals":"M"}"""))),
            scores = listOf(
                QuestionnaireScore(
                    "ams",
                    bands = listOf(band("Nenhum", max = 26.0), band("Leve", min = 27.0, max = 36.0), band("Moderado", min = 37.0, max = 49.0), band("Grave", min = 50.0)),
                ),
                QuestionnaireScore("ams-psicologica", questions = listOf(6, 7, 8, 11, 13).map { "ams$it" }),
                QuestionnaireScore("ams-somatica", questions = listOf(1, 2, 3, 4, 5, 9, 10).map { "ams$it" }),
                QuestionnaireScore("ams-sexual", questions = listOf(12, 14, 15, 16, 17).map { "ams$it" }),
            ),
        )
        val male = answersOf("sexo" to "M")
        fun all(v: Int) = answersOf(*items.map { it.id to v }.toTypedArray())
        assertEquals("Nenhum", scoreOf(q, "ams", all(1), male).band?.label)
        assertEquals(17.0, scoreOf(q, "ams", all(1), male).value)
        assertEquals("Leve", scoreOf(q, "ams", all(2), male).band?.label)
        assertEquals("Grave", scoreOf(q, "ams", all(3), male).band?.label)
        assertEquals(15.0, scoreOf(q, "ams-sexual", all(3), male).value)
        assertEquals(21.0, scoreOf(q, "ams-somatica", all(3), male).value)
        assertFalse(q.evaluate(all(3), answersOf("sexo" to "F")).visibleBlocks.any { it.id == "ams" }, "bloco só para o contexto masculino")
    }

    @Test
    fun `IIEF-5 - regua propria por pergunta e 21 ou menos sugere disfuncao`() {
        val itemScale = { a: String, e: String -> QuestionnaireScale(min = 1.0, max = 5.0, startAnchor = a, endAnchor = e) }
        val q = Questionnaire(
            blocks = listOf(
                block(
                    "iief",
                    question("iief1", scale = itemScale("Muito baixa", "Muito alta")),
                    question("iief2", scale = itemScale("Quase nunca", "Quase sempre")),
                    question("iief3", scale = itemScale("Quase nunca", "Quase sempre")),
                    question("iief4", scale = itemScale("Extremamente difícil", "Nada difícil")),
                    question("iief5", scale = itemScale("Quase nunca", "Quase sempre")),
                    reserved = true,
                ),
            ),
            scores = listOf(QuestionnaireScore("iief5", bands = listOf(band("Sugere disfunção erétil", max = 21.0), band("Sem disfunção", min = 22.0)))),
        )
        fun answers(vararg v: Int) = answersOf(*v.mapIndexed { i, x -> "iief${i + 1}" to x }.toTypedArray())
        assertEquals("Sem disfunção", scoreOf(q, "iief5", answers(5, 5, 5, 5, 5)).band?.label)
        assertEquals("Sugere disfunção erétil", scoreOf(q, "iief5", answers(5, 5, 5, 5, 1)).band?.label)
        assertTrue(q.evaluate().isReserved("iief3"), "pergunta íntima: bloco reservado")
    }

    @Test
    fun `IPSS - qualidade de vida tem regua 0-6 e fica fora do total`() {
        val q = Questionnaire(
            scale = scale(0, 5),
            blocks = listOf(
                block(
                    "ipss",
                    *likertItems("ipss", 7).toTypedArray(),
                    question("qv", scale = scale(0, 6, "Ótimo", "Muito bom", "Bom", "Mais ou menos", "Ruim", "Muito ruim", "Péssimo")),
                    visibleIf = cond("""{"all":[{"context":"sexo","equals":"M"},{"context":"idade","gte":40}]}"""),
                ),
            ),
            scores = listOf(
                QuestionnaireScore(
                    "ipss",
                    questions = (1..7).map { "ipss$it" },
                    bands = listOf(band("Leve", max = 7.0), band("Moderado", min = 8.0, max = 19.0), band("Grave", min = 20.0)),
                ),
                QuestionnaireScore("qv", questions = listOf("qv")),
            ),
        )
        val ctx = answersOf("sexo" to "M", "idade" to 62)
        val answers = answersOf(*(1..7).map { "ipss$it" to 3 }.toTypedArray()) + answersOf("qv" to 6)
        assertEquals(21.0, scoreOf(q, "ipss", answers, ctx).value)
        assertEquals("Grave", scoreOf(q, "ipss", answers, ctx).band?.label)
        assertEquals(6.0, scoreOf(q, "qv", answers, ctx).value, "a régua 0–6 da QV aceita o 6")
        assertTrue(q.evaluate(answers, answersOf("sexo" to "M", "idade" to 35)).visibleBlocks.isEmpty(), "abaixo de 40, o bloco não aparece")
    }

    @Test
    fun `MRS - total e subescalas`() {
        val items = likertItems("mrs", 11)
        val q = Questionnaire(
            scale = scale(0, 4, "Nenhum", "Leve", "Moderado", "Grave", "Muito grave"),
            blocks = listOf(block("mrs", *items.toTypedArray())),
            scores = listOf(
                QuestionnaireScore(
                    "mrs",
                    bands = listOf(band("Nenhum ou pouco", max = 4.0), band("Leve", min = 5.0, max = 8.0), band("Moderado", min = 9.0, max = 15.0), band("Grave", min = 16.0)),
                ),
                QuestionnaireScore("mrs-somatica", questions = listOf("mrs1", "mrs2", "mrs3", "mrs11")),
                QuestionnaireScore("mrs-psicologica", questions = listOf("mrs4", "mrs5", "mrs6", "mrs7")),
                QuestionnaireScore("mrs-urogenital", questions = listOf("mrs8", "mrs9", "mrs10")),
            ),
        )
        // mrs1..mrs11 = 0,1,2,0,1,2,0,1,2,0,1
        val answers = answersOf(*items.mapIndexed { i, it -> it.id to (i % 3) }.toTypedArray())
        val total = scoreOf(q, "mrs", answers)
        assertEquals(10.0, total.value)
        assertEquals("Moderado", total.band?.label)
        assertEquals(4.0, scoreOf(q, "mrs-somatica", answers).value) // mrs1+mrs2+mrs3+mrs11 = 0+1+2+1
        assertEquals(3.0, scoreOf(q, "mrs-psicologica", answers).value) // mrs4..mrs7 = 0+1+2+0
        assertEquals(3.0, scoreOf(q, "mrs-urogenital", answers).value) // mrs8..mrs10 = 1+2+0
    }

    /**
     * IPAQ curto: MET-min/semana = 8·vigorosa + 4·moderada + 3,3·caminhada (minutos × dias), e a
     * categoria depende de dias E de MET — produtos + escore de dias + faixas com `when`.
     */
    private val ipaq = QuestionnaireJson.decode(
        """
        {"blocks":[{"id":"ipaq","title":"Atividade física","questions":[
          {"id":"vd","text":"Dias de atividade vigorosa","type":"number","min":0,"max":7},
          {"id":"vm","text":"Minutos por dia (vigorosa)","type":"number","min":0,"max":1440,"visibleIf":{"question":"vd","gt":0}},
          {"id":"md","text":"Dias de atividade moderada","type":"number","min":0,"max":7},
          {"id":"mm","text":"Minutos por dia (moderada)","type":"number","min":0,"max":1440,"visibleIf":{"question":"md","gt":0}},
          {"id":"wd","text":"Dias de caminhada","type":"number","min":0,"max":7},
          {"id":"wm","text":"Minutos por dia (caminhada)","type":"number","min":0,"max":1440,"visibleIf":{"question":"wd","gt":0}},
          {"id":"sit","text":"Minutos sentado num dia de semana","type":"number","min":0,"max":1440}]}],
         "scores":[
           {"id":"dias","questions":["vd","md","wd"]},
           {"id":"met","label":"MET-min/semana","products":[
              {"questions":["vd","vm"],"weight":8},{"questions":["md","mm"],"weight":4},{"questions":["wd","wm"],"weight":3.3}],
            "bands":[
              {"label":"Alto","tone":"success","when":{"any":[
                {"all":[{"question":"vd","gte":3},{"score":"met","gte":1500}]},
                {"all":[{"score":"dias","gte":7},{"score":"met","gte":3000}]}]}},
              {"label":"Moderado","tone":"info","when":{"any":[
                {"all":[{"question":"vd","gte":3},{"question":"vm","gte":20}]},
                {"all":[{"question":"md","gte":5},{"question":"mm","gte":30}]},
                {"all":[{"question":"wd","gte":5},{"question":"wm","gte":30}]},
                {"all":[{"score":"dias","gte":5},{"score":"met","gte":600}]}]}},
              {"label":"Baixo","tone":"warning"}]}]}
        """.trimIndent(),
    )

    @Test
    fun `IPAQ curto - alto por vigorosa`() {
        val result = scoreOf(ipaq, "met", answersOf("vd" to 3, "vm" to 60, "md" to 0, "wd" to 2, "wm" to 30, "sit" to 300))
        assertTrue(result.isComplete, "md = 0 esconde mm: o termo não se aplica")
        assertEquals(1638.0, result.value) // 8·60·3 + 3,3·30·2
        assertEquals("Alto", result.band?.label)
    }

    @Test
    fun `IPAQ curto - moderado pela caminhada e baixo sem atividade`() {
        val walk = scoreOf(ipaq, "met", answersOf("vd" to 0, "md" to 0, "wd" to 5, "wm" to 30, "sit" to 300))
        assertEquals(495.0, walk.value) // 3,3·30·5, sem resíduo de ponto flutuante
        assertEquals("Moderado", walk.band?.label)
        val none = scoreOf(ipaq, "met", answersOf("vd" to 0, "md" to 0, "wd" to 0, "sit" to 600))
        assertTrue(none.isComplete)
        assertEquals(0.0, none.value)
        assertEquals("Baixo", none.band?.label)
    }

    @Test
    fun `IPAQ curto - minuto faltando deixa a categoria pendente`() {
        val result = scoreOf(ipaq, "met", answersOf("vd" to 2, "md" to 0, "wd" to 0, "sit" to 300))
        assertFalse(result.isComplete)
        assertNull(result.band)
    }
}
