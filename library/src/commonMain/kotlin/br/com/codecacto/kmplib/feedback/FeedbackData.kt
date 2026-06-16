package br.com.codecacto.kmplib.feedback

import kotlinx.serialization.Serializable

/**
 * Dados internos de um feedback (modelo de domínio do app, agnóstico de transporte).
 *
 * Continua existindo para a [ui.screens.feedback.FeedbackScreen] e para chamadas via
 * [FeedbackService.send]. O envio para o apps-api é feito mapeando esses campos para
 * [FeedbackRequest] (o endpoint central tem apenas `message`/`rating` + opcionais).
 */
@Serializable
data class FeedbackData(
    val appId: String = "",
    val appVersion: String = "",
    val source: String = "",
    val motivo: String = "",
    val mensagem: String = "",
    val nome: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val usuarioId: String = "",
    val usuarioEmail: String = "",
    val platform: String = "",
    val criadoEm: Long = 0L
)

/**
 * Corpo (camelCase) de `POST {appsApiBaseUrl}/feedback/v1` — casa exatamente o contrato do
 * módulo `feedback` do apps-api.
 *
 * Obrigatórios: [projectSlug], [message]. Opcionais: [name], [whatsapp], [email] (campos
 * ESTRUTURADOS — colunas dedicadas no banco central, a partir da 2.25.0), [rating] (1..5), [uid],
 * [appVersion], [platform] (`android`|`ios`|`web`). Campos nulos NÃO são serializados
 * (`encodeDefaults = false`).
 *
 * A partir da 2.25.0 o feedback é IDENTIFICADO: [name]/[email]/[whatsapp] vão como campos próprios
 * (não mais concatenados dentro de [message]). [message] passa a carregar apenas a descrição
 * (prefixada pelo `[motivo]`, que não tem coluna dedicada).
 */
@Serializable
data class FeedbackRequest(
    val projectSlug: String,
    val message: String,
    val name: String? = null,
    val whatsapp: String? = null,
    val email: String? = null,
    val rating: Int? = null,
    val uid: String? = null,
    val appVersion: String? = null,
    val platform: String? = null,
)

/** Resposta 201 de `POST /feedback/v1`. */
@Serializable
data class FeedbackResponse(
    val id: String = "",
    val projectSlug: String = "",
    val createdAt: Long = 0L,
)

/**
 * Motivos de feedback disponíveis.
 */
enum class FeedbackMotivo(val valor: String, val label: String) {
    SUGESTAO("sugestao", "Sugestão"),
    BUG("bug", "Reportar Bug"),
    RECLAMACAO("reclamacao", "Reclamação"),
    DUVIDA("duvida", "Dúvida"),
    ELOGIO("elogio", "Elogio"),
    OUTRO("outro", "Outro")
}

/**
 * Origem do feedback.
 */
enum class FeedbackSource(val valor: String) {
    FEEDBACK_SCREEN("feedback_screen"),
    REVIEW_DIALOG("review_dialog")
}
