package br.com.codecacto.kmplib.workout.energy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalorieEstimatorTest {

    @Test
    fun `60 min 70 kg esforco 6 - MET moderado 5 0 dao 350 kcal com fonte MET e faixa`() {
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
    fun `esforco baixo usa MET leve alto usa MET vigoroso`() {
        val light = CalorieEstimator.fromMet(70.0, 60.0, WorkoutType.STRENGTH, effort = 2)
        val vigorous = CalorieEstimator.fromMet(70.0, 60.0, WorkoutType.STRENGTH, effort = 10)

        requireNotNull(light)
        requireNotNull(vigorous)
        assertTrue(light.kcal < vigorous.kcal)
    }

    @Test
    fun `fromDevice nunca tem faixa so o valor`() {
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
    fun `fromHeartRate com FC baixa demais para a equacao nao inventa zero e o estimate cai para o MET`() {
        // Fórmula feminina de Keytel a 60 bpm dá gasto negativo: não é "0 kcal pela FC", é sem estimativa.
        assertNull(CalorieEstimator.fromHeartRate(60, weightKg = 70.0, ageYears = 30, isMale = false, minutes = 30.0))
        val result = CalorieEstimator.estimate(
            deviceKcal = null,
            heartRate = CalorieEstimator.HeartRateInput(60, 70.0, 30, false, 30.0),
            met = CalorieEstimator.MetInput(70.0, 30.0, WorkoutType.STRENGTH, 6),
        )
        requireNotNull(result)
        assertEquals(EnergySource.MET, result.source)
        assertTrue(result.kcal > 0)
    }

    @Test
    fun `roundKcal - dezena a partir de 10, unidade abaixo, nunca zero para gasto positivo`() {
        assertEquals(0, CalorieEstimator.roundKcal(0.0))
        assertEquals(0, CalorieEstimator.roundKcal(-3.0))
        assertEquals(0, CalorieEstimator.roundKcal(Double.NaN))
        assertEquals(0, CalorieEstimator.roundKcal(Double.POSITIVE_INFINITY))
        assertEquals(1, CalorieEstimator.roundKcal(0.01))
        assertEquals(1, CalorieEstimator.roundKcal(1.4))
        assertEquals(5, CalorieEstimator.roundKcal(4.5))
        assertEquals(9, CalorieEstimator.roundKcal(9.4))
        assertEquals(10, CalorieEstimator.roundKcal(9.6))
        assertEquals(10, CalorieEstimator.roundKcal(10.0))
        assertEquals(10, CalorieEstimator.roundKcal(14.9))
        assertEquals(20, CalorieEstimator.roundKcal(15.0))
        assertEquals(230, CalorieEstimator.roundKcal(234.0))
        assertEquals(240, CalorieEstimator.roundKcal(235.0))
    }

    @Test
    fun `treino curto nao zera - 40 segundos a 70 kg dao gasto positivo pelo MET`() {
        // 5.0 MET x 70 kg x (40/3600) h = 3,9 kcal — até a 2.298.0 isso virava 0.
        val estimate = CalorieEstimator.fromMet(70.0, minutes = 40.0 / 60.0, type = WorkoutType.STRENGTH, effort = 6)
        requireNotNull(estimate)
        assertEquals(4, estimate.kcal)
        assertEquals(3, estimate.low)
        assertEquals(5, estimate.high)
        assertEquals(5.0 * 70.0 * (40.0 / 3600.0), estimate.exactKcal, 1e-9)
    }

    @Test
    fun `treino de um segundo ainda mostra 1 kcal`() {
        val estimate = CalorieEstimator.fromMet(60.0, minutes = 1.0 / 60.0, type = WorkoutType.STRENGTH, effort = 2)
        requireNotNull(estimate)
        assertEquals(1, estimate.kcal)
        assertEquals(1, estimate.low)
        assertTrue(estimate.exactKcal > 0.0 && estimate.exactKcal < 1.0)
    }

    @Test
    fun `treino curto pela FC tambem nao zera`() {
        val estimate = CalorieEstimator.fromHeartRate(130, weightKg = 70.0, ageYears = 30, isMale = true, minutes = 0.5)
        requireNotNull(estimate)
        assertTrue(estimate.kcal in 1..9, "meio minuto a 130 bpm = poucas kcal, nunca 0 (foi ${estimate.kcal})")
    }

    @Test
    fun `exactKcal guarda o valor sem arredondar para somar a semana`() {
        val estimate = CalorieEstimator.fromDevice(234.4)
        assertEquals(230, estimate.kcal)
        assertEquals(234.4, estimate.exactKcal, 1e-9)
        // Construção à mão (código de antes do campo) continua valendo: exato = o próprio kcal.
        assertEquals(120.0, Estimate(120, null, null, EnergySource.DEVICE).exactKcal)
    }

    @Test
    fun `dispositivo com zero conta como ausente e cai para a proxima fonte`() {
        val result = CalorieEstimator.estimate(
            deviceKcal = 0.0,
            heartRate = null,
            met = CalorieEstimator.MetInput(70.0, 60.0, WorkoutType.STRENGTH, 6),
        )
        requireNotNull(result)
        assertEquals(EnergySource.MET, result.source)
        assertEquals(350, result.kcal)
    }

    @Test
    fun `teto da FC usa o valor exato do MET`() {
        val result = CalorieEstimator.estimate(
            deviceKcal = null,
            heartRate = CalorieEstimator.HeartRateInput(190, 70.0, 25, true, 60.0),
            met = CalorieEstimator.MetInput(70.0, 60.0, WorkoutType.STRENGTH, 6),
        )
        requireNotNull(result)
        assertEquals(EnergySource.HEART_RATE, result.source)
        assertEquals(700.0, result.exactKcal, 1e-9)
        assertEquals(700, result.kcal)
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
    fun `estimate usa FC quando nao ha dispositivo com teto de 2x o MET`() {
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
