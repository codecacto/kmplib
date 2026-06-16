package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.TimeSource

/**
 * Implementacao canonica de [EntitlementRepository] que LE plano/entitlement/uso do `admin-api`
 * central e faz o `assert` de cota — fonte unica de verdade (contrato `/monet/{slug}/...`,
 * `AdminUnificado/admin-api/docs/03-monetizacao-contrato.md`). Funciona no arquetipo D (backend
 * proprio) e em apps Firestore-only (abordagem B): o ponto de leitura/verificacao de monetizacao e
 * SEMPRE o admin-api.
 *
 * Nao escreve entitlement (o cliente nunca se autopromove). Usa apenas `ktor-client-core` +
 * kotlinx-json (sem ContentNegotiation) para nao forcar configuracao especifica no HttpClient do
 * consumidor.
 *
 * ### Contrato de fio (2.24.0 — alinhado ao admin-api real)
 * - **Rotas:** `GET /monet/{slug}/{plans,entitlement,usage}` e `POST /monet/{slug}/assert`.
 * - **Auth:** envia o **Firebase ID token do usuario** em `Authorization: Bearer <idToken>`. O
 *   servidor deriva o `tenant` do `uid` — o cliente **NAO** envia `tenantId`/`?tenant=` (se enviar
 *   divergente -> 403).
 * - **Envelope:** toda resposta vem como `{ "ok": true, "data": <payload> }`; o payload real e
 *   desembrulhado de `data`.
 * - **DTOs do servidor em ingles** (`active`/`plan`/`status`/`source`/`validUntil`;
 *   `count`/`limit`/`remaining`/`unlimited`) sao mapeados para os modelos PT da lib aqui na camada
 *   de rede, evitando churn nos consumidores.
 *
 * ### Cache curto em memoria (2.25.0 — reconciliado do origin/main)
 * Um cache opcional de TTL curto (`cacheTtlMillis`, default 60s) evita refazer as leituras
 * `getEntitlement`/`getUsage`/`getPlans` em navegacoes rapidas. **NUNCA concede cota offline:** o
 * cache so guarda leituras bem-sucedidas; sem cache valido e com rede falhando, o erro propaga (o
 * app trata como "sem informacao", nunca como "liberado"). Use [invalidateCache] apos compra/restore.
 *
 * @param httpClient HttpClient ja configurado pelo app (base, timeouts).
 * @param baseUrl URL base do admin-api, ex.: "https://admin.codecacto.com.br".
 * @param projectSlug `slug` do projeto (== `admin_projects.slug`), escopo de monetizacao.
 * @param authToken Lambda que fornece o **Firebase ID token do usuario** por requisicao.
 * @param cacheTtlMillis TTL do cache em memoria das leituras (default 60_000 ms; 0 = desabilitado).
 */
class AdminApiEntitlementRepository(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val projectSlug: String,
    private val authToken: suspend () -> String? = { null },
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS
) : EntitlementRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val root: String get() = baseUrl.trimEnd('/')
    private val project: String get() = projectSlug.encodeURLPathPart()

    private val timeSource = TimeSource.Monotonic

    private data class CacheEntry<T>(val value: T, val mark: TimeSource.Monotonic.ValueTimeMark)

    private var entitlementCache: CacheEntry<Entitlement>? = null
    private var plansCache: CacheEntry<List<Plan>>? = null
    private val usageCache: MutableMap<String, CacheEntry<UsageSnapshot>> = mutableMapOf()

    private fun <T> CacheEntry<T>?.takeFresh(): T? {
        val entry = this ?: return null
        if (cacheTtlMillis <= 0L) return null
        return if (entry.mark.elapsedNow().inWholeMilliseconds <= cacheTtlMillis) entry.value else null
    }

    override suspend fun getEntitlement(): ApiResult<Entitlement> {
        entitlementCache.takeFresh()?.let { return ApiResult.Success(it) }
        return handleApiCall {
            val body = httpClient.get("$root/monet/$project/entitlement") {
                authToken()?.let { header("Authorization", "Bearer $it") }
            }.bodyAsText()
            val dto = unwrap(body, EntitlementDto.serializer())
            dto.toModel()
        }.also { if (it is ApiResult.Success) entitlementCache = CacheEntry(it.data, timeSource.markNow()) }
    }

    override suspend fun getUsage(feature: String): ApiResult<UsageSnapshot> {
        usageCache[feature].takeFresh()?.let { return ApiResult.Success(it) }
        return handleApiCall {
            val body = httpClient.get("$root/monet/$project/usage?feature=${feature.encodeURLPathPart()}") {
                authToken()?.let { header("Authorization", "Bearer $it") }
            }.bodyAsText()
            val dto = unwrap(body, UsageDto.serializer())
            dto.toModel(feature)
        }.also { if (it is ApiResult.Success) usageCache[feature] = CacheEntry(it.data, timeSource.markNow()) }
    }

    override suspend fun getPlans(): ApiResult<List<Plan>> {
        plansCache.takeFresh()?.let { return ApiResult.Success(it) }
        return handleApiCall {
            val body = httpClient.get("$root/monet/$project/plans") {
                authToken()?.let { header("Authorization", "Bearer $it") }
            }.bodyAsText()
            val dtos = unwrap(body, ListSerializer(PlanDto.serializer()))
            dtos.map { it.toModel() }
        }.also { if (it is ApiResult.Success) plansCache = CacheEntry(it.data, timeSource.markNow()) }
    }

    /** Limpa o cache em memoria (ex.: apos compra/restore para forcar releitura do entitlement). */
    fun invalidateCache() {
        entitlementCache = null
        plansCache = null
        usageCache.clear()
    }

    /**
     * Verifica server-side se o uso de [feature] pode prosseguir (abordagem B / gauge):
     * `POST /monet/{slug}/assert` com `{ feature, currentCount, amount }`.
     *
     * - **200** -> [AssertResult.Allowed].
     * - **402** -> [AssertResult.Denied] com o [QuotaExceeded] parseado de `error.details` (o app
     *   abre o Paywall com contexto).
     * - **erro de rede/HTTP** -> [AssertResult.Failed] com a mensagem amigavel.
     *
     * @param feature chave da feature consumida (ex.: "active_loans").
     * @param currentCount contagem atual reportada pelo cliente (gauge — o servidor nega quando
     *   `currentCount + amount > limite`).
     * @param amount quanto se pretende consumir agora (default 1).
     */
    override suspend fun assertUsage(
        feature: String,
        currentCount: Int,
        amount: Int
    ): AssertResult {
        return try {
            // Nao dependemos de expectSuccess do HttpClient do consumidor: lemos status + corpo
            // direto, para que o 402 (paywall) seja sempre tratado, com ou sem ResponseException.
            val response = httpClient.post("$root/monet/$project/assert") {
                authToken()?.let { header("Authorization", "Bearer $it") }
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        AssertRequest.serializer(),
                        AssertRequest(feature = feature, currentCount = currentCount, amount = amount)
                    )
                )
            }
            classifyAssert(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        } catch (e: ResponseException) {
            // Client com expectSuccess=true: o nao-2xx vira excecao — classificamos igual.
            classifyAssert(e.response.status.value, runCatching { e.response.bodyAsText() }.getOrNull())
        } catch (e: Throwable) {
            AssertResult.Failed(code = -1, message = e.message ?: "Falha de conexao")
        }
    }

    private fun classifyAssert(status: Int, body: String?): AssertResult {
        if (status in 200..299) return AssertResult.Allowed
        val quota = parseQuotaExceeded(body)
        return if (status == 402 && quota != null) {
            AssertResult.Denied(quota)
        } else {
            AssertResult.Failed(
                code = status,
                message = extractErrorMessage(body) ?: "Nao foi possivel verificar a cota"
            )
        }
    }

    /** Desembrulha `{ ok, data }` e decodifica `data` no serializer alvo. */
    private fun <T> unwrap(
        body: String,
        dataSerializer: KSerializer<T>
    ): T {
        val envelope = json.decodeFromString(Envelope.serializer(JsonElement.serializer()), body)
        val data = envelope.data ?: error("Resposta do admin-api sem campo 'data'")
        return json.decodeFromJsonElement(dataSerializer, data)
    }

    private fun extractErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val env = json.decodeFromString(Envelope.serializer(JsonElement.serializer()), body)
            env.error?.message
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MILLIS: Long = 60_000L
    }
}

// ---------------------------------------------------------------------------
// DTOs de fio (ingles, contrato admin-api). Internos — mapeados para os modelos PT da lib.
// ---------------------------------------------------------------------------

/** Envelope canonico `{ ok, data, error }` (contrato §2). */
@Serializable
internal data class Envelope<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: EnvelopeError? = null
)

@Serializable
internal data class EnvelopeError(
    val code: String? = null,
    val message: String? = null,
    val details: JsonElement? = null
)

/** `GET /monet/{slug}/entitlement` -> `data` (contrato §3). */
@Serializable
internal data class EntitlementDto(
    val active: Boolean = false,
    val plan: PlanDto? = null,
    val status: String? = null,
    val source: String? = null,
    val validUntil: String? = null,
    val updatedAt: String? = null
) {
    fun toModel(): Entitlement {
        // SEGURANCA: a autoridade do direito vigente e `active`/`status` (contrato admin-api §3),
        // NUNCA a mera presenca do objeto `plan`. O servidor devolve `plan` mesmo para um
        // entitlement EXPIRED/CANCELED; mapear o `plan.code` premium nesses casos provocaria
        // autopromocao (isFree=false sem direito vigente). Por isso so honramos o plano pago quando
        // o direito esta efetivamente ativo; caso contrario, rebaixamos para "free".
        val isActive = active || status.equals("ACTIVE", ignoreCase = true)
        val planCode = if (isActive && plan != null) plan.code else "free"
        // Quando inativo, nenhuma feature do plano vale — o entitlement resultante e Free.
        val features = if (isActive) plan?.limits?.map { it.feature }?.toSet() ?: emptySet()
        else emptySet()
        return Entitlement(
            plano = planCode,
            features = features,
            validoAte = validUntil,
            fonte = source?.lowercase() ?: "manual",
            atualizadoEm = updatedAt
        )
    }
}

/** `GET /monet/{slug}/usage` -> `data` (contrato §3). */
@Serializable
internal data class UsageDto(
    val feature: String? = null,
    val count: Int = 0,
    val limit: Int = -1,
    val remaining: Int? = null,
    val unlimited: Boolean = false,
    val windowEnd: String? = null
) {
    fun toModel(fallbackFeature: String): UsageSnapshot = UsageSnapshot(
        feature = feature ?: fallbackFeature,
        contagem = count,
        limite = if (unlimited) -1 else limit,
        restante = remaining,
        janelaFim = windowEnd
    )
}

/** Plano do catalogo `GET /monet/{slug}/plans` (contrato §3). */
@Serializable
internal data class PlanDto(
    val code: String = "",
    val name: String = "",
    val price: String? = null,
    val currency: String = "BRL",
    val interval: String? = null,
    @SerialName("storeProductId") val storeProductId: String? = null,
    val limits: List<PlanLimitDto> = emptyList(),
    val highlights: List<String> = emptyList()
) {
    fun toModel(): Plan = Plan(
        plano = code,
        nome = name,
        preco = price,
        moeda = currency,
        intervalo = interval ?: "monthly",
        storeProductId = storeProductId,
        destaques = highlights
    )
}

@Serializable
internal data class PlanLimitDto(
    val feature: String,
    val limit: Int = -1,
    val window: String? = null
)

/** Corpo de `POST /monet/{slug}/assert` (contrato §3 — abordagem B). */
@Serializable
internal data class AssertRequest(
    val feature: String,
    val currentCount: Int,
    val amount: Int = 1
)
