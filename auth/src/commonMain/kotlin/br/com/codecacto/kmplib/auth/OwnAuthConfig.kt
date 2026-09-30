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
 * @param texts mensagens de erro. **Sem passar (ou `null`), a lib usa os próprios recursos no
 *   idioma da tela** (pt-BR, en, es, pt-PT — `loadOwnAuthTexts()`, 2.219.0), lidos a cada erro.
 *   Passando um objeto, é ele que vale, como antes.
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
    texts: OwnAuthTexts? = null,
    val diagnostics: Boolean = false,
    socialSuffix: String = DEFAULT_SOCIAL_SUFFIX,
    socialNonceSuffix: String = DEFAULT_SOCIAL_NONCE_SUFFIX,
    socialStartSuffix: String = DEFAULT_SOCIAL_START_SUFFIX,
    socialExchangeSuffix: String = DEFAULT_SOCIAL_EXCHANGE_SUFFIX,
) {
    /**
     * As mensagens fixas: as que o app passou ou, sem elas, os defaults pt-BR. O fluxo em si usa
     * as do idioma da tela quando o app não passou nada — ver [customTexts].
     */
    val texts: OwnAuthTexts = texts ?: OwnAuthTexts()

    /** As mensagens que o APP passou, ou `null` — aí a lib lê as dela no idioma da tela. */
    internal val customTexts: OwnAuthTexts? = texts

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
    /** Troca de senha com a sessão aberta: a senha ATUAL não confere (2.216.0). */
    val currentPasswordIncorrect: String = "Senha atual incorreta.",
    /**
     * Edição do perfil recusada sem frase do servidor (2.229.0). Quase nunca aparece: a backlib
     * explica o motivo (nome vazio/longo, usuário em uso) e é a frase dela que vai para o campo.
     */
    val profileRejected: String = "Não foi possível salvar as alterações do perfil.",
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

    /**
     * **Edição do perfil recusada** pelo servidor (`PATCH {authBasePath}/me`, 2.229.0) — 400, 403,
     * 409 ou 422, com o envelope da backlib por inteiro.
     *
     * @property serverCode o `code` do envelope — compare com [OwnAuthErrorCodes]
     *   (`NOTHING_TO_UPDATE`, `PASSWORD_CHANGE_REQUIRED`, `USERNAME_CHANGE_DISABLED`,
     *   `USERNAME_TAKEN`); `null` se o servidor não mandou.
     * @property fieldErrors `details` do envelope: campo → frase. É o que põe o erro **no campo**
     *   (`fieldError("username")` = "Este nome de usuário já está em uso"), como manda a regra de
     *   formulário da fábrica, em vez de num banner solto.
     */
    class ProfileRejected(
        message: String,
        code: Int,
        val serverCode: String? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : OwnAuthException(message, code) {
        /** A frase do servidor para [field] (`"name"`, `"username"`), ou `null`. */
        fun fieldError(field: String): String? = fieldErrors[field]
    }

    /** `true` para 4xx (erro do cliente/credencial) — o refresh deve **fail-closed** (derrubar sessão). */
    val isClientError: Boolean get() = code in 400..499

    /** `true` para falha de transporte/sem-rede (transitório — nunca derruba a sessão). */
    val isNetwork: Boolean get() = code == OFFLINE_CODE

    companion object {
        /** Código sentinela de falha de transporte (não é status HTTP). */
        const val OFFLINE_CODE: Int = -1
    }
}

/**
 * Os `code` que a backlib-auth-local devolve no envelope de erro e que a tela precisa distinguir
 * (2.229.0). Constantes para o app não escrever a string na mão — é assim que um typo vira um `if`
 * que nunca casa.
 */
object OwnAuthErrorCodes {
    /** `PATCH /me` com corpo vazio (`{}`) — 400. */
    const val NOTHING_TO_UPDATE: String = "NOTHING_TO_UPDATE"

    /** Sessão ainda com a **senha temporária** — 403. Abra o `ForcePasswordChangeDialog`. */
    const val PASSWORD_CHANGE_REQUIRED: String = "PASSWORD_CHANGE_REQUIRED"

    /** O backend não deixa editar o nome de usuário (`usernameEditable = false`) — 403. */
    const val USERNAME_CHANGE_DISABLED: String = "USERNAME_CHANGE_DISABLED"

    /** Nome de usuário já em uso por outra conta — 409, com a frase em `details.username`. */
    const val USERNAME_TAKEN: String = "USERNAME_TAKEN"
}
