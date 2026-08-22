package br.com.codecacto.kmplib.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Sessão own-auth persistida no cofre seguro: os dois tokens + o instante de expiração do access +
 * a identidade capturada no login/registro.
 *
 * **Por que guardar email/nome aqui:** o JWT próprio carrega só `sub` (id da conta) e o backend não
 * expõe um `GET /me` de perfil. Então o `currentUser` é remontado a partir do `sub` (decodificado do
 * access token) + o `email`/`name` capturados quando o usuário logou/registrou. Não inventamos endpoint.
 *
 * @property accessExpiresAtEpochSeconds epoch (segundos) em que o access token expira — base do
 *   refresh proativo (renovar um pouco ANTES de expirar).
 * @property providerId como o usuário entrou: `"password"`, `"google.com"` ou `"apple.com"` (mesmo
 *   vocabulário do `AuthRepository` Firebase, para `user.isGoogleProvider` valer nos dois mundos).
 *   Sessão gravada antes da 2.98.0 não tem o campo e cai no default `"password"` — que é a verdade,
 *   já que social não existia. **É o único campo que a lib sabe sobre a origem**: o JWT próprio
 *   carrega só `sub`, e o backend não expõe `GET /me`.
 */
@Serializable
data class OwnAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochSeconds: Long,
    val accountId: String,
    val email: String = "",
    val name: String = "",
    val providerId: String = DEFAULT_PROVIDER_ID,
    /**
     * `true` enquanto a conta estiver com a **senha temporária** posta pelo administrador — o
     * titular precisa definir a dele antes de usar o app (`backlib-auth-local` ≥ 0.80.0).
     *
     * O app observa isto para abrir o `ForcePasswordChangeDialog`. **Não é a trava**: quem obriga é
     * o servidor, que recusa toda rota do produto com `403 PASSWORD_CHANGE_REQUIRED`.
     *
     * Default `false` e não-nulável: sessão gravada por uma versão anterior desserializa como
     * `false`, que é o correto — quem já usava o app tem senha própria.
     */
    val passwordChangeRequired: Boolean = false,
) {
    companion object {
        /** Login por e-mail e senha — mesmo valor que o `User.providerId` default. */
        const val DEFAULT_PROVIDER_ID: String = "password"
    }
}

/**
 * Decodifica claims do payload de um JWT **sem verificar a assinatura** (a verificação é do servidor;
 * o cliente só precisa ler o `sub`). Tolerante: qualquer formato inesperado devolve `null`.
 */
internal object JwtDecoder {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Extrai o `sub` (id da conta) do access token, ou `null` se ilegível. */
    fun subject(jwt: String): String? = claim(jwt, "sub")

    fun claim(jwt: String, name: String): String? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        val payload = decodeBase64Url(parts[1]) ?: return null
        val obj = runCatching { lenientJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        return (obj[name] as? JsonPrimitive)?.contentOrNull
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /**
     * Decodifica base64url (RFC 4648, sem padding, alfabeto `-_`) para texto UTF-8. Puro/portável —
     * evita depender do `kotlin.io.encoding.Base64` (opt-in) e não lança (retorna `null` em entrada
     * inválida).
     */
    private fun decodeBase64Url(input: String): String? {
        if (input.isEmpty()) return ""
        val lookup = IntArray(128) { -1 }
        for (i in ALPHABET.indices) lookup[ALPHABET[i].code] = i
        val out = ArrayList<Byte>(input.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in input) {
            if (c == '=') break
            val code = c.code
            val v = if (code in 0..127) lookup[code] else -1
            if (v < 0) return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return runCatching { out.toByteArray().decodeToString() }.getOrNull()
    }
}
