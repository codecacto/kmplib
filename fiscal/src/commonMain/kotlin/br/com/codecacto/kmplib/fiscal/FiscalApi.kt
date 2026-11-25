package br.com.codecacto.kmplib.fiscal

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.MultipartPart
import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

// ───────────────────────────── Erro ─────────────────────────────

/** Tipo do erro de uma chamada fiscal — decide a frase e a saída na tela (mesmos tipos da weblib). */
enum class FiscalErrorKind {
    /** Recusa com erro por campo (400/422): o campo fica vermelho. */
    FIELDS,
    FORBIDDEN,
    /** 404 `NOT_FOUND`: o módulo não está liberado para a conta (o mesmo 404 de rota inexistente). */
    DISABLED,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMIT,
    /** 503: serviço de nota fora do ar (ou credencial do produto recusada). */
    UNAVAILABLE,
    /** 503 `FISCAL_RESULTADO_INCERTO`: o serviço confere com a prefeitura; repetir com a MESMA chave. */
    UNCERTAIN,
    NETWORK,
    SERVER,
}

/**
 * O erro de uma chamada, pronto para a tela: [kind] + [code] do servidor + a frase dele ([serverMessage], quando
 * útil) + erro por campo ([fields], caminho do pedido/cadastro). A frase final é da tela ([fiscalErrorMessage]).
 */
data class FiscalError(
    val kind: FiscalErrorKind,
    val code: String? = null,
    val serverMessage: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val httpStatus: Int = 0,
)

/** Desfecho de uma chamada fiscal. Nunca lança (o cancelamento da corrotina segue o curso normal). */
sealed interface FiscalResult<out T> {
    data class Ok<T>(val value: T) : FiscalResult<T>
    data class Failure(val error: FiscalError) : FiscalResult<Nothing>
}

inline fun <T, R> FiscalResult<T>.map(f: (T) -> R): FiscalResult<R> = when (this) {
    is FiscalResult.Ok -> FiscalResult.Ok(f(value))
    is FiscalResult.Failure -> this
}

fun <T> FiscalResult<T>.getOrNull(): T? = (this as? FiscalResult.Ok)?.value
fun <T> FiscalResult<T>.errorOrNull(): FiscalError? = (this as? FiscalResult.Failure)?.error

/** `code`s que a `backlib-fiscal` devolve e a tela trata de um jeito próprio. */
object FiscalErrorCodes {
    const val NAO_HABILITADO = "NOT_FOUND"
    const val EMISSOR_NAO_CADASTRADO = "FISCAL_EMISSOR_NAO_CADASTRADO"
    const val NOTA_NAO_ENCONTRADA = "FISCAL_NOTA_NAO_ENCONTRADA"
    const val SUGESTAO_INDISPONIVEL = "FISCAL_SUGESTAO_INDISPONIVEL"
    const val INDISPONIVEL = "FISCAL_INDISPONIVEL"
    const val RESULTADO_INCERTO = "FISCAL_RESULTADO_INCERTO"
    const val CREDENCIAL_RECUSADA = "FISCAL_CREDENCIAL_RECUSADA"
    const val CERTIFICADO_GRANDE = "FISCAL_CERTIFICADO_GRANDE"
    const val CERTIFICADO_FORA_DA_ICP_BRASIL = "CERTIFICADO_FORA_DA_ICP_BRASIL"
    const val TOKEN_TENTATIVAS_EXCEDIDAS = "TOKEN_TENTATIVAS_EXCEDIDAS"
    const val DOCUMENTO_JA_EM_SUBSTITUICAO = "DOCUMENTO_JA_EM_SUBSTITUICAO"

    /** O `code` do serviço chega como veio ou com prefixo do produto (`FISCAL_…`). */
    fun matches(code: String?, expected: String): Boolean = code == expected || (code?.endsWith("_$expected") == true)
}

/** O [DomainResult.Error] do cliente da lib → [FiscalError]. */
fun fiscalErrorOf(e: DomainResult.Error): FiscalError {
    val s = e.code
    val code = e.serverCode
    val fields = e.details
    val msg = e.serverMessage
    fun err(kind: FiscalErrorKind, fieldsOut: Map<String, String> = fields, message: String? = msg) =
        FiscalError(kind, code, message, fieldsOut, s)
    return when {
        FiscalErrorCodes.matches(code, FiscalErrorCodes.CERTIFICADO_FORA_DA_ICP_BRASIL) -> err(FiscalErrorKind.FIELDS, fields + ("arquivo" to (msg ?: "")))
        FiscalErrorCodes.matches(code, FiscalErrorCodes.TOKEN_TENTATIVAS_EXCEDIDAS) -> err(FiscalErrorKind.RATE_LIMIT)
        FiscalErrorCodes.matches(code, FiscalErrorCodes.DOCUMENTO_JA_EM_SUBSTITUICAO) -> err(FiscalErrorKind.CONFLICT)
        code == FiscalErrorCodes.RESULTADO_INCERTO -> err(FiscalErrorKind.UNCERTAIN)
        code == FiscalErrorCodes.CREDENCIAL_RECUSADA -> err(FiscalErrorKind.UNAVAILABLE)
        code == FiscalErrorCodes.INDISPONIVEL || s == 502 || s == 503 || s == 504 -> err(FiscalErrorKind.UNAVAILABLE)
        code == FiscalErrorCodes.CERTIFICADO_GRANDE || s == 413 -> err(FiscalErrorKind.FIELDS)
        s == 400 || s == 422 -> err(FiscalErrorKind.FIELDS)
        s == 403 -> err(FiscalErrorKind.FORBIDDEN)
        s == 404 && (code == FiscalErrorCodes.NAO_HABILITADO || code == null) -> err(FiscalErrorKind.DISABLED)
        s == 404 -> err(FiscalErrorKind.NOT_FOUND)
        s == 409 -> err(FiscalErrorKind.CONFLICT)
        s == 429 -> err(FiscalErrorKind.RATE_LIMIT)
        s == DomainResult.OFFLINE_CODE || s <= 0 -> err(FiscalErrorKind.NETWORK, message = null)
        else -> err(FiscalErrorKind.SERVER)
    }
}

// ───────────────────────────── Cliente ─────────────────────────────

/** Rotas do produto (relativas à base, ex. `/locaki/v2/fiscal`) — as da `fiscalProductRoutes` da backlib. */
object FiscalRoutes {
    const val STATUS = "/status"
    const val EMISSOR = "/emissor"
    const val CERTIFICADO = "/emissor/certificado"
    const val SERVICOS = "/catalogos/servicos"
    const val NBS = "/catalogos/nbs"
    const val PREVIA = "/notas/previa"
    const val NOTAS = "/notas"
    const val SUGESTAO = "/sugestao-tomador"
    fun parametros(ibge: String) = "/municipios/${seg(ibge)}/parametros"
    fun nota(id: String) = "/notas/${seg(id)}"
    fun pdf(id: String) = "/notas/${seg(id)}/pdf"
    fun xml(id: String) = "/notas/${seg(id)}/xml"
    fun cancelar(id: String) = "/notas/${seg(id)}/cancelar"
    fun substituir(id: String) = "/notas/${seg(id)}/substituir"
    fun reconsultar(id: String) = "/notas/${seg(id)}/reconsultar"
    private fun seg(v: String) = v.encodeURLQueryComponent(encodeFull = true)
}

/** Cabeçalho de idempotência da emissão e da substituição (obrigatório: 400 sem ele). */
const val IDEMPOTENCY_KEY_HEADER: String = "Idempotency-Key"

/**
 * O cliente das rotas do PRODUTO (`fiscalProductRoutes` da `backlib-fiscal`), sobre o [DomainApiClient] da lib — o
 * mesmo `HttpClient` do `createHttpClient` (log, gzip, nova tentativa de GET) e o token da sessão do app (401 →
 * renova e repete). O app nunca vê a URL do Fiscal Central.
 *
 * @param basePath onde o produto montou as rotas — ex. `/locaki/v2/fiscal`.
 */
class FiscalApi(private val client: DomainApiClient, basePath: String) {
    private val base = basePath.trimEnd('/')
    private fun p(route: String) = base + route

    /** Sem o módulo (404) devolve [FiscalStatusResposta.DESABILITADO] em vez de erro. */
    suspend fun status(): FiscalResult<FiscalStatusResposta> = when (val r = client.getJson(p(FiscalRoutes.STATUS))) {
        is DomainResult.Success -> decode(r.data, FiscalStatusResposta.serializer())
        is DomainResult.Error -> if (r.code == 404) FiscalResult.Ok(FiscalStatusResposta.DESABILITADO) else fail(r)
        is DomainResult.Quota -> quota()
    }

    /** `null` quando o cadastro ainda não existe (404 `FISCAL_EMISSOR_NAO_CADASTRADO`). */
    suspend fun emitter(): FiscalResult<FiscalEmissor?> = when (val r = client.getJson(p(FiscalRoutes.EMISSOR))) {
        is DomainResult.Success -> decode(r.data, FiscalEmissor.serializer())
        is DomainResult.Error -> if (r.code == 404 && r.serverCode == FiscalErrorCodes.EMISSOR_NAO_CADASTRADO) FiscalResult.Ok(null) else fail(r)
        is DomainResult.Quota -> quota()
    }

    suspend fun saveEmitter(input: FiscalEmissorInput): FiscalResult<FiscalEmissor> =
        client.putJson(p(FiscalRoutes.EMISSOR), json.encodeToString(FiscalEmissorInput.serializer(), input)).decoded(FiscalEmissor.serializer())

    /** `PUT {base}/emissor/certificado` em multipart (`arquivo` + `senha`). A senha vai só no corpo, nunca no log. */
    suspend fun uploadCertificate(bytes: ByteArray, fileName: String, password: String): FiscalResult<FiscalEmissor> =
        client.putMultipartParts(
            p(FiscalRoutes.CERTIFICADO),
            listOf(MultipartPart("arquivo", fileName.ifBlank { "certificado.pfx" }, bytes, "application/x-pkcs12")),
            mapOf("senha" to password),
        ).decoded(FiscalEmissor.serializer())

    suspend fun removeCertificate(): FiscalResult<Unit> = when (val r = client.delete(p(FiscalRoutes.CERTIFICADO))) {
        is DomainResult.Success -> FiscalResult.Ok(Unit)
        is DomainResult.Error -> fail(r)
        is DomainResult.Quota -> quota()
    }

    suspend fun municipalParameters(ibge: String): FiscalResult<FiscalParametrosMunicipais> =
        client.getJson(p(FiscalRoutes.parametros(ibge))).decoded(FiscalParametrosMunicipais.serializer())

    suspend fun searchServices(q: String): FiscalResult<List<FiscalCatalogoItem>> =
        client.getJson(p(FiscalRoutes.SERVICOS) + query("q" to q.trim())).decoded(FiscalCatalogo.serializer()).map { c -> c.items.filter { it.codigo.isNotBlank() } }

    suspend fun searchNbs(q: String): FiscalResult<List<FiscalCatalogoItem>> =
        client.getJson(p(FiscalRoutes.NBS) + query("q" to q.trim())).decoded(FiscalCatalogo.serializer()).map { c -> c.items.filter { it.codigo.isNotBlank() } }

    suspend fun preview(pedido: NfsePedido): FiscalResult<NfsePrevia> =
        client.postJson(p(FiscalRoutes.PREVIA), json.encodeToString(NfsePedido.serializer(), pedido)).decoded(NfsePrevia.serializer())

    suspend fun issue(pedido: NfsePedido, idempotencyKey: String): FiscalResult<FiscalDocumentoAceito> =
        client.postJson(
            p(FiscalRoutes.NOTAS),
            json.encodeToString(NfsePedido.serializer(), pedido),
            headers = mapOf(IDEMPOTENCY_KEY_HEADER to idempotencyKey),
        ).decoded(FiscalDocumentoAceito.serializer())

    suspend fun invoices(filter: FiscalDocumentoFiltro = FiscalDocumentoFiltro()): FiscalResult<FiscalDocumentosPagina> =
        client.getJson(p(FiscalRoutes.NOTAS) + invoicesQuery(filter)).decoded(FiscalDocumentosPagina.serializer())

    suspend fun invoice(id: String): FiscalResult<FiscalDocumento> =
        client.getJson(p(FiscalRoutes.nota(id))).decoded(FiscalDocumento.serializer())

    suspend fun cancel(id: String, input: FiscalCancelamento): FiscalResult<FiscalDocumento> =
        client.postJson(p(FiscalRoutes.cancelar(id)), json.encodeToString(FiscalCancelamento.serializer(), input)).decoded(FiscalDocumento.serializer())

    suspend fun substitute(id: String, input: FiscalSubstituicao, idempotencyKey: String): FiscalResult<FiscalDocumentoAceito> =
        client.postJson(
            p(FiscalRoutes.substituir(id)),
            json.encodeToString(FiscalSubstituicao.serializer(), input),
            headers = mapOf(IDEMPOTENCY_KEY_HEADER to idempotencyKey),
        ).decoded(FiscalDocumentoAceito.serializer())

    suspend fun reconsult(id: String): FiscalResult<FiscalDocumento> =
        client.postJson(p(FiscalRoutes.reconsultar(id)), "{}").decoded(FiscalDocumento.serializer())

    suspend fun pdf(id: String, numeroNfse: String?): FiscalResult<FiscalArquivo> =
        bytes(p(FiscalRoutes.pdf(id))).map { FiscalArquivo(it, invoiceFileName(id, numeroNfse, "pdf"), "application/pdf") }

    suspend fun xml(id: String, numeroNfse: String?): FiscalResult<FiscalArquivo> =
        bytes(p(FiscalRoutes.xml(id))).map { FiscalArquivo(it, invoiceFileName(id, numeroNfse, "xml"), "application/xml") }

    /** Sugestão para abrir a emissão a partir de um registro do produto (`locacao:123`). `null` sem sugestão. */
    suspend fun suggestion(ref: String): FiscalResult<TomadorSugerido?> = when (val r = client.getJson(p(FiscalRoutes.SUGESTAO) + query("ref" to ref))) {
        is DomainResult.Success -> decode(r.data, TomadorSugerido.serializer())
        is DomainResult.Error -> if (r.code == 404) FiscalResult.Ok(null) else fail(r)
        is DomainResult.Quota -> quota()
    }

    private suspend fun bytes(path: String): FiscalResult<ByteArray> = when (val r = client.getBytes(path, DOWNLOAD_TIMEOUT_MILLIS)) {
        is DomainResult.Success -> FiscalResult.Ok(r.data)
        is DomainResult.Error -> fail(r)
        is DomainResult.Quota -> quota()
    }

    private fun <T> DomainResult<String>.decoded(serializer: KSerializer<T>): FiscalResult<T> = when (this) {
        is DomainResult.Success -> decode(data, serializer)
        is DomainResult.Error -> fail(this)
        is DomainResult.Quota -> quota()
    }

    private fun <T> decode(body: String, serializer: KSerializer<T>): FiscalResult<T> =
        runCatching { json.decodeFromString(serializer, body) }
            .fold({ FiscalResult.Ok(it) }, { FiscalResult.Failure(FiscalError(FiscalErrorKind.SERVER)) })

    private fun fail(e: DomainResult.Error): FiscalResult.Failure = FiscalResult.Failure(fiscalErrorOf(e))

    // O módulo fiscal não tem cota por plano; um 402 aqui é recusa de acesso.
    private fun quota(): FiscalResult.Failure = FiscalResult.Failure(FiscalError(FiscalErrorKind.FORBIDDEN, httpStatus = 402))

    companion object {
        /** PDF/XML de nota: o servidor gera na hora; 60 s cobre a rede móvel. */
        const val DOWNLOAD_TIMEOUT_MILLIS: Long = 60_000L

        internal val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            // Nulo não vai no corpo: ausente = herda o padrão do emissor.
            explicitNulls = false
            coerceInputValues = true
        }

        internal fun query(vararg params: Pair<String, String?>): String {
            val parts = params.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { "$k=${it.encodeURLQueryComponent(encodeFull = true)}" } }
            return if (parts.isEmpty()) "" else parts.joinToString("&", prefix = "?")
        }

        /** Query da lista: o que veio no filtro + página e tamanho. */
        fun invoicesQuery(f: FiscalDocumentoFiltro): String = query(
            "status" to f.status,
            "ref" to f.ref,
            "de" to f.de,
            "ate" to f.ate,
            "q" to f.q?.trim(),
            "pagina" to f.pagina.toString(),
            "tamanho" to f.tamanho.toString(),
        )
    }
}
