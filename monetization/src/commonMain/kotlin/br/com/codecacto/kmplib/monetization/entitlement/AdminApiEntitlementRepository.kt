package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

/**
 * Implementacao canonica de [EntitlementRepository] que LE plano/entitlement/uso do PROPRIO usuario
 * no `admin-api` central — fonte unica de verdade da monetizacao. O cliente apenas LE; nunca se
 * autopromove e nunca decide/incrementa cota (enforcement e sempre server-side).
 *
 * Nao escreve entitlement. Usa apenas `ktor-client-core` + kotlinx-json (sem ContentNegotiation)
 * para nao forcar configuracao especifica no HttpClient do consumidor.
 *
 * ### Contrato de fio (2.57.0 — reconciliado com o admin-api real, arvore `/me/…`)
 * As rotas apps-facing do admin-api (`AdminUnificado/admin-api` → `ProjectsRoutes.kt`, route `/me`):
 * - `GET {baseUrl}/v1/projects/{slug}/me/entitlement`
 * - `GET {baseUrl}/v1/projects/{slug}/me/usage/{feature}`  (feature e SEGMENTO de path, nao query)
 * - `GET {baseUrl}/v1/projects/{slug}/me/plans`
 *
 * - **Auth:** envia o **Firebase ID token do usuario** em `Authorization: Bearer <idToken>` (via
 *   [authToken]). O servidor deriva o `tenant` do `firebaseUid` — o cliente **NUNCA** envia
 *   `tenant`/`?tenant=` no path/body/query (o `tenant` no path e proibido nesta arvore).
 * - **SEM envelope:** as respostas sao o **DTO puro** (nao ha `{ ok, data }`). O corpo e desserializado
 *   diretamente no DTO alvo.
 * - **Campos em PT** (`plano`/`features`/`validoAte`/`fonte`/`atualizadoEm`/`ativo`; `contagem`/`limite`/
 *   `restante`/`janelaFim`; `nome`/`preco`/`moeda`/`intervalo`/`ativo`/`tipo`/`durationMonths`/
 *   `storeProductId`) — mapeados para os modelos de dominio da lib nesta camada.
 * - **Estado free (200, sem grant):** quando o usuario nunca teve entitlement, o servidor responde
 *   **200** com o default free (`plano="free"`, `features=[]`, `ativo=false`, `fonte="NONE"`), NAO 404.
 *   [EntitlementDto.toModel] trata isso como [Entitlement.FREE] (nao-premium) sem erro.
 *
 * ### Cache curto em memoria
 * Um cache opcional de TTL curto (`cacheTtlMillis`, default 60s) evita refazer as leituras em
 * navegacoes rapidas. **NUNCA concede cota offline:** so guarda leituras bem-sucedidas; sem cache
 * valido e com rede falhando, o erro propaga (o app trata como "sem informacao", nunca como
 * "liberado"). Use [invalidateCache] apos compra/restore.
 *
 * ### `assertUsage` — GAP de backend (nao ha `/me/assert`)
 * O admin-api NAO expoe um `assert` na arvore `/me/…` (Firebase-authed). O unico `assert` existente
 * (`POST /v1/{slug}/{tenant}/assert`) exige **service token** e o `tenant` no path — inviavel no
 * device (o service token nunca pode viajar no app). Por isso [assertUsage] NAO chama rede (nao
 * inventa rota) e **degrada de forma segura** devolvendo [AssertResult.Failed] (nunca [Allowed], para
 * jamais autoconceder sem verificacao server-side). O gate REAL de enforcement continua sendo o **402
 * na acao de dominio** (o ViewModel chama a rota da acao → admin-api nega 402 →
 * `ResponseException.quotaExceededOrNull()` → Paywall); para UX "X de Y" use [getUsage]. Ver handoff
 * (gap reportado ao dev-backend).
 *
 * @param httpClient HttpClient ja configurado pelo app (base, timeouts).
 * @param baseUrl URL base do admin-api (ex.: "https://admin-api.codecacto.com.br").
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

    /** Prefixo apps-facing (`/me`) — leitura do proprio entitlement pelo usuario logado. */
    private val mePath: String get() = "$root/v1/projects/$project/me"

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
            val body = httpClient.get("$mePath/entitlement") {
                authToken()?.let { header("Authorization", "Bearer $it") }
            }.bodyAsText()
            json.decodeFromString(EntitlementDto.serializer(), body).toModel()
        }.also { if (it is ApiResult.Success) entitlementCache = CacheEntry(it.data, timeSource.markNow()) }
    }

    override suspend fun getUsage(feature: String): ApiResult<UsageSnapshot> {
        usageCache[feature].takeFresh()?.let { return ApiResult.Success(it) }
        return handleApiCall {
            val body = httpClient.get("$mePath/usage/${feature.encodeURLPathPart()}") {
                authToken()?.let { header("Authorization", "Bearer $it") }
            }.bodyAsText()
            json.decodeFromString(UsageDto.serializer(), body).toModel(feature)
        }.also { if (it is ApiResult.Success) usageCache[feature] = CacheEntry(it.data, timeSource.markNow()) }
    }

    override suspend fun getPlans(): ApiResult<List<Plan>> {
        plansCache.takeFresh()?.let { return ApiResult.Success(it) }
        return handleApiCall {
            val body = httpClient.get("$mePath/plans") {
                authToken()?.let { header("Authorization", "Bearer $it") }
            }.bodyAsText()
            json.decodeFromString(ListSerializer(PlanDto.serializer()), body).map { it.toModel() }
        }.also { if (it is ApiResult.Success) plansCache = CacheEntry(it.data, timeSource.markNow()) }
    }

    /** Limpa o cache em memoria (ex.: apos compra/restore para forcar releitura do entitlement). */
    fun invalidateCache() {
        entitlementCache = null
        plansCache = null
        usageCache.clear()
    }

    /**
     * **Degradacao segura — GAP de backend.** Nao existe `assert` na arvore `/me/…` (Firebase-authed)
     * do admin-api, e o `assert` service-token (`/v1/{slug}/{tenant}/assert`) e inviavel no device.
     * Portanto NAO chamamos rede (nao inventamos rota) e devolvemos sempre [AssertResult.Failed] —
     * NUNCA [AssertResult.Allowed], para jamais autoconceder consumo sem verificacao server-side.
     *
     * O gate real de enforcement e o **402 na acao de dominio** (ViewModel → rota da acao → 402 →
     * `ResponseException.quotaExceededOrNull()` → Paywall). Para UX "X de Y" antes de agir, use
     * [getUsage] + [UsageSnapshot.isExhausted] (indicativo; a autoridade continua no servidor).
     */
    override suspend fun assertUsage(
        feature: String,
        currentCount: Int,
        amount: Int
    ): AssertResult {
        AppLogger.w(
            TAG,
            "assertUsage('$feature') indisponivel via /me (sem rota no admin-api). " +
                "Enforcement e server-side na acao de dominio (402 -> quotaExceededOrNull)."
        )
        return AssertResult.Failed(
            code = ASSERT_UNAVAILABLE_CODE,
            message = "Verificacao de cota nao disponivel no cliente; enforcement e server-side."
        )
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MILLIS: Long = 60_000L

        private const val TAG = "AdminApiEntitlement"

        /** Codigo devolvido por [assertUsage] enquanto nao houver `assert` Firebase-authed no backend. */
        const val ASSERT_UNAVAILABLE_CODE: Int = 501
    }
}

// ---------------------------------------------------------------------------
// DTOs de fio (contrato admin-api `/me/…`, campos em PT, SEM envelope). Internos —
// mapeados para os modelos de dominio (Entitlement/UsageSnapshot/Plan) da lib.
// ---------------------------------------------------------------------------

/**
 * `GET /v1/projects/{slug}/me/entitlement` — DTO puro (espelha `EntitlementDto` do admin-api).
 * O default free (usuario sem grant) chega como `plano="free"`, `features=[]`, `ativo=false`.
 */
@Serializable
internal data class EntitlementDto(
    val project: String? = null,
    val tenant: String? = null,
    val plano: String = "free",
    val features: List<String> = emptyList(),
    val validoAte: String? = null,
    val fonte: String = "manual",
    val atualizadoEm: String? = null,
    val ativo: Boolean = false
) {
    fun toModel(): Entitlement {
        // SEGURANCA: a autoridade do direito vigente e o campo `ativo` (calculado pelo servidor,
        // `isActive(now)`), NUNCA a mera presenca de um `plano` pago. Um entitlement expirado/cancelado
        // pode vir com `plano="premium"` e `ativo=false`; honra-lo provocaria autopromocao. Por isso,
        // sem direito vigente rebaixamos para Free (nao-premium).
        if (!ativo) return Entitlement.FREE
        return Entitlement(
            plano = plano,
            features = features.toSet(),
            validoAte = validoAte,
            fonte = fonte.lowercase(),
            atualizadoEm = atualizadoEm?.takeIf { it.isNotBlank() }
        )
    }
}

/**
 * `GET /v1/projects/{slug}/me/usage/{feature}` — DTO puro (espelha `UsageDto` do admin-api).
 * `limite == -1` = ilimitado (o dominio [UsageSnapshot] deriva `isUnlimited`).
 */
@Serializable
internal data class UsageDto(
    val feature: String? = null,
    val contagem: Int = 0,
    val limite: Int = -1,
    val restante: Int? = null,
    val janelaFim: String? = null
) {
    fun toModel(fallbackFeature: String): UsageSnapshot = UsageSnapshot(
        feature = feature ?: fallbackFeature,
        contagem = contagem,
        limite = limite,
        restante = restante,
        janelaFim = janelaFim
    )
}

/**
 * Plano da oferta de paywall `GET /v1/projects/{slug}/me/plans` — DTO puro (espelha `PlanDto` do
 * admin-api). O servidor JA devolve so a oferta (ativo=true e tipo!=null), ordenada
 * Mensal→Semestral→Anual. `tipo` ∈ MENSAL | SEMESTRAL | ANUAL; `durationMonths` coerente (1/6/12) —
 * chave de correlacao com o `Package` do RevenueCat no paywall. O contrato `/me/plans` NAO carrega
 * `limits`/`highlights` (por isso `destaques` fica vazio aqui).
 */
@Serializable
internal data class PlanDto(
    val projectSlug: String? = null,
    val plano: String = "",
    val nome: String = "",
    val preco: String? = null,
    val moeda: String = "BRL",
    val intervalo: String = "monthly",
    val ativo: Boolean = true,
    val tipo: String? = null,
    val durationMonths: Int? = null,
    val storeProductId: String? = null
) {
    fun toModel(): Plan = Plan(
        plano = plano,
        nome = nome,
        preco = preco,
        moeda = moeda,
        intervalo = intervalo,
        ativo = ativo,
        storeProductId = storeProductId,
        tipo = tipo,
        durationMonths = durationMonths,
        destaques = emptyList()
    )
}
