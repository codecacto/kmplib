package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException

/**
 * Uma parte a enviar ao storage: o trecho [offset]..[offset]+[length] do arquivo [filePath], por
 * `PUT` na URL pré-assinada [url].
 *
 * @param key chave estável da parte (`<jobId>.<partNumber>`) — no iOS, o `taskDescription` que
 *   reencontra a tarefa de fundo depois de o processo morrer.
 * @param partFilePath onde gravar o trecho como arquivo, para o transporte que só envia de arquivo.
 *   No PUT único (o trecho é o arquivo inteiro) o transporte do iOS envia direto de [filePath].
 * @param method método HTTP (2.312.0; `PUT`).
 * @param contentType `content-type` assinado (PUT único) — vai como tipo do corpo, **exatamente** este
 *   texto. `null` = sem cabeçalho de tipo (multipart: a URL não o assina).
 * @param headers demais cabeçalhos assinados (`x-amz-checksum-sha256`…), já sem `content-length`,
 *   `content-type` e `host` ([directUploadExtraHeaders]). Nunca saem em `toString`.
 * @param requireEtag `true` (multipart) = 2xx sem `ETag` é falha, porque o `complete` precisa dela;
 *   `false` (PUT único) = o servidor confere o arquivo por conta própria no `concluir`.
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
    val method: String = "PUT",
    val contentType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val requireEtag: Boolean = true,
) {
    val key: String get() = directUploadPartKey(jobId, partNumber)

    /** `true` se o trecho é o arquivo inteiro de [fileSize] bytes (o PUT único). */
    fun coversWholeFile(fileSize: Long): Boolean = offset == 0L && length == fileSize

    override fun toString(): String =
        "DirectUploadPartRequest(jobId=$jobId, partNumber=$partNumber, method=$method, url=${safeUrl(url)}, " +
            "length=$length, headers=${headers.keys.sorted()}, wifiOnly=$wifiOnly)"
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

/**
 * Status HTTP do storage → desfecho. ETag ausente num 2xx é falha quando [requireEtag] (multipart: sem
 * ela não há `complete`); no PUT único vira [DIRECT_UPLOAD_NO_ETAG].
 */
fun classifyDirectUploadPartResponse(status: Int, etag: String?, requireEtag: Boolean = true): DirectUploadPartResult = when {
    status in 200..299 -> normalizeDirectUploadEtag(etag)?.let { DirectUploadPartResult.Uploaded(it) }
        ?: if (requireEtag) DirectUploadPartResult.Failed(MISSING_ETAG_STATUS) else DirectUploadPartResult.Uploaded(DIRECT_UPLOAD_NO_ETAG)
    status == 403 || status == 400 -> DirectUploadPartResult.Expired
    status == 404 -> DirectUploadPartResult.UploadGone
    else -> DirectUploadPartResult.Failed(status)
}

/** `true` se a falha HTTP da parte vale nova tentativa sozinha. */
fun isRetryableDirectUploadPartStatus(code: Int): Boolean =
    code == 408 || code == 429 || code in 500..599 || code == MISSING_ETAG_STATUS

/** Sentinela: 2xx sem `ETag` (CORS/proxy que tira o cabeçalho). Retentável. */
const val MISSING_ETAG_STATUS: Int = -30

/** "ETag" gravada para o PUT único aceito sem o cabeçalho (o `concluir` não usa ETag). */
const val DIRECT_UPLOAD_NO_ETAG: String = "-"

/**
 * Transporte pelo Ktor: faz o `PUT` na URL pré-assinada **lendo o trecho do arquivo em blocos**
 * enquanto envia (memória do tamanho do bloco, não da parte — o PUT único de 100 MB não cabe num
 * `ByteArray`), com `Content-Length` exato e **só** os cabeçalhos que o servidor assinou. **Sem**
 * Bearer: mandar o token do app ao storage seria vazar a credencial a outro host.
 *
 * @param httpClient cliente **dedicado** ao storage (o default não tem plugin nenhum além de timeout).
 */
class KtorDirectUploadPartTransport internal constructor(
    private val httpClient: HttpClient,
    private val fs: DirectUploadFiles,
) : DirectUploadPartTransport {

    constructor(httpClient: HttpClient = defaultStorageHttpClient()) : this(httpClient, platformDirectUploadFiles())

    override suspend fun upload(request: DirectUploadPartRequest): DirectUploadPartResult {
        val tamanho = fs.size(request.filePath)
        if (request.length <= 0L || request.offset < 0L || tamanho < request.offset + request.length) {
            return DirectUploadPartResult.SourceUnreadable
        }
        val corpo = FileRangeContent(fs, request)
        return try {
            val resposta = httpClient.request(request.url) {
                method = HttpMethod.parse(request.method.uppercase())
                request.headers.forEach { (k, v) -> header(k, v) }
                setBody(corpo)
            }
            if (corpo.readFailed) return DirectUploadPartResult.SourceUnreadable
            classifyDirectUploadPartResponse(resposta.status.value, resposta.headers[HttpHeaders.ETag], request.requireEtag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (corpo.readFailed) return DirectUploadPartResult.SourceUnreadable
            AppLogger.w(TAG, "parte ${request.partNumber} não subiu: ${e::class.simpleName}")
            DirectUploadPartResult.Offline
        }
    }

    /** Corpo que lê o trecho do arquivo em blocos de [DIRECT_UPLOAD_STREAM_CHUNK_BYTES] ao ser escrito. */
    private class FileRangeContent(
        private val files: DirectUploadFiles,
        private val request: DirectUploadPartRequest,
    ) : OutgoingContent.WriteChannelContent() {
        var readFailed = false
            private set

        override val contentLength: Long = request.length
        override val contentType: ContentType? = request.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }

        override suspend fun writeTo(channel: ByteWriteChannel) {
            var posicao = request.offset
            val fim = request.offset + request.length
            while (posicao < fim) {
                val bloco = minOf(DIRECT_UPLOAD_STREAM_CHUNK_BYTES.toLong(), fim - posicao)
                val bytes = files.readRange(request.filePath, posicao, bloco)
                if (bytes == null || bytes.size.toLong() != bloco) {
                    readFailed = true
                    throw IllegalStateException("arquivo da fila ilegível no meio do envio")
                }
                channel.writeFully(bytes, 0, bytes.size)
                posicao += bloco
            }
        }
    }

    private companion object {
        const val TAG = "DirectUploadTransport"
    }
}

/** Bloco de leitura do corpo enviado pelo [KtorDirectUploadPartTransport]: 512 KiB. */
const val DIRECT_UPLOAD_STREAM_CHUNK_BYTES: Int = 512 * 1024

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
