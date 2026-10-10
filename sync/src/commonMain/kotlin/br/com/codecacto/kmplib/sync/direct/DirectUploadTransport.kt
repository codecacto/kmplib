package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.CancellationException

/**
 * Uma parte a enviar ao storage: o trecho [offset]..[offset]+[length] do arquivo [filePath], por
 * `PUT` na URL pré-assinada [url].
 *
 * @param key chave estável da parte (`<jobId>.<partNumber>`) — no iOS, o `taskDescription` que
 *   reencontra a tarefa de fundo depois de o processo morrer.
 * @param partFilePath onde gravar o trecho como arquivo, para o transporte que só envia de arquivo.
 */
class DirectUploadPartRequest(
    val jobId: String,
    val partNumber: Int,
    val url: String,
    val filePath: String,
    val offset: Long,
    val length: Long,
    val partFilePath: String,
    val wifiOnly: Boolean,
) {
    val key: String get() = directUploadPartKey(jobId, partNumber)

    override fun toString(): String =
        "DirectUploadPartRequest(jobId=$jobId, partNumber=$partNumber, url=${safeUrl(url)}, length=$length, wifiOnly=$wifiOnly)"
}

/** O que aconteceu com uma parte. */
sealed interface DirectUploadPartResult {
    /** Aceita; [etag] vai no `complete`. */
    data class Uploaded(val etag: String) : DirectUploadPartResult

    /** A URL venceu ou foi recusada pela assinatura (403/400 do storage): reassinar e repetir. */
    data object Expired : DirectUploadPartResult

    /** O storage não conhece mais este envio (404 `NoSuchUpload` — o servidor abortou ou expirou). */
    data object UploadGone : DirectUploadPartResult

    /** Sem rede / transporte caiu — a fila pausa e retoma com a rede. */
    data object Offline : DirectUploadPartResult

    /** Cancelada por [DirectUploadPartTransport.cancelJob] (descartar, sair da conta). */
    data object Cancelled : DirectUploadPartResult

    /** O arquivo não pôde ser lido (sumiu, apagado por fora). Terminal para o envio. */
    data object SourceUnreadable : DirectUploadPartResult

    /** Outra falha HTTP ([code]): 5xx/408/429 retentáveis, resto terminal. */
    data class Failed(val code: Int) : DirectUploadPartResult
}

/**
 * Quem leva a parte ao storage. O default da plataforma ([createPlatformDirectUploadTransport]):
 * - **Android:** [KtorDirectUploadPartTransport], rodando dentro do `CoroutineWorker` do WorkManager —
 *   o agendador oficial de trabalho garantido do Android (sobrevive ao app fechado e ao reboot).
 * - **iOS:** sessão de **segundo plano** do `URLSession` (`backgroundSessionConfiguration`) — o
 *   sistema leva o upload mesmo com o app suspenso, e reabre o app para entregar o resultado.
 */
interface DirectUploadPartTransport {
    suspend fun upload(request: DirectUploadPartRequest): DirectUploadPartResult

    /** Cancela as partes em voo de [jobId] (a chamada que espera devolve [DirectUploadPartResult.Cancelled]). */
    suspend fun cancelJob(jobId: String) {}
}

/** `<jobId>.<partNumber>` — chave da parte. */
fun directUploadPartKey(jobId: String, partNumber: Int): String = "$jobId.$partNumber"

/** Decompõe [directUploadPartKey]; `null` se não for uma chave de parte. */
fun parseDirectUploadPartKey(key: String): Pair<String, Int>? {
    val i = key.lastIndexOf('.')
    if (i <= 0 || i == key.lastIndex) return null
    val n = key.substring(i + 1).toIntOrNull() ?: return null
    return if (n >= 1) key.substring(0, i) to n else null
}

/** Status HTTP do storage → desfecho. ETag ausente num 2xx é falha (sem ela não há `complete`). */
fun classifyDirectUploadPartResponse(status: Int, etag: String?): DirectUploadPartResult = when {
    status in 200..299 -> normalizeDirectUploadEtag(etag)?.let { DirectUploadPartResult.Uploaded(it) }
        ?: DirectUploadPartResult.Failed(MISSING_ETAG_STATUS)
    status == 403 || status == 400 -> DirectUploadPartResult.Expired
    status == 404 -> DirectUploadPartResult.UploadGone
    else -> DirectUploadPartResult.Failed(status)
}

/** `true` se a falha HTTP da parte vale nova tentativa sozinha. */
fun isRetryableDirectUploadPartStatus(code: Int): Boolean =
    code == 408 || code == 429 || code in 500..599 || code == MISSING_ETAG_STATUS

/** Sentinela: 2xx sem `ETag` (CORS/proxy que tira o cabeçalho). Retentável. */
const val MISSING_ETAG_STATUS: Int = -30

/**
 * Transporte pelo Ktor: lê o trecho do arquivo com posicionamento direto e faz o `PUT` na URL
 * pré-assinada, **sem** Bearer nem cabeçalho extra (a assinatura cobre só o que o servidor assinou;
 * mandar o token do app para o storage seria vazar a credencial a outro host).
 *
 * @param httpClient cliente **dedicado** ao storage (o default não tem plugin nenhum além de timeout).
 */
class KtorDirectUploadPartTransport(
    private val httpClient: HttpClient = defaultStorageHttpClient(),
) : DirectUploadPartTransport {

    private val files: DirectUploadFiles = platformDirectUploadFiles()

    override suspend fun upload(request: DirectUploadPartRequest): DirectUploadPartResult {
        val bytes = files.readRange(request.filePath, request.offset, request.length)
            ?.takeIf { it.size.toLong() == request.length }
            ?: return DirectUploadPartResult.SourceUnreadable
        return try {
            val resposta = httpClient.put(request.url) { setBody(ByteArrayContent(bytes)) }
            classifyDirectUploadPartResponse(resposta.status.value, resposta.headers[HttpHeaders.ETag])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "parte ${request.partNumber} não subiu: ${e::class.simpleName}")
            DirectUploadPartResult.Offline
        }
    }

    private companion object {
        const val TAG = "DirectUploadTransport"
    }
}

/** Cliente sem plugin de app: sem Bearer, sem log de corpo, sem gzip; timeout de socket de 60 s. */
fun defaultStorageHttpClient(): HttpClient = HttpClient {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 60_000
        requestTimeoutMillis = 15L * 60_000
    }
}

/** O transporte recomendado da plataforma (ver [DirectUploadPartTransport]). */
expect fun createPlatformDirectUploadTransport(outboxName: String): DirectUploadPartTransport
