package br.com.codecacto.kmplib.auth

import kotlinx.serialization.Serializable

/**
 * Par de tokens devolvido pelo backend own-auth em `register`/`login`/`refresh`.
 *
 * Espelha o contrato `AuthTokens` do `backlib-auth-local` (issuer próprio do projeto): o `accessToken`
 * é um JWT curto (Bearer) e o `refreshToken` é opaco e **rotativo** (cada `refresh` devolve um novo,
 * o antigo é revogado). `expiresInSeconds` é o tempo de vida do access token, usado para o refresh
 * proativo.
 */
@Serializable
data class OwnAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val tokenType: String = "Bearer",
    /**
     * `true` quando a conta ainda está com a **senha temporária** que o administrador não digitou —
     * o titular precisa escolher a dele antes de usar o app (`backlib-auth-local` ≥ 0.80.0).
     *
     * **Booleano com default `false`, nunca nulável:** campo ausente na resposta de um backend
     * anterior desserializa como `false`, que é o comportamento correto. Nulável convidaria ao
     * `!= null` no ViewModel, que devolve `true` para "não veio" — a armadilha que a fábrica já
     * pagou uma vez.
     *
     * ⚠️ Isto é **conveniência de UI**. Quem obriga a troca é o servidor, que recusa toda rota do
     * produto com `403 PASSWORD_CHANGE_REQUIRED` enquanto a senha for a temporária.
     */
    val passwordChangeRequired: Boolean = false,
)

/**
 * Nonce de uso único emitido pelo **servidor** (`GET {authBasePath}/social/nonce`), a ser embutido
 * no `idToken` do provedor social.
 *
 * **Por que vem do servidor:** o nonce existe para amarrar *aquele* `idToken` a *aquela* requisição
 * de login. Um nonce escolhido pelo próprio aparelho não amarra nada — quem obtiver um `idToken`
 * válido (log, proxy, app malicioso no mesmo dispositivo) o reapresenta escolhendo o mesmo valor. Só
 * o emissor que também verifica pode declarar "este token foi feito para esta sessão".
 *
 * @property expiresInSeconds validade informada pelo servidor (o backend do ecossistema usa 5 min).
 *   É informativo: quem invalida é o servidor, o cliente não decide expiração.
 */
@Serializable
data class SocialNonce(
    val nonce: String,
    val expiresInSeconds: Long = 0,
)

/**
 * Corpo de `POST {authBasePath}/social`.
 *
 * **`accessToken` do Google NÃO existe aqui, de propósito** — ele não é prova de identidade
 * (ver `GoogleSignInResult`). Só o `idToken` viaja.
 *
 * `name`/`email` são aceitos pelo servidor **apenas na criação** da identidade (a Apple só entrega
 * nome/e-mail na primeira autorização); em login subsequente o servidor os ignora.
 */
@Serializable
internal data class SocialBody(
    val provider: String,
    val idToken: String,
    val nonce: String,
    val name: String? = null,
    val email: String? = null,
)

@Serializable
internal data class RegisterBody(
    val name: String,
    val email: String,
    val password: String,
    val acceptedTerms: Boolean,
    /**
     * Telefone/WhatsApp — omitido do JSON quando nulo, para o corpo não dizer "informei nada".
     * O backend (backlib ≥ 0.68.0) trata ausente e em branco igual.
     */
    val phone: String? = null,
)

/**
 * Corpo do login. Manda **`identifier`** (campo novo, que serve para e-mail ou nome de usuário) e
 * **`email`** (campo histórico) — os dois, de propósito.
 *
 * Um app atualizado conversando com um backend ainda não bumpado desserializaria `identifier` como
 * vazio e receberia "usuário ou senha inválidos" para credencial correta. Mandar os dois custa uma
 * chave a mais no JSON e evita o pior sintoma que existe: credencial certa recusada.
 */
@Serializable
internal data class LoginBody(
    val identifier: String,
    val password: String,
    val email: String? = null,
)

/** Corpo do `POST {authBasePath}/password/first-access`. Sem senha atual — ver [OwnAuthApi]. */
@Serializable
internal data class FirstAccessBody(val newPassword: String)

@Serializable
internal data class RefreshBody(val refreshToken: String)

@Serializable
internal data class LogoutBody(val refreshToken: String)

@Serializable
internal data class PasswordForgotBody(val email: String)

@Serializable
internal data class PasswordResetBody(val token: String, val newPassword: String)
