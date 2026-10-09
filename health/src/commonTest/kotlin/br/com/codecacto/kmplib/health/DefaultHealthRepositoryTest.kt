package br.com.codecacto.kmplib.health

import br.com.codecacto.kmplib.workout.energy.EnergySource
import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

private val START = Instant.fromEpochSeconds(1_700_000_000)
private val END = START + 45.minutes

/** Repositório de saúde de mentira: cada resposta da plataforma é configurável, e conta as chamadas. */
private class FakeGateway : HealthStoreGateway {
    var availability = HealthAvailability.AVAILABLE
    var readGranted: Map<HealthDataType, Boolean?> = HealthDataType.entries.associateWith { true }
    var writeGranted = true
    var permissionResult = true
    var heartRate: HeartRateStats? = null
    var energy: Double? = null
    var workouts: List<StoredWorkout> = emptyList()
    var insertResult = WorkoutWriteResult.WRITTEN
    var denyOn: String? = null
    val calls = mutableListOf<String>()

    private fun call(name: String) {
        calls += name
        if (denyOn == name) throw HealthPermissionException()
    }

    override fun availability(): HealthAvailability = availability

    override suspend fun requestPermissions(types: Set<HealthDataType>): Boolean {
        call("request")
        return permissionResult
    }

    override suspend fun isReadGranted(type: HealthDataType): Boolean? = readGranted[type]

    override suspend fun isWorkoutWriteGranted(): Boolean = writeGranted

    override suspend fun heartRateStats(start: Instant, end: Instant): HeartRateStats? {
        call("heartRate")
        return heartRate
    }

    override suspend fun activeEnergyKcal(start: Instant, end: Instant): Double? {
        call("energy")
        return energy
    }

    override suspend fun workoutsOverlapping(start: Instant, end: Instant): List<StoredWorkout> {
        call("workouts")
        return workouts
    }

    override suspend fun insertWorkout(run: WorkoutRun, end: Instant, category: ExerciseCategory): WorkoutWriteResult {
        call("insert")
        return insertResult
    }
}

class DefaultHealthRepositoryTest {

    private val gateway = FakeGateway()
    private val repository = DefaultHealthRepository(gateway)
    private val run = WorkoutRun(localId = "run-1", planId = "plan-1", startedAt = START, finishedAt = END)

    @Test
    fun `disponibilidade vem da plataforma e isAvailable so no AVAILABLE`() = runTest {
        assertTrue(repository.isAvailable())
        gateway.availability = HealthAvailability.NEEDS_PROVIDER_UPDATE
        assertEquals(HealthAvailability.NEEDS_PROVIDER_UPDATE, repository.availability())
        assertFalse(repository.isAvailable())
        gateway.availability = HealthAvailability.NOT_SUPPORTED
        assertFalse(repository.isAvailable())
    }

    @Test
    fun `pedir permissao`() = runTest {
        assertTrue(repository.requestPermissions(emptySet()))
        assertTrue(gateway.calls.isEmpty())

        assertTrue(repository.requestPermissions(setOf(HealthDataType.HEART_RATE)))
        gateway.permissionResult = false
        assertFalse(repository.requestPermissions(setOf(HealthDataType.HEART_RATE)))

        gateway.permissionResult = true
        gateway.denyOn = "request"
        assertFalse(repository.requestPermissions(setOf(HealthDataType.HEART_RATE)))

        gateway.denyOn = null
        gateway.availability = HealthAvailability.NOT_SUPPORTED
        gateway.calls.clear()
        assertFalse(repository.requestPermissions(setOf(HealthDataType.HEART_RATE)))
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `metricas com valor arredondam a FC e marcam a energia como do dispositivo`() = runTest {
        gateway.heartRate = HeartRateStats(avg = 131.6, max = 171.4)
        gateway.energy = 312.5
        val metrics = repository.readSessionMetrics(START, END)
        assertEquals(HealthMetric.Value(HeartRateSummary(avg = 132, max = 171)), metrics.heartRate)
        assertEquals(HealthMetric.Value(EnergyReading(312.5, EnergySource.DEVICE)), metrics.energy)
    }

    @Test
    fun `sem amostra e NoData, nunca zero`() = runTest {
        val metrics = repository.readSessionMetrics(START, END)
        assertEquals(HealthMetric.NoData, metrics.heartRate)
        assertEquals(HealthMetric.NoData, metrics.energy)
    }

    @Test
    fun `leitura negada e Unavailable sem consultar, e leitura que o iOS nao informa e consultada`() = runTest {
        gateway.readGranted = mapOf(HealthDataType.HEART_RATE to false, HealthDataType.ACTIVE_ENERGY to null)
        gateway.energy = 10.0
        val metrics = repository.readSessionMetrics(START, END)
        assertEquals(HealthMetric.Unavailable, metrics.heartRate)
        assertEquals(HealthMetric.Value(EnergyReading(10.0)), metrics.energy)
        assertEquals(listOf("energy"), gateway.calls)
    }

    @Test
    fun `permissao revogada no meio da leitura vira Unavailable`() = runTest {
        gateway.denyOn = "heartRate"
        gateway.energy = 5.0
        val metrics = repository.readSessionMetrics(START, END)
        assertEquals(HealthMetric.Unavailable, metrics.heartRate)
        assertEquals(HealthMetric.Value(EnergyReading(5.0)), metrics.energy)
    }

    @Test
    fun `plataforma indisponivel nao consulta nada`() = runTest {
        gateway.availability = HealthAvailability.NOT_SUPPORTED
        assertEquals(SessionMetrics(HealthMetric.Unavailable, HealthMetric.Unavailable), repository.readSessionMetrics(START, END))
        assertFalse(repository.hasDeviceWorkoutOverlapping(START, END))
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `intervalo invertido ou vazio e erro de quem chama`() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.readSessionMetrics(END, START) }
        assertFailsWith<IllegalArgumentException> { repository.readSessionMetrics(START, START) }
        assertFailsWith<IllegalArgumentException> { repository.hasDeviceWorkoutOverlapping(END, START) }
    }

    @Test
    fun `treino do relogio no intervalo conta, o deste app nao`() = runTest {
        gateway.workouts = listOf(StoredWorkout(fromThisApp = true, externalId = "run-0"))
        assertFalse(repository.hasDeviceWorkoutOverlapping(START, END))
        gateway.workouts += StoredWorkout(fromThisApp = false, externalId = null)
        assertTrue(repository.hasDeviceWorkoutOverlapping(START, END))
    }

    @Test
    fun `sem como ler treinos a sobreposicao responde false`() = runTest {
        gateway.workouts = listOf(StoredWorkout(fromThisApp = false, externalId = null))
        gateway.readGranted = mapOf(HealthDataType.EXERCISE_SESSION to false)
        assertFalse(repository.hasDeviceWorkoutOverlapping(START, END))
        gateway.readGranted = mapOf(HealthDataType.EXERCISE_SESSION to true)
        gateway.denyOn = "workouts"
        assertFalse(repository.hasDeviceWorkoutOverlapping(START, END))
    }

    @Test
    fun `gravar treino encerrado e valido`() = runTest {
        assertEquals(WorkoutWriteResult.WRITTEN, repository.writeWorkout(run, ExerciseCategory.STRENGTH_TRAINING))
        assertEquals(listOf("workouts", "insert"), gateway.calls)
    }

    @Test
    fun `treino deste app com outro id nao impede gravar`() = runTest {
        gateway.workouts = listOf(StoredWorkout(fromThisApp = true, externalId = "run-anterior"))
        assertEquals(WorkoutWriteResult.WRITTEN, repository.writeWorkout(run, ExerciseCategory.HIIT))
    }

    @Test
    fun `reenviar o mesmo treino e seguro`() = runTest {
        gateway.workouts = listOf(StoredWorkout(fromThisApp = true, externalId = "run-1"))
        assertEquals(WorkoutWriteResult.ALREADY_WRITTEN, repository.writeWorkout(run, ExerciseCategory.CARDIO))
        assertFalse("insert" in gateway.calls)
    }

    @Test
    fun `nao grava por cima do treino do relogio`() = runTest {
        gateway.workouts = listOf(StoredWorkout(fromThisApp = false, externalId = null))
        assertEquals(WorkoutWriteResult.SKIPPED_DEVICE_WORKOUT_EXISTS, repository.writeWorkout(run, ExerciseCategory.CARDIO))
        assertFalse("insert" in gateway.calls)
    }

    @Test
    fun `treino nao encerrado ou com fim antes do inicio nao grava`() = runTest {
        assertEquals(WorkoutWriteResult.RUN_NOT_FINISHED, repository.writeWorkout(run.copy(finishedAt = null), ExerciseCategory.CARDIO))
        assertEquals(WorkoutWriteResult.INVALID_RUN, repository.writeWorkout(run.copy(finishedAt = START), ExerciseCategory.CARDIO))
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `gravar sem plataforma ou sem permissao`() = runTest {
        gateway.availability = HealthAvailability.NEEDS_PROVIDER_UPDATE
        assertEquals(WorkoutWriteResult.UNAVAILABLE, repository.writeWorkout(run, ExerciseCategory.MOBILITY))

        gateway.availability = HealthAvailability.AVAILABLE
        gateway.readGranted = mapOf(HealthDataType.EXERCISE_SESSION to false)
        assertEquals(WorkoutWriteResult.PERMISSION_DENIED, repository.writeWorkout(run, ExerciseCategory.MOBILITY))

        gateway.readGranted = mapOf(HealthDataType.EXERCISE_SESSION to null) // iOS: não informa
        gateway.writeGranted = false
        assertEquals(WorkoutWriteResult.PERMISSION_DENIED, repository.writeWorkout(run, ExerciseCategory.MOBILITY))

        gateway.writeGranted = true
        assertEquals(WorkoutWriteResult.WRITTEN, repository.writeWorkout(run, ExerciseCategory.MOBILITY))

        gateway.denyOn = "insert"
        assertEquals(WorkoutWriteResult.PERMISSION_DENIED, repository.writeWorkout(run, ExerciseCategory.MOBILITY))
    }

    @Test
    fun `falha da plataforma na gravacao chega como esta`() = runTest {
        gateway.insertResult = WorkoutWriteResult.FAILED
        assertEquals(WorkoutWriteResult.FAILED, repository.writeWorkout(run, ExerciseCategory.CIRCUIT))
    }
}
