package br.com.codecacto.kmplib.sync.background

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.SyncNetworkPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Desfecho de uma rodada de sincronização em 2º plano, devolvido pelo trabalho do app.
 *
 * - [SUCCESS] — terminou (inclusive "não havia nada a enviar").
 * - [RETRY] — falha passageira (sem rede, servidor instável, ciclo interrompido): o sistema tenta de
 *   novo com o recuo dele. Nada se perde — a fila do app é durável.
 */
enum class BackgroundSyncResult { SUCCESS, RETRY }

/**
 * Configuração do [BackgroundSyncScheduler].
 *
 * @param periodMinutes intervalo do trabalho periódico. **15 é o mínimo do WorkManager**; no iOS é o
 *   `earliestBeginDate` da próxima tarefa (o sistema decide quando roda de fato — pode ser bem depois).
 * @param soonDelaySeconds atraso do "logo" ([BackgroundSyncScheduler.requestSoon]) — junta várias
 *   gravações seguidas numa rodada só. No iOS, o mínimo prático é ~60 s (a Apple não roda antes).
 * @param uniqueWorkName prefixo dos trabalhos únicos do WorkManager (troque só se o app tiver duas
 *   sincronizações independentes).
 */
data class BackgroundSyncConfig(
    val periodMinutes: Long = 15,
    val soonDelaySeconds: Long = 5,
    val uniqueWorkName: String = "kmplib-background-sync",
) {
    init {
        require(periodMinutes >= 15) { "periodMinutes mínimo é 15 (limite do WorkManager)" }
        require(soonDelaySeconds >= 0) { "soonDelaySeconds não pode ser negativo" }
        require(uniqueWorkName.isNotBlank()) { "uniqueWorkName em branco" }
    }
}

/**
 * **O trabalho que roda em 2º plano** (2.328.0) — registrado pelo APP, chamado pela lib quando o
 * sistema acorda a sincronização (WorkManager no Android, `BGProcessingTask` no iOS).
 *
 * ```kotlin
 * // Android: Application.onCreate(), DEPOIS do startKoin (o WorkManager pode subir o processo
 * // só para rodar o trabalho — sem tela nenhuma).
 * BackgroundSyncWork.register {
 *     if (get<RestCrudSyncEngine>().syncNow()) BackgroundSyncResult.SUCCESS else BackgroundSyncResult.RETRY
 * }
 * ```
 *
 * Rodadas são **serializadas** (o periódico e o "logo" nunca rodam o trabalho ao mesmo tempo).
 * Exceção do trabalho vira [BackgroundSyncResult.RETRY] (com log); cancelamento (o sistema tirou o
 * tempo) é propagado.
 */
object BackgroundSyncWork {

    @Volatile
    private var work: (suspend () -> BackgroundSyncResult)? = null

    private val serial = Mutex()

    /** Registra (ou troca) o trabalho. Chame na abertura do processo, antes de qualquer agendamento rodar. */
    fun register(work: suspend () -> BackgroundSyncResult) {
        this.work = work
    }

    /** Remove o trabalho (o agendado que acordar sem ele devolve [BackgroundSyncResult.RETRY]). */
    fun clear() {
        work = null
    }

    /** Há trabalho registrado? */
    val isRegistered: Boolean get() = work != null

    /**
     * Roda uma rodada. `null` = nenhum trabalho registrado (o app não montou a sincronização na
     * abertura — quem chama trata como retentar).
     */
    suspend fun run(): BackgroundSyncResult? {
        val atual = work ?: run {
            AppLogger.w(TAG, "Trabalho de 2º plano sem registro (BackgroundSyncWork.register não foi chamado na abertura)")
            return null
        }
        return serial.withLock {
            try {
                atual()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Trabalho de 2º plano lançou; será retentado", e)
                BackgroundSyncResult.RETRY
            }
        }
    }

    private const val TAG = "BackgroundSync"
}

/**
 * **Agendador da sincronização em 2º plano** (2.328.0) — quem acorda a sincronização com o app
 * fechado ou em 2º plano, conforme a [SyncNetworkPolicy]. Promovido do Raiz.
 *
 * - **Android — WorkManager:** trabalho **periódico** ([BackgroundSyncConfig.periodMinutes]) e um
 *   trabalho **"logo"** (one-shot, [BackgroundSyncConfig.soonDelaySeconds]), ambos com
 *   `NetworkType.CONNECTED` — dado de texto sobe em qualquer rede nos modos automáticos; o "só no
 *   Wi-Fi" é da mídia, nas filas de envio. Em [SyncNetworkPolicy.MANUAL_ONLY], nada agendado (os dois
 *   são cancelados). Precisa do `initKmpLibSync(context)`; o WorkManager persiste o agendamento.
 * - **iOS — `BGTaskScheduler` (`BGProcessingTaskRequest`, rede exigida):** a tarefa roda o
 *   [BackgroundSyncWork] e **reagenda a si mesma** (a Apple não repete sozinha) enquanto a política
 *   gravada for automática. Exige `registerBackgroundSyncTask(id)` no
 *   `application(_:didFinishLaunchingWithOptions:)`, o mesmo id em `BGTaskSchedulerPermittedIdentifiers`
 *   e `processing` em `UIBackgroundModes` no `Info.plist`.
 *
 * Chame [apply] na abertura e sempre que a pessoa trocar a política; [requestSoon] depois de gravar
 * algo que deveria subir já (o motor confere a rede na hora). O envio manual ("Sincronizar agora")
 * não passa por aqui.
 */
interface BackgroundSyncScheduler {
    /** Reprograma o trabalho periódico conforme a política (ou o cancela, em manual). */
    fun apply(policy: SyncNetworkPolicy)

    /** Pede uma rodada assim que houver rede (no-op em manual). */
    fun requestSoon(policy: SyncNetworkPolicy)

    /** Cancela todo agendamento (logout, por exemplo). */
    fun cancel()
}

/** O [BackgroundSyncScheduler] da plataforma (WorkManager / `BGTaskScheduler`). */
expect fun createBackgroundSyncScheduler(config: BackgroundSyncConfig = BackgroundSyncConfig()): BackgroundSyncScheduler
