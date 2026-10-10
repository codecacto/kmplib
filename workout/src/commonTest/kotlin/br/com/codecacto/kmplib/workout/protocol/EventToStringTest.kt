package br.com.codecacto.kmplib.workout.protocol

import br.com.codecacto.kmplib.workout.engine.WorkoutEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Higiene de log (2.273.0): eventos do motor e mensagens do protocolo não imprimem carga, repetições,
 * FC, kcal, instante nem o motivo do "pular" — só QUAIS campos vieram. `equals` e a serialização não
 * mudam.
 */
class EventToStringTest {

    private fun assertSemDado(texto: String, vararg proibidos: String) {
        proibidos.forEach { assertFalse(it in texto, "\"$it\" vazou em: $texto") }
    }

    @Test
    fun completeSetDoMotorNaoImprimeCargaRepsNemFc() {
        val evento = WorkoutEvent.CompleteSet(reps = 11, load = 92.5, heartRateAvg = 143, heartRateMax = 171)
        val texto = evento.toString()
        assertSemDado(texto, "11", "92.5", "143", "171")
        assertEquals("CompleteSet(hasReps=true, hasLoad=true, hasHeartRate=true)", texto)
        assertEquals(
            "CompleteSet(hasReps=false, hasLoad=false, hasHeartRate=false)",
            WorkoutEvent.CompleteSet(reps = null, load = null).toString(),
        )
        assertEquals(evento, evento.copy())
    }

    @Test
    fun skipExerciseNaoImprimeOMotivo() {
        val texto = WorkoutEvent.SkipExercise("dor no joelho esquerdo").toString()
        assertSemDado(texto, "joelho")
        assertEquals("SkipExercise(hasReason=true)", texto)
        assertEquals("SkipExercise(hasReason=false)", WorkoutEvent.SkipExercise().toString())
        assertSemDado(CommandEvent.SkipExercise("tontura").toString(), "tontura")
    }

    @Test
    fun completeSetDoProtocoloNaoImprimeCargaNemReps() {
        val texto = CommandEvent.CompleteSet(reps = 8, load = 60.0).toString()
        assertSemDado(texto, "8,", "60.0")
        assertEquals("CompleteSet(hasReps=true, hasLoad=true)", texto)
    }

    @Test
    fun metricsNaoImprimeFcKcalNemInstante() {
        val m = Metrics(seq = 7, heartRate = 158, kcal = 312.4, epochMillis = 1_700_000_123_456)
        val texto = m.toString()
        assertSemDado(texto, "158", "312.4", "1700000123456")
        assertTrue(texto.startsWith("Metrics(seq=7"))
        assertTrue("hasHeartRate=true" in texto && "hasKcal=true" in texto)
    }

    @Test
    fun serializacaoContinuaComOsValores() {
        val json = Json { encodeDefaults = true }
        val m = Metrics(seq = 1, heartRate = 150, kcal = 10.0, epochMillis = 5)
        val texto = json.encodeToString(Metrics.serializer(), m)
        assertTrue("150" in texto && "10.0" in texto)
        assertEquals(m, json.decodeFromString(Metrics.serializer(), texto))

        val cmd = Command(seq = 2, id = "c1", event = CommandEvent.SkipExercise("dor"))
        val cmdJson = json.encodeToString(Command.serializer(), cmd)
        assertTrue("dor" in cmdJson)
        assertEquals(cmd, json.decodeFromString(Command.serializer(), cmdJson))
    }
}
