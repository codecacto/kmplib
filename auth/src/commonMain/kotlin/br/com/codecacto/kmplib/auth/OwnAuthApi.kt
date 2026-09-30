package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.rest.ServerErrorEnvelope
import br.com.codecacto.kmplib.sync.rest.parseServerErrorEnvelope
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter

/**
 * Cliente REST **puro/sem estado** dos endpoints da autenticação própria (senha, social e, desde a
 * 2.229.0, o perfil `GET`/`PATCH {authBasePath}/me`). Ktor core puro (SEM
 * `ContentNegotiation`) + kotlinx-json manual, mesmo padrão de `DomainApiClient`/`RestSyncPort`.
 *
 * Traduz status HTTP em [OwnAuthException] tipada (para o [OwnAuthTokenManager] distinguir 4xx de
 * rede); erro de transporte **nunca lança tipo cru** — vira [OwnAuthException.Network]. Este cliente
 * NÃO guarda tokens nem decide refresh; isso é do [OwnAuthTokenManager].
 */
class OwnAuthApi(private val config: OwnAuthConfig) {

    private val client get() = config.httpClient
    private val json get() = config.json

    /** As mensagens da vez: as do app, se ele passou; senão as da lib, no idioma da tela. */
    private suspend fun texts(): OwnAuthTexts = config.customTexts ?: loadOwnAuthTexts()

    /**
     * Cria a conta.
     *
     * @param locale idioma da conta, BCP 47 (`pt-BR`, `en`, `es`, `pt-PT`) — 2.219.0, par da backlib
     *   0.134.0. É por ele que o servidor escreve e-mail, PDF e push no idioma da pessoa. Passe
     *   `appLanguageTag()` (ou `uiLanguageTag()`); `null` (default) não manda o campo, e servidor
     *   anterior à 0.134.0 continua recebendo exatamente o corpo de antes.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        acceptedTerms: Boolean,
        phone: String? = null,
        locale: String? = null,
    ): Result<OwnAuthTokens> =
        postForTokens(
            "register",
            json.encodeToString(
                RegisterBody(name, email, password, acceptedTerms, phone, locale?.trim()?.takeIf { it.isNotEmpty() }),
            ),
        )

    /**
     * Login por **e-mail ou nome de usuário** — o que o campo aceita é decidido pelo servidor
     * (`GET {authBasePath}/config`), e quem resolve onde procurar é o `@`.
     *
     * O parâmetro se chama `identifier` desde a 2.132.0; chamadas posicionais existentes não sentem.
     */
    suspend fun login(identifier: String, password: String): Result<OwnAuthTokens> {
        logCredentialShape("login", identifier, password)
        val body = LoginBody(
            identifier = identifier,
            password = password,
            // O campo histórico vai junto quando é um e-mail: backend anterior à 0.80.0 só entende
            // `email`, e sem ele o login responderia "inválido" para credencial correta.
            email = identifier.takeIf { it.contains('@') },
        )
        return postForTokens("login", json.encodeToString(body))
    }

    /**
     * `password/first-access` — o titular troca a **senha temporária** pela dele, no primeiro acesso.
     *
     * Não manda a senha atual: quem chega aqui acabou de apresentá-la no login, e é o **access token
     * da sessão restrita** que autoriza a chamada. Responde com **tokens novos e plenos** — a troca
     * revoga todas as sessões, então o par antigo morre no mesmo instante e precisa ser substituído
     * no [AuthSessionStore]. Guardar os novos é obrigação de quem chama; sem isso o usuário define a
     * senha e é jogado para a tela de login no toque seguinte.
     */
    suspend fun firstAccessPasswordChange(
        newPassword: String,
        accessToken: String,
    ): Result<OwnAuthTokens> =
        send("password/first-access", "POST") {
            client.post(config.url("password/first-access")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                setBody(json.encodeToString(FirstAccessBody(newPassword)))
            }
        }.mapCatching { response ->
            json.decodeFromString(OwnAuthTokens.serializer(), response.bodyAsText())
        }

    /**
     * `password/change` (backlib-auth-local ≥ 0.46.0) — a **troca voluntária**, com a sessão aberta.
     *
     * Exige a senha atual (uma sessão sequestrada não pode expulsar o dono) e responde **204**. O
     * servidor **derruba todas as sessões** da conta ao fim, inclusive a desta chamada: o refresh
     * guardado morre no mesmo instante. Quem cuida disso é o
     * [EmailPasswordAuthRepository.changeOwnPassword], que entra de novo com a senha nova — chamar
     * este método cru deixa a pessoa deslogada no próximo refresh.
     *
     * Erros: senha atual errada → [OwnAuthException.InvalidCredentials] (com a frase do servidor);
     * senha nova recusada (curta, igual à atual) → [OwnAuthException.WeakPassword] com o motivo.
     */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        accessToken: String,
    ): Result<Unit> =
        send("password/change", "POST") {
            client.post(config.url("password/change")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                setBody(json.encodeToString(ChangePasswordBody(currentPassword, newPassword)))
            }
        }.map { }

    /**
     * `GET {authBasePath}/me` — o perfil da conta dona do [accessToken]: id, e-mail, nome e, desde a
     * backlib 0.140.0, o **nome de usuário** (2.229.0).
     *
     * Na sessão com senha temporária o servidor devolve `email` vazio e `username` nulo, de
     * propósito — ver [OwnAuthProfile]. Erros: 401 → [OwnAuthException.NotAuthenticated] (token
     * vencido ou conta que não existe mais).
     */
    suspend fun me(accessToken: String): Result<OwnAuthProfile> =
        send(ME_SUFFIX, "GET") {
            client.get(config.url(ME_SUFFIX)) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.mapCatching { response ->
            json.decodeFromString(OwnAuthProfile.serializer(), response.bodyAsText())
        }

    /**
     * `PATCH {authBasePath}/me` (backlib-auth-local ≥ 0.140.0) — o titular edita o **próprio**
     * nome e, se o backend liga `usernameEditable`, o nome de usuário. Parcial: parâmetro `null` não
     * vai no corpo e o servidor o deixa como está. Responde 200 com o perfil já gravado.
     *
     * A conta vem do token, nunca do corpo. Erros, todos com a frase do servidor:
     * - [OwnAuthException.ProfileRejected] — 400 (`NOTHING_TO_UPDATE`, nome vazio/longo em
     *   `fieldErrors["name"]`, usuário fora da régua em `fieldErrors["username"]`), 403
     *   (`PASSWORD_CHANGE_REQUIRED`, `USERNAME_CHANGE_DISABLED`), 409 (`USERNAME_TAKEN`). Os códigos
     *   estão em [OwnAuthErrorCodes].
     * - [OwnAuthException.Unsupported] — **404**: o backend não publica a rota (sem
     *   `AuthLocalProfileEditor` no Koin, ou backlib anterior à 0.140.0).
     * - [OwnAuthException.NotAuthenticated] — 401. [OwnAuthException.TooManyRequests] — 429.
     *
     * Prefira [OwnAuthService.updateOwnProfile], que pega um token válido e atualiza o
     * `currentUser` com a resposta.
     */
    suspend fun updateMe(
        accessToken: String,
        name: String? = null,
        username: String? = null,
    ): Result<OwnAuthProfile> =
        send(ME_SUFFIX, "PATCH") {
            client.patch(config.url(ME_SUFFIX)) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                setBody(json.encodeToString(UpdateMeBody.serializer(), UpdateMeBody(name, username)))
            }
        }.mapCatching { response ->
            json.decodeFromString(OwnAuthProfile.serializer(), response.bodyAsText())
        }

    suspend fun refresh(refreshToken: String): Result<OwnAuthTokens> =
        postForTokens("refresh", json.encodeToString(RefreshBody(refreshToken)))

    /**
     * `GET {authBasePath}/social/nonce` — pede ao **servidor** o nonce de uso único que será
     * embutido no `idToken` do provedor social.
     *
     * O nonce **nunca** é gerado no aparelho: ele só prova alguma coisa se quem verifica for quem
     * emitiu. Ver [SocialNonce].
     */
    /**
     * `GET {authBasePath}/config` — **o que o campo de login deve pedir, e com que rótulo.**
     *
     * Público de propósito: a tela precisa disso antes de existir qualquer token. E **nunca falha
     * para o usuário** — rede fora, backend anterior à 0.80.0 (404) ou corpo inesperado devolvem
     * [OwnAuthIdentifierConfig.Default] (`EMAIL`), que é o comportamento de sempre. Uma tela de login
     * que não abre porque o endpoint do *rótulo* caiu seria trocar um inconveniente por uma porta
     * trancada.
     *
     * É o que mantém app e portal **em sincronia sem republicar nada**: os dois falam com o mesmo
     * backend e leem a mesma configuração.
     */
    suspend fun identifierConfig(): OwnAuthIdentifierConfig =
        executeGet("config")
            .mapCatching { json.decodeFromString(OwnAuthIdentifierConfig.serializer(), it.bodyAsText()) }
            .getOrDefault(OwnAuthIdentifierConfig.Default)

    suspend fun socialNonce(): Result<SocialNonce> =
        executeGet(config.socialNonceSuffix).mapCatching { response ->
            json.decodeFromString(SocialNonce.serializer(), response.bodyAsText())
        }

    /**
     * `POST {authBasePath}/social` — troca o `idToken` do provedor pelo par de tokens **próprio**,
     * no MESMO shape de `login`/`register`/`refresh` (`{accessToken, refreshToken,
     * expiresInSeconds}` na raiz).
     *
     * @param idToken o JWT do provedor. **Nunca** um access token do Google: ele não prova
     *   identidade e o backend o recusa (ver `GoogleSignInResult`).
     * @param nonce o valor **cru** emitido por [socialNonce] (a Apple recebeu o SHA-256 dele; o
     *   servidor refaz o hash e compara).
     * @param name/@param email só têm efeito na **criação** da identidade — a Apple só entrega
     *   nome/e-mail na primeira autorização, e em login subsequente o servidor os ignora.
     */
    suspend fun social(
        provider: SocialProvider,
        idToken: String,
        nonce: String,
        name: String? = null,
        email: String? = null,
    ): Result<OwnAuthTokens> = postForTokens(
        config.socialSuffix,
        json.encodeToString(
            SocialBody(
                provider = provider.wire,
                idToken = idToken,
                nonce = nonce,
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                email = email?.trim()?.takeIf { it.isNotEmpty() },
            )
        ),
    )

    /**
     * URL do `GET {authBasePath}/social/start` — para **abrir no navegador do sistema**, não para
     * chamar pelo cliente HTTP (o backend responde com um redirecionamento para o provedor, e quem
     * precisa segui-lo é o navegador, com a sessão do usuário).
     *
     * @param appId qual aplicativo está pedindo o login. Numa família de flavors é o que decide para
     *   onde o usuário volta — e o backend confere contra a allowlist dele.
     * @param codeChallenge desafio PKCE gerado por [br.com.codecacto.kmplib.auth.social.PkcePair].
     */
    fun socialStartUrl(
        provider: SocialProvider,
        appId: String,
        codeChallenge: String,
        loginHint: String? = null,
    ): String {
        val params = buildList {
            add("app" to appId)
            add("provider" to provider.wire)
            add("codeChallenge" to codeChallenge)
            loginHint?.trim()?.takeIf { it.isNotEmpty() }?.let { add("loginHint" to it) }
        }
        val query = params.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
        return config.url(config.socialStartSuffix) + "?" + query
    }

    /**
     * `POST {authBasePath}/social/exchange` — troca o código que voltou no *deep link* pelo par de
     * tokens, no MESMO shape de `login`/`register`/`refresh`.
     *
     * @param codeVerifier o par do desafio enviado no [socialStartUrl]. Sem ele o backend recusa: é
     *   o que impede que outro aplicativo, capaz de reivindicar o mesmo esquema de URL, troque um
     *   código interceptado pela sessão.
     */
    suspend fun socialExchange(code: String, codeVerifier: String): Result<OwnAuthTokens> =
        postForTokens(
            config.socialExchangeSuffix,
            json.encodeToString(SocialExchangeBody.serializer(), SocialExchangeBody(code, codeVerifier)),
        )

    /** `logout` revoga a família do refresh. Best-effort e idempotente (204). */
    suspend fun logout(refreshToken: String): Result<Unit> =
        postForUnit("logout", json.encodeToString(LogoutBody(refreshToken)))

    /** `password/forgot` — SEMPRE 200 genérico (não revela se o e-mail existe). */
    suspend fun requestPasswordReset(email: String): Result<Unit> =
        postForUnit("password/forgot", json.encodeToString(PasswordForgotBody(email)))

    /** `password/reset` — consome o token (uso único) e grava a nova senha (204). */
    suspend fun confirmPasswordReset(token: String, newPassword: String): Result<Unit> =
        postForUnit("password/reset", json.encodeToString(PasswordResetBody(token, newPassword)))

    // ---- núcleo ----------------------------------------------------------

    private suspend fun postForTokens(suffix: String, body: String): Result<OwnAuthTokens> =
        execute(suffix, body).mapCatching { response ->
            val text = response.bodyAsText()
            json.decodeFromString(OwnAuthTokens.serializer(), text)
        }

    private suspend fun postForUnit(suffix: String, body: String): Result<Unit> =
        execute(suffix, body).map { }

    private suspend fun execute(suffix: String, body: String): Result<HttpResponse> =
        send(suffix, "POST") {
            client.post(config.url(suffix)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend fun executeGet(suffix: String): Result<HttpResponse> =
        send(suffix, "GET") { client.get(config.url(suffix)) }

    private suspend fun send(
        suffix: String,
        verb: String,
        call: suspend () -> HttpResponse,
    ): Result<HttpResponse> {
        val url = config.url(suffix)
        if (config.diagnostics) AppLogger.d(TAG, "→ $verb $url")
        val response = try {
            call()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha de transporte em $suffix: ${e.message}")
            return Result.failure(OwnAuthException.Network(texts().network))
        }
        val status = response.status.value
        if (config.diagnostics) AppLogger.d(TAG, "← $status $url")
        return if (status in 200..299) {
            Result.success(response)
        } else {
            // A mensagem do servidor é mais útil que qualquer texto fixo daqui: ele é quem sabe o
            // mínimo de caracteres exigido, qual campo faltou, etc. O texto local vira fallback.
            val envelope = parseServerErrorEnvelope(runCatching { response.bodyAsText() }.getOrNull())
            Result.failure(mapStatus(texts(), suffix, status, envelope))
        }
    }

    /**
     * Rastro de diagnóstico das credenciais (só com [OwnAuthConfig.diagnostics] ligado — ver o KDoc
     * de lá: imprime dado pessoal, é para build de debug).
     *
     * Mostra o e-mail **entre delimitadores**, o comprimento e os pontos de código dos caracteres
     * não-ASCII. É o que revela o que a tela não mostra: espaço invisível colado pelo teclado, acento
     * inserido pelo corretor, ou palavra inteira substituída por sugestão. Da senha sai **apenas** o
     * comprimento e se ela tem espaço nas bordas — nunca o valor.
     */
    private fun logCredentialShape(suffix: String, email: String, password: String) {
        if (!config.diagnostics) return
        val estranhos = email.mapIndexedNotNull { i, c ->
            if (c.code in 32..126) null else "[$i]=U+${c.code.toString(16).uppercase().padStart(4, '0')}"
        }
        AppLogger.d(TAG, "$suffix e-mail=>>>$email<<< (${email.length} chars)" +
            if (estranhos.isEmpty()) " — só ASCII imprimível" else " — NÃO-ASCII: ${estranhos.joinToString(" ")}")
        AppLogger.d(TAG, "$suffix senha=${password.length} chars" +
            (if (password != password.trim()) " — ATENÇÃO: tem espaço no começo/fim" else ""))
    }

    private fun mapStatus(texts: OwnAuthTexts, suffix: String, status: Int, envelope: ServerErrorEnvelope?): OwnAuthException {
        val serverMessage = envelope?.message
        if (suffix == ME_SUFFIX) return mapProfileStatus(texts, status, envelope)
        // Social tem um vocabulário de recusa próprio: nonce vencido/reusado, `aud` inesperado,
        // `email_verified=false`, assinatura inválida. Todos significam a mesma coisa para a tela —
        // "esta prova de identidade não serve" —, e a mensagem do servidor é a única que diz qual
        // dos casos foi (por isso ela tem prioridade, ao contrário do 401 de senha, que é genérico
        // de propósito para não revelar se o e-mail existe).
        if (suffix.startsWith(config.socialSuffix)) {
            return when (status) {
                400, 401, 403, 422 ->
                    OwnAuthException.InvalidCredentials(serverMessage ?: texts.socialRejected)
                429 -> OwnAuthException.TooManyRequests(texts.tooManyRequests)
                else -> serverFailure(texts, status, serverMessage)
            }
        }
        return mapPasswordStatus(texts, suffix, status, serverMessage)
    }

    private fun mapPasswordStatus(texts: OwnAuthTexts, suffix: String, status: Int, serverMessage: String?): OwnAuthException =
        // Na troca com a sessão aberta, o 401 é "senha ATUAL incorreta" — e o texto de login
        // ("e-mail ou senha incorretos") mandaria a pessoa conferir um e-mail que ela nem digitou.
        if (status == 401 && suffix == "password/change") {
            OwnAuthException.InvalidCredentials(texts.currentPasswordIncorrect)
        } else {
            mapGenericPasswordStatus(texts, suffix, status, serverMessage)
        }

    private fun mapGenericPasswordStatus(texts: OwnAuthTexts, suffix: String, status: Int, serverMessage: String?): OwnAuthException = when (status) {
        // Credencial inválida mantém o texto local DE PROPÓSITO: o servidor responde genérico para não
        // revelar se o e-mail existe, e repassar a mensagem dele não acrescentaria nada.
        401, 403 -> OwnAuthException.InvalidCredentials(texts.invalidCredentials)
        409 -> OwnAuthException.EmailAlreadyInUse(serverMessage ?: texts.emailAlreadyInUse)
        422 -> if (suffix.startsWith("register") || suffix.startsWith("password")) {
            OwnAuthException.WeakPassword(serverMessage ?: texts.weakPassword)
        } else {
            serverFailure(texts, status, serverMessage)
        }
        400 -> when {
            suffix.startsWith("password/reset") ->
                OwnAuthException.InvalidResetToken(serverMessage ?: texts.invalidResetToken)
            // Validação do backend (ex.: "A senha deve ter ao menos 6 caracteres") — mostrar o motivo
            // real, que é justamente o que a pessoa precisa saber para corrigir.
            suffix.startsWith("register") || suffix.startsWith("password") ->
                OwnAuthException.WeakPassword(serverMessage ?: texts.weakPassword)
            else -> serverFailure(texts, status, serverMessage)
        }
        429 -> OwnAuthException.TooManyRequests(texts.tooManyRequests)
        else -> serverFailure(texts, status, serverMessage)
    }

    /**
     * Perfil (`GET`/`PATCH /me`). Recusa de regra (400/403/409/422) carrega o envelope inteiro —
     * `code` e `details` — para a tela marcar o campo certo com a frase do servidor.
     */
    private fun mapProfileStatus(texts: OwnAuthTexts, status: Int, envelope: ServerErrorEnvelope?): OwnAuthException =
        when (status) {
            401 -> OwnAuthException.NotAuthenticated(texts.sessionExpired)
            400, 403, 409, 422 -> OwnAuthException.ProfileRejected(
                message = envelope?.message ?: texts.profileRejected,
                code = status,
                serverCode = envelope?.code,
                fieldErrors = envelope?.details.orEmpty(),
            )
            // Rota ausente: backend sem `AuthLocalProfileEditor` (ou anterior à 0.140.0). Não é
            // falha do usuário nem do servidor — a operação não existe ali.
            404 -> OwnAuthException.Unsupported(texts.unsupported)
            429 -> OwnAuthException.TooManyRequests(texts.tooManyRequests)
            else -> serverFailure(texts, status, envelope?.message)
        }

    /**
     * Falha genérica do servidor. Em **5xx** a mensagem do corpo é descartada (2.218.0): numa falha
     * interna ela é o que o servidor deixou escapar — exceção, SQL, nome de tabela, trecho de stack —,
     * e ia parar na tela. Só 4xx (validação, regra) traz texto escrito para a pessoa ler.
     */
    private fun serverFailure(texts: OwnAuthTexts, status: Int, serverMessage: String?): OwnAuthException.Server =
        OwnAuthException.Server(
            if (status >= 500) texts.server(status) else serverMessage ?: texts.server(status),
            status,
        )

    companion object {
        private const val TAG = "OwnAuthApi"

        /** `GET`/`PATCH {authBasePath}/me` — perfil da conta autenticada. */
        private const val ME_SUFFIX = "me"
    }
}
