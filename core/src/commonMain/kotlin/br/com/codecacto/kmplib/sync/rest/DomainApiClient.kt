package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.ReauthRequiredException
import br.com.codecacto.kmplib.core.network.RecentAuthChallenge
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.monetization.entitlement.QuotaExceeded
import br.com.codecacto.kmplib.monetization.entitlement.parseQuotaExceeded
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Cliente HTTP do **backend REST-CRUD de domínio** de um app (`/v1/...`) — a base de rede da camada
 * offline-first REST-CRUD ([OfflineFirstRestRepository]/[RestCrudSyncEngine]/[RestUploadQueue]).
 *
 * Promovido do padrão que a Onda 3 do onboarding replicava app a app (piloto MinhasHoras): a
 * [SyncEngine][br.com.codecacto.kmplib.sync.SyncEngine] `/pull`+`/push` (que continua para backends
 * com esse protocolo) **não encaixa** num backend REST-CRUD comum (`GET/POST/PUT/PATCH/DELETE` por
 * recurso). Este cliente é a peça de transporte desse cenário.
 *
 * ### Padrão-ouro (idêntico aos demais serviços REST da lib — `AdminApiEntitlementRepository`/`RestSyncPort`)
 * - **`ktor-client-core` puro** (SEM `ContentNegotiation`): os repositórios (de)serializam o payload
 *   com kotlinx-json. Use o [createHttpClient][br.com.codecacto.kmplib.core.network.createHttpClient]
 *   da lib (`expectSuccess=false`, para 4xx/5xx virarem resposta classificável aqui).
 * - **Bearer Firebase** (`Authorization: Bearer <ID token>`) via [DomainTokenProvider].
 *
 * ### Resiliência (memória `app-baseline-resilience-ux`)
 *  - **401 → refresh de token** (`token(forceRefresh = true)`) e **1 retry** automático — **exceto
 *    o 401 `REAUTH_REQUIRED`** (2.261.0), que volta direto como [DomainResult.Error.isReauthRequired]:
 *    renovar não muda o `auth_time`, e quem resolve é a pessoa provar a credencial de novo.
 *  - **402 → [DomainResult.Quota]** (paywall) — extrai o [QuotaExceeded] do corpo (`error.details`).
 *  - **429 → [DomainResult.Error]** amigável de rate-limit (semântica distinta da cota do plano).
 *  - Erros de rede/transporte **nunca lançam** para a UI: viram [DomainResult.Error] com
 *    `code == `[DomainResult.OFFLINE_CODE].
 *
 * ### Escopo de host (lição MeuFrete — nunca vazar token para outro host)
 * O token só é anexado às requisições **deste** cliente, cujo destino é sempre `baseUrl + path`
 * (host fixado na construção). O cliente **não instala** um `Authorization` default no [HttpClient],
 * então um `HttpClient` compartilhado com outros hosts nunca recebe o Bearer de domínio. Passe sempre
 * caminhos **relativos** ([getJson]/[postJson]/...); um caminho absoluto para outro host é rejeitado
 * ([DomainResult.Error] sem token anexado).
 *
 * ### Idioma das mensagens locais (2.219.0)
 * [texts] traz os defaults em pt-BR (este módulo não carrega Compose, e os recursos traduzidos moram
 * no `kmplib-ui`). App com mais de um idioma passa [textsProvider] — `{ loadDomainApiTexts() }` do
 * `kmplib-ui` lê os 4 idiomas da fábrica no idioma da tela, **a cada erro** (troca de idioma com o
 * processo vivo não deixa mensagem velha). Com [textsProvider], [texts] é ignorado.
 */
class DomainApiClient(
    private val httpClient: HttpClient,
    private val tokenProvider: DomainTokenProvider,
    baseUrl: String,
    private val texts: DomainApiTexts = DomainApiTexts(),
    private val textsProvider: (suspend () -> DomainApiTexts)? = null,
) {
    private val root: String = baseUrl.trimEnd('/')
    private val host: String = runCatching { Url(root).host }.getOrDefault("")

    /** Conveniência: constrói sobre o [IAuthRepository] da lib (Bearer = Firebase ID token). */
    constructor(
        httpClient: HttpClient,
        auth: IAuthRepository,
        baseUrl: String,
        texts: DomainApiTexts = DomainApiTexts(),
        textsProvider: (suspend () -> DomainApiTexts)? = null,
    ) : this(httpClient, auth.asDomainTokenProvider(), baseUrl, texts, textsProvider)

    /** Os textos da vez: o do provedor (idioma da tela) ou os fixos. Provedor que falha cai nos fixos. */
    private suspend fun currentTexts(): DomainApiTexts =
        textsProvider?.let { provedor ->
            try {
                provedor()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "textsProvider falhou; usando os textos fixos: ${e.message}")
                texts
            }
        } ?: texts

    private fun url(path: String): String = root + "/" + path.trimStart('/')

    /** Garante que o path é do mesmo host (nunca vaza o Bearer). */
    private fun sameHost(path: String): Boolean {
        val p = path.trim()
        if (!p.startsWith("http://", true) && !p.startsWith("https://", true)) return true
        return runCatching { Url(p).host == host }.getOrDefault(false)
    }

    // ---- JSON ------------------------------------------------------------

    /**
     * `GET` que devolve o corpo como texto.
     *
     * @param headers cabeçalhos extras desta chamada (2.185.0). Existe para a **capacidade de
     *   leitura sobre UM recurso** — o token que um produto sem conta entrega ao criar um registro
     *   e que autoriza consultar aquele registro depois. Ele não pode viajar na URL: credencial em
     *   query entra no log de acesso de todo intermediário, no histórico do navegador e no
     *   `Referer`. E não é `Authorization`: não há sessão nem portador de identidade, então usar
     *   aquele header faria clientes e proxies tratarem-no como credencial de sessão.
     */
    suspend fun getJson(
        path: String,
        headers: Map<String, String> = emptyMap(),
    ): DomainResult<String> =
        execute(path) { token ->
            httpClient.get(url(path)) {
                bearer(token)
                headers.forEach { (nome, valor) -> header(nome, valor) }
            }
        }.texto()

    /**
     * `GET` **condicional** por ETag (2.272.0) — RFC 9110 §8.8.3 / §13.1.2 / §15.4.5.
     *
     * Com [etag] não nulo, envia `If-None-Match: <etag>` (o valor **exatamente** como o servidor o
     * mandou: aspas e `W/` incluídos — ETag é opaco, não se monta nem se interpreta). A resposta vira:
     * - **304** → [EtagResult.NotModified] — o que o aparelho guardou continua valendo; o `etag` é o
     *   da resposta (o servidor deve repeti-lo) ou, se não veio, o que foi enviado;
     * - **2xx** → [EtagResult.Modified] com o corpo e o `ETag` da resposta (`null` se o servidor não
     *   mandou — aí não há o que guardar, e a próxima chamada vai sem condição). Se o `ETag` do 2xx
     *   **casa** com o enviado (comparação fraca, §8.8.3.2 — o caso de um intermediário que
     *   revalidou e devolveu 200 do próprio cache), vira [EtagResult.NotModified] também: é a mesma
     *   representação, e a tela não deve reprocessar nada;
     * - o resto → [DomainResult.Error]/[DomainResult.Quota], com o mesmo tratamento do [getJson]
     *   (401 → refresh + 1 retry levando o MESMO `If-None-Match`, 402 → cota, transporte nunca lança).
     *
     * Sem [etag] (primeira carga), é um `GET` comum que devolve o `ETag` para guardar; um 304 sem
     * condição enviada não tem significado e vira erro.
     *
     * ### Por que existe
     * O [getJson] devolve só o corpo: o `ETag` (cabeçalho) se perdia, então o app não tinha o valor
     * opaco para reenviar — e o 304 caía no ramo de erro ("Erro do servidor (304)"). Esta variante é
     * a forma oficial de economizar a carga de um snapshot grande (o programa de treino do aluno)
     * sem cache HTTP escondido no cliente.
     *
     * @param headers cabeçalhos extras, como no [getJson]. Um `If-None-Match` passado aqui é ignorado
     *   — quem decide a condição é [etag].
     */
    suspend fun getJsonWithEtag(
        path: String,
        etag: String?,
        headers: Map<String, String> = emptyMap(),
    ): DomainResult<EtagResult<String>> {
        val condicao = etag?.trim()?.takeIf { it.isNotEmpty() }
        return execute(path, notModifiedIsSuccess = condicao != null) { token ->
            httpClient.get(url(path)) {
                bearer(token)
                headers.forEach { (nome, valor) ->
                    if (!nome.equals(HttpHeaders.IfNoneMatch, ignoreCase = true)) header(nome, valor)
                }
                condicao?.let { header(HttpHeaders.IfNoneMatch, it) }
            }
        }.corpo { response ->
            val recebido = response.headers[HttpHeaders.ETag]?.trim()?.takeIf { it.isNotEmpty() }
            when {
                response.status.value == 304 -> EtagResult.NotModified(recebido ?: condicao!!)
                condicao != null && recebido != null && etagWeakMatch(condicao, recebido) ->
                    EtagResult.NotModified(recebido)
                else -> EtagResult.Modified(response.bodyAsText(), recebido)
            }
        }
    }

    suspend fun postJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.post(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.texto()

    suspend fun putJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.put(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.texto()

    suspend fun patchJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.patch(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.texto()

    suspend fun delete(path: String): DomainResult<Unit> =
        execute(path) { token -> httpClient.delete(url(path)) { bearer(token) } }.map { }

    /**
     * `DELETE` que **devolve o corpo** — para a API que responde com o estado depois de apagar.
     *
     * O [delete] acima descarta a resposta, e é o certo quando o servidor responde 204. Mas um
     * `DELETE` que devolve a lista já sem o item apagado é padrão comum, e com ele a tela não precisa
     * de uma segunda chamada para se atualizar — nem do intervalo em que o item sumiu da tela e os
     * contadores ainda não sabem disso. Sem esta variante, quem precisa do corpo escreve o `execute`
     * à mão no projeto e perde o tratamento de token, quota e erro que mora aqui.
     */
    suspend fun deleteJson(path: String): DomainResult<String> =
        execute(path) { token -> httpClient.delete(url(path)) { bearer(token) } }.texto()

    /**
     * `DELETE` que **leva corpo** — para a rota que identifica o que apagar por um valor que não
     * pode viajar na URL.
     *
     * O caso que a trouxe: desregistrar o aparelho no logout (`DELETE /dispositivos` com
     * `{"token": "..."}`). O token de push é a identidade do aparelho, e pôr um segredo desses num
     * segmento de caminho o entrega ao log de acesso de todo intermediário — a regra da fábrica é
     * que **toda** requisição registra método e URL. O corpo não vai para o log.
     *
     * A alternativa que este método evita é pior: sem ele, o projeto monta o `HttpClient` na mão
     * para uma chamada só, e perde de uma vez o 401→refresh, o 402→[DomainResult.Quota] e o
     * transporte que nunca lança.
     *
     * `DELETE` com corpo é permitido pela RFC 9110 §9.3.5 (sem semântica definida, e é por isso que
     * ela mora numa variante explícita, não no [delete] de sempre).
     */
    suspend fun deleteJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.delete(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.texto()

    // ---- Binário (anexos) ------------------------------------------------

    /** Upload multipart (campo `file`) de um binário autenticado — ex.: `POST /v1/.../anexos`. */
    suspend fun postMultipart(
        path: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        fieldName: String = "file",
    ): DomainResult<String> =
        postMultipartParts(path, listOf(MultipartPart(fieldName, fileName, fileBytes, mimeType)))

    /**
     * Upload multipart de **múltiplas partes nomeadas** num único request autenticado — ex.: uma
     * foto-prova enviando `full` + `thumb` (JPEG) juntas (`POST /v1/inspections/{id}/photos`). O
     * [postMultipart] de parte única delega a este. Mesma resiliência (401→refresh, host-scoped, 402→
     * quota, transporte nunca lança).
     */
    suspend fun postMultipartParts(
        path: String,
        parts: List<MultipartPart>,
    ): DomainResult<String> = postMultipartParts(path, parts, emptyMap())

    /**
     * Upload multipart via **PUT** — para recursos que já existem e cujo binário é *substituído*
     * (ex.: `PUT /v1/me/professionals/{id}/photo` troca a foto do profissional). Mesmo corpo e mesma
     * resiliência do [postMultipart]; muda só o verbo, porque a semântica é substituir, não criar.
     */
    suspend fun putMultipart(
        path: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        fieldName: String = "file",
    ): DomainResult<String> =
        putMultipartParts(path, listOf(MultipartPart(fieldName, fileName, fileBytes, mimeType)))

    /** Versão PUT do [postMultipartParts] (múltiplas partes nomeadas). */
    suspend fun putMultipartParts(
        path: String,
        parts: List<MultipartPart>,
    ): DomainResult<String> = putMultipartParts(path, parts, emptyMap())

    /**
     * Upload multipart com **campos de texto junto do arquivo** (2.216.0) — ex.: a foto de uma
     * lesão que precisa chegar com `"regiao" = "antebraco"` e `"capturadaEm" = "2026-09-26T10:12"`
     * no MESMO request.
     *
     * ### Por que no mesmo request, e não num `PATCH` depois
     * Dois requests são dois pontos de falha: a foto sobe, o `PATCH` cai na fila, e durante esse
     * intervalo — que offline pode durar dias — o servidor guarda uma foto **sem o dado que a
     * classifica**. Pior: se o campo é obrigatório no servidor, ele recusa a foto inteira, e a única
     * saída que sobrava ao app era montar o `HttpClient` na mão e perder o 401→refresh e o 402→quota
     * que moram aqui.
     *
     * ### Ordem no corpo
     * Os campos de texto vão **antes** das partes binárias. O `receiveMultipart` do Ktor (e todo
     * servidor que lê em fluxo) consegue validar o formulário antes de começar a gravar o arquivo —
     * recusar um upload de 5 MB depois de lê-lo inteiro, por causa de um campo que chegou depois
     * dele, é desperdício de banda de quem está no 3G.
     *
     * @param formFields `nome → valor`, na ordem do mapa. Nome em branco é ignorado. ⚠️ Os valores
     *   viajam no CORPO (não vão para o log de requisição), mas não coloque segredo aqui.
     */
    suspend fun postMultipartParts(
        path: String,
        parts: List<MultipartPart>,
        formFields: Map<String, String>,
    ): DomainResult<String> =
        execute(path) { token ->
            httpClient.post(url(path)) {
                bearer(token)
                setBody(multipartBody(parts, formFields))
            }
        }.texto()

    /** Versão PUT do [postMultipartParts] com campos de texto (2.216.0). */
    suspend fun putMultipartParts(
        path: String,
        parts: List<MultipartPart>,
        formFields: Map<String, String>,
    ): DomainResult<String> =
        execute(path) { token ->
            httpClient.put(url(path)) {
                bearer(token)
                setBody(multipartBody(parts, formFields))
            }
        }.texto()

    /** [postMultipart] de parte única com campos de texto junto (2.216.0). */
    suspend fun postMultipart(
        path: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        fieldName: String,
        formFields: Map<String, String>,
    ): DomainResult<String> =
        postMultipartParts(path, listOf(MultipartPart(fieldName, fileName, fileBytes, mimeType)), formFields)

    private fun multipartBody(
        parts: List<MultipartPart>,
        formFields: Map<String, String> = emptyMap(),
    ) = MultiPartFormDataContent(
        formData {
            formFields.forEach { (nome, valor) ->
                if (nome.isNotBlank()) append(nome, valor)
            }
            parts.forEach { part ->
                append(
                    key = part.fieldName,
                    value = part.bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, part.mimeType)
                        append(
                            HttpHeaders.ContentDisposition,
                            "filename=\"${part.fileName.ifBlank { part.fieldName.ifBlank { "anexo" } }}\"",
                        )
                    },
                )
            }
        },
    )

    /** Stream autenticado dos bytes de um binário — ex.: `GET /v1/anexos/{id}/bytes`. */
    suspend fun getBytes(path: String): DomainResult<ByteArray> =
        execute(path) { token -> httpClient.get(url(path)) { bearer(token) } }.bytes()

    /**
     * `POST` com corpo JSON cuja resposta é BINÁRIA (2.222.0) — ex.: `POST /v1/etiquetas/pdf` com
     * `{"ids":[…]}`, que devolve `application/pdf`.
     *
     * Existe porque documento gerado a partir de uma LISTA não cabe em `GET ?ids=`: com algumas
     * centenas de UUIDs a query estoura a linha inicial que o servidor aceita, e ele responde 400
     * antes de a aplicação ver o pedido (no ExtinRota isso acontecia a partir de ~110 ids). Mesmo
     * tratamento das demais chamadas: Bearer, refresh + 1 retry no 401, 402 vira [DomainResult.Quota],
     * envelope de erro da backlib em [DomainResult.Error], e o corpo lido sob a mesma proteção de
     * [getBytes] (conexão caída no meio do download vira erro, não corrotina morta).
     */
    suspend fun postJsonForBytes(path: String, body: String): DomainResult<ByteArray> =
        execute(path) { token ->
            httpClient.post(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.bytes()

    // ---- Núcleo ----------------------------------------------------------

    private fun HttpRequestBuilder.bearer(token: String?) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    /**
     * Lê o CORPO da resposta sob a mesma proteção do [execute].
     *
     * ## O furo que isto fecha
     *
     * O `try/catch` do [execute] cobre a requisição — mas o corpo só é lido **depois** que ele
     * retornou. Escrito como `execute(...).map { it.bodyAsText() }`, o download acontecia FORA de
     * qualquer proteção: uma conexão derrubada no meio da leitura (o caso comum num payload grande
     * e demorado) lançava, a exceção subia pelo repositório e caía no `launch` do `BaseViewModel`,
     * que não captura nada. A corrotina morria em silêncio — sem log, sem erro na tela, com o
     * `carregando` aceso. **A tela girava para sempre.**
     *
     * Diagnosticado em 26/ago/2026 no relatório de 10 páginas do NeuroCoreX, gerado sob demanda: o
     * defeito só aparecia na PRIMEIRA abertura, que é a mais lenta. Todo consumidor de
     * `getJson`/`postJson`/`getBytes` da fábrica estava exposto ao mesmo travamento mudo.
     */
    private suspend fun DomainResult<HttpResponse>.texto(): DomainResult<String> =
        corpo { it.bodyAsText() }

    /** Par de [texto] para binário — mesma proteção, ver o KDoc acima. */
    private suspend fun DomainResult<HttpResponse>.bytes(): DomainResult<ByteArray> =
        corpo { it.readRawBytes() }

    private suspend fun <T> DomainResult<HttpResponse>.corpo(
        ler: suspend (HttpResponse) -> T,
    ): DomainResult<T> = when (this) {
        is DomainResult.Success -> try {
            DomainResult.Success(ler(data))
        } catch (e: CancellationException) {
            // Encerramento normal do escopo (a pessoa saiu da tela). Engolir isto pintaria erro de
            // rede toda vez que uma tela fosse fechada no meio de uma chamada.
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao ler o corpo da resposta: ${e.message}")
            DomainResult.Error(code = DomainResult.OFFLINE_CODE, message = currentTexts().offline)
        }
        // `Error` e `Quota` não têm corpo a ler — atravessam intactos. O `Quota` é o 402 do
        // paywall, cujo contexto já foi extraído em `classify`; relê-lo aqui consumiria um corpo
        // que já acabou.
        is DomainResult.Error -> this
        is DomainResult.Quota -> this
    }

    /**
     * Executa a requisição com o ID token corrente; em **401**, força refresh e tenta 1 vez de novo.
     * Classifica o status em [DomainResult]. Toda exceção de transporte vira [DomainResult.Error].
     */
    private suspend fun execute(
        path: String,
        notModifiedIsSuccess: Boolean = false,
        block: suspend (token: String?) -> HttpResponse,
    ): DomainResult<HttpResponse> {
        if (!sameHost(path)) {
            AppLogger.w(TAG, "Requisição bloqueada: path de outro host ($path) — token não anexado.")
            return DomainResult.Error(DomainResult.OFFLINE_CODE, currentTexts().offline)
        }
        return try {
            val token = tokenProvider.token(forceRefresh = false)
            val response = block(token)
            when {
                response.status.value != 401 -> classify(response, notModifiedIsSuccess)
                // 2.261.0: o 401 de REAUTENTICAÇÃO não renova nem repete. O refresh preserva o
                // `auth_time`, então a repetição voltaria o mesmo 401 (gastando uma rotação de
                // refresh) — e quem resolve é a pessoa provar a credencial (`withRecentAuth`).
                isReauthChallenge(response) -> classify(response, notModifiedIsSuccess)
                else -> {
                    val fresh = tokenProvider.token(forceRefresh = true)
                    classify(block(fresh), notModifiedIsSuccess)
                }
            }
        } catch (e: CancellationException) {
            // Sair da tela cancela o escopo, e o `catch (Exception)` abaixo capturaria isso como se
            // fosse falha de rede — a tela seguinte nasceria com "sem conexão" sem nunca ter feito
            // uma chamada.
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha de transporte em requisição de domínio: ${e.message}")
            DomainResult.Error(code = DomainResult.OFFLINE_CODE, message = currentTexts().offline)
        }
    }

    private suspend fun classify(
        response: HttpResponse,
        notModifiedIsSuccess: Boolean = false,
    ): DomainResult<HttpResponse> {
        val status = response.status.value
        return when {
            status in 200..299 -> DomainResult.Success(response)
            // 304 só é resposta válida a um GET condicional ([getJsonWithEtag]); fora dele é erro.
            status == 304 && notModifiedIsSuccess -> DomainResult.Success(response)
            status == 402 -> {
                val quota = parseQuotaExceeded(runCatching { response.bodyAsText() }.getOrNull())
                if (quota != null) DomainResult.Quota(quota) else DomainResult.Error(status, currentTexts().quotaReached)
            }
            else -> {
                val envelope = envelopeDeErro(response)
                val wwwAuthenticate = response.headers[HttpHeaders.WWWAuthenticate]
                val reauth = RecentAuthChallenge.matches(status, envelope?.code, wwwAuthenticate)
                val mensagemLocal = when {
                    reauth -> currentTexts().reauthRequired
                    status == 429 -> currentTexts().rateLimited
                    status == 401 -> currentTexts().sessionExpired
                    else -> currentTexts().serverError(status)
                }
                val detalhes = envelope?.details.orEmpty()
                DomainResult.Error(
                    code = status,
                    message = mensagemLocal,
                    // Só o cabeçalho veio (corpo vazio de um proxy): o código é o do desafio.
                    serverCode = if (reauth) RecentAuthChallenge.CODE else envelope?.code,
                    serverMessage = envelope?.message,
                    details = if (reauth && !detalhes.containsKey(RecentAuthChallenge.DETAIL_MAX_AGE)) {
                        RecentAuthChallenge.maxAgeSeconds(detalhes, wwwAuthenticate)
                            ?.let { detalhes + (RecentAuthChallenge.DETAIL_MAX_AGE to it.toString()) }
                            ?: detalhes
                    } else {
                        detalhes
                    },
                )
            }
        }
    }

    /** `true` se o 401 é o desafio de step-up (RFC 9470), não sessão inválida. */
    private suspend fun isReauthChallenge(response: HttpResponse): Boolean =
        RecentAuthChallenge.matches(
            response.status.value,
            envelopeDeErro(response)?.code,
            response.headers[HttpHeaders.WWWAuthenticate],
        )

    private suspend fun envelopeDeErro(response: HttpResponse): ServerErrorEnvelope? =
        parseServerErrorEnvelope(runCatching { response.bodyAsText() }.getOrNull())

    companion object {
        private const val TAG = "DomainApi"
    }
}

/**
 * Resultado de um `GET` condicional por ETag ([DomainApiClient.getJsonWithEtag]).
 *
 * O `toString` não imprime o corpo (pode ser dado de saúde, de cadastro…): só se mudou e o ETag, que
 * é um identificador opaco de versão.
 */
sealed class EtagResult<out T> {
    /** O ETag que o aparelho deve guardar e mandar no próximo `If-None-Match` (`null` = não veio). */
    abstract val etag: String?

    /** A representação mudou (ou é a primeira carga): [body] novo e o [etag] dele. */
    data class Modified<T>(val body: T, override val etag: String?) : EtagResult<T>() {
        override fun toString(): String = "EtagResult.Modified(etag=$etag)"
    }

    /** 304 — o que o aparelho já tem continua valendo. */
    data class NotModified(override val etag: String) : EtagResult<Nothing>()
}

/**
 * Comparação **fraca** de entity-tags (RFC 9110 §8.8.3.2), a que vale para `If-None-Match`: iguais
 * depois de tirar o prefixo `W/` dos dois lados. `"abc"` casa com `W/"abc"`; `"abc"` não casa com
 * `"ABC"`.
 */
internal fun etagWeakMatch(a: String, b: String): Boolean {
    fun opaca(tag: String): String = tag.trim().let { if (it.startsWith("W/")) it.substring(2) else it }
    return opaca(a) == opaca(b)
}

/**
 * Uma parte nomeada de um upload multipart ([DomainApiClient.postMultipartParts]). Ex.: a foto-prova
 * de uma vistoria envia duas partes (`full` e `thumb`) num único request.
 */
class MultipartPart(
    val fieldName: String,
    val fileName: String,
    val bytes: ByteArray,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultipartPart) return false
        return fieldName == other.fieldName &&
            fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fieldName.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * Fornece o **Firebase ID token** para o [DomainApiClient]. `forceRefresh = true` no retry pós-401.
 * `null` = sem header (usuário deslogado — o backend responde 401 e a UI reage).
 */
fun interface DomainTokenProvider {
    suspend fun token(forceRefresh: Boolean): String?
}

/** Adapta o [IAuthRepository] da lib a um [DomainTokenProvider] (Bearer = Firebase ID token). */
fun IAuthRepository.asDomainTokenProvider(): DomainTokenProvider =
    DomainTokenProvider { forceRefresh -> getIdToken(forceRefresh).getOrNull() }

/** Mensagens de erro do [DomainApiClient] (defaults pt-BR; injete traduções via `*Texts` do app). */
data class DomainApiTexts(
    val offline: String = "Sem conexão com o servidor.",
    val rateLimited: String = "Muitas requisições. Tente novamente em instantes.",
    val sessionExpired: String = "Sessão expirada. Entre novamente.",
    /** 401 `REAUTH_REQUIRED` (2.261.0): a sessão vale, a ação pede a credencial de novo. */
    val reauthRequired: String = ReauthRequiredException.DEFAULT_MESSAGE,
    val quotaReached: String = "Limite atingido.",
    val serverError: (Int) -> String = { code -> "Erro do servidor ($code)." },
)

/**
 * Resultado tipado de uma chamada ao backend de domínio. Distingue **cota estourada** (402 → paywall)
 * de erro comum — os repositórios propagam essa distinção para os ViewModels (abrir o Paywall vs. erro).
 */
sealed class DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>()

    /** 402 — cota estourada; abre o Paywall com o contexto ([QuotaExceeded]). */
    data class Quota(val quota: QuotaExceeded) : DomainResult<Nothing>()

    /**
     * @param code o status HTTP, ou [OFFLINE_CODE] quando nem chegou a haver resposta.
     * @param serverCode o **código de negócio** que o backend mandou no corpo (`{"code": "..."}`).
     *
     * `serverCode` existe porque o status sozinho não distingue dois erros diferentes com o mesmo
     * número: um 409 pode ser "o resultado ainda está sendo preparado" e outro "esta resposta já
     * foi enviada", e a tela precisa dizer coisas opostas em cada caso. Sem ele, cada app ou
     * adivinhava pelo status — acertando por acaso enquanto houvesse um 409 só na rota — ou refazia
     * a chamada para ler o corpo que este cliente tinha acabado de descartar.
     *
     * É `null` quando o corpo não é JSON, não tem `code`, ou o erro é de transporte.
     *
     * @param message a frase **local** (dos [DomainApiTexts]) — genérica, sempre presente.
     * @param serverMessage a `message` que o backend mandou no corpo (2.216.0). É a frase que diz o
     *   motivo real ("A data não pode ser futura"); `null` quando não veio. Para mostrar ao usuário,
     *   prefira [userMessage], que escolhe entre as duas.
     * @param details os **erros por campo** do envelope da backlib (2.216.0) — `{nome do campo no
     *   DTO: frase}`, o que `ValidationException.forField`/`FieldErrors` do servidor produzem. Vazio
     *   quando o erro não é de campo. Ver [fieldError] e [fieldErrors].
     *
     * ### Por que `details` chegou aqui
     * A constituição manda o erro de campo ficar **no campo** (borda vermelha + frase embaixo) e só o
     * resto ir para o banner junto do botão. O servidor já mandava `details`; o cliente o descartava
     * junto com o corpo, e a tela só tinha "Erro do servidor (400)" para pôr num banner — sem dizer
     * qual das sete linhas corrigir.
     */
    data class Error(
        val code: Int,
        val message: String,
        val serverCode: String? = null,
        val serverMessage: String? = null,
        val details: Map<String, String> = emptyMap(),
    ) : DomainResult<Nothing>() {

        /** A frase do servidor para [field], ou `null` se aquele campo não foi recusado. */
        fun fieldError(field: String): String? = details[field]

        /**
         * Os erros de campo — o mesmo [details], com nome que diz o que é. Mapa na ordem em que o
         * servidor mandou: o primeiro é o campo que deve receber o foco.
         */
        val fieldErrors: Map<String, String> get() = details

        /**
         * `true` no 401 `REAUTH_REQUIRED` (2.261.0) — a ação sensível pede que a pessoa prove a
         * credencial de novo. **Não é logout**: não limpe a sessão; passe a ação pelo
         * `RecentAuthCoordinator.withRecentAuth`. Ver [RecentAuthChallenge].
         */
        val isReauthRequired: Boolean get() = code == 401 && serverCode == RecentAuthChallenge.CODE

        /** A janela exigida pelo servidor (segundos), quando [isReauthRequired]. */
        val reauthMaxAgeSeconds: Long? get() = RecentAuthChallenge.maxAgeSeconds(details, null)

        /** O erro tipado para atravessar um `Result` (`Result.failure(e.toReauthRequiredException())`). */
        fun toReauthRequiredException(): ReauthRequiredException =
            ReauthRequiredException(reauthMaxAgeSeconds, message)

        /** `true` se o servidor apontou ao menos um campo. */
        val hasFieldErrors: Boolean get() = details.isNotEmpty()

        /**
         * A frase para a tela: a do **servidor** em recusa de negócio/validação (4xx, exceto 401 e
         * 429 — "sessão expirada" e "muitas requisições" são textos locais de propósito), e a
         * [message] local no resto. 5xx nunca mostra a frase do servidor: ela é para o log, não para
         * quem está usando o app.
         */
        val userMessage: String
            get() = serverMessage
                ?.takeIf { code in 400..499 && code != 401 && code != 429 }
                ?: message
    }

    inline fun <R> map(transform: (T) -> R): DomainResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Quota -> this
        is Error -> this
    }

    fun getOrNull(): T? = (this as? Success)?.data
    val isSuccess: Boolean get() = this is Success

    /** `true` se é erro de transporte/sem-rede (código sentinela [OFFLINE_CODE]). */
    val isOffline: Boolean get() = this is Error && code == OFFLINE_CODE

    companion object {
        /** Código sentinela de "sem conexão / falha de transporte" (não é um status HTTP). */
        const val OFFLINE_CODE: Int = -1
    }
}

/**
 * O envelope de erro que a backlib devolve (`backlib-errors` → `ErrorResponse`):
 * `{"message": ..., "code": ..., "details": {campo: frase}, "traceId": ...}`.
 *
 * Aceita também a forma aninhada `{"ok": false, "error": {...}}`, que é a do 402 de cota.
 */
data class ServerErrorEnvelope(
    val code: String? = null,
    val message: String? = null,
    val details: Map<String, String> = emptyMap(),
)

/**
 * Lê o [ServerErrorEnvelope] de um corpo de erro. **Nunca lança**: corpo vazio, HTML de um proxy,
 * JSON sem os campos — tudo devolve `null` (ou um envelope com os campos nulos). Um erro de
 * transporte não pode virar um segundo erro dentro do tratamento do primeiro.
 *
 * Em `details`, só entram valores **primitivos** (a frase do campo); objeto ou lista aninhados são
 * ignorados em vez de virarem `toString()` de JSON numa legenda de formulário.
 */
fun parseServerErrorEnvelope(body: String?): ServerErrorEnvelope? = runCatching {
    val texto = body?.trim().orEmpty()
    if (!texto.startsWith("{")) return@runCatching null
    val raiz = errorEnvelopeJson.parseToJsonElement(texto).jsonObject
    val alvo = (raiz["error"] as? JsonObject) ?: raiz
    fun primitivo(nome: String): String? =
        (alvo[nome] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val detalhes = (alvo["details"] as? JsonObject)
        ?.mapNotNull { (campo, valor) ->
            val frase = (valor as? JsonPrimitive)?.contentOrNull?.trim()
            if (campo.isBlank() || frase.isNullOrEmpty()) null else campo to frase
        }
        ?.toMap()
        .orEmpty()
    ServerErrorEnvelope(code = primitivo("code"), message = primitivo("message"), details = detalhes)
}.getOrNull()

private val errorEnvelopeJson = Json { ignoreUnknownKeys = true; isLenient = true }
