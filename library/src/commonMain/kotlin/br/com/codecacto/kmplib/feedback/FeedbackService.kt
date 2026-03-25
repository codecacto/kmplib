package br.com.codecacto.kmplib.feedback

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Serviço de feedback centralizado via Firestore REST API.
 *
 * Envia feedbacks para o projeto Firebase "code-cacto", usando REST API
 * para evitar conflito com o Firebase principal do app.
 *
 * ## Inicialização
 *
 * ```kotlin
 * // No Application.onCreate() ou ponto de entrada do app
 * FeedbackService.initialize(
 *     FeedbackConfig(
 *         appId = "br.com.codecacto.locadora",
 *         appVersion = "1.2.0"
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
 *     mensagem = "O app trava ao abrir..."
 * )
 * ```
 */
object FeedbackService {

    private const val TAG = "FeedbackService"
    private const val COLLECTION = "feedbacks"

    private var _config: FeedbackConfig? = null
    val config: FeedbackConfig? get() = _config

    /**
     * Inicializa o serviço de feedback.
     */
    fun initialize(config: FeedbackConfig) {
        _config = config
        AppLogger.d(TAG, "Inicializado para app: ${config.appId}")
    }

    /**
     * Atualiza dados do usuário (chamar após login/logout).
     */
    fun updateUser(userId: String = "", userEmail: String = "") {
        _config = _config?.copy(userId = userId, userEmail = userEmail)
    }

    /**
     * Envia um feedback completo (da FeedbackScreen).
     */
    suspend fun sendFeedback(
        source: FeedbackSource,
        motivo: String = "",
        mensagem: String,
        email: String = "",
        whatsapp: String = ""
    ): Result<Unit> {
        val cfg = _config
            ?: return Result.failure(IllegalStateException("FeedbackService não inicializado. Chame FeedbackService.initialize() primeiro."))

        val data = FeedbackData(
            appId = cfg.appId,
            appVersion = cfg.appVersion,
            source = source.valor,
            motivo = motivo,
            mensagem = mensagem,
            email = email,
            whatsapp = whatsapp,
            usuarioId = cfg.userId,
            usuarioEmail = cfg.userEmail,
            platform = currentPlatform,
            criadoEm = currentTimeMillis()
        )

        return send(data)
    }

    /**
     * Envia um FeedbackData diretamente.
     */
    suspend fun send(data: FeedbackData): Result<Unit> {
        return try {
            val cfg = _config
                ?: return Result.failure(IllegalStateException("FeedbackService não inicializado."))
            val json = buildFirestoreJson(data)
            val url = "https://firestore.googleapis.com/v1/projects/${cfg.firebaseProjectId}/databases/(default)/documents/$COLLECTION?key=${cfg.firebaseApiKey}"

            AppLogger.d(TAG, "Enviando feedback: source=${data.source}, app=${data.appId}")
            val result = httpPost(url, json)

            result.onSuccess {
                AppLogger.d(TAG, "Feedback enviado com sucesso")
            }.onFailure { e ->
                AppLogger.e(TAG, "Erro ao enviar feedback", e)
            }

            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao enviar feedback", e)
            Result.failure(e)
        }
    }

    private fun buildFirestoreJson(data: FeedbackData): String {
        val doc = buildJsonObject {
            putJsonObject("fields") {
                putJsonObject("appId") { put("stringValue", data.appId) }
                putJsonObject("appVersion") { put("stringValue", data.appVersion) }
                putJsonObject("source") { put("stringValue", data.source) }
                putJsonObject("motivo") { put("stringValue", data.motivo) }
                putJsonObject("mensagem") { put("stringValue", data.mensagem) }
                putJsonObject("email") { put("stringValue", data.email) }
                putJsonObject("whatsapp") { put("stringValue", data.whatsapp) }
                putJsonObject("usuarioId") { put("stringValue", data.usuarioId) }
                putJsonObject("usuarioEmail") { put("stringValue", data.usuarioEmail) }
                putJsonObject("platform") { put("stringValue", data.platform) }
                putJsonObject("criadoEm") { put("integerValue", data.criadoEm.toString()) }
            }
        }
        return doc.toString()
    }
}

internal expect val currentPlatform: String
internal expect suspend fun httpPost(url: String, body: String): Result<Unit>
