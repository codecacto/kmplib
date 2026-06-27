package br.com.codecacto.kmplib.contact

import io.ktor.client.HttpClient

/**
 * Configuração do módulo de Contato ("Entrar em contato" da tela "Desenvolvido por").
 *
 * O contato é enviado ao backend central **apps-api** (`POST {appsApiBaseUrl}/contact/v1`) — mesmo
 * endpoint usado pelos formulários de contato dos sites (weblib `ContactForm`). O endpoint é PÚBLICO
 * (sem auth) e protegido por rate-limit no servidor. Cada app passa o seu [projectSlug], que
 * diferencia os leads no painel admin (`central.contact`).
 *
 * Deve ser passado a [ContactService.initialize] na inicialização do app (mesmo ponto onde já se
 * inicializa o [br.com.codecacto.kmplib.feedback.FeedbackService] e o
 * [br.com.codecacto.kmplib.developer.DeveloperInfoService]).
 *
 * @param projectSlug Slug do app de origem (ex.: "influencer", "super-8"). OBRIGATÓRIO.
 * @param httpClient `HttpClient` (Ktor) do app. Ktor core puro — NÃO exige ContentNegotiation.
 * @param appsApiBaseUrl Base URL do apps-api (sem barra final). Default de produção.
 * @param source Origem do lead, para triagem no admin (ex.: "influencer-app"). Default "app".
 * @param userEmail E-mail do usuário autenticado (opcional). Usado como fallback do campo `email`
 *   quando o usuário não informa um e-mail no formulário.
 */
data class ContactConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    val source: String = "app",
    val userEmail: String = "",
) {
    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }
}
