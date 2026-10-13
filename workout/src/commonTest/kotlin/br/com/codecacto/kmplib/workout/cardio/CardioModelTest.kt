package br.com.codecacto.kmplib.workout.cardio

import br.com.codecacto.kmplib.workout.model.CardioBlock
import br.com.codecacto.kmplib.workout.model.CardioMode
import br.com.codecacto.kmplib.workout.model.CardioPhase
import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.HeartRateZone
import br.com.codecacto.kmplib.workout.model.phaseDurationRange
import br.com.codecacto.kmplib.workout.model.phasesPerRound
import br.com.codecacto.kmplib.workout.model.secondsOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `CardioBlock` = o `CardioBlockDto` do contrato A15b (K-13), campo a campo. */
class CardioModelTest {

    @Test
    fun `total e voltas vezes trabalho mais descanso, o Tabata de 4 min`() {
        assertEquals(480, CardioBlock.interval(40, 20, 8).totalSeconds)
        assertEquals(240, CardioBlock.interval(20, 10, 8).totalSeconds)
        assertEquals(1800, CardioBlock.continuous(1800).totalSeconds)
        // Valor negativo (dado corrompido) não vira total negativo.
        assertEquals(20, CardioBlock(CardioMode.INTERVAL, -5, 10, 2).totalSeconds)
        assertEquals(0, CardioBlock(CardioMode.INTERVAL, 30, 10, -1).totalSeconds)
    }

    @Test
    fun `faixas do contrato no intervalado`() {
        assertTrue(CardioBlock.interval(10, 0, 1).isValid)
        assertTrue(CardioBlock.interval(3600, 900, 50).isValid)
        assertEquals(listOf("workSeconds", "restSeconds", "rounds"), CardioBlock.interval(9, 901, 51).invalidFields())
        assertEquals(listOf("workSeconds"), CardioBlock.interval(3601, 0, 1).invalidFields())
        assertEquals(listOf("restSeconds"), CardioBlock.interval(30, -1, 1).invalidFields())
        assertEquals(listOf("rounds"), CardioBlock.interval(30, 10, 0).invalidFields())
    }

    @Test
    fun `faixas do contrato no continuo - 1 volta sem descanso, ate 2 h`() {
        assertTrue(CardioBlock.continuous(7200).isValid)
        assertEquals(listOf("workSeconds"), CardioBlock.continuous(7201).invalidFields())
        assertEquals(listOf("workSeconds"), CardioBlock.continuous(9).invalidFields())
        assertEquals(listOf("restSeconds", "rounds"), CardioBlock(CardioMode.CONTINUOUS, 600, 10, 2).invalidFields())
        assertFalse(CardioBlock(CardioMode.CONTINUOUS, 600, 10, 2).isValid)
    }

    @Test
    fun `fases por volta e duracao aceita por fase`() {
        val tabata = CardioBlock.interval(20, 10, 8)
        assertEquals(listOf(CardioPhase.WORK, CardioPhase.REST), tabata.phasesPerRound())
        assertEquals(listOf(CardioPhase.WORK), CardioBlock.continuous(60).phasesPerRound())
        assertEquals(listOf(CardioPhase.WORK), CardioBlock.interval(30, 0, 3).phasesPerRound())
        assertEquals(listOf(CardioPhase.REST), CardioBlock(CardioMode.INTERVAL, 0, 10, 1).phasesPerRound())
        assertEquals(20, tabata.secondsOf(CardioPhase.WORK))
        assertEquals(10, tabata.secondsOf(CardioPhase.REST))
        assertEquals(0..20, tabata.phaseDurationRange(CardioPhase.WORK))
        assertEquals(0..0, CardioBlock(CardioMode.INTERVAL, 30, -3, 1).phaseDurationRange(CardioPhase.REST))
    }

    @Test
    fun `zonas por percentual da FC maxima`() {
        assertEquals(listOf(50, 60, 70, 80, 90), HeartRateZone.entries.map { it.lowerPercent })
        assertEquals(listOf(60, 70, 80, 90, 100), HeartRateZone.entries.map { it.upperPercent })
    }

    @Test
    fun `JSON do bloco tem os nomes do contrato e a zona opcional`() {
        val step = ExerciseStep(
            "x", "treadmill", "Esteira", emptyList(), 60,
            cardio = CardioBlock.interval(40, 20, 8, HeartRateZone.Z4),
        )
        val json = Json.encodeToString(step)
        val cardio = Json.parseToJsonElement(json).jsonObject["cardio"]!!.jsonObject
        assertEquals("INTERVAL", cardio["mode"]!!.jsonPrimitive.content)
        assertEquals("40", cardio["workSeconds"]!!.jsonPrimitive.content)
        assertEquals("20", cardio["restSeconds"]!!.jsonPrimitive.content)
        assertEquals("8", cardio["rounds"]!!.jsonPrimitive.content)
        assertEquals("Z4", cardio["targetZone"]!!.jsonPrimitive.content)
        assertEquals(step, Json.decodeFromString<ExerciseStep>(json))

        val dto = """{"mode":"CONTINUOUS","workSeconds":1800,"restSeconds":0,"rounds":1,"targetZone":null}"""
        assertNull(Json.decodeFromString<CardioBlock>(dto).targetZone)
        // Item antigo (sem o campo) continua lendo como força.
        val legacy = """{"id":"e","exerciseRefId":"r","name":"n","sets":[],"restSeconds":0}"""
        assertNull(Json.decodeFromString<ExerciseStep>(legacy).cardio)
    }
}
