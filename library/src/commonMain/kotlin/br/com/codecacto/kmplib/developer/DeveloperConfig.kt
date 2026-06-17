package br.com.codecacto.kmplib.developer

import io.ktor.client.HttpClient

/**
 * Configuração do módulo "Desenvolvido por CodeCacto".
 *
 * A partir da kmplib 2.36.0 o catálogo do desenvolvedor (contato + apps) NÃO é mais lido do
 * Firestore `code-cacto`. Ele é buscado do backend central **apps-api** num único GET PÚBLICO
 * (`GET {appsApiBaseUrl}/public/developer`). Por isso o serviço só precisa de um [HttpClient] e da
 * base URL — não há mais `projectId`/`apiKey` do Firebase.
 *
 * Deve ser passado a [DeveloperInfoService.initialize] na inicialização do app (mesmo ponto onde já
 * se inicializa o [br.com.codecacto.kmplib.feedback.FeedbackService]).
 *
 * @param httpClient `HttpClient` (Ktor) do app. Ktor core puro — NÃO exige ContentNegotiation.
 * @param appsApiBaseUrl Base URL do apps-api (sem barra final). Default de produção.
 */
data class DeveloperConfig(
    val httpClient: HttpClient,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
) {
    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }
}
