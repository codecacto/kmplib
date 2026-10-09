package br.com.codecacto.kmplib.workout.metrics

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Roda a [SESSION_VALIDATION_CASES] contra o `validateRun`, em todo alvo. */
class SessionValidationTableTest {

    @Test
    fun `cada sessao da tabela recebe o veredito esperado`() {
        assertTrue(SESSION_VALIDATION_CASES.size > 20)
        for (case in SESSION_VALIDATION_CASES) {
            assertEquals(case.expectedIssues, validateRun(case.plan, case.run), case.name)
        }
    }

    @Test
    fun `as duas familias estao presentes e os nomes sao unicos`() {
        val names = SESSION_VALIDATION_CASES.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.any { it.startsWith("motor (tabela)") })
        assertTrue(names.any { it.startsWith("motor (completo)") })
        assertTrue(SESSION_VALIDATION_CASES.any { it.expectedIssues.isNotEmpty() })
    }

    @Test
    fun `RunIssue atravessa JSON com o discriminador em maiusculas`() {
        val issues: List<RunIssue> = listOf(
            RunIssue.PlanMismatch("a", "b"),
            RunIssue.UnknownStep(1),
            RunIssue.DuplicateStep(2),
            RunIssue.NegativeDuration(3),
            RunIssue.UnknownSkippedExercise("x"),
        )
        val text = Json.encodeToString(issues)
        assertTrue("\"type\":\"PLAN_MISMATCH\"" in text, text)
        assertTrue("\"type\":\"UNKNOWN_SKIPPED_EXERCISE\"" in text, text)
        assertEquals(issues, Json.decodeFromString<List<RunIssue>>(text))
    }
}
