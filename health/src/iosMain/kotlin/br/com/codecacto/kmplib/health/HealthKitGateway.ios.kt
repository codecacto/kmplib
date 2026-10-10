package br.com.codecacto.kmplib.health

import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSPredicate
import platform.Foundation.NSTimeIntervalSince1970
import platform.HealthKit.HKAuthorizationRequestStatusShouldRequest
import platform.HealthKit.HKAuthorizationStatusNotDetermined
import platform.HealthKit.HKAuthorizationStatusSharingAuthorized
import platform.HealthKit.HKAuthorizationStatusSharingDenied
import platform.HealthKit.HKDevice
import platform.HealthKit.HKErrorAuthorizationDenied
import platform.HealthKit.HKErrorAuthorizationNotDetermined
import platform.HealthKit.HKErrorDomain
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKMetadataKeyExternalUUID
import platform.HealthKit.HKObjectQueryNoLimit
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionNone
import platform.HealthKit.HKSample
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKSampleType
import platform.HealthKit.HKSource
import platform.HealthKit.HKStatistics
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsOptionDiscreteAverage
import platform.HealthKit.HKStatisticsOptionDiscreteMax
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.HKWorkoutActivityTypeFlexibility
import platform.HealthKit.HKWorkoutActivityTypeHighIntensityIntervalTraining
import platform.HealthKit.HKWorkoutActivityTypeMixedCardio
import platform.HealthKit.HKWorkoutActivityTypeTraditionalStrengthTraining
import platform.HealthKit.HKWorkoutBuilder
import platform.HealthKit.HKWorkoutConfiguration
import platform.HealthKit.HKWorkoutSessionLocationTypeIndoor
// Métodos de classe chegam como extensão sobre o "…Meta" (categoria ObjC) — import do NOME da função +
// chamada via `Classe.Companion.funcao(...)`, ver `kmplib-catalog/references/ios-cinterop.md` §3.
import platform.HealthKit.kilocalorieUnit
import platform.HealthKit.predicateForSamplesWithStartDate
import kotlin.coroutines.resume

/** Conversão estável por época — `kotlinx.datetime.Instant` virou `typealias` de `kotlin.time.Instant`
 * na 0.7.1 e o caminho do `toNSDate()` mudou entre versões. */
private fun Instant.toNSDate(): NSDate =
    NSDate(timeIntervalSinceReferenceDate = toEpochMilliseconds() / 1000.0 - NSTimeIntervalSince1970)

/**
 * Gateway iOS via **HealthKit**, por cinterop (compila neste servidor, sem Swift). Leitura e escrita
 * POSTERIOR; a sessão AO VIVO (`HKWorkoutSession`, iOS 26 no iPhone) e a Live Activity ficam em Swift,
 * no projeto (`06-relogio-kmp-e-biblioteca.md` §2.3).
 *
 * - Escrita pelo `HKWorkoutBuilder` (o recomendado; `HKWorkout(activityType:start:end:)` está
 *   depreciado desde o iOS 17), marcada com `HKMetadataKeyExternalUUID = run.localId` e o
 *   `HKDevice.local()` — é por essa chave que um reenvio vira [WorkoutWriteResult.ALREADY_WRITTEN].
 * - Origem: um treino é "deste app" quando `sourceRevision.source.bundleIdentifier` é o do
 *   `HKSource.default()`.
 * - Todo completion handler do HealthKit roda em thread própria do sistema — cada chamada é um
 *   `suspendCancellableCoroutine`; consulta cancelada é parada com `stopQuery`.
 * - A LEITURA não tem como ser conferida (privacidade da Apple): [isReadGranted] devolve `null`.
 */
internal class HealthKitGateway : HealthStoreGateway {

    private val store by lazy { HKHealthStore() }

    override fun availability(): HealthAvailability =
        if (HKHealthStore.isHealthDataAvailable()) HealthAvailability.AVAILABLE else HealthAvailability.NOT_SUPPORTED

    override suspend fun requestPermissions(types: Set<HealthDataType>): Boolean {
        val readTypes = types.mapNotNull(::sampleTypeFor).toSet()
        val shareTypes: Set<HKSampleType> =
            if (HealthDataType.EXERCISE_SESSION in types) setOf(HKObjectType.workoutType()) else emptySet()
        if (readTypes.isEmpty() && shareTypes.isEmpty()) return true

        val completed = suspendCancellableCoroutine { continuation ->
            store.requestAuthorizationToShareTypes(shareTypes, readTypes) { success, _ ->
                if (continuation.isActive) continuation.resume(success)
            }
        }
        return completed && (shareTypes.isEmpty() || isWorkoutWriteGranted())
    }

    override suspend fun isReadGranted(type: HealthDataType): Boolean? = null

    override suspend fun isWorkoutWriteGranted(): Boolean =
        store.authorizationStatusForType(HKObjectType.workoutType()) == HKAuthorizationStatusSharingAuthorized

    /**
     * A Apple não revela a resposta de LEITURA. O que dá para saber, pela API oficial, é se a folha
     * AINDA seria mostrada: `getRequestStatusForAuthorization(toShare: [], read: [tipo])` →
     * `.shouldRequest` = nunca perguntado; `.unnecessary` = já respondido (sim ou não, oculto).
     * `.unknown`/erro também viram `NOT_REVEALED` — "não sei", nunca negado inventado.
     */
    override suspend fun readPermissionState(type: HealthDataType): HealthPermissionState {
        val sampleType = sampleTypeFor(type) ?: return HealthPermissionState.NOT_REVEALED
        val status = suspendCancellableCoroutine { continuation ->
            store.getRequestStatusForAuthorizationToShareTypes(emptySet<HKSampleType>(), setOf(sampleType)) { status, _ ->
                if (continuation.isActive) continuation.resume(status)
            }
        }
        return if (status == HKAuthorizationRequestStatusShouldRequest) {
            HealthPermissionState.NOT_REQUESTED
        } else {
            HealthPermissionState.NOT_REVEALED
        }
    }

    /** Escrita o HealthKit informa (`authorizationStatus(for:)`). */
    override suspend fun workoutWritePermissionState(): HealthPermissionState =
        when (store.authorizationStatusForType(HKObjectType.workoutType())) {
            HKAuthorizationStatusSharingAuthorized -> HealthPermissionState.GRANTED
            HKAuthorizationStatusSharingDenied -> HealthPermissionState.NOT_GRANTED
            HKAuthorizationStatusNotDetermined -> HealthPermissionState.NOT_REQUESTED
            else -> HealthPermissionState.NOT_REVEALED
        }

    override suspend fun heartRateStats(start: Instant, end: Instant): HeartRateStats? {
        val stats = queryStatistics(
            identifier = HKQuantityTypeIdentifierHeartRate,
            predicate = intervalPredicate(start, end),
            options = HKStatisticsOptionDiscreteAverage or HKStatisticsOptionDiscreteMax,
        ) ?: return null
        val unit = HKUnit.unitFromString("count/min")
        val avg = stats.averageQuantity()?.doubleValueForUnit(unit) ?: return null
        val max = stats.maximumQuantity()?.doubleValueForUnit(unit) ?: return null
        return HeartRateStats(avg, max)
    }

    override suspend fun activeEnergyKcal(start: Instant, end: Instant): Double? =
        queryStatistics(
            identifier = HKQuantityTypeIdentifierActiveEnergyBurned,
            predicate = intervalPredicate(start, end),
            options = HKStatisticsOptionCumulativeSum,
        )?.sumQuantity()?.doubleValueForUnit(HKUnit.Companion.kilocalorieUnit())

    override suspend fun workoutsOverlapping(start: Instant, end: Instant): List<StoredWorkout> {
        val ownBundle = HKSource.defaultSource().bundleIdentifier
        val samples: List<HKSample> = suspendCancellableCoroutine { continuation ->
            val query = HKSampleQuery(
                sampleType = HKObjectType.workoutType(),
                predicate = intervalPredicate(start, end),
                limit = HKObjectQueryNoLimit,
                sortDescriptors = null,
            ) { _, results, _ ->
                if (continuation.isActive) continuation.resume(results.orEmpty().filterIsInstance<HKSample>())
            }
            continuation.invokeOnCancellation { store.stopQuery(query) }
            store.executeQuery(query)
        }
        return samples.map { sample ->
            StoredWorkout(
                fromThisApp = sample.sourceRevision.source.bundleIdentifier == ownBundle,
                externalId = sample.metadata?.get(HKMetadataKeyExternalUUID) as? String,
            )
        }
    }

    override suspend fun insertWorkout(run: WorkoutRun, end: Instant, category: ExerciseCategory): WorkoutWriteResult {
        val configuration = HKWorkoutConfiguration().apply {
            activityType = activityTypeFor(category)
            locationType = HKWorkoutSessionLocationTypeIndoor
        }
        val builder = HKWorkoutBuilder(store, configuration, HKDevice.localDevice())

        step { done -> builder.beginCollectionWithStartDate(run.startedAt.toNSDate(), done) }?.let { return it }
        step { done -> builder.addMetadata(mapOf<Any?, Any>(HKMetadataKeyExternalUUID to run.localId), done) }
            ?.let { builder.discardWorkout(); return it }
        step { done -> builder.endCollectionWithEndDate(end.toNSDate(), done) }
            ?.let { builder.discardWorkout(); return it }

        return suspendCancellableCoroutine { continuation ->
            builder.finishWorkoutWithCompletion { workout, error ->
                val result = when {
                    workout != null -> WorkoutWriteResult.WRITTEN
                    else -> error.toWriteFailure()
                }
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    /** Um passo do builder; `null` = deu certo, senão o resultado de falha. */
    private suspend fun step(call: (done: (Boolean, NSError?) -> Unit) -> Unit): WorkoutWriteResult? =
        suspendCancellableCoroutine { continuation ->
            call { success, error ->
                if (continuation.isActive) continuation.resume(if (success) null else error.toWriteFailure())
            }
        }

    private fun NSError?.toWriteFailure(): WorkoutWriteResult {
        val denied = this != null && domain == HKErrorDomain &&
            (code == HKErrorAuthorizationDenied || code == HKErrorAuthorizationNotDetermined)
        return if (denied) WorkoutWriteResult.PERMISSION_DENIED else WorkoutWriteResult.FAILED
    }

    /** Sem `strictStartDate`/`strictEndDate`: casa toda amostra que CRUZA o intervalo. */
    private fun intervalPredicate(start: Instant, end: Instant): NSPredicate =
        HKQuery.Companion.predicateForSamplesWithStartDate(start.toNSDate(), end.toNSDate(), HKQueryOptionNone)

    private suspend fun queryStatistics(identifier: String?, predicate: NSPredicate, options: ULong): HKStatistics? {
        val quantityType: HKQuantityType = HKObjectType.quantityTypeForIdentifier(identifier) ?: return null
        return suspendCancellableCoroutine { continuation ->
            // Sem amostra no intervalo o HealthKit devolve erro `HKErrorNoData` e `statistics` nulo:
            // vira `null` aqui, que o repositório transforma em `HealthMetric.NoData`.
            val query = HKStatisticsQuery(quantityType, predicate, options) { _, statistics, _ ->
                if (continuation.isActive) continuation.resume(statistics)
            }
            continuation.invokeOnCancellation { store.stopQuery(query) }
            store.executeQuery(query)
        }
    }
}

private fun sampleTypeFor(type: HealthDataType): HKSampleType? = when (type) {
    HealthDataType.HEART_RATE -> HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate)
    HealthDataType.ACTIVE_ENERGY -> HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)
    HealthDataType.EXERCISE_SESSION -> HKObjectType.workoutType()
}

/** Categoria -> `HKWorkoutActivityType`, os mesmos de `HealthPlatformMapping.healthKitActivityTypeName`. */
private fun activityTypeFor(category: ExerciseCategory): ULong = when (category) {
    ExerciseCategory.STRENGTH_TRAINING -> HKWorkoutActivityTypeTraditionalStrengthTraining
    ExerciseCategory.CARDIO -> HKWorkoutActivityTypeMixedCardio
    ExerciseCategory.MOBILITY -> HKWorkoutActivityTypeFlexibility
    ExerciseCategory.CIRCUIT, ExerciseCategory.HIIT -> HKWorkoutActivityTypeHighIntensityIntervalTraining
}

actual fun createHealthRepository(): HealthRepository = DefaultHealthRepository(HealthKitGateway())
