package br.com.codecacto.kmplib.monetization.access

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request

/**
 * Configuração do [SubscriptionBlockDetector].
 */
class SubscriptionBlockDetectorConfig {
    /** Onde registrar o bloqueio. Default: [SubscriptionBlockState.Shared] (o mesmo do gate). */
    var state: SubscriptionBlockState = SubscriptionBlockState.Shared

    /**
     * Só observa estes hosts (ex.: `setOf("api-locasys.codecacto.com.br")`). `null` (default) = todo
     * host deste cliente. Restrinja quando o mesmo cliente fala com terceiros.
     */
    var onlyHosts: Set<String>? = null

    /**
     * Um **2xx** do MESMO host que bloqueou tira o bloqueio — o acesso voltou (pagou na web, virou
     * conta por contrato) e a próxima recarga da tela (`ON_RESUME`) já o prova. Default `true`.
     */
    var clearOnSuccess: Boolean = true

    /**
     * Segmentos de caminho cujas rotas o portão do servidor NÃO protege — um 2xx nelas não prova que
     * o acesso voltou e, portanto, **não** tira o bloqueio (o login e o refresh de token respondem
     * 200 para conta bloqueada). Comparação por segmento inteiro, sem caixa.
     */
    var neutralPathSegments: Set<String> = DEFAULT_NEUTRAL_PATH_SEGMENTS

    companion object {
        /** Rotas de sessão, pública, saúde e cobrança — as que o `SubscriptionGuard` deixa passar. */
        val DEFAULT_NEUTRAL_PATH_SEGMENTS: Set<String> = setOf(
            "auth", "public", "health", "billing", "subscription", "subscriptions",
            "entitlement", "plans", "checkout", "me",
        )
    }
}

/**
 * **Detector do bloqueio por assinatura no `HttpClient`** (2.300.0): todo **402** com `code`
 * `TRIAL_EXPIRED`/`SUBSCRIPTION_REQUIRED` vira [SubscriptionBlockState.report] — sem cada
 * ViewModel/repositório precisar saber disso. A tela é o `SubscriptionBlockGate` (`kmplib-monetization`).
 *
 * - Só lê o corpo no **402** (o Ktor 3 guarda a resposta em memória: quem chamou continua lendo o
 *   corpo normalmente). Os demais status passam intactos.
 * - O 402 de **cota** (`QUOTA_EXCEEDED`) é ignorado — esse abre o paywall da ação, não bloqueia a conta.
 * - Com [SubscriptionBlockDetectorConfig.clearOnSuccess], um 2xx de rota protegida do mesmo host
 *   tira o bloqueio (ver [SubscriptionBlockDetectorConfig.neutralPathSegments]).
 *
 * Opt-in no factory — só app de SaaS com portão pós-teste liga:
 * ```kotlin
 * createHttpClient(HttpClientOptions(subscriptionBlock = SubscriptionBlockState.Shared))
 * // ou, configurando:
 * createHttpClient { install(SubscriptionBlockDetector) { onlyHosts = setOf("api-locasys.codecacto.com.br") } }
 * ```
 */
val SubscriptionBlockDetector = createClientPlugin(
    "KmplibSubscriptionBlockDetector",
    ::SubscriptionBlockDetectorConfig,
) {
    val state = pluginConfig.state
    val onlyHosts = pluginConfig.onlyHosts?.map { it.lowercase() }?.toSet()
    val clearOnSuccess = pluginConfig.clearOnSuccess
    val neutral = pluginConfig.neutralPathSegments.map { it.lowercase() }.toSet()
    // O host que bloqueou: só um 2xx DELE prova que o acesso voltou (o admin-api central, no mesmo
    // cliente, responde 200 para conta bloqueada).
    var blockedHost: String? = null

    onResponse { response ->
        val url = response.request.url
        val host = url.host.lowercase()
        if (onlyHosts != null && host !in onlyHosts) return@onResponse
        val status = response.status.value
        when {
            status == SUBSCRIPTION_BLOCK_STATUS -> {
                val body = runCatching { response.bodyAsText() }.getOrNull()
                if (state.reportIfBlocked(status, body)) blockedHost = host
            }
            clearOnSuccess && status in 200..299 && state.isBlocked && host == blockedHost &&
                url.segments.none { it.lowercase() in neutral } -> {
                state.clear()
                blockedHost = null
            }
        }
    }
}
