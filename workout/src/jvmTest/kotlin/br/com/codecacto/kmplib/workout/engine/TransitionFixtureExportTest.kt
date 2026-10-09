package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.metrics.RunIssue
import br.com.codecacto.kmplib.workout.metrics.SESSION_VALIDATION_CASES
import br.com.codecacto.kmplib.workout.metrics.SESSION_VALIDATION_TABLE_VERSION
import br.com.codecacto.kmplib.workout.metrics.SessionValidationTable
import br.com.codecacto.kmplib.workout.metrics.validateRun
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import br.com.codecacto.kmplib.workout.protocol.WORKOUT_PROTOCOL_VERSION
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mantém os arquivos de `workout/fixtures/` iguais às tabelas do `commonTest`:
 * - `engine-transitions.json` = [ENGINE_TRANSITION_CASES], o que outra implementação da máquina
 *   (Monkey C, RF-REL-18) roda como caso de teste;
 * - `session-validation.json` = [SESSION_VALIDATION_CASES], o que o teste do backend (A5) roda com o
 *   JAR JVM contra o `validateRun`.
 *
 * Os dois saem no artefato `kmplib-workout-fixtures`. Se uma tabela mudou, este teste FALHA até o
 * arquivo ser regenerado:
 *
 * ```
 * ./gradlew :kmplib-workout:jvmTest -Pkmplib.workout.updateFixtures=true
 * ```
 */
class TransitionFixtureExportTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private fun renderedTransitions(): String = json.encodeToString(
        TransitionTable.serializer(),
        TransitionTable(
            version = TRANSITION_TABLE_VERSION,
            protocolVersion = WORKOUT_PROTOCOL_VERSION,
            epochBaseSeconds = TABLE_T0.epochSeconds,
            cases = ENGINE_TRANSITION_CASES,
        ),
    ) + "\n"

    private fun renderedSessions(): String = json.encodeToString(
        SessionValidationTable.serializer(),
        SessionValidationTable(
            version = SESSION_VALIDATION_TABLE_VERSION,
            protocolVersion = WORKOUT_PROTOCOL_VERSION,
            cases = SESSION_VALIDATION_CASES,
        ),
    ) + "\n"

    private fun assertFixture(fileName: String, expected: String) {
        val dir = System.getProperty("kmplib.workout.fixturesDir")
            ?: error("kmplib.workout.fixturesDir não configurado no build.gradle.kts do módulo")
        val file = File(dir, fileName)
        if (System.getProperty("kmplib.workout.updateFixtures") == "true") {
            file.parentFile.mkdirs()
            file.writeText(expected)
        }
        assertTrue(file.exists(), "$fileName ausente: rode com -Pkmplib.workout.updateFixtures=true")
        assertEquals(expected, file.readText(), "$fileName desatualizada: rode com -Pkmplib.workout.updateFixtures=true")
    }

    @Test
    fun `fixture JSON reflete a tabela`() {
        assertFixture("engine-transitions.json", renderedTransitions())
    }

    @Test
    fun `fixture de sessoes reflete a tabela`() {
        assertFixture("session-validation.json", renderedSessions())
    }

    @Test
    fun `fixture volta a ser lida igual`() {
        val table = json.decodeFromString(TransitionTable.serializer(), renderedTransitions())
        assertEquals(ENGINE_TRANSITION_CASES, table.cases)
    }

    /**
     * O que o backend faz: lê o JSON SÓ com os tipos do artefato principal (sem as classes desta
     * tabela de teste) e confere o `validateRun`. É o trecho que `references/workout.md` documenta.
     */
    @Test
    fun `backend le a fixture de sessoes so com o artefato principal`() {
        val reader = Json { ignoreUnknownKeys = true }
        val root = reader.parseToJsonElement(renderedSessions()).jsonObject
        val cases = root.getValue("cases").jsonArray
        assertEquals(SESSION_VALIDATION_CASES.size, cases.size)
        for (element in cases) {
            val case = element.jsonObject
            val plan = reader.decodeFromJsonElement(WorkoutPlan.serializer(), case.getValue("plan"))
            val run = reader.decodeFromJsonElement(WorkoutRun.serializer(), case.getValue("run"))
            val expected = reader.decodeFromJsonElement(
                kotlinx.serialization.builtins.ListSerializer(RunIssue.serializer()),
                case.getValue("expectedIssues"),
            )
            assertEquals(expected, validateRun(plan, run), case.getValue("name").jsonPrimitive.content)
        }
    }
}
