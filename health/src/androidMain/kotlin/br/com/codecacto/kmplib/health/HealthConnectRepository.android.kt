package br.com.codecacto.kmplib.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import kotlin.coroutines.resume

/**
 * Implementação Android via **Health Connect** (`androidx.health.connect:connect-client`,
 * substituto oficial do Google Fit). Guia de API: `kmplib-catalog/references/health.md`.
 *
 * O pedido de permissão usa o `ActivityResultRegistry` direto (como o
 * `PermissionManager.android.kt` do `kmplib-platform`) — não precisa ser registrado antes do
 * `onCreate` chegar a `STARTED`, ao contrário de `ComponentActivity.registerForActivityResult`.
 */
/** Conversão estável por época (ms) — evita depender da extensão `toJavaInstant()` da stdlib/
 * kotlinx-datetime, cujo caminho de import mudou entre versões (`kotlinx.datetime.Instant` virou
 * `typealias` de `kotlin.time.Instant` na 0.7.1). */
private fun Instant.toJavaInstant(): java.time.Instant = java.time.Instant.ofEpochMilli(toEpochMilliseconds())

internal class HealthConnectRepository(private val context: Context) : HealthRepository {

    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    override suspend fun isAvailable(): Boolean = client != null

    override suspend fun requestPermissions(types: Set<HealthDataType>): Boolean {
        val healthConnect = client ?: return false
        val wanted = types.flatMap(::permissionsFor).toSet()
        if (wanted.isEmpty()) return true

        val granted = healthConnect.permissionController.getGrantedPermissions()
        if (granted.containsAll(wanted)) return true

        val activity = HealthActivityHolder.getActivity() ?: return false
        val contract: ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()

        val result = suspendCancellableCoroutine<Set<String>> { continuation ->
            val key = "kmplib_health_permission_${System.nanoTime()}"
            val launcher = activity.activityResultRegistry.register(key, contract) { grantedNow ->
                continuation.resume(grantedNow)
            }
            launcher.launch(wanted)
            continuation.invokeOnCancellation { launcher.unregister() }
        }
        return result.containsAll(wanted)
    }

    override suspend fun readSessionMetrics(start: Instant, end: Instant): SessionMetrics {
        val healthConnect = client ?: return SessionMetrics(HealthMetric.Unavailable, HealthMetric.Unavailable)
        val range = TimeRangeFilter.between(start.toJavaInstant(), end.toJavaInstant())

        val result = healthConnect.aggregate(
            AggregateRequest(
                metrics = setOf(
                    HeartRateRecord.BPM_AVG,
                    HeartRateRecord.BPM_MAX,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                ),
                timeRangeFilter = range,
            ),
        )

        val heartRate = if (result.contains(HeartRateRecord.BPM_AVG) && result.contains(HeartRateRecord.BPM_MAX)) {
            val avg = result.get(HeartRateRecord.BPM_AVG)
            val max = result.get(HeartRateRecord.BPM_MAX)
            if (avg != null && max != null) {
                HealthMetric.Value(HeartRateSummary(avg.toInt(), max.toInt()))
            } else {
                HealthMetric.NoData
            }
        } else {
            HealthMetric.NoData
        }

        val energy = if (result.contains(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)) {
            val kcal = result.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories
            if (kcal != null) HealthMetric.Value(EnergyReading(kcal)) else HealthMetric.NoData
        } else {
            HealthMetric.NoData
        }

        return SessionMetrics(heartRate, energy)
    }

    override suspend fun hasDeviceWorkoutOverlapping(start: Instant, end: Instant): Boolean {
        val healthConnect = client ?: return false
        val range = TimeRangeFilter.between(start.toJavaInstant(), end.toJavaInstant())
        val response = healthConnect.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range))
        return response.records.isNotEmpty()
    }

    override suspend fun writeWorkout(run: WorkoutRun, category: ExerciseCategory): WorkoutWriteResult {
        val healthConnect = client ?: return WorkoutWriteResult.UNAVAILABLE
        val finishedAt = run.finishedAt ?: return WorkoutWriteResult.UNAVAILABLE

        val record = ExerciseSessionRecord(
            startTime = run.startedAt.toJavaInstant(),
            startZoneOffset = null,
            endTime = finishedAt.toJavaInstant(),
            endZoneOffset = null,
            exerciseType = exerciseTypeFor(category),
            metadata = Metadata.manualEntry(),
        )
        healthConnect.insertRecords(listOf(record))
        return WorkoutWriteResult.WRITTEN
    }

    private fun permissionsFor(type: HealthDataType): Set<String> = when (type) {
        HealthDataType.HEART_RATE -> setOf(HealthPermission.getReadPermission(HeartRateRecord::class))
        HealthDataType.ACTIVE_ENERGY -> setOf(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
        HealthDataType.EXERCISE_SESSION -> setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
    }

    private fun exerciseTypeFor(category: ExerciseCategory): Int = when (category) {
        ExerciseCategory.STRENGTH_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        ExerciseCategory.CARDIO -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        ExerciseCategory.MOBILITY -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
        ExerciseCategory.CIRCUIT, ExerciseCategory.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
    }
}

actual fun createHealthRepository(): HealthRepository {
    val context = HealthContextHolder.applicationContext
        ?: error("kmplib-health: chame initKmpLibHealth(context) no Application.onCreate() antes de usar a lib.")
    return HealthConnectRepository(context)
}
