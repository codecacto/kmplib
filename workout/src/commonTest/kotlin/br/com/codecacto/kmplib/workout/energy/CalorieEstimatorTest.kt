package br.com.codecacto.kmplib.workout.energy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalorieEstimatorTest {

    @Test
    fun `60 min, 70 kg, esforco 6 (MET moderado 5,0) dao 350 kcal com fonte MET e faixa`() {
        val estimate = CalorieEstimator.fromMet(weightKg = 70.0, minutes = 60.0, type = WorkoutType.STRENGTH, effort = 6)

        requireNotNull(estimate)
        assertEquals(350, estimate.kcal)
        assertEquals(EnergySource.MET, estimate.source)
        requireNotNull(estimate.low)
        requireNotNull(estimate.high)
        assertTrue(estimate.low!! < estimate.kcal)
        assertTrue(estimate.high!! > estimate.kcal)
    }

    @Test
    fun `sem peso nao ha estimativa por MET`() {
        val estimate = CalorieEstimator.fromMet(weightKg = null, minutes = 60.0, type = WorkoutType.STRENGTH, effort = 6)
        assertNull(estimate)
    }

    @Test
    fun `esforco baixo usa MET leve, alto usa MET vigoroso`() {
        val light = CalorieEstimator.fromMet(70.0, 60.0, WorkoutType.STRENGTH, effort = 2)
        val vigorous = CalorieEstimator.fromMet(70.0, 60.0, WorkoutType.STRENGTH, effort = 10)

        requireNotNull(light)
        requireNotNull(vigorous)
        assertTrue(light.kcal < vigorous.kcal)
    }

    @Test
    fun `fromDevice nunca tem faixa, so o valor`() {
        val estimate = CalorieEstimator.fromDevice(234.0)
        assertEquals(EnergySource.DEVICE, estimate.source)
        assertNull(estimate.low)
        assertNull(estimate.high)
        assertEquals(230, estimate.kcal) // arredondado para dezenas
    }

    @Test
    fun `fromHeartRate exige peso e minutos positivos`() {
        assertNull(CalorieEstimator.fromHeartRate(130, weightKg = 0.0, ageYears = 30, isMale = true, minutes = 30.0))
        assertNull(CalorieEstimator.fromHeartRate(130, weightKg = 70.0, ageYears = 30, isMale = true, minutes = 0.0))
    }

    @Test
    fun `fromHeartRate nunca devolve negativo mesmo com FC baixa`() {
        val estimate = CalorieEstimator.fromHeartRate(60, weightKg = 70.0, ageYears = 30, isMale = false, minutes = 30.0)
        requireNotNull(estimate)
        assertTrue(estimate.kcal >= 0)
    }

    @Test
    fun `estimate prioriza o dispositivo quando disponivel`() {
        val result = CalorieEstimator.estimate(
            deviceKcal = 300.0,
            heartRate = CalorieEstimator.HeartRateInput(140, 70.0, 30, true, 60.0),
            met = CalorieEstimator.MetInput(70.0, 60.0, WorkoutType.STRENGTH, 6),
        )
        requireNotNull(result)
        assertEquals(EnergySource.DEVICE, result.source)
    }

    @Test
    fun `estimate usa FC quando nao ha dispositivo, com teto de 2x o MET`() {
        // FC bem alta tenderia a superestimar muito em força; o teto evita um número absurdo.
        val result = CalorieEstimator.estimate(
            deviceKcal = null,
            heartRate = CalorieEstimator.HeartRateInput(180, 70.0, 25, true, 60.0),
            met = CalorieEstimator.MetInput(70.0, 60.0, WorkoutType.STRENGTH, 6), // MET ~ 350
        )
        requireNotNull(result)
        assertEquals(EnergySource.HEART_RATE, result.source)
        assertTrue(result.kcal <= 700) // teto = 2x 350
    }

    @Test
    fun `estimate cai para MET quando nao ha dispositivo nem FC`() {
        val result = CalorieEstimator.estimate(
            deviceKcal = null,
            heartRate = null,
            met = CalorieEstimator.MetInput(70.0, 60.0, WorkoutType.STRENGTH, 6),
        )
        requireNotNull(result)
        assertEquals(EnergySource.MET, result.source)
    }

    @Test
    fun `estimate sem nenhuma fonte devolve null`() {
        val result = CalorieEstimator.estimate(
            deviceKcal = null,
            heartRate = null,
            met = CalorieEstimator.MetInput(null, 60.0, WorkoutType.STRENGTH, 6),
        )
        assertNull(result)
    }
}
