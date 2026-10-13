package br.com.codecacto.kmplib.workout.cardio

import br.com.codecacto.kmplib.workout.engine.T0
import br.com.codecacto.kmplib.workout.model.HeartRateZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HeartRateZonesTest {

    private fun at(s: Int) = T0 + s.seconds

    @Test
    fun `FC maxima de Tanaka`() {
        assertEquals(180, estimatedMaxHeartRate(40))
        assertEquals(187, estimatedMaxHeartRate(30))
        assertEquals(166, estimatedMaxHeartRate(60))
        assertFailsWith<IllegalArgumentException> { estimatedMaxHeartRate(0) }
        assertFailsWith<IllegalArgumentException> { estimatedMaxHeartRate(121) }
    }

    @Test
    fun `faixas em bpm para FC maxima 190`() {
        assertEquals(95..113, HeartRateZone.Z1.bpmRange(190))
        assertEquals(114..132, HeartRateZone.Z2.bpmRange(190))
        assertEquals(133..151, HeartRateZone.Z3.bpmRange(190))
        assertEquals(152..170, HeartRateZone.Z4.bpmRange(190))
        assertEquals(171..190, HeartRateZone.Z5.bpmRange(190))
        assertFailsWith<IllegalArgumentException> { HeartRateZone.Z1.bpmRange(0) }
    }

    @Test
    fun `zonas vizinhas nao se sobrepoem nem deixam buraco, e a zona do bpm bate com a faixa`() {
        for (max in 100..220) {
            val ranges = HeartRateZone.entries.map { it.bpmRange(max) }
            ranges.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first, "max $max") }
            assertEquals(max, ranges.last().last)
            for (bpm in 0..250) {
                val expected = when {
                    bpm < ranges.first().first -> null
                    bpm > max -> HeartRateZone.Z5
                    else -> HeartRateZone.entries.first { bpm in it.bpmRange(max) }
                }
                assertEquals(expected, heartRateZoneOf(bpm, max), "bpm $bpm, max $max")
            }
        }
    }

    @Test
    fun `zona do bpm e limites`() {
        assertNull(heartRateZoneOf(94, 190))
        assertEquals(HeartRateZone.Z1, heartRateZoneOf(95, 190))
        assertEquals(HeartRateZone.Z2, heartRateZoneOf(114, 190))
        assertEquals(HeartRateZone.Z5, heartRateZoneOf(230, 190))
        assertFailsWith<IllegalArgumentException> { heartRateZoneOf(100, -1) }
    }

    @Test
    fun `posicao em relacao a zona-alvo`() {
        assertEquals(ZonePosition.BELOW, HeartRateZone.Z4.positionOf(150, 190))
        assertEquals(ZonePosition.INSIDE, HeartRateZone.Z4.positionOf(152, 190))
        assertEquals(ZonePosition.INSIDE, HeartRateZone.Z4.positionOf(170, 190))
        assertEquals(ZonePosition.ABOVE, HeartRateZone.Z4.positionOf(171, 190))
        assertEquals(ZonePosition.BELOW, HeartRateZone.Z5.positionOf(170, 190))
        assertEquals(ZonePosition.INSIDE, HeartRateZone.Z5.positionOf(205, 190))
    }

    @Test
    fun `tempo na zona com lacuna do sensor limitada a 5 s`() {
        val samples = listOf(
            HeartRateSample(at(10), 160), // vale 10..12
            HeartRateSample(at(0), 160), // vale 0..3 (fora de ordem: é ordenada)
            HeartRateSample(at(3), 160), // próxima em 10: vale só até 8
            HeartRateSample(at(12), 140), // abaixo da Z4
        )
        assertEquals(10, secondsInZone(samples, HeartRateZone.Z4, 190, T0, at(20)))
        assertEquals(5, secondsInZone(samples, HeartRateZone.Z4, 190, at(5), at(20)))
        assertEquals(1, secondsInZone(samples, HeartRateZone.Z4, 190, at(11), at(20)))
        assertEquals(12, secondsInZone(samples, HeartRateZone.Z4, 190, T0, at(20), maxGap = 10.seconds))
    }

    @Test
    fun `amostra anterior a janela vale para o comeco - janela vazia ou invertida e zero`() {
        val samples = listOf(HeartRateSample(at(-2), 160))
        assertEquals(3, secondsInZone(samples, HeartRateZone.Z4, 190, T0, at(10)))
        assertEquals(0, secondsInZone(samples, HeartRateZone.Z4, 190, at(10), at(10)))
        assertEquals(0, secondsInZone(samples, HeartRateZone.Z4, 190, at(10), T0))
        assertEquals(0, secondsInZone(emptyList(), HeartRateZone.Z4, 190, T0, at(10)))
        assertEquals(0, secondsInZone(samples, HeartRateZone.Z4, 190, T0, at(10), maxGap = Duration.ZERO))
        assertFailsWith<IllegalArgumentException> {
            secondsInZone(samples, HeartRateZone.Z4, 190, T0, at(10), maxGap = (-1).seconds)
        }
        assertEquals(5.seconds, DEFAULT_SAMPLE_MAX_GAP)
    }

    @Test
    fun `amostra nao imprime bpm nem instante`() {
        assertEquals("HeartRateSample(***)", HeartRateSample(at(1), 172).toString())
    }
}
