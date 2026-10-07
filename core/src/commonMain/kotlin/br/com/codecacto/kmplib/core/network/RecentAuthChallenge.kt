package br.com.codecacto.kmplib.core.network

/**
 * **Reautenticação exigida (step-up)** — o 401 que NÃO é sessão expirada (2.261.0).
 *
 * A backlib (`backlib-auth-local` ≥ 0.151.0, `requireRecentAuth()`) protege a ação que não se
 * desfaz — excluir a conta, primeiro — exigindo que a pessoa tenha provado a credencial há pouco
 * (claim `auth_time` do access token; o refresh NÃO o renova). Fora da janela, a rota responde:
 *
 * ```
 * HTTP/1.1 401
 * WWW-Authenticate: Bearer error="insufficient_user_authentication", error_description="…", max_age=300
 * {"message": "Confirme sua identidade para continuar.", "code": "REAUTH_REQUIRED",
 *  "details": {"maxAgeSeconds": "300"}}
 * ```
 *
 * É o desafio de step-up da **RFC 9470** (OAuth 2.0 Step Up Authentication Challenge).
 *
 * ## Por que tem de ser reconhecido ANTES do tratamento de 401
 * O tratamento genérico de 401 da lib renova o token e repete (`DomainApiClient`) ou avisa o app
 * para deslogar (`RestConfig.onUnauthorized`). Os dois são errados aqui:
 * - **renovar não resolve** — o refresh preserva o `auth_time` da família, então a repetição volta
 *   o mesmo 401, e ainda gasta uma rotação de refresh à toa;
 * - **deslogar é o oposto do pedido** — o servidor quer que a pessoa confirme quem é *na mesma
 *   tela*, não que perca a sessão no meio da exclusão da conta.
 *
 * Por isso os clientes da lib conferem [matches] primeiro, e devolvem o erro tipado
 * ([ReauthRequiredException], `DomainResult.Error.isReauthRequired`, `ApiResult.Error.isReauthRequired`)
 * sem refresh, sem retry e sem `onUnauthorized`. Quem pede a credencial e repete é o
 * `RecentAuthCoordinator` do `kmplib-auth`.
 */
object RecentAuthChallenge {

    /** `code` do envelope de erro da backlib (`RecentAuth.CODE`). */
    const val CODE: String = "REAUTH_REQUIRED"

    /** `error` do desafio `WWW-Authenticate` (RFC 9470 §3). */
    const val CHALLENGE_ERROR: String = "insufficient_user_authentication"

    /** Chave de `details` que leva a janela, em segundos (`RecentAuth.reauthRequired`). */
    const val DETAIL_MAX_AGE: String = "maxAgeSeconds"

    /**
     * Um parâmetro `nome=valor` do desafio (RFC 9110 §11.2: `token` ou `quoted-string`). Ler
     * parâmetro a parâmetro — e não "a primeira ocorrência de `error=`" — impede que um `error=`
     * DENTRO do texto entre aspas de outro parâmetro (o `error_description`) seja tomado pelo `error`.
     */
    private val AUTH_PARAM = Regex("""([A-Za-z0-9_\-]+)\s*=\s*("(?:[^"\\]|\\.)*"|[^\s,]+)""")

    /**
     * `true` quando a resposta é o desafio de reautenticação: status **401** e (o `code` do corpo é
     * [CODE] **ou** o `WWW-Authenticate` traz `error="insufficient_user_authentication"`).
     * Qualquer outro 401 é sessão inválida e segue o caminho de sempre.
     */
    fun matches(status: Int, serverCode: String?, wwwAuthenticate: String?): Boolean {
        if (status != 401) return false
        if (serverCode == CODE) return true
        return challengeError(wwwAuthenticate) == CHALLENGE_ERROR
    }

    /**
     * A janela exigida pelo servidor, em segundos: `details.maxAgeSeconds` ou, na falta dele, o
     * `max_age` do `WWW-Authenticate`. `null` quando nenhum dos dois veio ou é ilegível.
     */
    fun maxAgeSeconds(details: Map<String, String>, wwwAuthenticate: String?): Long? =
        details[DETAIL_MAX_AGE]?.trim()?.toLongOrNull()?.takeIf { it > 0 }
            ?: bearerParams(wwwAuthenticate)["max_age"]?.toLongOrNull()?.takeIf { it > 0 }

    private fun challengeError(header: String?): String? = bearerParams(header)["error"]

    /** Os parâmetros do desafio `Bearer` (nome em minúsculas → valor sem aspas); vazio se não for Bearer. */
    private fun bearerParams(header: String?): Map<String, String> {
        if (header.isNullOrBlank()) return emptyMap()
        val trimmed = header.trimStart()
        // Só o esquema Bearer carrega o desafio da RFC 6750/9470.
        if (!trimmed.startsWith("Bearer", ignoreCase = true)) return emptyMap()
        val params = LinkedHashMap<String, String>()
        AUTH_PARAM.findAll(trimmed.substring("Bearer".length)).forEach { m ->
            val name = m.groupValues[1].lowercase()
            val raw = m.groupValues[2]
            val value = if (raw.startsWith('"')) raw.substring(1, raw.length - 1).replace("\\\"", "\"") else raw
            if (name !in params) params[name] = value
        }
        return params
    }
}

/**
 * O servidor pediu que a pessoa **prove de novo a credencial** antes desta ação (401
 * `REAUTH_REQUIRED`, ver [RecentAuthChallenge]). **Não é logout**: a sessão continua válida.
 *
 * Quem recebe isto não desloga nem tenta de novo sozinho — passa a ação pelo
 * `RecentAuthCoordinator.withRecentAuth { … }` (ou usa o `RecentAuthHost` na tela), que pede a
 * senha / refaz o login social, confere que é a MESMA conta, troca os tokens e repete UMA vez.
 *
 * @property maxAgeSeconds a janela exigida pelo servidor (5 min por padrão na backlib), ou `null`
 *   se ele não informou.
 */
class ReauthRequiredException(
    val maxAgeSeconds: Long? = null,
    message: String = DEFAULT_MESSAGE,
) : Exception(message) {
    companion object {
        /** Frase pt-BR de fallback; a da tela vem dos textos traduzidos do `kmplib-auth`. */
        const val DEFAULT_MESSAGE: String = "Confirme sua identidade para continuar."
    }
}

/** `true` se esta falha é o pedido de reautenticação — atravessa `Result`, `DomainResult` e `ApiResult`. */
fun Throwable.isReauthRequired(): Boolean = this is ReauthRequiredException
