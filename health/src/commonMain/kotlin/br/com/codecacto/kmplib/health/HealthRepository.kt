package br.com.codecacto.kmplib.health

import br.com.codecacto.kmplib.workout.energy.EnergySource
import br.com.codecacto.kmplib.workout.mapping.ExerciseCategory
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.datetime.Instant

/** Tipo de dado de saúde pedido na permissão — cada um some/aparece sozinho (Health Connect e
 * HealthKit são granulares por tipo), nunca "tudo ou nada". `EXERCISE_SESSION` pede LEITURA e
 * ESCRITA de treino: ler é o que impede gravar por cima do treino do relógio. */
enum class HealthDataType { HEART_RATE, ACTIVE_ENERGY, EXERCISE_SESSION }

/**
 * Se o repositório de saúde existe no aparelho.
 * - [AVAILABLE]: pronto (permissão é outra pergunta — [HealthRepository.requestPermissions]).
 * - [NEEDS_PROVIDER_UPDATE]: Android com o Health Connect ausente ou desatualizado
 *   (`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`). O caminho oficial é levar à Play Store:
 *   `market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding`.
 * - [NOT_SUPPORTED]: o aparelho não tem (Android sem suporte, iPad sem app Saúde).
 */
enum class HealthAvailability { AVAILABLE, NEEDS_PROVIDER_UPDATE, NOT_SUPPORTED }

/**
 * "Sem dado ainda" é um ESTADO, nunca zero — a tela mostra "aguardando dados do seu relógio",
 * não "0 kcal". `Unavailable` é a plataforma sem suporte ou sem permissão; `NoData` é suportado e
 * permitido (ou, no iOS, impossível de distinguir de "sem permissão" — ver [HealthRepository]), mas
 * nenhuma amostra caiu no intervalo pedido (a sincronização do relógio não é instantânea).
 */
sealed interface HealthMetric<out T> {
    data object Unavailable : HealthMetric<Nothing>

    data object NoData : HealthMetric<Nothing>

    data class Value<T>(val value: T) : HealthMetric<T>
}

/** FC média e máxima do intervalo, em bpm, arredondadas ao inteiro mais próximo. */
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
    /** Gravado agora. */
    WRITTEN,

    /** Já existe treino de OUTRO app/relógio cobrindo o intervalo — não se grava por cima (RF-REL-06). */
    SKIPPED_DEVICE_WORKOUT_EXISTS,

    /** Este mesmo `WorkoutRun.localId` já foi gravado por este app (repetir a chamada é seguro). */
    ALREADY_WRITTEN,

    /** Repositório de saúde ausente no aparelho. */
    UNAVAILABLE,

    /** Sem permissão de ESCREVER treino, ou de LER treinos (sem ler, a lib não tem como saber se o
     * relógio já gravou — e por isso não grava). */
    PERMISSION_DENIED,

    /** `finishedAt` nulo: só se grava treino encerrado. */
    RUN_NOT_FINISHED,

    /** `finishedAt` não é posterior a `startedAt` — o repositório recusaria. */
    INVALID_RUN,

    /** A plataforma recusou por outro motivo (serviço do Health Connect fora, erro do HealthKit). Pode
     * tentar de novo depois. */
    FAILED,
}

/**
 * Leitura/escrita POSTERIOR no repositório de saúde da plataforma — Health Connect no Android,
 * HealthKit no iOS. Sem watchOS: a sessão ao vivo do relógio é nativa e fica no projeto
 * (`06-relogio-kmp-e-biblioteca.md` §2.3).
 *
 * **Contrato:** nunca lança por falta de dado ou de permissão — devolve [HealthMetric.Unavailable]/
 * [HealthMetric.NoData] e os valores de [WorkoutWriteResult]. Só lança [IllegalArgumentException] para
 * intervalo invertido (erro de quem chama). Nenhum método escreve em log: dado de saúde não sai do
 * aparelho por aqui.
 *
 * **iOS — limite da Apple, não da lib:** o HealthKit não revela se a LEITURA foi negada (privacidade);
 * sem permissão, a consulta simplesmente volta vazia. Por isso no iOS "sem permissão de leitura"
 * chega como [HealthMetric.NoData], e um treino do Apple Watch escondido por leitura negada não
 * impede a gravação. Pedir `EXERCISE_SESSION` (leitura + escrita juntas) é o que cobre o caso comum.
 */
interface HealthRepository {
    /** Ver [HealthAvailability]. Reavaliado a cada chamada (o usuário pode instalar o Health Connect
     * com o app aberto). */
    suspend fun availability(): HealthAvailability

    /** `availability() == AVAILABLE`. Não diz nada sobre PERMISSÃO — ver [requestPermissions]. */
    suspend fun isAvailable(): Boolean = availability() == HealthAvailability.AVAILABLE

    /**
     * Pede ao usuário os tipos informados. Android: `true` só se TODOS foram concedidos. iOS: `true`
     * quando o diálogo terminou e, se `EXERCISE_SESSION` foi pedido, a escrita de treino foi
     * concedida — a leitura o iOS não informa (ver o KDoc da interface).
     */
    suspend fun requestPermissions(types: Set<HealthDataType>): Boolean

    /** FC média/máxima e energia ativa no intervalo exato do treino. **Nunca soma** o treino do
     * relógio com o nosso: lê as amostras daquele intervalo, já deduplicadas pela plataforma por
     * origem (05 §6.2). */
    suspend fun readSessionMetrics(start: Instant, end: Instant): SessionMetrics

    /** `true` quando já existe uma sessão de exercício de OUTRO app/relógio cruzando o intervalo. O
     * que este app gravou não conta. `false` também quando não há como ler (indisponível/sem
     * permissão) — para decidir gravar, use [writeWorkout], que trata esses casos. */
    suspend fun hasDeviceWorkoutOverlapping(start: Instant, end: Instant): Boolean

    /**
     * Grava o nosso treino como sessão de exercício (RF-REL-06), aplicando a guarda AQUI DENTRO:
     * confere antes se outro app/relógio já gravou treino no intervalo
     * ([WorkoutWriteResult.SKIPPED_DEVICE_WORKOUT_EXISTS]) e se este `run.localId` já foi gravado
     * ([WorkoutWriteResult.ALREADY_WRITTEN]) — chamar de novo depois de uma falha de rede é seguro.
     * Só a SESSÃO (início, fim, tipo) é gravada: nenhuma caloria estimada vai junto.
     */
    suspend fun writeWorkout(run: WorkoutRun, category: ExerciseCategory): WorkoutWriteResult
}

/** Implementação da plataforma. Android: chame `initKmpLibHealth(context)` antes. */
expect fun createHealthRepository(): HealthRepository
