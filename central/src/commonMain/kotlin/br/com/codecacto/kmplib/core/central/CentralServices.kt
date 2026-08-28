package br.com.codecacto.kmplib.core.central

import br.com.codecacto.kmplib.contact.ContactConfig
import br.com.codecacto.kmplib.contact.ContactService
import br.com.codecacto.kmplib.developer.DeveloperConfig
import br.com.codecacto.kmplib.developer.DeveloperInfoService
import br.com.codecacto.kmplib.feedback.FeedbackConfig
import br.com.codecacto.kmplib.feedback.FeedbackService
import io.ktor.client.HttpClient
import kotlin.concurrent.Volatile

/**
 * Amarração ÚNICA dos **serviços online centrais** que todo app do ecossistema reusa (GAP-NS-M-04;
 * promovido do `NetworkBootstrap` local do Números da Sorte, confirmado em ≥2 apps do arquétipo A).
 *
 * Inicializa de uma vez os três toques públicos do **apps-api central** — sem login, sem Firestore:
 * - [FeedbackService] — envio de feedback (`POST /feedback/v1`);
 * - [ContactService] — "Entrar em contato" (`POST /contact/v1`), MESMO lead do site;
 * - [DeveloperInfoService] — grade "Desenvolvido por CodeCacto" (`GET /public/developer`).
 *
 * Nenhum dado de domínio trafega aqui — apps offline-first (arquétipo A) guardam local e usam o
 * central só nestes toques; apps com backend próprio também reusam esta amarração para os 3 públicos.
 * Tudo é **best-effort** (falha de rede nunca derruba a UI). Um único [HttpClient] (idealmente o
 * [createHttpClient] da lib) serve os três.
 *
 * O pacote é `core.central`, mas o artefato é o **`kmplib-central`** — pasta e pacote divergem de
 * propósito. O pacote não pode mudar: `br.com.codecacto.kmplib.core.central.CentralServices` está
 * escrito em 105 arquivos do portfólio. E o `kmplib-core` não pode hospedá-lo, porque ele amarra
 * `contact`, `developer` e `feedback`, que ficam acima da base — era esse o ciclo que impedia a
 * lib de virar módulos.
 *
 * Uso no bootstrap do app:
 * ```kotlin
 * CentralServices.initialize(
 *     CentralServicesConfig(
 *         projectSlug = AppConfig.projectSlug,
 *         httpClient = koinInject(),
 *         appVersion = AppConfig.appVersion,
 *     ),
 * )
 * ```
 * Quando o usuário logar/atualizar contato:
 * ```kotlin
 * CentralServices.updateUser(userId = uid, userEmail = email)
 * ```
 */
object CentralServices {

    @Volatile
    private var initialized = false

    /** `true` após a primeira [initialize] bem-sucedida no processo. */
    val isInitialized: Boolean get() = initialized

    /**
     * Inicializa (idempotente) os serviços centrais habilitados em [config]. Chamadas subsequentes
     * são no-op — chame uma vez por processo, no bootstrap do app.
     */
    fun initialize(config: CentralServicesConfig) {
        if (initialized) return
        initialized = true

        if (config.enableFeedback) {
            FeedbackService.initialize(
                FeedbackConfig(
                    projectSlug = config.projectSlug,
                    httpClient = config.httpClient,
                    appsApiBaseUrl = config.appsApiBaseUrl,
                    appVersion = config.appVersion,
                    userId = config.userId,
                    userEmail = config.userEmail,
                ),
            )
        }

        if (config.enableContact) {
            ContactService.initialize(
                ContactConfig(
                    projectSlug = config.projectSlug,
                    httpClient = config.httpClient,
                    appsApiBaseUrl = config.appsApiBaseUrl,
                    source = config.contactSource ?: "${config.projectSlug}-app",
                    userEmail = config.userEmail,
                ),
            )
        }

        if (config.enableDeveloper) {
            DeveloperInfoService.initialize(
                DeveloperConfig(
                    httpClient = config.httpClient,
                    appsApiBaseUrl = config.appsApiBaseUrl,
                ),
            )
        }
    }

    /**
     * Propaga a identidade do usuário para os serviços que a usam como fallback de e-mail/uid
     * (feedback e contato). Seguro chamar antes ou depois de [initialize].
     */
    fun updateUser(userId: String = "", userEmail: String = "") {
        FeedbackService.updateUser(userId = userId, userEmail = userEmail)
        ContactService.updateUser(userEmail = userEmail)
    }

    /** Apenas para testes — reseta o estado de inicialização deste processo. */
    internal fun resetForTesting() {
        initialized = false
    }
}

/**
 * Configuração da [CentralServices.initialize].
 *
 * @property projectSlug slug do projeto no apps-api/admin (escopa feedback/contato).
 * @property httpClient cliente HTTP compartilhado (idealmente o `createHttpClient` da lib).
 * @property appVersion versão do app exibida junto do feedback (default vazio).
 * @property appsApiBaseUrl base pública do apps-api central (default oficial do ecossistema).
 * @property contactSource origem do lead de contato (default `"$projectSlug-app"` se `null`).
 * @property userId uid conhecido (fallback do feedback identificado; default vazio).
 * @property userEmail e-mail conhecido (fallback de feedback/contato; default vazio).
 * @property enableFeedback liga o [FeedbackService] (default `true`).
 * @property enableContact liga o [ContactService] (default `true`).
 * @property enableDeveloper liga o [DeveloperInfoService] (default `true`).
 */
data class CentralServicesConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val appVersion: String = "",
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    val contactSource: String? = null,
    val userId: String = "",
    val userEmail: String = "",
    val enableFeedback: Boolean = true,
    val enableContact: Boolean = true,
    val enableDeveloper: Boolean = true,
) {
    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }
}
