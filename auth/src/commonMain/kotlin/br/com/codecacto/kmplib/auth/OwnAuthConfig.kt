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
 * @param diagnostics liga o **rastro de diagnóstico** do login no [AppLogger] (tag `OwnAuthApi`):
 *   rota chamada, status HTTP e o e-mail EXATO que o app enviou, com comprimento e os pontos de
 *   código dos caracteres não-ASCII — é assim que se enxerga espaço invisível, acento ou palavra
 *   trocada pelo corretor do teclado. **Nunca loga a senha** (só o comprimento).
 *   **Default `false`: isto imprime dado pessoal no log do aparelho.** Ligue apenas em build de
 *   debug (ex.: `diagnostics = isDebugBuild`), jamais em release.
 */
class OwnAuthConfig(
    val httpClient: HttpClient,
    baseUrl: String,
    authBasePath: String = DEFAULT_AUTH_BASE_PATH,
    val refreshSkewSeconds: Long = DEFAULT_REFRESH_SKEW_SECONDS,
    val json: Json = DefaultHttpClientJson,
    val texts: OwnAuthTexts = OwnAuthTexts(),
    val diagnostics: Boolean = false,
    socialSuffix: String = DEFAULT_SOCIAL_SUFFIX,
    socialNonceSuffix: String = DEFAULT_SOCIAL_NONCE_SUFFIX,
    socialStartSuffix: String = DEFAULT_SOCIAL_START_SUFFIX,
    socialExchangeSuffix: String = DEFAULT_SOCIAL_EXCHANGE_SUFFIX,
) {
    /** Base normalizada (sem barra final). */
    val baseUrl: String = baseUrl.trimEnd('/')

    /** Prefixo de auth normalizado (com barra inicial, sem barra final). */
    val authBasePath: String = "/" + authBasePath.trim('/')

    /** Sufixo de `POST .../social` (login social). Normalizado sem barras nas pontas. */
    val socialSuffix: String = socialSuffix.trim('/')

    /** Sufixo de `GET .../social/nonce` (emissão do nonce). Normalizado sem barras nas pontas. */
    val socialNonceSuffix: String = socialNonceSuffix.trim('/')

    /** Sufixo de `GET .../social/start` — abre o login social conduzido pelo backend. */
    val socialStartSuffix: String = socialStartSuffix.trim('/')

    /** Sufixo de `POST .../social/exchange` — troca o código do *deep link* pela sessão. */
    val socialExchangeSuffix: String = socialExchangeSuffix.trim('/')

    init {
        // Sufixo em branco casaria com TODA rota no roteamento de erro (`"login".startsWith("")`),
        // e o 401 genérico do login por senha passaria a vazar a mensagem do servidor sob a regra do
        // social. Falha alto na construção, que é onde o erro é do programador e não do usuário.
        require(this.socialSuffix.isNotBlank()) { "socialSuffix não pode ser vazio" }
        require(this.socialNonceSuffix.isNotBlank()) { "socialNonceSuffix não pode ser vazio" }
    }

    internal fun url(suffix: String): String = baseUrl + authBasePath + "/" + suffix.trimStart('/')

    companion object {
        const val DEFAULT_AUTH_BASE_PATH: String = "/v1/staff/auth"
        const val DEFAULT_REFRESH_SKEW_SECONDS: Long = 60

        /** `POST {authBasePath}/social` — troca o `idToken` do provedor pelo par de tokens próprio. */
        const val DEFAULT_SOCIAL_SUFFIX: String = "social"

        /** `GET {authBasePath}/social/nonce` — nonce de uso único emitido pelo servidor. */
        const val DEFAULT_SOCIAL_NONCE_SUFFIX: String = "social/nonce"

        /**
         * `GET {authBasePath}/social/start` — o login social **conduzido pelo backend**.
         *
         * Esta URL não é chamada pelo cliente HTTP: ela é **aberta no navegador do sistema**, e o
         * backend responde com um redirecionamento para o provedor.
         */
        const val DEFAULT_SOCIAL_START_SUFFIX: String = "social/start"

        /** `POST {authBasePath}/social/exchange` — o código do *deep link* vira sessão. */
        const val DEFAULT_SOCIAL_EXCHANGE_SUFFIX: String = "social/exchange"
    }
}

/** Mensagens de erro do fluxo own-auth (defaults pt-BR). */
data class OwnAuthTexts(
    val invalidCredentials: String = "E-mail ou senha incorretos.",
    val emailAlreadyInUse: String = "Este e-mail já está em uso.",
    /**
     * Só entra em cena se o servidor NÃO explicar o motivo — o `OwnAuthApi` prefere a mensagem do
     * backend, que sabe o mínimo real ("A senha deve ter ao menos 6 caracteres"). "Senha fraca" não
     * diz a ninguém o que corrigir.
     */
    val weakPassword: String = "A senha não atende aos requisitos mínimos.",
    val invalidResetToken: String = "Link de definição de senha inválido ou expirado.",
    val tooManyRequests: String = "Muitas tentativas. Tente novamente em instantes.",
    val network: String = "Sem conexão com o servidor.",
    val sessionExpired: String = "Sessão expirada. Entre novamente.",
    /**
     * Fallback quando o servidor recusa o `idToken` social sem explicar (nonce vencido/reusado,
     * `aud` inesperado, e-mail não verificado pelo provedor). Se o backend mandar `message`, é ela
     * que aparece — ela sabe o motivo real.
     */
    val socialRejected: String = "Não foi possível concluir o login com esta conta. Tente novamente.",
    /**
     * Erro de PROGRAMAÇÃO do app, não do usuário: pediu `signInWithGoogle` sem antes buscar o nonce
     * no servidor. Aparece explícito em vez de mandar um nonce inventado (que o servidor recusaria
     * com uma mensagem enganosa de credencial inválida).
     */
    val socialNonceMissing: String =
        "Nonce do servidor ausente: chame socialNonce() antes do login social (ou use signInWithSocial).",
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
