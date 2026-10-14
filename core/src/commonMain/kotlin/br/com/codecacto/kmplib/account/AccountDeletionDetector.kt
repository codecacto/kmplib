package br.com.codecacto.kmplib.account

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/** Configuração do [AccountDeletionDetector]. */
class AccountDeletionDetectorConfig {
    /** Onde registrar. Default: [AccountDeletionState.Shared] (o mesmo do `AccountDeletionGate`). */
    var state: AccountDeletionState = AccountDeletionState.Shared

    /**
     * Só observa estes hosts. `null` (default) = todo host deste cliente.
     *
     * **Recomendado: o host da API do projeto** (`HttpClientOptions.accountDeletionHosts`). O estado é
     * de processo ([AccountDeletionState.Shared]); restringir ao host da API impede que um 403 de outro
     * servidor que o app chame (CDN, serviço de terceiro) com o mesmo `code` cubra a tela.
     */
    var onlyHosts: Set<String>? = null

    /**
     * Um **2xx** de rota protegida do MESMO serviço que recusou (host + primeiro segmento do caminho),
     * **numa requisição que levou `Authorization`**, tira o bloqueio — a conta foi restaurada por outro
     * caminho (web, admin). Requisição sem credencial não prova nada sobre a conta (rota pública que
     * responde 200 a qualquer um). Default `true`.
     */
    var clearOnSuccess: Boolean = true

    /**
     * Segmentos de caminho cujas rotas respondem 2xx mesmo com a conta pendente — um 2xx nelas **não**
     * prova que a conta voltou: o login e o refresh (`auth`), a própria consulta da exclusão
     * (`me/deletion`), saúde e rotas públicas. Comparação por segmento inteiro, sem caixa.
     */
    var neutralPathSegments: Set<String> = DEFAULT_NEUTRAL_PATH_SEGMENTS

    companion object {
        val DEFAULT_NEUTRAL_PATH_SEGMENTS: Set<String> = setOf(
            "auth", "public", "health", "me", "deletion", "restore",
        )
    }
}

/**
 * **Detector da conta com exclusão programada no `HttpClient`** (2.301.0): todo **403** com
 * `code: "ACCOUNT_PENDING_DELETION"` vira [AccountDeletionState.report] — e o `AccountDeletionGate`
 * (kmplib-auth) cobre a área logada com "Sua conta está programada para exclusão". Nenhum
 * ViewModel/repositório precisa saber disso.
 *
 * - Só lê o corpo no **403 com corpo JSON** (ou sem `Content-Type`) (o Ktor 3 guarda a resposta em memória: quem chamou
 *   continua lendo normalmente). Um 403 de outro tipo (imagem de URL assinada vencida, HTML de proxy)
 *   passa intacto, sem leitura.
 * - Com [AccountDeletionDetectorConfig.clearOnSuccess], um 2xx de rota protegida do mesmo serviço
 *   (host + primeiro segmento do caminho), **com `Authorization` na requisição**, tira o bloqueio.
 *   A restauração e a consulta (`AccountDeletionRepository`) atualizam o estado pela própria resposta.
 * - Instale com [AccountDeletionDetectorConfig.onlyHosts] = o host da API do projeto
 *   (`HttpClientOptions.accountDeletionHosts`).
 *
 * Ligado por default no `createHttpClient` (`HttpClientOptions.accountDeletion`): sem o gate na tela,
 * o estado só é escrito e ninguém o lê.
 */
val AccountDeletionDetector = createClientPlugin(
    "KmplibAccountDeletionDetector",
    ::AccountDeletionDetectorConfig,
) {
    val state = pluginConfig.state
    val onlyHosts = pluginConfig.onlyHosts?.map { it.lowercase() }?.toSet()
    val clearOnSuccess = pluginConfig.clearOnSuccess
    val neutral = pluginConfig.neutralPathSegments.map { it.lowercase() }.toSet()
    // Onde a recusa aconteceu: host + PRIMEIRO segmento do caminho. Num servidor que hospeda vários
    // serviços (o apps-api: `/<slug>/v1/…` ao lado de `/feedback/v1`, `/ads/…`), só um 2xx do MESMO
    // serviço prova que a conta voltou — o feedback ou o anúncio respondendo 200 não provam nada.
    var blockedHost: String? = null
    var blockedRoot: String? = null

    onResponse { response ->
        val url = response.request.url
        val host = url.host.lowercase()
        if (onlyHosts != null && host !in onlyHosts) return@onResponse
        val status = response.status.value
        when {
            status == ACCOUNT_PENDING_DELETION_STATUS -> {
                // Só JSON (`application/json`, `…+json`) ou sem cabeçalho: um 403 de mídia/HTML
                // não é lido — o corpo de um download recusado continua de quem chamou.
                val tipo = response.contentType()
                if (tipo != null && !tipo.contentSubtype.endsWith("json", ignoreCase = true)) return@onResponse
                val body = runCatching { response.bodyAsText() }.getOrNull()
                if (state.reportIfPending(status, body)) {
                    blockedHost = host
                    blockedRoot = url.segments.firstOrNull()?.lowercase()
                }
            }
            clearOnSuccess && status in 200..299 && state.isPending && host == blockedHost &&
                response.request.headers.contains(HttpHeaders.Authorization) &&
                url.segments.firstOrNull()?.lowercase() == blockedRoot &&
                url.segments.none { it.lowercase() in neutral } -> {
                state.clear()
                blockedHost = null
                blockedRoot = null
            }
        }
    }
}
