package br.com.codecacto.kmplib.ui.form

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * O que o callback de upload do app recebe além do arquivo — a lib não sabe a rota; o projeto sabe.
 *
 * @property purpose o `file.purpose` da pergunta (`"LAB_REPORT"`) — o projeto o manda à rota.
 * @property onProgress progresso REAL do envio, 0..1 (Ktor: `onUpload { enviado, total -> … }`).
 *   Pode ser chamado de qualquer thread.
 */
class FormUploadContext internal constructor(
    val questionId: String,
    val purpose: String,
    val onProgress: (fraction: Float) -> Unit,
)

/**
 * Falha de upload com a frase do SERVIDOR para gente ("Arquivo maior que 10 MB."). Qualquer outra
 * exceção vira a frase da lib (`fileUploadError`) — mensagem técnica ("Failed to connect…") nunca
 * chega à tela.
 */
class FormUploadException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

/**
 * **O envio de anexo por CALLBACK** — o `onUploadFile` do `FormRunner` da weblib, no ViewModel: a lib
 * não conhece a rota (cada projeto tem a sua, autenticada do seu jeito), então o app passa o
 * [upload] que sobe UM arquivo e devolve o `{fileId}` que a rota gerou.
 *
 * ```kotlin
 * private val anexos = FormFileUploader(viewModelScope) { arquivo, ctx ->
 *     api.enviarArquivo(preconsultaId, ctx.questionId, arquivo.name, arquivo.mimeType, arquivo.bytes) { enviado, total ->
 *         ctx.onProgress(enviado.toFloat() / total)
 *     }                                                    // → FormFileRef(fileId)
 * }
 * // FormRunnerEvent.UploadRequested → anexos.start(evento.request) { onAction(Formulario(it)) }
 * // FormRunnerEvent.UploadCancelled → anexos.cancel(evento.key)
 * ```
 *
 * O resultado volta como AÇÃO do runner ([FormRunnerAction.UploadProgress], `UploadSucceeded`,
 * `UploadFailed`) pelo [start]`(onUpdate)`, sempre no despachante do [scope] (o `viewModelScope` é o
 * principal): o reducer nunca é chamado de duas threads ao mesmo tempo. Cancelar ([cancel]) não
 * reporta falha — quem cancelou já tirou a linha da tela.
 */
class FormFileUploader(
    private val scope: CoroutineScope,
    private val upload: suspend (file: FormPickedFile, context: FormUploadContext) -> FormFileRef,
) {
    private val jobs = HashMap<String, Job>()

    /** Sobe o arquivo do pedido; [onUpdate] recebe progresso e desfecho como ações do runner. */
    fun start(request: FormUploadRequest, onUpdate: (FormRunnerAction) -> Unit) {
        jobs.remove(request.key)?.cancel()
        val progress = MutableStateFlow<Float?>(null)
        // LAZY: entra no mapa ANTES de rodar — com o despachante imediato do `viewModelScope`, um
        // upload que falha sem suspender terminaria antes de existir no mapa.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            // O progresso chega da thread de rede; o reducer só o vê aqui, no despachante do escopo.
            val reporter = launch {
                progress.filterNotNull().collectLatest { onUpdate(FormRunnerAction.UploadProgress(request.key, it)) }
            }
            val context = FormUploadContext(request.questionId, request.purpose) { progress.value = it }
            try {
                val outcome = try {
                    FormRunnerAction.UploadSucceeded(request.key, upload(request.file, context))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: FormUploadException) {
                    FormRunnerAction.UploadFailed(request.key, failure.userMessage)
                } catch (failure: Throwable) {
                    FormRunnerAction.UploadFailed(request.key, null)
                } finally {
                    reporter.cancel()
                }
                onUpdate(outcome)
            } finally {
                if (jobs[request.key] === self) jobs.remove(request.key)
            }
        }
        jobs[request.key] = job
        job.start()
    }

    /** Cancela o envio em curso (o X da pessoa, ou a tela saindo). */
    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
    }

    /** Cancela todos (a tela saiu — ninguém mais vai receber o `fileId`). */
    fun cancelAll() {
        val all = jobs.values.toList()
        jobs.clear()
        all.forEach { it.cancel() }
    }
}
