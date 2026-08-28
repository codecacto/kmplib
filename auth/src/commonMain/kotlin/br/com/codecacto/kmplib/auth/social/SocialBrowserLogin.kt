package br.com.codecacto.kmplib.auth.social

/**
 * Login social **pelo navegador do sistema**, contra o nosso próprio backend.
 *
 * ## Por que não é o SDK nativo
 * No fluxo nativo (Credential Manager no Android, `GIDSignIn` no iOS), o Google identifica o
 * aplicativo pelo par *package + SHA-1* e exige **um cliente OAuth para cada par** — e o projeto do
 * Google Cloud tem teto. Uma família de aplicativos sobre a mesma base de código bate nesse teto, e o
 * sintoma é mudo: o console mostra a impressão digital cadastrada, mas o cliente não é criado e o
 * botão do Google devolve `DEVELOPER_ERROR`.
 *
 * Aqui quem negocia com o provedor é o **backend**, com um cliente web só. O aplicativo abre uma aba
 * do navegador no NOSSO domínio, o provedor nunca o vê, e no fim o backend devolve um código por
 * *deep link* que o app troca pela sessão. É o desenho da RFC 8252 (*OAuth 2.0 for Native Apps*) com
 * o padrão *Backend for Frontend* — o mesmo que se observa nos aplicativos grandes, em que o login
 * com Google abre uma aba no domínio da empresa e não uma tela do Google dentro do app.
 *
 * ## Navegador do sistema, nunca WebView
 * A RFC 8252 é explícita: WebView embutida quebra a proteção do provedor (o app hospedeiro enxerga o
 * que o usuário digita) e perde a sessão do navegador, obrigando a digitar a senha do Google toda
 * vez. No Android isso é **Custom Tabs**; no iOS, `ASWebAuthenticationSession`.
 */
expect class SocialBrowserLogin() {

    /**
     * Abre [startUrl] no navegador do sistema e devolve o **código de troca** que o backend anexa ao
     * *deep link* de volta.
     *
     * @param redirectScheme esquema do *deep link* registrado pelo aplicativo (ex.:
     *   `brcodecacto.inssnegou`). É por ele que o iOS sabe quando encerrar a sessão do navegador, e
     *   é o que o Android casa no `intent-filter`.
     * @return o código, ou falha com [SocialBrowserException] se o usuário cancelar ou o backend
     *   devolver `?erro=`.
     */
    suspend fun authenticate(startUrl: String, redirectScheme: String): String
}

/** Cancelamento do usuário ou erro devolvido pelo backend no *deep link* de volta. */
class SocialBrowserException(
    message: String,
    /** Valor de `?erro=` quando veio do backend: `cancelado`, `falha`, `sessao_expirada`. */
    val reason: String? = null,
) : Exception(message)

/**
 * Par PKCE do aplicativo **com o nosso backend** — RFC 7636.
 *
 * Não se confunde com o PKCE que o backend usa com o provedor: são dois pares distintos, em dois
 * trechos distintos do caminho.
 *
 * O que ele protege: em Android e iOS um esquema de URL pode ser reivindicado por **mais de um
 * aplicativo instalado**. Sem o `verifier`, quem interceptasse o *deep link* de volta trocaria o
 * código pela sessão. Com ele, o código só vale para quem começou o login.
 */
class PkcePair private constructor(val verifier: String, val challenge: String) {
    companion object {
        /**
         * Gera um par novo. O `verifier` tem 43 caracteres do alfabeto base64url — o piso da RFC
         * 7636 §4.1, e o mesmo que o backend cobra.
         */
        fun generate(): PkcePair {
            val verifier = base64UrlNoPadding(PkceCrypto.randomBytes(32))
            val challenge = base64UrlNoPadding(PkceCrypto.sha256(verifier.encodeToByteArray()))
            return PkcePair(verifier, challenge)
        }
    }
}

/** Primitivas de plataforma do PKCE — aleatoriedade forte e SHA-256. */
expect object PkceCrypto {
    /** Bytes de um gerador criptográfico do sistema. `Random` comum não serve: é previsível. */
    fun randomBytes(size: Int): ByteArray

    fun sha256(input: ByteArray): ByteArray
}

/** base64url sem preenchimento, como a RFC 7636 exige. */
internal fun base64UrlNoPadding(bytes: ByteArray): String {
    val alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val sb = StringBuilder()
    var i = 0
    while (i + 2 < bytes.size) {
        val n = ((bytes[i].toInt() and 0xFF) shl 16) or
            ((bytes[i + 1].toInt() and 0xFF) shl 8) or
            (bytes[i + 2].toInt() and 0xFF)
        sb.append(alfabeto[(n shr 18) and 0x3F])
        sb.append(alfabeto[(n shr 12) and 0x3F])
        sb.append(alfabeto[(n shr 6) and 0x3F])
        sb.append(alfabeto[n and 0x3F])
        i += 3
    }
    when (bytes.size - i) {
        1 -> {
            val n = (bytes[i].toInt() and 0xFF) shl 16
            sb.append(alfabeto[(n shr 18) and 0x3F])
            sb.append(alfabeto[(n shr 12) and 0x3F])
        }
        2 -> {
            val n = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            sb.append(alfabeto[(n shr 18) and 0x3F])
            sb.append(alfabeto[(n shr 12) and 0x3F])
            sb.append(alfabeto[(n shr 6) and 0x3F])
        }
    }
    return sb.toString()
}
