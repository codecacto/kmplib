package br.com.codecacto.kmplib.ui.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** A leitura TOLERANTE do schema (o app instalado atrás do servidor) e o fio das respostas. */
class FormSchemaJsonTest {

    private val minimal = """
        {"schemaVersion":1,"id":"f","title":"F","futuro":{"x":1},"sections":[
          {"id":"s","title":"S","kind":"fields","nova":true,"questions":[
            {"id":"a","type":"assinatura","text":"Assine"},
            {"id":"b","type":"singleChoice","text":"B","display":"carrossel","options":[{"value":"x","label":"X"}]},
            {"id":"c","type":"file","text":"C","file":{"accept":["image","video","pdf"],"maxFiles":2,"maxSizeMb":10,"purpose":"LAB_REPORT"}},
            {"id":"d","type":"number","text":"D","number":{"min":20,"max":300.5,"decimals":1,"unit":"kg"}}
          ]},
          {"id":"t","title":"T","kind":"radar","questions":[{"id":"e","type":"info","text":"E"}]}
        ]}
    """.trimIndent()

    @Test
    fun `campo - tipo - display e anexo desconhecidos nao derrubam a leitura`() {
        val schema = FormJson.decodeSchema(minimal)
        val questions = schema.sections.first().questions
        assertEquals(FormQuestionType.UNSUPPORTED, questions[0].type)
        assertEquals(FormChoiceDisplay.BUTTONS, questions[1].display)
        assertEquals(listOf(FormFileKind.IMAGE, FormFileKind.PDF), questions[2].file!!.accept)
        assertEquals("300.5", questions[3].number!!.max!!.toPlainString())
        assertEquals(FormSectionKind.FIELDS, schema.sections[1].kind, "kind desconhecido desenha campo a campo")
        assertTrue(questions[3].required, "required ausente = obrigatória")
    }

    @Test
    fun `tipo desconhecido vale para as condicoes mas o cliente nao o exige nem valida`() {
        val schema = FormJson.decodeSchema(
            """{"id":"f","title":"F","sections":[{"id":"s","title":"S","questions":[
                {"id":"a","type":"assinatura","text":"A"},
                {"id":"b","type":"text","text":"B","visibleIf":{"question":"a","op":"answered"}}]}]}""",
        )
        val answers = mapOf("a" to FormAnswerValue.text("qualquer"))
        assertEquals(listOf("a", "b"), schema.evaluateVisibility(answers).visibleQuestionIds)
        val submission = schema.validateSubmission(answers)
        assertEquals(listOf("b"), submission.questionIds, "a pendência é só a que o app desenha")
        assertTrue(schema.validateValues(answers).isValid, "valor de tipo desconhecido: o servidor confere")
    }

    @Test
    fun `numero de configuracao e exato e volta igual`() {
        val schema = FormJson.decodeSchema(minimal)
        val encoded = FormJson.encodeSchema(schema)
        assertTrue("\"max\":300.5" in encoded, encoded)
        assertTrue("\"maxSizeMb\":10" in encoded, encoded)
        assertEquals(schema, FormJson.decodeSchema(encoded))
    }

    @Test
    fun `condicao malformada e sempre falsa e o JSON e preservado`() {
        val format = FormJson.format
        fun parse(json: String) = format.decodeFromString(FormCondition.serializer(), json)
        assertIs<FormCondition.Invalid>(parse("""{"question":"a","op":"talvez"}"""))
        assertIs<FormCondition.Invalid>(parse("""{"context":"cidade","op":"eq","value":"x"}"""))
        assertIs<FormCondition.Invalid>(parse("""{"question":"a","context":"ageYears","op":"answered"}"""))
        assertIs<FormCondition.Invalid>(parse("""{"all":[],"any":[]}"""))
        assertIs<FormCondition.Invalid>(parse("""{"not":5}"""))
        assertFalse(parse("""{"question":"a","op":"talvez"}""").evaluate(null) { FormAnswerValue.text("x") })
        // `not` de malformada é verdadeira — a mesma leitura da weblib.
        assertTrue(parse("""{"not":{"foo":1}}""").evaluate(null) { null })
        val raw = """{"question":"a","op":"talvez","value":1}"""
        assertEquals(format.parseToJsonElement(raw), format.parseToJsonElement(format.encodeToString(FormCondition.serializer(), parse(raw))))
    }

    @Test
    fun `aninhamento alem do teto vira invalida em vez de estourar a pilha`() {
        var json = """{"question":"a","op":"answered"}"""
        repeat(40) { json = """{"not":$json}""" }
        val condition = FormJson.format.decodeFromString(FormCondition.serializer(), json)
        var node: FormCondition = condition
        var depth = 0
        while (node is FormCondition.Not) {
            node = node.condition
            depth++
        }
        assertIs<FormCondition.Invalid>(node)
        assertTrue(depth < FormConditionJson.MAX_DEPTH + 1)
    }

    @Test
    fun `resposta guarda o JSON e le pelo tipo`() {
        val number = FormAnswerValue.fromJson(Json.parseToJsonElement("72.55"))
        assertEquals("72.55", number.numberOrNull()!!.toPlainString())
        assertNull(number.textOrNull(), "número não vira texto")
        assertNull(FormAnswerValue.text("7").numberOrNull(), "texto não vira número")
        assertEquals(false, FormAnswerValue.bool(false).booleanOrNull())
        assertTrue(FormAnswerValue.bool(false).isAnswered, "false é resposta")
        assertTrue(FormAnswerValue.number(0).isAnswered, "0 é resposta")
        assertFalse(FormAnswerValue.text("  ﻿ ").isAnswered, "em branco pela régua do trim() do JS")
        assertTrue(FormAnswerValue.text("\u001C").isAnswered, "FS não é espaço para o JS (o isBlank do Kotlin acharia que é)")
        assertFalse(FormAnswerValue.choices(emptyList()).isAnswered)
        assertEquals(listOf(FormFileRef("3f1c2a4e-0b7d-4c1a-9e2f-1a2b3c4d5e6f")), FormAnswerValue.files(listOf(FormFileRef("3f1c2a4e-0b7d-4c1a-9e2f-1a2b3c4d5e6f"))).fileRefsOrNull())
        assertNull(FormAnswerValue.fromJson(Json.parseToJsonElement("""[{"fileId":"x","nome":"y"}]""")).fileRefsOrNull())
        assertEquals("FormAnswerValue(<redigido>)", FormAnswerValue.text("dado de saúde").toString())
        assertEquals("FormContext(<redigido>)", FormContext(sexAtBirth = "F").toString())
    }

    @Test
    fun `numero criado no app sai sem casa decimal sobrando`() {
        assertEquals("72.5", FormAnswerValue.number(FormDecimal.parse("72.50")!!).json.toString())
        assertEquals("3", FormAnswerValue.number(3).json.toString())
        assertEquals("3", formListNumber(FormDecimal.parse("3.0")!!).toString())
    }

    @Serializable
    private data class Corpo(
        @Serializable(with = FormAnswerPatchSerializer::class) val answers: Map<String, FormAnswerValue?>,
    )

    @Serializable
    private data class Dto(
        @Serializable(with = FormAnswerValuesSerializer::class) val answers: Map<String, FormAnswerValue>,
    )

    @Test
    fun `patch leva o apagado como null explicito e o mapa de respostas descarta null`() {
        val body = Corpo(linkedMapOf("q1" to FormAnswerValue.text("sim"), "q2" to null))
        val json = FormJson.format.encodeToString(Corpo.serializer(), body)
        assertEquals("""{"answers":{"q1":"sim","q2":null}}""", json)
        assertEquals(body, FormJson.format.decodeFromString(Corpo.serializer(), json))
        val dto = FormJson.format.decodeFromString(Dto.serializer(), """{"answers":{"a":1,"b":null,"c":["x"]}}""")
        assertEquals(setOf("a", "c"), dto.answers.keys)
        assertEquals(JsonNull, mapOf<String, FormAnswerValue?>("x" to null).toFormAnswerPatchJson().jsonObject["x"])
    }

    @Test
    fun `contexto le o publico e ignora o desconhecido`() {
        val context = FormJson.format.decodeFromString(FormContext.serializer(), """{"sexAtBirth":"F","ageYears":34,"audience":"DOCTOR","x":1}""")
        assertEquals(FormRole.DOCTOR, context.audience)
        assertTrue(FormCondition.context(FormContextKey.AUDIENCE, FormCondOp.EQ, JsonPrimitive("DOCTOR")).evaluate(context) { null })
    }
}
