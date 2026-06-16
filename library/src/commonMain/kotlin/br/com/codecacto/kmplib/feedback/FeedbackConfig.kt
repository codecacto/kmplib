package br.com.codecacto.kmplib.feedback

import io.ktor.client.HttpClient

/**
 * Configuração do módulo de Feedback.
 *
 * Deve ser passado ao [FeedbackService.initialize] na inicialização do app.
 *
 * A partir da kmplib 2.24.0 o feedback é enviado ao backend central **apps-api**
 * (`POST {appsApiBaseUrl}/feedback/v1`) e não mais ao Firestore `code-cacto`. O endpoint é
 * PÚBLICO (sem Firebase ID token) e protegido por rate-limit no servidor, então não há mais
 * `firebaseProjectId`/`firebaseApiKey` nem necessidade de auth anônimo só para o feedback.
 *
 * @param projectSlug Slug do app de origem no catálogo central (ex.: "super-8"). OBRIGATÓRIO —
 *   é o que diferencia os feedbacks no painel admin.
 * @param httpClient `HttpClient` (Ktor) do app. Ktor core puro — NÃO exige ContentNegotiation.
 * @param appsApiBaseUrl Base URL do apps-api (sem barra final). Default de produção.
 * @param appVersion Versão do app (ex.: "1.2.0"). Opcional; usado só para triagem.
 * @param userId UID do usuário autenticado (opcional). Capturado como NÃO confiável no servidor.
 * @param userEmail Email do usuário autenticado (opcional). A partir da 2.25.0 é usado como
 *   fallback do campo estruturado `email` do feedback quando o usuário não informa um e-mail.
 */
data class FeedbackConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    val appVersion: String = "",
    val userId: String = "",
    val userEmail: String = "",
) {
    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }
}
