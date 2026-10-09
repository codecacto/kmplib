package br.com.codecacto.kmplib.health

import br.com.codecacto.kmplib.workout.energy.EnergySource
import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.datetime.Instant

/** Tipo de dado de saúde pedido na permissão — cada um some/aparece sozinho (Health Connect e
 * HealthKit são granulares por tipo), nunca "tudo ou nada". */
enum class HealthDataType { HEART_RATE, ACTIVE_ENERGY, EXERCISE_SESSION }

/**
 * "Sem dado ainda" é um ESTADO, nunca zero — a tela mostra "aguardando dados do seu relógio",
 * não "0 kcal". `Unavailable` é a plataforma sem suporte ou sem permissão; `NoData` é suportado e
 * permitido, mas nenhuma amostra caiu no intervalo pedido (a sincronização do relógio não é
 * instantânea).
 */
sealed interface HealthMetric<out T> {
    data object Unavailable : HealthMetric<Nothing>

    data object NoData : HealthMetric<Nothing>

    data class Value<T>(val value: T) : HealthMetric<T>
}

data class HeartRateSummary(val avg: Int, val max: Int)

/** `source` é sempre [EnergySource.DEVICE] aqui — é o valor que JÁ está gravado no repositório de
 * saúde, nunca a nossa estimativa (que mora em `kmplib-workout.energy.CalorieEstimator` e NUNCA é
 * gravada de volta como medição, Apple 5.1.3(ii)). */
data class EnergyReading(val kcal: Double, val source: EnergySource = EnergySource.DEVICE)

data class SessionMetrics(
    val heartRate: HealthMetric<HeartRateSummary>,
    val energy: HealthMetric<EnergyReading>,
)

enum class WorkoutWriteResult {
    WRITTEN,
    SKIPPED_DEVICE_WORKOUT_EXISTS,
    UNAVAILABLE,
    PERMISSION_DENIED,
}

/**
 * Leitura/escrita no repositório de saúde da plataforma — Health Connect no Android, HealthKit no
 * iOS. Implementações em `androidMain`/`iosMain`; sem watchOS (a sessão ao vivo do relógio é nativa
 * e fica no projeto, ver `06-relogio-kmp-e-biblioteca.md` §2.3).
 *
 * **Contrato que não promete o que a plataforma não dá:** nunca lança por falta de dado — devolve
 * [HealthMetric.Unavailable]/[HealthMetric.NoData]. Quem decide o que mostrar é a tela.
 */
interface HealthRepository {
    /** Repositório existe no aparelho (Health Connect instalado/suportado; HealthKit sempre
     * disponível no iOS exceto iPad). Não diz nada sobre PERMISSÃO — ver [requestPermissions]. */
    suspend fun isAvailable(): Boolean

    /** Pede ao usuário os tipos informados. Devolve `true` só se TODOS foram concedidos — a tela
     * decide o que fazer com concessão parcial (ex.: seguir sem FC, mas avisar). */
    suspend fun requestPermissions(types: Set<HealthDataType>): Boolean

    /** FC média/máxima e energia no intervalo exato do treino. **Nunca soma** o treino do relógio
     * com o nosso: lê só as amostras daquele intervalo, não "o treino dele + o nosso" (05 §6.2). */
    suspend fun readSessionMetrics(start: Instant, end: Instant): SessionMetrics

    /** `true` quando já existe uma sessão de exercício do PRÓPRIO relógio cobrindo o intervalo —
     * sinal para [writeWorkout] não duplicar (RF-REL-06). */
    suspend fun hasDeviceWorkoutOverlapping(start: Instant, end: Instant): Boolean

    /** Grava o nosso treino como sessão de exercício, SÓ quando [hasDeviceWorkoutOverlapping] for
     * `false` para o mesmo intervalo — o chamador decide isso antes de chamar (este método não
     * confere de novo, para não duplicar a consulta). */
    suspend fun writeWorkout(run: WorkoutRun, category: ExerciseCategory): WorkoutWriteResult
}

expect fun createHealthRepository(): HealthRepository
