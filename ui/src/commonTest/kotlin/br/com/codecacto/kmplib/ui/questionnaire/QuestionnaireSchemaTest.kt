package br.com.codecacto.kmplib.ui.questionnaire

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O contrato do fio: um documento da weblib roda sem mudança; as extensões são opcionais; versão
 * nova do servidor (tipo/campo/tom desconhecido) não derruba a leitura.
 */
class QuestionnaireSchemaTest {

    /** Exatamente o que a weblib consome: `blocks` + `scale`, perguntas só com id/text/required/hint. */
    private val weblibDocument = """
        {
          "scale": {"min": 1, "max": 5, "optionLabels": ["Nunca","Raramente","Às vezes","Frequentemente","Sempre"],
                    "startAnchor": "Nunca", "endAnchor": "Sempre"},
          "blocks": [
            {"id": "foco", "title": "Foco", "context": "Nas últimas semanas…",
             "questions": [
               {"id": "f1", "text": "Retomo o foco depois de uma interrupção"},
               {"id": "f2", "text": "Percebo sinais de cansaço", "required": false, "hint": "Pense no dia a dia"}
             ]}
          ]
        }
    """.trimIndent()

    @Test
    fun `documento da weblib roda sem mudar uma virgula`() {
        val q = QuestionnaireJson.decode(weblibDocument)
        assertEquals(1, q.blocks.size)
        val block = q.blocks.single()
        assertEquals("Foco", block.title)
        assertEquals("Nas últimas semanas…", block.context)
        val (f1, f2) = block.questions
        assertEquals(QuestionnaireQuestionType.SCALE, f1.type, "sem `type` é a régua da weblib")
        assertTrue(f1.required, "required default true, como na weblib")
        assertFalse(f2.required)
        assertEquals("Pense no dia a dia", f2.hint)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), q.scaleOf(f1)?.points())
        assertFalse(f1.reserved)
        assertNull(f1.visibleIf)
        assertTrue(q.scores.isEmpty())
    }

    @Test
    fun `extensoes opcionais sao lidas`() {
        val q = QuestionnaireJson.decode(
            """
            {"id":"pre","title":"Pré-consulta",
             "blocks":[{"id":"b","title":"B","reserved":true,"visibleIf":{"context":"sexo","equals":"F"},
               "questions":[
                 {"id":"c","text":"C","type":"choice","options":[{"value":"sim","label":"Sim","score":1},{"value":0,"label":"Zero"}]},
                 {"id":"m","text":"M","type":"multi-choice","options":[{"value":"has"},{"value":"dm"}]},
                 {"id":"n","text":"N","type":"number","min":0,"max":7,"decimals":1,"unit":"kg"},
                 {"id":"t","text":"T","type":"text","multiline":true,"maxLength":500,"reserved":true},
                 {"id":"d","text":"D","type":"date","visibleIf":{"question":"c","equals":"sim"}},
                 {"id":"s","text":"S","scale":{"min":0,"max":6}}
               ]}],
             "scores":[{"id":"x","label":"X","questions":["c"],"products":[{"questions":["n","n"],"weight":2}],
                        "bands":[{"label":"Alto","min":3,"tone":"danger","when":{"context":"idade","gte":40}}]}]}
            """.trimIndent(),
        )
        assertEquals("pre", q.id)
        assertEquals("Pré-consulta", q.title)
        val block = q.blocks.single()
        assertTrue(block.reserved)
        assertEquals("sexo", block.visibleIf?.context)
        val byId = block.questions.associateBy { it.id }
        assertEquals(QuestionnaireQuestionType.CHOICE, byId.getValue("c").type)
        assertEquals("0", byId.getValue("c").options[1].value, "número no fio vira texto")
        assertEquals("Zero", byId.getValue("c").options[1].displayLabel)
        assertEquals(1.0, byId.getValue("c").options[0].score)
        assertEquals("dm", byId.getValue("m").options[1].displayLabel, "sem label, aparece o value")
        assertEquals(QuestionnaireQuestionType.MULTI_CHOICE, byId.getValue("m").type)
        with(byId.getValue("n")) {
            assertEquals(QuestionnaireQuestionType.NUMBER, type)
            assertEquals(0.0, min)
            assertEquals(7.0, max)
            assertEquals(1, decimals)
            assertEquals("kg", unit)
        }
        with(byId.getValue("t")) {
            assertTrue(multiline)
            assertEquals(500, maxLength)
            assertTrue(reserved)
        }
        assertEquals("c", byId.getValue("d").visibleIf?.question)
        assertEquals(6.0, byId.getValue("s").scale?.max)
        val score = q.scores.single()
        assertEquals(listOf("c"), score.questions)
        assertEquals(2.0, score.products.single().weight)
        with(score.bands.single()) {
            assertEquals(QuestionnaireTone.DANGER, tone)
            assertEquals(3.0, min)
            assertEquals(40.0, condition?.gte)
        }
    }

    @Test
    fun `servidor uma versao a frente nao derruba a leitura`() {
        val q = QuestionnaireJson.decode(
            """
            {"blocks":[{"id":"b","title":"B","novoCampo":{"x":1},
              "questions":[{"id":"p","text":"P","type":"assinatura","outroCampo":true}]}],
             "scores":[{"id":"s","bands":[{"label":"L","tone":"roxo"}]}]}
            """.trimIndent(),
        )
        val p = q.blocks.single().questions.single()
        assertEquals(QuestionnaireQuestionType.UNSUPPORTED, p.type)
        assertFalse(q.isAnswerable(p), "tipo desconhecido nunca é obrigatório")
        assertEquals(QuestionnaireTone.NEUTRAL, q.scores.single().bands.single().tone)
    }

    @Test
    fun `nomes do fio dos tipos e tons`() {
        assertEquals(QuestionnaireQuestionType.SCALE, QuestionnaireQuestionType.fromWireName(null))
        assertEquals(QuestionnaireQuestionType.SCALE, QuestionnaireQuestionType.fromWireName(""))
        assertEquals(QuestionnaireQuestionType.MULTI_CHOICE, QuestionnaireQuestionType.fromWireName("multi-choice"))
        assertEquals(QuestionnaireQuestionType.UNSUPPORTED, QuestionnaireQuestionType.fromWireName("multiChoice"))
        QuestionnaireQuestionType.entries.forEach { assertEquals(it, QuestionnaireQuestionType.fromWireName(it.wireName)) }
        QuestionnaireTone.entries.forEach { assertEquals(it, QuestionnaireTone.fromWireName(it.wireName)) }
        assertEquals(QuestionnaireTone.NEUTRAL, QuestionnaireTone.fromWireName(null))
    }

    @Test
    fun `ida e volta do documento preserva o conteudo`() {
        val q = QuestionnaireJson.decode(weblibDocument)
        assertEquals(q, QuestionnaireJson.decode(QuestionnaireJson.encode(q)))
        val withTypes = Questionnaire(
            blocks = listOf(block("b", choice("c", option("a", 1.0)), number("n", 0.0, 9.0))),
            scores = listOf(QuestionnaireScore("s", bands = listOf(band("x", tone = QuestionnaireTone.INFO)))),
        )
        val encoded = QuestionnaireJson.encode(withTypes)
        assertTrue("\"type\":\"choice\"" in encoded, encoded)
        assertTrue("\"tone\":\"info\"" in encoded, encoded)
        assertEquals(withTypes, QuestionnaireJson.decode(encoded))
    }

    // ------------------------------------------------------------------------------------------
    // Respostas
    // ------------------------------------------------------------------------------------------

    @Test
    fun `respostas no formato da weblib - inteiro sem casa decimal`() {
        val answers = linkedMapOf<String, QuestionnaireValue>(
            "q1" to QuestionnaireValue.of(2),
            "q2" to QuestionnaireValue.of(72.5),
            "q3" to QuestionnaireValue.of("sim"),
            "q4" to QuestionnaireValue.of(listOf("has", "dm")),
        )
        assertEquals("""{"q1":2,"q2":72.5,"q3":"sim","q4":["has","dm"]}""", QuestionnaireJson.encodeAnswers(answers))
        assertEquals(answers, QuestionnaireJson.decodeAnswers(QuestionnaireJson.encodeAnswers(answers)))
    }

    @Test
    fun `leitura de respostas descarta o que nao e resposta`() {
        val decoded = QuestionnaireJson.decodeAnswers(
            """{"a":3,"b":null,"c":{"x":1},"d":true,"e":[1,"x",null],"f":"texto"}""",
        )
        assertEquals(QuestionnaireValue.Number(3.0), decoded["a"])
        assertFalse("b" in decoded)
        assertFalse("c" in decoded)
        assertEquals(QuestionnaireValue.Text("true"), decoded["d"])
        assertEquals(QuestionnaireValue.Choices(listOf("1", "x")), decoded["e"])
        assertEquals(QuestionnaireValue.Text("texto"), decoded["f"])
        assertTrue(QuestionnaireJson.answersFrom(JsonArray(emptyList())).isEmpty(), "não-objeto = vazio")
    }

    @Test
    fun `valor do fio`() {
        assertEquals(JsonPrimitive(2L), QuestionnaireValue.of(2.0).toJsonElement())
        assertEquals(JsonPrimitive(-3L), QuestionnaireValue.of(-3).toJsonElement())
        assertEquals(JsonPrimitive(0.5), QuestionnaireValue.of(0.5).toJsonElement())
        assertEquals(JsonPrimitive(1e20), QuestionnaireValue.of(1e20).toJsonElement(), "acima de 2^53 fica decimal")
        assertNull(QuestionnaireValue.fromJson(JsonNull))
        assertNull(QuestionnaireValue.fromJson(JsonObject(emptyMap())))
        assertEquals(QuestionnaireValue.Text("false"), QuestionnaireValue.of(false))
    }

    @Test
    fun `item do lote e o AnswerBatchItem da weblib`() {
        val json = Json { encodeDefaults = false }
        val serializer = ListSerializer(QuestionnaireAnswerItem.serializer())
        val batch = listOf(
            QuestionnaireAnswerItem("q1", QuestionnaireValue.of(2)),
            QuestionnaireAnswerItem("q2", QuestionnaireValue.of("sim")),
            QuestionnaireAnswerItem("q3", null),
        )
        val encoded = json.encodeToString(serializer, batch)
        assertEquals("""[{"questionId":"q1","value":2},{"questionId":"q2","value":"sim"},{"questionId":"q3"}]""", encoded)
        assertEquals(batch, json.decodeFromString(serializer, encoded))
        assertEquals(
            QuestionnaireAnswerItem("q3", null),
            json.decodeFromString(QuestionnaireAnswerItem.serializer(), """{"questionId":"q3","value":null}"""),
        )
    }

    @Serializable
    private data class RespostasDto(
        @Serializable(with = QuestionnaireAnswersSerializer::class)
        val respostas: Map<String, QuestionnaireValue>,
    )

    @Test
    fun `serializador tolerante do mapa num DTO do app`() {
        val json = Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString(RespostasDto.serializer(), """{"respostas":{"a":1,"b":null,"c":["x"]}}""")
        assertEquals(mapOf("a" to QuestionnaireValue.Number(1.0), "c" to QuestionnaireValue.Choices(listOf("x"))), dto.respostas)
        val encoded = json.encodeToString(RespostasDto.serializer(), dto)
        assertEquals("""{"respostas":{"a":1,"c":["x"]}}""", encoded)
        assertEquals(setOf("a", "c"), json.parseToJsonElement(encoded).jsonObject.getValue("respostas").jsonObject.keys)
    }

    // ------------------------------------------------------------------------------------------
    // Régua
    // ------------------------------------------------------------------------------------------

    @Test
    fun `pontos da regua - a conta da weblib`() {
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), QuestionnaireScale(0.0, 3.0).points())
        assertEquals(listOf(1.0, 1.5, 2.0, 2.5, 3.0), QuestionnaireScale(1.0, 3.0, step = 0.5).points())
        assertEquals(listOf(0.0, 0.1, 0.2, 0.3), QuestionnaireScale(0.0, 0.3, step = 0.1).points(), "sem erro de soma acumulada")
        assertEquals(listOf(-2.0, -1.0, 0.0, 1.0, 2.0), QuestionnaireScale(-2.0, 2.0).points())
        assertEquals(listOf(0.0, 2.0, 4.0), QuestionnaireScale(0.0, 5.0, step = 2.0).points(), "a ponta que não cai no passo fica de fora")
    }

    @Test
    fun `regua impossivel devolve vazio`() {
        assertTrue(QuestionnaireScale(5.0, 1.0).points().isEmpty(), "invertida")
        assertTrue(QuestionnaireScale(3.0, 3.0).points().isEmpty(), "um ponto só")
        assertTrue(QuestionnaireScale(1.0, 5.0, step = 0.0).points().isEmpty(), "passo zero")
        assertTrue(QuestionnaireScale(1.0, 5.0, step = -1.0).points().isEmpty(), "passo negativo")
        assertTrue(QuestionnaireScale(0.0, 500.0).points().isEmpty(), "acima do teto")
        assertTrue(QuestionnaireScale(Double.NaN, 5.0).points().isEmpty(), "não finito")
        assertEquals(QUESTIONNAIRE_MAX_SCALE_POINTS, QuestionnaireScale(0.0, 100.0).points().size, "0..100 cabe")
    }
}
