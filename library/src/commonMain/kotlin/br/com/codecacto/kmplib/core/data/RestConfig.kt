package br.com.codecacto.kmplib.core.data

import io.ktor.client.HttpClient

/**
 * Configuração compartilhada por todos os [RestRepository] de um app que falam com o **mesmo**
 * backend REST central (`apps-api`).
 *
 * Declare **uma** instância por app (singleton no Koin) e reaproveite-a ao criar cada repositório
 * de entidade via [RestRepositoryFactory]. Assim `httpClient`, `baseUrl`, autenticação e o
 * tratamento de 401 ficam num só lugar.
 *
 * @param httpClient Cliente Ktor (pode ser o mesmo do app; **não** precisa de ContentNegotiation —
 *  a serialização é feita internamente com kotlinx-json a partir do texto cru, exatamente como o
 *  `AdminApiEntitlementRepository`).
 * @param baseUrl Base do backend, SEM barra final (ex.: `"https://api.codecacto.com.br"`).
 * @param projectSlug Slug do projeto no backend (ex.: `"meu-advogado"`). Opcional; só é usado se o
 *  `pathPrefix` do repositório referenciar o slug — o repositório monta o caminho final por conta
 *  própria, este campo é só conveniência/documentação para a factory.
 * @param tokenProvider Provedor do **Firebase ID token** do usuário logado. É `suspend` porque
 *  `getIdToken()` do Firebase é suspenso. Quando devolve não-nulo, vai como
 *  `Authorization: Bearer <token>`. Devolve `null` quando não há sessão (chamada anônima).
 * @param onUnauthorized Callback invocado quando o servidor responde **401** (sessão inválida/
 *  expirada). O app tipicamente força re-login ou refaz o token. É chamado de forma best-effort,
 *  antes de o erro ser propagado como `ApiResult.Error(code = 401, ...)`.
 * @param cacheTtlMillis TTL do cache curto em memória das leituras (`getById`/`list`), em ms. Use
 *  `0` para desabilitar. NUNCA é cache offline persistente — apenas evita refazer a mesma chamada
 *  em navegações rápidas, e nunca cacheia erros.
 */
class RestConfig(
    val httpClient: HttpClient,
    baseUrl: String,
    val projectSlug: String? = null,
    val tokenProvider: (suspend () -> String?)? = null,
    val onUnauthorized: (suspend () -> Unit)? = null,
    val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
) {
    /** Base já normalizada (sem barra final). */
    val baseUrl: String = baseUrl.trimEnd('/')

    companion object {
        const val DEFAULT_CACHE_TTL_MILLIS: Long = 30_000L
    }
}
