package br.com.codecacto.kmplib.feedback

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentPlatform
import br.com.codecacto.kmplib.platform.AppReviewManager
import br.com.codecacto.kmplib.platform.ReviewConfigSource
import br.com.codecacto.kmplib.platform.ReviewPromptConfig
import br.com.codecacto.kmplib.platform.reviewConfigJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlin.concurrent.Volatile

/**
 * A regra do pedido de avaliação do **Nexus → Avaliação do app** (2.335.0):
 * `GET {appsApiBaseUrl}/public/review-config?project=&platform=` → `{enabled, triggerCount,
 * remindAfterDays, maxPrompts}` (sem linha cadastrada o servidor devolve `{true, 3, 30, 3}`).
 * Falha (rede, 5xx, corpo ilegível) → `null`; o [AppReviewManager] mantém a última conhecida.
 */
class RestReviewConfigSource(
    private val httpClient: HttpClient,
    private val projectSlug: String,
    private val appsApiBaseUrl: String = AppReviewServiceConfig.DEFAULT_APPS_API_BASE_URL,
    private val platform: String = currentPlatform,
) : ReviewConfigSource {
    override suspend fun fetch(): ReviewPromptConfig? {
        val url = appsApiBaseUrl.trimEnd('/') + PATH
        val result: ApiResult<String> = handleApiCall {
            httpClient.get(url) {
                expectSuccess = true
                parameter("project", projectSlug)
                parameter("platform", platform)
            }.bodyAsText()
        }
        return when (result) {
            is ApiResult.Success -> runCatching {
                reviewConfigJson.decodeFromString(ReviewPromptConfig.serializer(), result.data).sanitized()
            }.getOrElse {
                AppLogger.w(TAG, "Config da avaliação ilegível — mantida a última conhecida.")
                null
            }
            is ApiResult.Error -> {
                AppLogger.w(TAG, "Config da avaliação indisponível (code=${result.code}) — mantida a última conhecida.")
                null
            }
            ApiResult.Loading -> null
        }
    }

    companion object {
        /** Caminho no apps-api. */
        const val PATH: String = "/public/review-config"
        private const val TAG = "AppReviewService"
    }
}

/**
 * Configuração do [AppReviewService].
 *
 * @property triggerCount gatilho usado enquanto não houver config do servidor (cache vazio, sem rede).
 */
data class AppReviewServiceConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    val triggerCount: Int = ReviewPromptConfig.DEFAULT.triggerCount,
) {
    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }
}

/**
 * O **pedido de avaliação com regra remota** do processo (2.335.0). O `CentralServices.initialize` já
 * inicializa; o `rememberAppReviewPrompt` usa o [manager] daqui.
 *
 * Sem [initialize], o [manager] é o de sempre (regra local `{true, 3, 30, 3}`, sem servidor).
 */
object AppReviewService {

    @Volatile
    private var _config: AppReviewServiceConfig? = null

    @Volatile
    private var _manager: AppReviewManager? = null

    /** A config em uso (`null` antes de [initialize]). */
    val config: AppReviewServiceConfig? get() = _config

    val isInitialized: Boolean get() = _config != null

    /** Liga a regra remota. Chamar de novo troca a config (e recria o manager). */
    fun initialize(config: AppReviewServiceConfig) {
        _config = config
        _manager = null
    }

    /** A fonte remota (`null` sem [initialize]). */
    fun configSource(): ReviewConfigSource? = _config?.let {
        RestReviewConfigSource(it.httpClient, it.projectSlug, it.appsApiBaseUrl)
    }

    /**
     * O [AppReviewManager] do processo — um só, para a config em memória e o contador serem os mesmos
     * em toda tela. No Android exige a lib inicializada (`initKmpLibCore`), como o construtor de sempre.
     */
    fun manager(): AppReviewManager {
        _manager?.let { return it }
        val c = _config
        val created = AppReviewManager(
            triggerCount = c?.triggerCount ?: ReviewPromptConfig.DEFAULT.triggerCount,
            configSource = configSource(),
        )
        _manager = created
        return created
    }

    /** Apenas para testes. */
    internal fun resetForTesting() {
        _config = null
        _manager = null
    }
}
