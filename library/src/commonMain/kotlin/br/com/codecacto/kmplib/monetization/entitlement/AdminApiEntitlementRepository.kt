package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Implementacao canonica de [EntitlementRepository] que LE plano/entitlement/uso do `admin-api`
 * central (fonte unica de verdade — doc 03 §4/§7). Funciona tanto no arquetipo D (backend proprio)
 * quanto em apps Firestore-only: o ponto de leitura de monetizacao e SEMPRE o admin-api.
 *
 * Nao escreve nada (o cliente nunca se autopromove). Usa apenas `ktor-client-core` + kotlinx-json
 * (sem ContentNegotiation) para nao forcar configuracao especifica no HttpClient do consumidor.
 *
 * @param httpClient HttpClient ja configurado pelo app (base, auth, timeouts).
 * @param baseUrl URL base do admin-api, ex.: "https://admin.codecacto.com.br".
 * @param projectSlug `slug` do projeto (== `admin_projects.slug`), escopo de monetizacao.
 * @param authToken Lambda que fornece o token do tenant (Firebase ID token / JWT) por requisicao.
 */
class AdminApiEntitlementRepository(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val projectSlug: String,
    private val authToken: suspend () -> String? = { null }
) : EntitlementRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val root: String get() = baseUrl.trimEnd('/')
    private val project: String get() = projectSlug.encodeURLPathPart()

    override suspend fun getEntitlement(): ApiResult<Entitlement> = handleApiCall {
        val body = httpClient.get("$root/v1/$project/entitlement") {
            authToken()?.let { header("Authorization", "Bearer $it") }
        }.bodyAsText()
        json.decodeFromString(Entitlement.serializer(), body)
    }

    override suspend fun getUsage(feature: String): ApiResult<UsageSnapshot> = handleApiCall {
        val body = httpClient.get("$root/v1/$project/usage/${feature.encodeURLPathPart()}") {
            authToken()?.let { header("Authorization", "Bearer $it") }
        }.bodyAsText()
        json.decodeFromString(UsageSnapshot.serializer(), body)
    }

    override suspend fun getPlans(): ApiResult<List<Plan>> = handleApiCall {
        val body = httpClient.get("$root/v1/$project/plans") {
            authToken()?.let { header("Authorization", "Bearer $it") }
        }.bodyAsText()
        json.decodeFromString(ListSerializer(Plan.serializer()), body)
    }
}
