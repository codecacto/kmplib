package br.com.codecacto.kmplib.feedback

/**
 * Configuração do módulo de Feedback.
 *
 * Deve ser passado ao [FeedbackService.initialize] na inicialização do app.
 *
 * @param appId Identificador do app (ex: "br.com.codecacto.locadora")
 * @param appVersion Versão do app (ex: "1.2.0")
 * @param userId ID do usuário autenticado (opcional)
 * @param userEmail Email do usuário autenticado (opcional)
 */
data class FeedbackConfig(
    val appId: String,
    val appVersion: String = "",
    val userId: String = "",
    val userEmail: String = ""
)
