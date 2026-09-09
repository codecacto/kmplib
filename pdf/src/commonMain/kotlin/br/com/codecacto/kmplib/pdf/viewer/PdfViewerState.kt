package br.com.codecacto.kmplib.pdf.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.storage.createBlobStore
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess

/** Em que ponto a abertura do documento está. */
sealed interface PdfViewerStatus {
    /** Baixando, lendo do disco ou abrindo o documento. */
    data object Loading : PdfViewerStatus

    /** Pronto: há páginas para desenhar. */
    data object Ready : PdfViewerStatus

    /** Falhou. [message] é a frase que vai para a tela. */
    data class Error(val kind: PdfViewerError, val message: String, val cause: String? = null) :
        PdfViewerStatus
}

/** A natureza da falha ao abrir um PDF. */
enum class PdfViewerError {
    /** Sem rede, tempo esgotado, servidor fora. Tentar de novo faz sentido. */
    Network,

    /** 404, ou o arquivo local não existe mais. */
    NotFound,

    /** Baixou, mas não é um PDF legível (truncado, HTML de erro salvo como `.pdf`, cifrado). */
    Corrupted,

    /** Falha de disco: sem espaço, sem permissão. */
    Io,
}

/** Os textos do visualizador. Defaults em pt-BR. */
data class PdfViewerTexts(
    val loading: String = "Abrindo o documento…",
    val retry: String = "Tentar de novo",
    val errorNetwork: String = "Não foi possível baixar o documento. Verifique a conexão.",
    val errorNotFound: String = "Este documento não está disponível.",
    val errorCorrupted: String = "O arquivo está danificado e não pôde ser aberto.",
    val errorIo: String = "Não foi possível abrir o documento.",
    /** O indicador de página. Recebe a página atual (a partir de 1) e o total. */
    val pageIndicator: (page: Int, total: Int) -> String = { pagina, total -> "$pagina de $total" },
) {
    fun messageFor(kind: PdfViewerError): String = when (kind) {
        PdfViewerError.Network -> errorNetwork
        PdfViewerError.NotFound -> errorNotFound
        PdfViewerError.Corrupted -> errorCorrupted
        PdfViewerError.Io -> errorIo
    }
}

/**
 * O estado do visualizador: o que já foi carregado, quantas páginas há e em qual delas se está.
 *
 * Obtenha um com [rememberPdfViewerState] e entregue-o ao [PdfViewer].
 */
@Stable
class PdfViewerState internal constructor(
    /** Os textos, para as mensagens de falha. */
    val texts: PdfViewerTexts,
) {

    /** Em que ponto a abertura está. */
    var status: PdfViewerStatus by mutableStateOf(PdfViewerStatus.Loading)
        internal set

    /** Os bytes do documento, quando prontos. */
    var bytes: ByteArray? by mutableStateOf(null)
        internal set

    /** Quantas páginas o documento tem. `0` enquanto não abriu. */
    var pageCount: Int by mutableStateOf(0)
        internal set

    /** A página em que a rolagem está, **a partir de 1**. `0` enquanto não abriu. */
    var currentPage: Int by mutableStateOf(0)
        internal set

    /** O rótulo "3 de 12" pronto para a tela, ou `null` antes de o documento abrir. */
    val pageLabel: String?
        get() = if (pageCount > 0 && currentPage > 0) texts.pageIndicator(currentPage, pageCount) else null

    internal var reloadToken: Int by mutableStateOf(0)
        private set

    /** Tenta abrir de novo — o botão da tela de erro. Rebaixa quando a origem é remota. */
    fun retry() {
        reloadToken++
    }
}

/**
 * Abre [source] e devolve o estado, **reusando o que a lib já tem** para as duas metades chatas:
 * o `createHttpClient` do `kmplib-core` (com log de requisição e gzip) para baixar e o `BlobStore`
 * para guardar.
 *
 * ### Onde o arquivo fica, e por que não num cache de verdade
 * O `BlobStore` grava no diretório **privado e durável** do app (`filesDir` no Android, *Application
 * Support* no iOS) — de propósito, e não por falta de um cache: material de curso é exatamente o
 * que o aluno quer encontrar **sem sinal**, e o `cacheDir` do Android é apagado pelo sistema sob
 * pressão de espaço, justamente quando o aparelho está cheio. A contrapartida é que ninguém limpa
 * sozinho: o app é dono da faxina (`createPdfCache().delete(id)` / `ids()` / `totalBytes()`), o que
 * ele consegue fazer com regra de produto ("materiais de cursos concluídos"), e a lib não.
 *
 * @param source `null` = nada a abrir (o visualizador desenha o espaço reservado).
 * @param httpClient default: um cliente próprio, fechado junto com a composição.
 * @param cache default: [createPdfCache].
 */
@Composable
fun rememberPdfViewerState(
    source: PdfSource?,
    texts: PdfViewerTexts = PdfViewerTexts(),
    httpClient: HttpClient? = null,
    cache: BlobStore? = null,
): PdfViewerState {
    val state = remember(texts) { PdfViewerState(texts) }
    val armazem = remember(cache) { cache ?: createPdfCache() }

    LaunchedEffect(state, source, state.reloadToken) {
        if (source == null) {
            state.bytes = null
            state.pageCount = 0
            state.currentPage = 0
            state.status = PdfViewerStatus.Loading
            return@LaunchedEffect
        }
        state.status = PdfViewerStatus.Loading
        state.pageCount = 0
        state.currentPage = 0

        val resultado = loadPdfBytes(source, armazem, httpClient)
        resultado.fold(
            onSuccess = { conteudo ->
                state.bytes = conteudo
                // O `status` continua `Loading` até a plataforma abrir o documento e dizer quantas
                // páginas há: bytes em mãos não são um PDF legível, e é o `PdfViewer` que descobre.
            },
            onFailure = { falha ->
                val kind = (falha as? PdfLoadException)?.kind ?: PdfViewerError.Io
                AppLogger.e(PDF_VIEWER_TAG, "Falha ao abrir o PDF: ${falha.message}")
                state.bytes = null
                state.status = PdfViewerStatus.Error(
                    kind = kind,
                    message = texts.messageFor(kind),
                    cause = falha.message,
                )
            },
        )
    }

    return state
}

/**
 * O armazém dos PDFs baixados. Diretório próprio, para a faxina do app não esbarrar na fila de
 * upload (que usa o diretório default do `BlobStore`).
 */
fun createPdfCache(): BlobStore = createBlobStore(PDF_CACHE_DIRECTORY)

/** O diretório do cache de PDF dentro do armazenamento privado do app. */
const val PDF_CACHE_DIRECTORY: String = "kmplib_pdf_cache"

internal const val PDF_VIEWER_TAG = "KmpLibPdfViewer"

/** Falha tipada do carregamento — o que separa "sem rede" de "arquivo corrompido" na tela. */
internal class PdfLoadException(val kind: PdfViewerError, message: String) : Exception(message)

/**
 * Resolve [source] em bytes.
 *
 * O caminho remoto é: **cache primeiro, rede depois**. Ler o disco antes de tentar a rede é o que
 * faz o material abrir no avião — e o que evita rebaixar 4 MB de apostila a cada abertura da aula.
 */
internal suspend fun loadPdfBytes(
    source: PdfSource,
    cache: BlobStore,
    httpClient: HttpClient?,
): Result<ByteArray> = runCatching {
    when (source) {
        is PdfSource.Bytes -> source.bytes

        is PdfSource.LocalFile -> readLocalPdfFile(source.path)
            ?: throw PdfLoadException(PdfViewerError.NotFound, "arquivo não encontrado: ${source.path}")

        is PdfSource.Url -> {
            val id = source.cacheId ?: pdfCacheIdFor(source.url)
            cache.read(id) ?: baixarEGuardar(source.url, id, cache, httpClient)
        }
    }
}

private suspend fun baixarEGuardar(
    url: String,
    id: String,
    cache: BlobStore,
    httpClient: HttpClient?,
): ByteArray {
    val proprio = httpClient == null
    val client = httpClient ?: createHttpClient()
    try {
        val resposta = try {
            client.get(url)
        } catch (e: Exception) {
            throw PdfLoadException(PdfViewerError.Network, e.message ?: "falha de rede")
        }
        if (!resposta.status.isSuccess()) {
            val kind = if (resposta.status.value == 404) PdfViewerError.NotFound else PdfViewerError.Network
            throw PdfLoadException(kind, "HTTP ${resposta.status.value}")
        }
        val conteudo = resposta.bodyAsBytes()
        if (!looksLikePdf(conteudo)) {
            // Página de erro do CDN salva como `.pdf`, download truncado, token recusado com um
            // HTML de 200. Guardar isto no cache seria pior: o documento nunca mais abriria.
            throw PdfLoadException(PdfViewerError.Corrupted, "resposta não é um PDF")
        }
        if (!cache.write(id, conteudo)) {
            AppLogger.w(PDF_VIEWER_TAG, "Não foi possível guardar o PDF $id — segue só em memória")
        }
        return conteudo
    } finally {
        if (proprio) client.close()
    }
}

/**
 * `true` se os bytes começam com `%PDF-`, a assinatura do formato.
 *
 * Existe porque **um download que falha nem sempre falha**: o CDN devolve `200` com uma página de
 * erro, o proxy do wi-fi de hotel devolve um portal cativo, o token recusado vira um JSON. Sem esta
 * conferência, esse HTML entraria no cache com o nome do arquivo e o documento passaria a não abrir
 * **nunca mais**, mesmo com rede boa — um bug de cache que só some desinstalando o app.
 */
fun looksLikePdf(bytes: ByteArray): Boolean {
    if (bytes.size < PDF_MAGIC.size) return false
    return PDF_MAGIC.indices.all { bytes[it] == PDF_MAGIC[it] }
}

private val PDF_MAGIC = "%PDF-".encodeToByteArray()

/** Lê um arquivo local. `null` quando não existe ou não dá para ler. */
internal expect suspend fun readLocalPdfFile(path: String): ByteArray?
