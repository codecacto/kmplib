package br.com.codecacto.kmplib.feedback

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentPlatform
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Serviço de feedback centralizado via backend **apps-api**.
 *
 * A partir da kmplib 2.24.0 o feedback NÃO é mais gravado no Firestore `code-cacto`. Ele é enviado
 * para `POST {appsApiBaseUrl}/feedback/v1` — endpoint PÚBLICO (sem Firebase ID token), protegido por
 * rate-limit no servidor. O feedback é online e compartilhado por todos os apps; cada app passa o
 * seu `projectSlug`.
 *
 * **Robustez:** é best-effort e tolerante a falha. Nem `send`/`sendFeedback` nem a rede derrubam a
 * UI — qualquer erro (rede, 400 de validação, 429 de rate-limit) é tratado silenciosamente (log leve)
 * e devolvido como `Result.failure`, nunca propagado como exceção.
 *
 * ## Inicialização
 *
 * ```kotlin
 * // No Application.onCreate() / ponto de entrada do app
 * FeedbackService.initialize(
 *     FeedbackConfig(
 *         projectSlug = "super-8",
 *         httpClient = appHttpClient,     // Ktor core puro (sem ContentNegotiation obrigatório)
 *         appVersion = BuildConfig.VERSION_NAME,
 *     )
 * )
 *
 * // Após login (opcional)
 * FeedbackService.updateUser(userId = "abc123", userEmail = "user@email.com")
 * ```
 *
 * ## Uso direto
 *
 * ```kotlin
 * FeedbackService.sendFeedback(
 *     source = FeedbackSource.FEEDBACK_SCREEN,
 *     motivo = "bug",
 *     mensagem = "O app trava ao abrir...",
 * )
 * ```
 */
object FeedbackService {

    private const val TAG = "FeedbackService"
    private const val PATH = "/feedback/v1"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false // não serializa campos nulos (opcionais omitidos)
    }

    private var _config: FeedbackConfig? = null
    val config: FeedbackConfig? get() = _config

    /**
     * Inicializa o serviço de feedback.
     */
    fun initialize(config: FeedbackConfig) {
        _config = config
        AppLogger.d(TAG, "Inicializado para projectSlug='${config.projectSlug}' base='${config.appsApiBaseUrl}'")
    }

    /**
     * Atualiza dados do usuário (chamar após login/logout).
     */
    fun updateUser(userId: String = "", userEmail: String = "") {
        _config = _config?.copy(userId = userId, userEmail = userEmail)
    }

    /**
     * Envia um feedback completo (da FeedbackScreen).
     *
     * A partir da 2.25.0 o feedback é IDENTIFICADO: [nome], [email] e [whatsapp] vão como campos
     * ESTRUTURADOS do [FeedbackRequest] (não mais concatenados em [FeedbackData.mensagem]). A
     * [mensagem] carrega apenas a descrição; o [motivo] continua prefixado na `message` (sem coluna
     * dedicada no central).
     */
    suspend fun sendFeedback(
        source: FeedbackSource,
        motivo: String = "",
        mensagem: String,
        nome: String = "",
        email: String = "",
        whatsapp: String = "",
        rating: Int? = null,
    ): Result<Unit> {
        val cfg = _config
            ?: return failNotInitialized()

        val data = FeedbackData(
            appId = cfg.projectSlug,
            appVersion = cfg.appVersion,
            source = source.valor,
            motivo = motivo,
            mensagem = mensagem,
            nome = nome.trim(),
            email = email.trim(),
            whatsapp = whatsapp.trim(),
            usuarioId = cfg.userId,
            usuarioEmail = cfg.userEmail.trim(),
            platform = currentPlatform,
        )

        return send(data, rating)
    }

    /**
     * Envia um [FeedbackData] diretamente. Best-effort: não lança; loga e devolve `Result.failure`
     * em qualquer falha (rede/400/429).
     */
    suspend fun send(data: FeedbackData, rating: Int? = null): Result<Unit> {
        val cfg = _config ?: return failNotInitialized()

        val request = FeedbackRequest(
            projectSlug = data.appId.ifBlank { cfg.projectSlug },
            message = composeMessage(data),
            name = data.nome.trim().ifBlank { null },
            whatsapp = data.whatsapp.trim().ifBlank { null },
            email = data.email.trim().ifBlank { cfg.userEmail.trim().ifBlank { null } },
            rating = rating?.takeIf { it in 1..5 },
            uid = data.usuarioId.ifBlank { null },
            appVersion = data.appVersion.ifBlank { null },
            platform = data.platform.ifBlank { currentPlatform }.ifBlank { null },
        )

        val url = cfg.appsApiBaseUrl.trimEnd('/') + PATH
        AppLogger.d(TAG, "Enviando feedback: projectSlug=${request.projectSlug}, source=${data.source}")

        val result: ApiResult<Unit> = handleApiCall {
            cfg.httpClient.post(url) {
                expectSuccess = true
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(FeedbackRequest.serializer(), request))
            }.bodyAsText() // 201 — corpo ignorado (fire-and-forget)
            Unit
        }

        return when (result) {
            is ApiResult.Success -> {
                AppLogger.d(TAG, "Feedback enviado com sucesso")
                Result.success(Unit)
            }
            is ApiResult.Error -> {
                // 400 (validação) / 429 (rate-limit) / falha de rede: nunca derruba a UI.
                AppLogger.w(TAG, "Feedback não enviado (code=${result.code}): ${result.message}")
                Result.failure(FeedbackSendException(result.code, result.message))
            }
            ApiResult.Loading -> Result.failure(FeedbackSendException(-1, "estado inválido"))
        }
    }

    /**
     * Compõe a `message` enviada ao endpoint central. A partir da 2.25.0 carrega apenas o [motivo]
     * (prefixo, sem coluna dedicada) + a descrição — nome/email/WhatsApp viram campos ESTRUTURADOS
     * do [FeedbackRequest], não mais concatenados aqui.
     */
    private fun composeMessage(data: FeedbackData): String = buildString {
        if (data.motivo.isNotBlank()) append("[").append(data.motivo).append("] ")
        append(data.mensagem.trim())
    }.trim()

    private fun failNotInitialized(): Result<Unit> {
        AppLogger.w(TAG, "FeedbackService não inicializado — feedback ignorado.")
        return Result.failure(
            IllegalStateException("FeedbackService não inicializado. Chame FeedbackService.initialize() primeiro.")
        )
    }
}

/** Falha não fatal de envio de feedback (best-effort). [code] = status HTTP ou -1 para rede. */
class FeedbackSendException(val code: Int, message: String) : Exception(message)
