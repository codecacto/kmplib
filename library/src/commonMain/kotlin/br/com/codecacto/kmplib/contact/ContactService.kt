package br.com.codecacto.kmplib.contact

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Serviço de "Entrar em contato" centralizado via backend **apps-api**.
 *
 * Envia o formulário de contato para `POST {appsApiBaseUrl}/contact/v1` — o MESMO endpoint público
 * usado pelos sites (weblib `ContactForm`), então leads de app e de site caem no mesmo
 * `central.contact` e são lidos no mesmo painel admin. Cada app passa o seu `projectSlug`.
 *
 * **Robustez (regra de ouro):** best-effort e tolerante a falha. Nenhum erro (rede, 400 de validação,
 * 429 de rate-limit) derruba a UI — é tratado silenciosamente (log leve) e devolvido como
 * `Result.failure`, nunca propagado como exceção.
 *
 * ## Inicialização
 *
 * ```kotlin
 * // No ponto de entrada do app (junto do FeedbackService / DeveloperInfoService)
 * ContactService.initialize(
 *     ContactConfig(
 *         projectSlug = "influencer",
 *         httpClient = appHttpClient,
 *         source = "influencer-app",
 *     )
 * )
 * ```
 */
object ContactService {

    private const val TAG = "ContactService"
    private const val PATH = "/contact/v1"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false // não serializa campos nulos (opcionais omitidos)
    }

    private var _config: ContactConfig? = null
    val config: ContactConfig? get() = _config

    /** Inicializa o serviço de contato. */
    fun initialize(config: ContactConfig) {
        _config = config
        AppLogger.d(TAG, "Inicializado para projectSlug='${config.projectSlug}' base='${config.appsApiBaseUrl}'")
    }

    /** Atualiza o e-mail do usuário (chamar após login/logout) para fallback do campo `email`. */
    fun updateUser(userEmail: String = "") {
        _config = _config?.copy(userEmail = userEmail)
    }

    /**
     * Envia um contato. Best-effort: não lança; loga e devolve `Result.failure` em qualquer falha
     * (rede/400/429). [name], [email] e [message] são obrigatórios; [whatsapp] e [subject] opcionais.
     */
    suspend fun send(
        name: String,
        email: String,
        message: String,
        whatsapp: String = "",
        subject: String = "",
    ): Result<Unit> {
        val cfg = _config ?: return failNotInitialized()

        val request = ContactRequest(
            projectSlug = cfg.projectSlug,
            name = name.trim(),
            email = email.trim().ifBlank { cfg.userEmail.trim() },
            message = message.trim(),
            whatsapp = whatsapp.trim().ifBlank { null },
            subject = subject.trim().ifBlank { null },
            source = cfg.source.trim().ifBlank { null },
        )

        val url = cfg.appsApiBaseUrl.trimEnd('/') + PATH
        AppLogger.d(TAG, "Enviando contato: projectSlug=${request.projectSlug}, source=${request.source}")

        val result: ApiResult<Unit> = handleApiCall {
            cfg.httpClient.post(url) {
                expectSuccess = true
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ContactRequest.serializer(), request))
            }.bodyAsText() // 201 — corpo ignorado (fire-and-forget)
            Unit
        }

        return when (result) {
            is ApiResult.Success -> {
                AppLogger.d(TAG, "Contato enviado com sucesso")
                Result.success(Unit)
            }
            is ApiResult.Error -> {
                // 400 (validação) / 429 (rate-limit) / falha de rede: nunca derruba a UI.
                AppLogger.w(TAG, "Contato não enviado (code=${result.code}): ${result.message}")
                Result.failure(ContactSendException(result.code, result.message))
            }
            ApiResult.Loading -> Result.failure(ContactSendException(-1, "estado inválido"))
        }
    }

    private fun failNotInitialized(): Result<Unit> {
        AppLogger.w(TAG, "ContactService não inicializado — contato ignorado.")
        return Result.failure(
            IllegalStateException("ContactService não inicializado. Chame ContactService.initialize() primeiro.")
        )
    }
}
