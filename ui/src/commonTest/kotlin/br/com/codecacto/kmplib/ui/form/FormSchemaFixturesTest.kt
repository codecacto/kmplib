package br.com.codecacto.kmplib.ui.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * **O contrato compartilhado das três libs.** `form-schema-v1.fixtures.json` é COPIADO sem edição do
 * `backlib-forms` (o mesmo arquivo que a weblib copia) e vira fonte do `commonTest` na compilação —
 * este teste roda no Android E no iOS. Fixture que falha aqui é divergência de semântica entre
 * servidor, portal e app, não detalhe de teste: a correção é no motor, nunca na cópia (mudou
 * expectativa = sobe `fixturesVersion` nas três).
 *
 * Roda os grupos que `consumers` marca para a kmplib: `conditions`, `visibility` (seções e perguntas
 * na ORDEM), `prune` (ids na ordem) e `validation` (`issues` como CONJUNTO de `{questionId, code, path}`).
 * Os demais (lint, reserva, público, pontuação) são só do servidor — pontuação clínica é só dele (D11).
 */
class FormSchemaFixturesTest {

    /** A versão das fixtures que ESTA cópia representa — sobe junto com a do servidor. */
    private val fixturesVersion = 1

    private val root: JsonObject = Json.parseToJsonElement(FORM_SCHEMA_FIXTURES_JSON).jsonObject
    private val schemas: JsonObject = root.getValue("schemas").jsonObject

    private fun schemaOf(ref: JsonElement): FormSchemaV1 {
        val element = if (ref is JsonPrimitive && ref.isString) schemas[ref.content] ?: fail("schema desconhecido: $ref") else ref
        return FormJson.format.decodeFromJsonElement(FormSchemaV1.serializer(), element)
    }

    private fun contextOf(case: JsonObject): FormContext =
        case["context"]?.let { FormJson.format.decodeFromJsonElement(FormContext.serializer(), it) } ?: FormContext()

    private fun answersOf(case: JsonObject): Map<String, FormAnswerValue?> =
        (case["answers"] as? JsonObject).orEmpty().mapValues { (_, raw) -> FormAnswerValue.fromJsonOrNull(raw) }

    private fun cases(group: String): List<JsonObject> = root.getValue(group).jsonArray.map { it.jsonObject }

    private fun name(case: JsonObject): String = case.getValue("name").jsonPrimitive.content

    @Test
    fun `a copia e a versao declarada - do formato v1 - e a kmplib consome os 4 grupos`() {
        assertEquals(fixturesVersion, root.getValue("fixturesVersion").jsonPrimitive.int)
        assertEquals(FORM_SCHEMA_VERSION, root.getValue("schemaVersion").jsonPrimitive.int)
        val consumers = root.getValue("consumers").jsonObject
        for (group in listOf("conditions", "visibility", "prune", "validation")) {
            assertTrue("kmplib" in consumers.getValue(group).jsonArray.map { it.jsonPrimitive.content }, group)
        }
        // Os grupos que NÃO são da kmplib continuam só do servidor (pontuação inclusive — D11).
        for (group in listOf("lint", "reservation", "audience", "scoring")) {
            assertTrue("kmplib" !in consumers.getValue(group).jsonArray.map { it.jsonPrimitive.content }, group)
        }
    }

    @Test
    fun `todos os schemas nomeados das fixtures sao lidos pelo modelo do app`() {
        assertEquals(19, schemas.size)
        schemas.forEach { (name, element) ->
            val schema = FormJson.format.decodeFromJsonElement(FormSchemaV1.serializer(), element)
            assertTrue(schema.sections.isNotEmpty(), name)
            // Ida e volta pelo JSON não perde nada que o cliente lê.
            val again = FormJson.decodeSchema(FormJson.encodeSchema(schema))
            assertEquals(schema, again, name)
        }
    }

    @Test
    fun conditions() {
        val all = cases("conditions")
        assertEquals(51, all.size)
        val failures = all.mapNotNull { case ->
            val condition = FormJson.format.decodeFromJsonElement(FormCondition.serializer(), case.getValue("condition"))
            val answers = answersOf(case)
            val got = condition.evaluate(contextOf(case)) { answers[it] }
            val expected = case.getValue("expected").jsonPrimitive.boolean
            if (got == expected) null else "${name(case)}: esperava $expected, veio $got"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun visibility() {
        val all = cases("visibility")
        assertEquals(10, all.size)
        val failures = all.mapNotNull { case ->
            val visibility = schemaOf(case.getValue("schema")).evaluateVisibility(answersOf(case), contextOf(case))
            val sections = case.getValue("visibleSections").jsonArray.map { it.jsonPrimitive.content }
            val questions = case.getValue("visibleQuestions").jsonArray.map { it.jsonPrimitive.content }
            when {
                visibility.visibleSectionIds != sections -> "${name(case)}: seções ${visibility.visibleSectionIds} ≠ $sections"
                visibility.visibleQuestionIds != questions -> "${name(case)}: perguntas ${visibility.visibleQuestionIds} ≠ $questions"
                else -> null
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun prune() {
        val all = cases("prune")
        assertEquals(3, all.size)
        val failures = all.mapNotNull { case ->
            val kept = schemaOf(case.getValue("schema")).pruneAnswers(answersOf(case), contextOf(case)).keys.toList()
            val expected = case.getValue("kept").jsonArray.map { it.jsonPrimitive.content }
            if (kept == expected) null else "${name(case)}: $kept ≠ $expected"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun validation() {
        val all = cases("validation")
        assertEquals(66, all.size)
        val failures = all.mapNotNull { case ->
            val schema = schemaOf(case.getValue("schema"))
            val today = LocalDate.parse(case.getValue("today").jsonPrimitive.content)
            val answers = answersOf(case)
            val options = case["options"] as? JsonObject
            val result = when (val mode = case.getValue("mode").jsonPrimitive.content) {
                "values" -> schema.validateValues(answers, today)
                "submission" -> schema.validateSubmission(
                    answers = answers,
                    today = today,
                    context = contextOf(case),
                    onlyQuestionIds = (options?.get("onlyQuestionIds") as? JsonArray)?.map { it.jsonPrimitive.content },
                    requireConsent = (options?.get("requireConsent") as? JsonPrimitive)?.boolean ?: true,
                )
                else -> fail("modo desconhecido: $mode")
            }
            val got = result.issues.map { key(it.questionId, it.code.name, it.path) }.toSet()
            val expected = case.getValue("issues").jsonArray.map { issue ->
                val obj = issue.jsonObject
                key(
                    obj.getValue("questionId").jsonPrimitive.content,
                    obj.getValue("code").jsonPrimitive.content,
                    (obj["path"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content,
                )
            }.toSet()
            when {
                got != expected -> "${name(case)}: ${got.sorted()} ≠ ${expected.sorted()}"
                // "No máximo um problema por valor": o conjunto não esconde repetição.
                result.issues.size != got.size -> "${name(case)}: problema repetido ${result.issues}"
                result.isValid != expected.isEmpty() -> "${name(case)}: isValid=${result.isValid}"
                else -> null
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun key(questionId: String, code: String, path: String?): String = "$questionId|$code|${path.orEmpty()}"
}
