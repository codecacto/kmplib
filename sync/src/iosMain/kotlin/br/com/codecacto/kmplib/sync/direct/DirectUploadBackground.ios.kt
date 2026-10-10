@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class, kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.setHTTPMethod
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.native.ObjCName

actual fun createPlatformDirectUploadTransport(outboxName: String): DirectUploadPartTransport =
    BackgroundSessionDirectUploadTransport.forOutbox(outboxName)

actual fun createPlatformDirectUploadScheduler(): DirectUploadScheduler =
    InProcessDirectUploadScheduler(runInBackgroundWindow = ::withBackgroundTask)

/**
 * Pede ao sistema os segundos de 2º plano (`beginBackgroundTask`) enquanto a drenagem roda — sem isso,
 * sair do app no meio do `complete` suspende o processo antes da resposta. O sistema encerra a janela
 * quando quiser (expiração): a fila para no próximo passo e retoma depois.
 */
private suspend fun withBackgroundTask(block: suspend () -> Unit) {
    val app = UIApplication.sharedApplication
    var id = UIBackgroundTaskInvalid
    id = app.beginBackgroundTaskWithName("kmplib-direct-upload") {
        if (id != UIBackgroundTaskInvalid) app.endBackgroundTask(id)
        id = UIBackgroundTaskInvalid
    }
    try {
        block()
    } finally {
        if (id != UIBackgroundTaskInvalid) app.endBackgroundTask(id)
        id = UIBackgroundTaskInvalid
    }
}

/**
 * Transporte pela **sessão de segundo plano do `URLSession`** — o caminho que a Apple recomenda para
 * upload que precisa terminar com o app suspenso ou encerrado pelo sistema: o `nsurlsessiond` leva a
 * parte, e ao fim o sistema reabre o app em 2º plano para entregar o resultado.
 *
 * - Cada parte vira um arquivo próprio (`part-N`, a sessão de fundo só envia de arquivo) e uma
 *   tarefa com `taskDescription` = [DirectUploadPartRequest.key]. Processo morto e reaberto: a
 *   tarefa ainda em voo é **reencontrada** pela chave (não sobe duas vezes), e o resultado que chegou
 *   sem ninguém esperando fica guardado até a fila pedir aquela parte de novo.
 * - "Só no Wi-Fi" = `allowsCellularAccess = false` + `allowsExpensiveNetworkAccess = false` na
 *   requisição: a tarefa espera o Wi-Fi sozinha.
 * - Sem Bearer: só a URL pré-assinada.
 */
class BackgroundSessionDirectUploadTransport private constructor(
    private val outboxName: String,
    val sessionIdentifier: String,
) : DirectUploadPartTransport {

    private val esperando = AtomicReference<Map<String, CompletableDeferred<DirectUploadPartResult>>>(emptyMap())
    private val tardios = AtomicReference<Map<String, DirectUploadPartResult>>(emptyMap())
    private val delegate = Delegate()

    private val session: NSURLSession by lazy {
        val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(sessionIdentifier).apply {
            sessionSendsLaunchEvents = true
            setDiscretionary(false)
            timeoutIntervalForResource = RESOURCE_TIMEOUT_SECONDS
        }
        val fila = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }
        NSURLSession.sessionWithConfiguration(config, delegate, fila)
    }

    /** Garante a sessão viva (o `handleEventsForBackgroundURLSession` precisa dela para entregar os eventos). */
    internal fun attach() {
        session.configuration
    }

    override suspend fun upload(request: DirectUploadPartRequest): DirectUploadPartResult {
        val chave = request.key
        tirar(tardios, chave)?.let {
            apagarTrecho(request)
            return it
        }
        val adiado = CompletableDeferred<DirectUploadPartResult>()
        // Registra quem espera ANTES de olhar as tarefas: a conclusão que chegar no meio cai no adiado.
        colocar(esperando, chave, adiado)
        tirar(tardios, chave)?.let {
            tirar(esperando, chave)
            apagarTrecho(request)
            return it
        }
        val emVoo = tarefas().firstOrNull { it.taskDescription == chave && it.state < TASK_STATE_CANCELING }
        if (emVoo == null) {
            if (!IosDirectUploadFiles.writeRange(request.filePath, request.offset, request.length, request.partFilePath)) {
                tirar(esperando, chave)
                return DirectUploadPartResult.SourceUnreadable
            }
            val url = NSURL.URLWithString(request.url) ?: run {
                tirar(esperando, chave)
                apagarTrecho(request)
                return DirectUploadPartResult.Expired
            }
            val req = NSMutableURLRequest(uRL = url).apply {
                setHTTPMethod("PUT")
                setAllowsCellularAccess(!request.wifiOnly)
                setAllowsExpensiveNetworkAccess(!request.wifiOnly)
            }
            val tarefa = session.uploadTaskWithRequest(req, fromFile = NSURL.fileURLWithPath(request.partFilePath))
            tarefa.taskDescription = chave
            tarefa.resume()
        }
        // Sem cancelar a tarefa se a corrotina for cancelada: o envio de fundo segue com o app suspenso
        // — é o ponto. Quem cancela de propósito é o cancelJob (descartar, sair da conta).
        val resultado = adiado.await()
        apagarTrecho(request)
        return resultado
    }

    override suspend fun cancelJob(jobId: String) {
        val prefixo = "$jobId."
        tarefas().filter { it.taskDescription?.startsWith(prefixo) == true }.forEach { it.cancel() }
        esperando.load().filterKeys { it.startsWith(prefixo) }.keys.forEach { chave ->
            tirar(esperando, chave)?.complete(DirectUploadPartResult.Cancelled)
        }
        tardios.load().keys.filter { it.startsWith(prefixo) }.forEach { tirar(tardios, it) }
    }

    private fun apagarTrecho(request: DirectUploadPartRequest) {
        IosDirectUploadFiles.deleteRecursively(request.partFilePath)
    }

    private suspend fun tarefas(): List<NSURLSessionTask> = suspendCancellableCoroutine { cont ->
        session.getAllTasksWithCompletionHandler { lista ->
            cont.resume(lista?.filterIsInstance<NSURLSessionTask>().orEmpty())
        }
    }

    private fun entregar(tarefa: NSURLSessionTask, erro: NSError?) {
        val chave = tarefa.taskDescription ?: return
        val resultado = when {
            erro != null && erro.domain == NSURLErrorDomain && erro.code == NSURLErrorCancelled -> DirectUploadPartResult.Cancelled
            erro != null -> DirectUploadPartResult.Offline
            else -> {
                val resposta = tarefa.response as? NSHTTPURLResponse
                if (resposta == null) {
                    DirectUploadPartResult.Offline
                } else {
                    classifyDirectUploadPartResponse(resposta.statusCode.toInt(), resposta.valueForHTTPHeaderField("ETag"))
                }
            }
        }
        val quem = tirar(esperando, chave)
        if (quem != null) {
            quem.complete(resultado)
        } else if (resultado !is DirectUploadPartResult.Cancelled) {
            // Processo reaberto pelo sistema: ninguém espera esta parte ainda. Guarda o resultado e
            // acorda a fila, que vai pedir a parte e recebê-lo sem reenviar.
            colocar(tardios, chave, resultado)
            DirectUploadOutboxRegistry.get(outboxName)?.requestDrain()
        }
    }

    private inner class Delegate : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            entregar(task, didCompleteWithError)
        }

        override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
            DirectUploadBackgroundEvents.finished(sessionIdentifier)
        }
    }

    companion object {
        private const val RESOURCE_TIMEOUT_SECONDS = 6.0 * 60 * 60
        private const val TASK_STATE_CANCELING = 2L // NSURLSessionTaskStateCanceling (0 = Running, 1 = Suspended, 3 = Completed)

        private val porNome = AtomicReference<Map<String, BackgroundSessionDirectUploadTransport>>(emptyMap())

        /** Identificador da sessão de fundo da fila [outboxName]: `<bundle id>.kmplib.direct-upload.<nome>`. */
        fun sessionIdentifierFor(outboxName: String): String =
            (NSBundle.mainBundle.bundleIdentifier ?: "app") + ".kmplib.direct-upload." + outboxName

        /** Um transporte por fila no processo (duas sessões com o mesmo identificador não podem coexistir). */
        fun forOutbox(outboxName: String): BackgroundSessionDirectUploadTransport {
            porNome.load()[outboxName]?.let { return it }
            val novo = BackgroundSessionDirectUploadTransport(outboxName, sessionIdentifierFor(outboxName))
            while (true) {
                val atual = porNome.load()
                atual[outboxName]?.let { return it }
                if (porNome.compareAndSet(atual, atual + (outboxName to novo))) return novo
            }
        }

        internal fun bySessionIdentifier(identifier: String): BackgroundSessionDirectUploadTransport? =
            porNome.load().values.firstOrNull { it.sessionIdentifier == identifier }
    }
}

/**
 * A ponte do `AppDelegate` para a sessão de fundo. Quando um envio termina com o app fora do ar, o
 * iOS reabre o app em 2º plano e chama `application(_:handleEventsForBackgroundURLSession:completionHandler:)`;
 * encaminhe para cá **depois** de a fila ter sido construída (no `didFinishLaunching`/inicialização do Koin):
 *
 * ```swift
 * func application(_ application: UIApplication,
 *                  handleEventsForBackgroundURLSession identifier: String,
 *                  completionHandler: @escaping () -> Void) {
 *     if !DirectUploadBackgroundEvents.shared.handle(identifier: identifier, completionHandler: completionHandler) {
 *         completionHandler()   // não é sessão da kmplib
 *     }
 * }
 * ```
 * (App SwiftUI: `@UIApplicationDelegateAdaptor` com um `AppDelegate` que tenha só esse método. O
 * módulo `kmplib-sync` precisa estar no `export(...)` do framework para o Swift enxergar este objeto.)
 */
@ObjCName("DirectUploadBackgroundEvents")
object DirectUploadBackgroundEvents {
    private val pendentes = AtomicReference<Map<String, () -> Unit>>(emptyMap())

    /**
     * `true` se [identifier] é de uma fila da kmplib: o [completionHandler] será chamado (na main
     * thread) quando o sistema terminar de entregar os eventos. `false`: não é nosso — chame você.
     */
    fun handle(identifier: String, completionHandler: () -> Unit): Boolean {
        if (!identifier.contains(".kmplib.direct-upload.")) return false
        colocar(pendentes, identifier, completionHandler)
        val transporte = BackgroundSessionDirectUploadTransport.bySessionIdentifier(identifier)
        if (transporte == null) {
            // A fila ainda não foi construída: recriar a sessão aqui sem a fila não teria quem ouvisse.
            AppLogger.w("DirectUploadBackground", "evento de envio de fundo sem a fila construída; construa-a na abertura")
            finished(identifier)
            return true
        }
        transporte.attach()
        return true
    }

    internal fun finished(identifier: String) {
        val handler = tirar(pendentes, identifier) ?: return
        dispatch_async(dispatch_get_main_queue()) { handler() }
    }
}

private fun <V> colocar(ref: AtomicReference<Map<String, V>>, chave: String, valor: V) {
    while (true) {
        val atual = ref.load()
        if (ref.compareAndSet(atual, atual + (chave to valor))) return
    }
}

private fun <V> tirar(ref: AtomicReference<Map<String, V>>, chave: String): V? {
    while (true) {
        val atual = ref.load()
        val v = atual[chave] ?: return null
        if (ref.compareAndSet(atual, atual - chave)) return v
    }
}
