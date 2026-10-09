package br.com.codecacto.kmplib.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import java.time.ZoneId
import kotlin.coroutines.resume

/** Conversão estável por época (ms) — `kotlinx.datetime.Instant` virou `typealias` de
 * `kotlin.time.Instant` na 0.7.1 e o caminho da extensão `toJavaInstant()` mudou entre versões. */
internal fun Instant.toJavaInstant(): java.time.Instant = java.time.Instant.ofEpochMilli(toEpochMilliseconds())

/**
 * Gateway Android via **Health Connect** (`androidx.health.connect:connect-client`, o substituto
 * oficial do Google Fit). Só tradução — a regra mora em [DefaultHealthRepository].
 *
 * - O cliente é obtido A CADA chamada depois de conferir `getSdkStatus`: o usuário pode instalar ou
 *   atualizar o Health Connect com o app aberto.
 * - `SecurityException` (permissão revogada no meio) vira [HealthPermissionException].
 * - A sessão gravada é `activelyRecorded` pelo telefone, com `clientRecordId = run.localId`: reenviar
 *   o mesmo treino SOBRESCREVE o registro em vez de duplicar (contrato do Health Connect para
 *   `clientRecordId` + `clientRecordVersion`).
 * - O pedido de permissão usa o `ActivityResultRegistry` direto (como o `PermissionManager` do
 *   `kmplib-platform`) — não precisa ter sido registrado antes do `onCreate`.
 */
internal class HealthConnectGateway(private val context: Context) : HealthStoreGateway {

    override fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_PROVIDER_UPDATE
        else -> HealthAvailability.NOT_SUPPORTED
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    override suspend fun requestPermissions(types: Set<HealthDataType>): Boolean {
        val wanted = types.flatMap(::permissionsFor).toSet()
        if (wanted.isEmpty()) return true
        if (granted().containsAll(wanted)) return true

        val activity = HealthActivityHolder.getActivity() ?: return false
        val contract: ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()

        val result = suspendCancellableCoroutine<Set<String>> { continuation ->
            val key = "kmplib_health_permission_${System.nanoTime()}"
            lateinit var launcher: androidx.activity.result.ActivityResultLauncher<Set<String>>
            launcher = activity.activityResultRegistry.register(key, contract) { grantedNow ->
                launcher.unregister()
                if (continuation.isActive) continuation.resume(grantedNow)
            }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(wanted)
        }
        return result.containsAll(wanted)
    }

    override suspend fun isReadGranted(type: HealthDataType): Boolean =
        granted().contains(readPermissionFor(type))

    override suspend fun isWorkoutWriteGranted(): Boolean =
        granted().contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    override suspend fun heartRateStats(start: Instant, end: Instant): HeartRateStats? = guarded {
        val result = client().aggregate(
            AggregateRequest(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX), range(start, end)),
        )
        val avg = result[HeartRateRecord.BPM_AVG]
        val max = result[HeartRateRecord.BPM_MAX]
        if (avg != null && max != null) HeartRateStats(avg.toDouble(), max.toDouble()) else null
    }

    override suspend fun activeEnergyKcal(start: Instant, end: Instant): Double? = guarded {
        client().aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range(start, end)))
            .get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            ?.inKilocalories
    }

    override suspend fun workoutsOverlapping(start: Instant, end: Instant): List<StoredWorkout> = guarded {
        val ownPackage = context.packageName
        val sessions = mutableListOf<StoredWorkout>()
        var pageToken: String? = null
        do {
            val response = client().readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, range(start, end), pageToken = pageToken),
            )
            response.records.mapTo(sessions) { record ->
                StoredWorkout(
                    fromThisApp = record.metadata.dataOrigin.packageName == ownPackage,
                    externalId = record.metadata.clientRecordId,
                )
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        sessions
    }

    override suspend fun insertWorkout(run: WorkoutRun, end: Instant, category: ExerciseCategory): WorkoutWriteResult {
        val zone = ZoneId.systemDefault().rules
        val startTime = run.startedAt.toJavaInstant()
        val endTime = end.toJavaInstant()
        val record = ExerciseSessionRecord(
            startTime = startTime,
            startZoneOffset = zone.getOffset(startTime),
            endTime = endTime,
            endZoneOffset = zone.getOffset(endTime),
            metadata = Metadata.activelyRecorded(
                device = Device(type = Device.TYPE_PHONE),
                clientRecordId = run.localId,
                clientRecordVersion = 1,
            ),
            exerciseType = exerciseTypeFor(category),
        )
        return try {
            client().insertRecords(listOf(record))
            WorkoutWriteResult.WRITTEN
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            WorkoutWriteResult.PERMISSION_DENIED
        } catch (_: Exception) {
            // RemoteException/IOException do serviço do Health Connect, limite de taxa. Sem log: a
            // mensagem da plataforma pode carregar o registro.
            WorkoutWriteResult.FAILED
        }
    }

    private suspend fun granted(): Set<String> = client().permissionController.getGrantedPermissions()

    private fun range(start: Instant, end: Instant) = TimeRangeFilter.between(start.toJavaInstant(), end.toJavaInstant())

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (_: SecurityException) {
        throw HealthPermissionException()
    }

    private fun readPermissionFor(type: HealthDataType): String = when (type) {
        HealthDataType.HEART_RATE -> HealthPermission.getReadPermission(HeartRateRecord::class)
        HealthDataType.ACTIVE_ENERGY -> HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        HealthDataType.EXERCISE_SESSION -> HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    }

    private fun permissionsFor(type: HealthDataType): Set<String> = when (type) {
        HealthDataType.EXERCISE_SESSION -> setOf(
            readPermissionFor(type),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
        else -> setOf(readPermissionFor(type))
    }
}

/** Categoria -> `ExerciseSessionRecord.EXERCISE_TYPE_*`. Os nomes batem com
 * `HealthPlatformMapping.healthConnectExerciseTypeName` (conferido em teste). */
internal fun exerciseTypeFor(category: ExerciseCategory): Int = when (category) {
    ExerciseCategory.STRENGTH_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
    ExerciseCategory.CARDIO -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    ExerciseCategory.MOBILITY -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
    ExerciseCategory.CIRCUIT, ExerciseCategory.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
}

actual fun createHealthRepository(): HealthRepository =
    DefaultHealthRepository(HealthConnectGateway(HealthContextHolder.requireContext()))
