package br.com.codecacto.kmplib.health

import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

/**
 * Porta fina para o repositório de saúde da plataforma: só traduz chamada, sem regra. Toda a regra
 * (estado "sem dado", guarda de escrita, idempotência) fica em [DefaultHealthRepository], em
 * `commonMain`, testada com dublê. Falta de permissão sobe como [HealthPermissionException].
 */
internal interface HealthStoreGateway {
    fun availability(): HealthAvailability

    suspend fun requestPermissions(types: Set<HealthDataType>): Boolean

    /** `null` = a plataforma não informa (leitura no HealthKit). */
    suspend fun isReadGranted(type: HealthDataType): Boolean?

    suspend fun isWorkoutWriteGranted(): Boolean

    /** Estado da LEITURA do tipo, sem diálogo. Só `GRANTED`/`NOT_GRANTED` (Android) ou
     * `NOT_REQUESTED`/`NOT_REVEALED` (iOS). */
    suspend fun readPermissionState(type: HealthDataType): HealthPermissionState

    /** Estado da ESCRITA de treino, sem diálogo. `GRANTED`/`NOT_GRANTED`/`NOT_REQUESTED`. */
    suspend fun workoutWritePermissionState(): HealthPermissionState

    /** `null` = nenhuma amostra no intervalo. */
    suspend fun heartRateStats(start: Instant, end: Instant): HeartRateStats?

    /** `null` = nenhuma amostra no intervalo. */
    suspend fun activeEnergyKcal(start: Instant, end: Instant): Double?

    /** Sessões de exercício que cruzam o intervalo, de qualquer origem. */
    suspend fun workoutsOverlapping(start: Instant, end: Instant): List<StoredWorkout>

    /** Grava a sessão marcada com `run.localId` como id externo. */
    suspend fun insertWorkout(run: WorkoutRun, end: Instant, category: ExerciseCategory): WorkoutWriteResult
}

internal data class HeartRateStats(val avg: Double, val max: Double) {
    override fun toString(): String = "HeartRateStats(<redigido>)"
}

/** Uma sessão já gravada: se veio DESTE app e, se sim, com qual id externo (`WorkoutRun.localId`). */
internal data class StoredWorkout(val fromThisApp: Boolean, val externalId: String?) {
    override fun toString(): String = "StoredWorkout(fromThisApp=$fromThisApp, hasExternalId=${externalId != null})"
}

/** Recusa por permissão vinda da plataforma (Android: `SecurityException`). Mensagem sem dado. */
internal class HealthPermissionException : Exception("health permission denied")

internal class DefaultHealthRepository(private val gateway: HealthStoreGateway) : HealthRepository {

    override suspend fun availability(): HealthAvailability = gateway.availability()

    override suspend fun requestPermissions(types: Set<HealthDataType>): Boolean {
        if (types.isEmpty()) return true
        if (gateway.availability() != HealthAvailability.AVAILABLE) return false
        return permissionSafe(false) { gateway.requestPermissions(types) }
    }

    override suspend fun permissionStatus(types: Set<HealthDataType>): HealthPermissionStatus {
        val availability = gateway.availability()
        if (availability != HealthAvailability.AVAILABLE) {
            return HealthPermissionStatus(availability, types.associateWith { HealthPermissionState.UNAVAILABLE })
        }
        val byType = types.associateWith { type ->
            permissionSafe(HealthPermissionState.NOT_GRANTED) {
                val read = gateway.readPermissionState(type)
                if (type == HealthDataType.EXERCISE_SESSION) {
                    combineHealthPermissionStates(listOf(read, gateway.workoutWritePermissionState()))
                } else {
                    read
                }
            }
        }
        return HealthPermissionStatus(availability, byType)
    }

    override suspend fun readSessionMetrics(start: Instant, end: Instant): SessionMetrics {
        require(end > start) { "intervalo invertido ou vazio" }
        if (gateway.availability() != HealthAvailability.AVAILABLE) {
            return SessionMetrics(HealthMetric.Unavailable, HealthMetric.Unavailable)
        }
        val heartRate = readMetric(HealthDataType.HEART_RATE) {
            gateway.heartRateStats(start, end)?.let { HeartRateSummary(it.avg.roundToInt(), it.max.roundToInt()) }
        }
        val energy = readMetric(HealthDataType.ACTIVE_ENERGY) {
            gateway.activeEnergyKcal(start, end)?.let { EnergyReading(it) }
        }
        return SessionMetrics(heartRate, energy)
    }

    override suspend fun hasDeviceWorkoutOverlapping(start: Instant, end: Instant): Boolean {
        require(end > start) { "intervalo invertido ou vazio" }
        if (gateway.availability() != HealthAvailability.AVAILABLE) return false
        if (gateway.isReadGranted(HealthDataType.EXERCISE_SESSION) == false) return false
        return permissionSafe(false) { gateway.workoutsOverlapping(start, end).any { !it.fromThisApp } }
    }

    override suspend fun writeWorkout(run: WorkoutRun, category: ExerciseCategory): WorkoutWriteResult {
        val end = run.finishedAt ?: return WorkoutWriteResult.RUN_NOT_FINISHED
        if (end <= run.startedAt) return WorkoutWriteResult.INVALID_RUN
        if (gateway.availability() != HealthAvailability.AVAILABLE) return WorkoutWriteResult.UNAVAILABLE
        if (gateway.isReadGranted(HealthDataType.EXERCISE_SESSION) == false) return WorkoutWriteResult.PERMISSION_DENIED
        if (!gateway.isWorkoutWriteGranted()) return WorkoutWriteResult.PERMISSION_DENIED

        return permissionSafe(WorkoutWriteResult.PERMISSION_DENIED) {
            val existing = gateway.workoutsOverlapping(run.startedAt, end)
            when {
                existing.any { it.fromThisApp && it.externalId == run.localId } -> WorkoutWriteResult.ALREADY_WRITTEN
                existing.any { !it.fromThisApp } -> WorkoutWriteResult.SKIPPED_DEVICE_WORKOUT_EXISTS
                else -> gateway.insertWorkout(run, end, category)
            }
        }
    }

    private suspend fun <T> readMetric(type: HealthDataType, read: suspend () -> T?): HealthMetric<T> {
        if (gateway.isReadGranted(type) == false) return HealthMetric.Unavailable
        return permissionSafe<HealthMetric<T>>(HealthMetric.Unavailable) {
            read()?.let { HealthMetric.Value(it) } ?: HealthMetric.NoData
        }
    }

    private suspend fun <T> permissionSafe(onDenied: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: HealthPermissionException) {
        onDenied
    }
}
