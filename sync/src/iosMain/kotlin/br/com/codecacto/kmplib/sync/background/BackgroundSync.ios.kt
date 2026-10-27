@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.sync.background

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.SyncNetworkPolicy
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateWithTimeIntervalSinceNow
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "BackgroundSync"

/** Onde a última política aplicada fica gravada — a tarefa acordada a frio precisa dela para reagendar. */
private const val POLICY_KEY = "br.com.codecacto.kmplib.backgroundSync.policy"
private const val PERIOD_KEY = "br.com.codecacto.kmplib.backgroundSync.periodSeconds"

/** Intervalo mínimo prático do "logo" no iOS (a Apple não roda uma tarefa de processamento antes disso). */
private const val IOS_MIN_SOON_SECONDS = 60.0

private object IosBackgroundSyncState {
    @Volatile
    var taskIdentifier: String? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/**
 * **Registra a tarefa de sincronização em 2º plano do iOS** (2.328.0). Chame do
 * `application(_:didFinishLaunchingWithOptions:)` — **a Apple exige o registro antes do fim da
 * abertura** (registrar depois lança `NSInternalInconsistencyException`). Ponha a chamada numa função
 * Kotlin do app (ex.: `MainViewController.kt`) e chame essa do Swift.
 *
 * - [taskIdentifier] tem de estar em `BGTaskSchedulerPermittedIdentifiers` no `Info.plist`, e
 *   `UIBackgroundModes` precisa de `processing`. Convenção: `<bundle id>.sync`.
 *
 * ⚠️ **O app pode ser aberto SÓ em 2º plano** para rodar a tarefa: nenhuma cena, nenhuma tela, nenhum
 * `MainViewController` é criado. Tudo o que o trabalho usa — `startKoin`, o banco, a fila, o
 * [BackgroundSyncWork.register] — precisa estar de pé **também** nesse caminho: inicialize no
 * `didFinishLaunching` (antes ou junto deste registro), nunca só no `onAppear` da tela. Sem isso a
 * tarefa acorda, não acha o trabalho e devolve "retentar" — nada se perde, mas nada sobe.
 *
 * Idempotente quanto ao id: segunda chamada com o mesmo id é ignorada.
 */
fun registerBackgroundSyncTask(taskIdentifier: String) {
    require(taskIdentifier.isNotBlank()) { "taskIdentifier em branco" }
    if (IosBackgroundSyncState.taskIdentifier == taskIdentifier) return
    IosBackgroundSyncState.taskIdentifier = taskIdentifier
    val ok = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(taskIdentifier, usingQueue = null) { tarefa ->
        val bg = tarefa as? BGProcessingTask ?: return@registerForTaskWithIdentifier
        val concluida = AtomicBoolean(false)
        fun concluir(sucesso: Boolean) {
            if (concluida.compareAndSet(false, true)) bg.setTaskCompletedWithSuccess(sucesso)
        }
        val trabalho = IosBackgroundSyncState.scope.launch {
            val resultado = BackgroundSyncWork.run()
            // A Apple não repete sozinha: a próxima é agendada aqui, enquanto a política for automática.
            val politica = SyncNetworkPolicy.fromWire(NSUserDefaults.standardUserDefaults.stringForKey(POLICY_KEY))
            if (politica.isAutomatic) submit(taskIdentifier, storedPeriodSeconds())
            concluir(resultado == BackgroundSyncResult.SUCCESS)
        }
        bg.expirationHandler = {
            // O sistema tirou o tempo: o motor do app é seguro contra corte no meio (o que estava
            // em voo volta à fila na próxima rodada).
            trabalho.cancel()
            concluir(false)
        }
    }
    if (!ok) AppLogger.w(TAG, "BGTaskScheduler recusou o registro — o id está em BGTaskSchedulerPermittedIdentifiers?")
}

private fun storedPeriodSeconds(): Double {
    val gravado = NSUserDefaults.standardUserDefaults.doubleForKey(PERIOD_KEY)
    return if (gravado > 0.0) gravado else 15.0 * 60
}

private fun submit(taskIdentifier: String, inSeconds: Double) {
    val pedido = BGProcessingTaskRequest(taskIdentifier)
    pedido.requiresNetworkConnectivity = true
    pedido.requiresExternalPower = false
    pedido.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(inSeconds)
    memScoped {
        val erro = alloc<ObjCObjectVar<NSError?>>()
        val ok = BGTaskScheduler.sharedScheduler.submitTaskRequest(pedido, erro.ptr)
        // Falha aqui (simulador, app sem a capability, limite de pedidos) não é erro do usuário: o app
        // sincroniza ao abrir e ao reconectar. Fica no log para o diagnóstico.
        if (!ok) AppLogger.w(TAG, "BGTaskScheduler recusou o agendamento (código ${erro.value?.code})")
    }
}

actual fun createBackgroundSyncScheduler(config: BackgroundSyncConfig): BackgroundSyncScheduler =
    BgTaskBackgroundSyncScheduler(config)

/**
 * [BackgroundSyncScheduler] pelo `BGTaskScheduler` (2.328.0). Grava a política e o período em
 * `NSUserDefaults` para a tarefa acordada a frio saber se reagenda. Exige [registerBackgroundSyncTask].
 */
class BgTaskBackgroundSyncScheduler(private val config: BackgroundSyncConfig = BackgroundSyncConfig()) :
    BackgroundSyncScheduler {

    private fun id(): String? = IosBackgroundSyncState.taskIdentifier ?: run {
        AppLogger.w(TAG, "registerBackgroundSyncTask não foi chamado no didFinishLaunching — nada agendado")
        null
    }

    override fun apply(policy: SyncNetworkPolicy) {
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.setObject(policy.wire, forKey = POLICY_KEY)
        defaults.setDouble(config.periodMinutes * 60.0, forKey = PERIOD_KEY)
        val id = id() ?: return
        if (policy.isAutomatic) submit(id, config.periodMinutes * 60.0)
        else BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(id)
    }

    override fun requestSoon(policy: SyncNetworkPolicy) {
        if (!policy.isAutomatic) return
        val id = id() ?: return
        submit(id, maxOf(config.soonDelaySeconds.toDouble(), IOS_MIN_SOON_SECONDS))
    }

    override fun cancel() {
        NSUserDefaults.standardUserDefaults.setObject(SyncNetworkPolicy.MANUAL_ONLY.wire, forKey = POLICY_KEY)
        val id = IosBackgroundSyncState.taskIdentifier ?: return
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(id)
    }
}
