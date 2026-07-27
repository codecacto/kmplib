package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cliente REST **puro/sem estado** dos 6 endpoints da autenticação própria. Ktor core puro (SEM
 * `ContentNegotiation`) + kotlinx-json manual, mesmo padrão de `DomainApiClient`/`RestSyncPort`.
 *
 * Traduz status HTTP em [OwnAuthException] tipada (para o [OwnAuthTokenManager] distinguir 4xx de
 * rede); erro de transporte **nunca lança tipo cru** — vira [OwnAuthException.Network]. Este cliente
 * NÃO guarda tokens nem decide refresh; isso é do [OwnAuthTokenManager].
 */
class OwnAuthApi(private val config: OwnAuthConfig) {

    private val client get() = config.httpClient
    private val json get() = config.json
    private val texts get() = config.texts

    suspend fun register(name: String, email: String, password: String, acceptedTerms: Boolean): Result<OwnAuthTokens> =
        postForTokens("register", json.encodeToString(RegisterBody(name, email, password, acceptedTerms)))

    suspend fun login(email: String, password: String): Result<OwnAuthTokens> {
        logCredentialShape("login", email, password)
        return postForTokens("login", json.encodeToString(LoginBody(email, password)))
    }

    suspend fun refresh(refreshToken: String): Result<OwnAuthTokens> =
        postForTokens("refresh", json.encodeToString(RefreshBody(refreshToken)))

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

    private suspend fun execute(suffix: String, body: String): Result<HttpResponse> {
        val url = config.url(suffix)
        if (config.diagnostics) AppLogger.d(TAG, "→ POST $url")
        val response = try {
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha de transporte em $suffix: ${e.message}")
            return Result.failure(OwnAuthException.Network(texts.network))
        }
        val status = response.status.value
        if (config.diagnostics) AppLogger.d(TAG, "← $status $url")
        return if (status in 200..299) {
            Result.success(response)
        } else {
            // A mensagem do servidor é mais útil que qualquer texto fixo daqui: ele é quem sabe o
            // mínimo de caracteres exigido, qual campo faltou, etc. O texto local vira fallback.
            Result.failure(mapStatus(suffix, status, response.serverMessageOrNull()))
        }
    }

    /** `message` do envelope de erro do backend (backlib-errors), se vier legível. */
    private suspend fun HttpResponse.serverMessageOrNull(): String? = runCatching {
        val body = bodyAsText()
        json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

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

    private fun mapStatus(suffix: String, status: Int, serverMessage: String?): OwnAuthException = when (status) {
        // Credencial inválida mantém o texto local DE PROPÓSITO: o servidor responde genérico para não
        // revelar se o e-mail existe, e repassar a mensagem dele não acrescentaria nada.
        401, 403 -> OwnAuthException.InvalidCredentials(texts.invalidCredentials)
        409 -> OwnAuthException.EmailAlreadyInUse(serverMessage ?: texts.emailAlreadyInUse)
        422 -> if (suffix.startsWith("register") || suffix.startsWith("password")) {
            OwnAuthException.WeakPassword(serverMessage ?: texts.weakPassword)
        } else {
            OwnAuthException.Server(serverMessage ?: texts.server(status), status)
        }
        400 -> when {
            suffix.startsWith("password/reset") ->
                OwnAuthException.InvalidResetToken(serverMessage ?: texts.invalidResetToken)
            // Validação do backend (ex.: "A senha deve ter ao menos 6 caracteres") — mostrar o motivo
            // real, que é justamente o que a pessoa precisa saber para corrigir.
            suffix.startsWith("register") || suffix.startsWith("password") ->
                OwnAuthException.WeakPassword(serverMessage ?: texts.weakPassword)
            else -> OwnAuthException.Server(serverMessage ?: texts.server(status), status)
        }
        429 -> OwnAuthException.TooManyRequests(texts.tooManyRequests)
        else -> OwnAuthException.Server(serverMessage ?: texts.server(status), status)
    }

    companion object {
        private const val TAG = "OwnAuthApi"
    }
}
