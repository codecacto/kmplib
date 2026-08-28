package br.com.codecacto.kmplib.appupdate

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * Serviço de checagem de versão (Force Update) contra o endpoint PÚBLICO do admin-api.
 *
 * `GET {adminApiBaseUrl}/public/app-version?project={slug}&platform={android|ios}&versionCode={int}`
 * → 200 [AppVersionCheckResponse]. O resultado é mapeado para [AppUpdateStatus] (`None`/`Soft`/`Hard`).
 *
 * **Robustez (regra de ouro):** é best-effort. Qualquer falha — rede, timeout, 4xx/5xx, corpo
 * inválido ou `action` desconhecida — resolve para [AppUpdateStatus.None]. A checagem NUNCA pode
 * travar o app por conta própria; só o `action == "hard"` legítimo do servidor bloqueia.
 *
 * Stateless quanto a transporte: o `HttpClient` e a URL vêm de [AppUpdateConfig]. Pode ser
 * instanciado direto ou registrado no Koin (`single { AppUpdateService(config) }`).
 *
 * ```kotlin
 * val service = AppUpdateService(
 *     AppUpdateConfig(
 *         projectSlug = "super-8",
 *         httpClient = appHttpClient,
 *         currentVersionCode = BuildConfig.VERSION_CODE, // iOS: CFBundleVersion -> Int
 *     )
 * )
 * when (val status = service.check()) {
 *     is AppUpdateStatus.Hard -> /* bloquear */
 *     is AppUpdateStatus.Soft -> /* sugerir */
 *     AppUpdateStatus.None     -> /* seguir */
 * }
 * ```
 */
class AppUpdateService(private val config: AppUpdateConfig) {

    private companion object {
        const val TAG = "AppUpdateService"
        const val PATH = "/public/app-version"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Consulta o admin-api e devolve a decisão de UI. Best-effort: nunca lança; falha → [AppUpdateStatus.None].
     */
    suspend fun check(): AppUpdateStatus {
        val url = config.adminApiBaseUrl.trimEnd('/') + PATH

        val result: ApiResult<AppVersionCheckResponse> = handleApiCall {
            val raw = config.httpClient.get(url) {
                expectSuccess = true
                parameter("project", config.projectSlug)
                parameter("platform", config.platform)
                parameter("versionCode", config.currentVersionCode)
            }.bodyAsText()
            json.decodeFromString(AppVersionCheckResponse.serializer(), raw)
        }

        return when (result) {
            is ApiResult.Success -> mapToStatus(result.data)
            is ApiResult.Error -> {
                // 4xx/5xx/rede/corpo inválido: nunca bloqueia o app.
                AppLogger.w(TAG, "Checagem de versão falhou (code=${result.code}): ${result.message}")
                AppUpdateStatus.None
            }
            ApiResult.Loading -> AppUpdateStatus.None
        }
    }

    private fun mapToStatus(resp: AppVersionCheckResponse): AppUpdateStatus =
        when (resp.action.trim().lowercase()) {
            AppVersionCheckResponse.ACTION_HARD -> AppUpdateStatus.Hard(
                storeUrl = resp.storeUrl,
                message = resp.message,
            )
            AppVersionCheckResponse.ACTION_SOFT -> AppUpdateStatus.Soft(
                storeUrl = resp.storeUrl,
                message = resp.message,
                latestVersionName = resp.latestVersionName,
            )
            else -> AppUpdateStatus.None // "none" e qualquer valor desconhecido (fail-safe)
        }
}
