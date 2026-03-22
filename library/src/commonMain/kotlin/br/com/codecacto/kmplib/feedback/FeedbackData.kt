package br.com.codecacto.kmplib.feedback

import kotlinx.serialization.Serializable

/**
 * Dados de um feedback enviado ao Firestore.
 */
@Serializable
data class FeedbackData(
    val appId: String = "",
    val appVersion: String = "",
    val source: String = "",
    val motivo: String = "",
    val mensagem: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val usuarioId: String = "",
    val usuarioEmail: String = "",
    val platform: String = "",
    val criadoEm: Long = 0L
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
