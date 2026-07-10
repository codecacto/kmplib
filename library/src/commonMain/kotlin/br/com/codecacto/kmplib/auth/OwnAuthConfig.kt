package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.core.network.DefaultHttpClientJson
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

/**
 * Configuração do cliente de **autenticação própria (own-auth)** — e-mail + senha contra um backend
 * REST que É o próprio IdP (sem Firebase). O `authBasePath` é configurável para o cliente servir
 * qualquer projeto CodeCacto (o piloto Meu Barbeiro usa `/v1/staff/auth`).
 *
 * @param httpClient cliente Ktor do app. **Não** precisa de `ContentNegotiation` — a (de)serialização
 *   é feita internamente com kotlinx-json a partir do texto cru (mesmo padrão de `RestConfig`/
 *   `DomainApiClient`). Use o `createHttpClient()` da lib (`expectSuccess=false`).
 * @param baseUrl base do backend, SEM barra final (ex.: `"https://meubarbeiro-api.codecacto.com.br"`).
 * @param authBasePath prefixo das rotas de auth, com barra inicial e SEM barra final
 *   (default `"/v1/staff/auth"`). As rotas finais são `$authBasePath/register`, `/login`, `/refresh`,
 *   `/logout`, `/password/forgot`, `/password/reset`.
 * @param refreshSkewSeconds margem (segundos) para o **refresh proativo**: o token é renovado quando
 *   falta menos que isto para expirar (default 60s), evitando enviar um access token quase-morto.
 * @param json instância kotlinx [Json] usada na (de)serialização (default tolerante da lib).
 * @param texts mensagens de erro (defaults pt-BR; injete traduções via `*Texts` do app).
 */
class OwnAuthConfig(
    val httpClient: HttpClient,
    baseUrl: String,
    authBasePath: String = DEFAULT_AUTH_BASE_PATH,
    val refreshSkewSeconds: Long = DEFAULT_REFRESH_SKEW_SECONDS,
    val json: Json = DefaultHttpClientJson,
    val texts: OwnAuthTexts = OwnAuthTexts(),
) {
    /** Base normalizada (sem barra final). */
    val baseUrl: String = baseUrl.trimEnd('/')

    /** Prefixo de auth normalizado (com barra inicial, sem barra final). */
    val authBasePath: String = "/" + authBasePath.trim('/')

    internal fun url(suffix: String): String = baseUrl + authBasePath + "/" + suffix.trimStart('/')

    companion object {
        const val DEFAULT_AUTH_BASE_PATH: String = "/v1/staff/auth"
        const val DEFAULT_REFRESH_SKEW_SECONDS: Long = 60
    }
}

/** Mensagens de erro do fluxo own-auth (defaults pt-BR). */
data class OwnAuthTexts(
    val invalidCredentials: String = "E-mail ou senha incorretos.",
    val emailAlreadyInUse: String = "Este e-mail já está em uso.",
    val weakPassword: String = "Senha muito fraca. Use pelo menos 6 caracteres.",
    val invalidResetToken: String = "Link de definição de senha inválido ou expirado.",
    val tooManyRequests: String = "Muitas tentativas. Tente novamente em instantes.",
    val network: String = "Sem conexão com o servidor.",
    val sessionExpired: String = "Sessão expirada. Entre novamente.",
    val server: (Int) -> String = { code -> "Erro do servidor ($code)." },
    val unsupported: String = "Operação não disponível para login por e-mail e senha.",
)

/**
 * Exceção tipada do fluxo own-auth. [code] carrega o status HTTP (ou [OFFLINE_CODE] para falha de
 * transporte), o que permite ao gerenciador de token distinguir **4xx (fail-closed: derruba a sessão)**
 * de **erro de rede (transitório: preserva a sessão)**.
 */
sealed class OwnAuthException(override val message: String, val code: Int) : Exception(message) {
    class InvalidCredentials(message: String) : OwnAuthException(message, 401)
    class EmailAlreadyInUse(message: String) : OwnAuthException(message, 409)
    class WeakPassword(message: String) : OwnAuthException(message, 422)
    class InvalidResetToken(message: String) : OwnAuthException(message, 400)
    class TooManyRequests(message: String) : OwnAuthException(message, 429)
    class Server(message: String, code: Int) : OwnAuthException(message, code)
    class Network(message: String) : OwnAuthException(message, OFFLINE_CODE)
    class NotAuthenticated(message: String) : OwnAuthException(message, 401)
    class Unsupported(message: String) : OwnAuthException(message, -2)

    /** `true` para 4xx (erro do cliente/credencial) — o refresh deve **fail-closed** (derrubar sessão). */
    val isClientError: Boolean get() = code in 400..499

    /** `true` para falha de transporte/sem-rede (transitório — nunca derruba a sessão). */
    val isNetwork: Boolean get() = code == OFFLINE_CODE

    companion object {
        /** Código sentinela de falha de transporte (não é status HTTP). */
        const val OFFLINE_CODE: Int = -1
    }
}
