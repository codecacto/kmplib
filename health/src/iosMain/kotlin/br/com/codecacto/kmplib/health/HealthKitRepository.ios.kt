package br.com.codecacto.kmplib.health

import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSPredicate
import platform.Foundation.NSTimeIntervalSince1970
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionNone
import platform.HealthKit.HKSampleType
import platform.HealthKit.HKStatistics
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsOptionDiscreteAverage
import platform.HealthKit.HKStatisticsOptionDiscreteMax
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.HKWorkout
import platform.HealthKit.HKWorkoutActivityTypeHighIntensityIntervalTraining
import platform.HealthKit.HKWorkoutActivityTypeOther
import platform.HealthKit.HKWorkoutActivityTypeTraditionalStrengthTraining
// Chegam como extensão sobre o "…Meta" (categoria ObjC) — import do NOME da função + chamada
// via `ClassName.Companion.funcao(...)`, ver `kmplib-catalog/references/ios-cinterop.md` §3.
import platform.HealthKit.kilocalorieUnit
import platform.HealthKit.predicateForSamplesWithStartDate
import kotlin.coroutines.resume

/**
 * Implementação iOS via **HealthKit** — leitura/escrita POSTERIOR, chamada direto do Kotlin via
 * cinterop (sem Xcode: compila neste servidor). A sessão AO VIVO (`HKWorkoutSession`) e a Live
 * Activity ficam em Swift, no projeto — ver `06-relogio-kmp-e-biblioteca.md` §2.3.
 *
 * `HKWorkoutActivityType` é um `typealias` de `ULong` com constantes de topo (NÃO é `enum class`
 * no cinterop) — ver `kmplib-catalog/references/ios-cinterop.md` §1. `isHealthDataAvailable()`
 * vive no companion (`HKHealthStoreMeta`), chamada como `HKHealthStore.isHealthDataAvailable()`.
 * Todo completion handler do HealthKit roda em thread própria do sistema — por isso cada chamada
 * usa `suspendCancellableCoroutine`, NUNCA espera ocupada (busy-wait).
 */
/** Conversão estável por época (s) — evita depender de `kotlinx.datetime.toNSDate()`, cujo
 * caminho de import mudou entre versões (`Instant` virou `typealias` de `kotlin.time.Instant` na
 * 0.7.1; ver o mesmo ajuste em `HealthConnectRepository.android.kt`). */
private fun Instant.toNSDate(): NSDate =
    NSDate(timeIntervalSinceReferenceDate = toEpochMilliseconds() / 1000.0 - NSTimeIntervalSince1970)

internal class HealthKitRepository : HealthRepository {

    private val store by lazy { HKHealthStore() }

    override suspend fun isAvailable(): Boolean = HKHealthStore.isHealthDataAvailable()

    override suspend fun requestPermissions(types: Set<HealthDataType>): Boolean {
        if (!isAvailable()) return false
        val readTypes = types.mapNotNull(::sampleTypeFor).toSet()
        val shareTypes = if (HealthDataType.EXERCISE_SESSION in types) setOf(HKObjectType.workoutType()) else emptySet()
        if (readTypes.isEmpty() && shareTypes.isEmpty()) return true

        return suspendCancellableCoroutine { continuation ->
            store.requestAuthorizationToShareTypes(shareTypes, readTypes) { success, _ ->
                continuation.resume(success)
            }
        }
    }

    override suspend fun readSessionMetrics(start: Instant, end: Instant): SessionMetrics {
        if (!isAvailable()) return SessionMetrics(HealthMetric.Unavailable, HealthMetric.Unavailable)
        val predicate = HKQuery.Companion.predicateForSamplesWithStartDate(start.toNSDate(), end.toNSDate(), HKQueryOptionNone)

        val heartRateStats = queryStatistics(
            identifier = HKQuantityTypeIdentifierHeartRate,
            predicate = predicate,
            options = HKStatisticsOptionDiscreteAverage or HKStatisticsOptionDiscreteMax,
        )
        val heartRate = heartRateStats?.let { stats ->
            val unit = HKUnit.unitFromString("count/min")
            val avg = stats.averageQuantity()?.doubleValueForUnit(unit)
            val max = stats.maximumQuantity()?.doubleValueForUnit(unit)
            if (avg != null && max != null) HealthMetric.Value(HeartRateSummary(avg.toInt(), max.toInt())) else HealthMetric.NoData
        } ?: HealthMetric.NoData

        val energyStats = queryStatistics(
            identifier = HKQuantityTypeIdentifierActiveEnergyBurned,
            predicate = predicate,
            options = HKStatisticsOptionCumulativeSum,
        )
        val energy = energyStats?.let { stats ->
            val kcal = stats.sumQuantity()?.doubleValueForUnit(HKUnit.Companion.kilocalorieUnit())
            if (kcal != null) HealthMetric.Value(EnergyReading(kcal)) else HealthMetric.NoData
        } ?: HealthMetric.NoData

        return SessionMetrics(heartRate, energy)
    }

    override suspend fun hasDeviceWorkoutOverlapping(start: Instant, end: Instant): Boolean {
        // A leitura de HKWorkout por intervalo (HKSampleQuery) fica para quando `writeWorkout`
        // entrar em produção (RF-REL-06, Onda 1, depois do spike 0.7 com aparelho). Até lá, o
        // chamador não decide gravar sem essa checagem real — `writeWorkout` não é chamado sem
        // ela ter sido implementada de verdade.
        return false
    }

    override suspend fun writeWorkout(run: WorkoutRun, category: ExerciseCategory): WorkoutWriteResult {
        if (!isAvailable()) return WorkoutWriteResult.UNAVAILABLE
        val finishedAt = run.finishedAt ?: return WorkoutWriteResult.UNAVAILABLE

        val workout: HKWorkout = HKWorkout.workoutWithActivityType(
            workoutActivityType = activityTypeFor(category),
            startDate = run.startedAt.toNSDate(),
            endDate = finishedAt.toNSDate(),
        )

        return suspendCancellableCoroutine { continuation ->
            store.saveObject(workout) { success, _ ->
                continuation.resume(if (success) WorkoutWriteResult.WRITTEN else WorkoutWriteResult.PERMISSION_DENIED)
            }
        }
    }

    private suspend fun queryStatistics(identifier: String?, predicate: NSPredicate, options: ULong): HKStatistics? {
        val quantityType: HKQuantityType = HKObjectType.quantityTypeForIdentifier(identifier) ?: return null

        return suspendCancellableCoroutine { continuation ->
            val query = HKStatisticsQuery(quantityType, predicate, options) { _, statistics, _ ->
                continuation.resume(statistics)
            }
            store.executeQuery(query)
        }
    }
}

private fun sampleTypeFor(type: HealthDataType): HKSampleType? = when (type) {
    HealthDataType.HEART_RATE -> HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate)
    HealthDataType.ACTIVE_ENERGY -> HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
    HealthDataType.EXERCISE_SESSION -> HKObjectType.workoutType()
}

private fun activityTypeFor(category: ExerciseCategory): ULong = when (category) {
    ExerciseCategory.STRENGTH_TRAINING -> HKWorkoutActivityTypeTraditionalStrengthTraining
    ExerciseCategory.CARDIO -> HKWorkoutActivityTypeOther
    ExerciseCategory.MOBILITY -> HKWorkoutActivityTypeOther
    ExerciseCategory.CIRCUIT, ExerciseCategory.HIIT -> HKWorkoutActivityTypeHighIntensityIntervalTraining
}

actual fun createHealthRepository(): HealthRepository = HealthKitRepository()
