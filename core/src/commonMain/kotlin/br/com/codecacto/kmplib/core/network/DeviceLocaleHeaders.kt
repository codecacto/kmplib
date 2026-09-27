package br.com.codecacto.kmplib.core.network

import br.com.codecacto.kmplib.core.locale.appLanguageTag
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.datetime.TimeZone

/**
 * Nome do cabeçalho com o fuso IANA do aparelho (`America/Cuiaba`, `Europe/Lisbon`). É o que a
 * backlib lê para gerar hora de relatório/e-mail no relógio de quem está usando o app.
 */
const val HEADER_TIME_ZONE: String = "X-Time-Zone"

/**
 * Configuração do [DeviceLocaleHeaders].
 *
 * Os dois provedores são chamados **a cada requisição**, nunca guardados: o `HttpClient` vive o
 * processo inteiro, e a pessoa pode trocar o idioma (ou o idioma só do app, no Android 13+/iOS) e
 * viajar de fuso com o processo vivo.
 */
class DeviceLocaleHeadersConfig {
    /**
     * O idioma a declarar no `Accept-Language`. Default: [appLanguageTag] — o idioma **em que a tela
     * está**, com a mesma escolha de pasta do `compose-resources` (um aparelho em francês vê pt-BR,
     * e é pt-BR que o servidor deve usar). `null` = não manda o cabeçalho.
     *
     * Quem tem Compose e quer a resposta exata da pasta escolhida passa `{ uiLanguageTag() }`
     * (`kmplib-ui`); app que traduz outros idiomas passa `{ appLanguageTag(listOf(...)) }`.
     */
    var languageTag: suspend () -> String? = { appLanguageTag() }

    /** O fuso IANA para o [HEADER_TIME_ZONE]. Default: o do aparelho. `null` = não manda. */
    var timeZoneId: () -> String? = { TimeZone.currentSystemDefault().id }

    /**
     * Restringe os cabeçalhos a estes hosts (ex.: `setOf("api-queimap.codecacto.com.br")`). `null`
     * (default) = toda requisição deste cliente. Restrinja quando o mesmo cliente fala com terceiros
     * e não há motivo para contar a eles o fuso da pessoa.
     */
    var onlyHosts: Set<String>? = null
}

/**
 * **`Accept-Language` + `X-Time-Zone` em toda requisição** — o que faz o servidor responder no idioma
 * da tela e no relógio do aparelho.
 *
 * Sem ele, o backend de um app global não tem como saber em que língua escrever a mensagem de erro,
 * o e-mail de boas-vindas ou o PDF, nem em que fuso formatar "avaliado às 14:05". É o par da
 * backlib (≥ 0.134.0), que lê os dois.
 *
 * - Cabeçalho que a chamada **já definiu** não é tocado (a rota que precisa de outro idioma manda o
 *   dela).
 * - É **opt-in** no [createHttpClient] (`HttpClientOptions(sendLocaleHeaders = true)`), para não
 *   mudar a resposta de servidores que já existem; a `casca-mobile` já nasce com ele ligado.
 *
 * ```kotlin
 * createHttpClient(HttpClientOptions(sendLocaleHeaders = true))
 * // ou, configurando:
 * createHttpClient { install(DeviceLocaleHeaders) { onlyHosts = setOf("api-meuapp.codecacto.com.br") } }
 * ```
 */
val DeviceLocaleHeaders = createClientPlugin("KmplibDeviceLocaleHeaders", ::DeviceLocaleHeadersConfig) {
    val languageTag = pluginConfig.languageTag
    val timeZoneId = pluginConfig.timeZoneId
    val onlyHosts = pluginConfig.onlyHosts?.map { it.lowercase() }?.toSet()

    onRequest { request, _ ->
        if (onlyHosts != null && request.url.host.lowercase() !in onlyHosts) return@onRequest
        if (!request.headers.contains(HttpHeaders.AcceptLanguage)) {
            runCatching { languageTag() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                request.headers.append(HttpHeaders.AcceptLanguage, it)
            }
        }
        if (!request.headers.contains(HEADER_TIME_ZONE)) {
            runCatching { timeZoneId() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                request.headers.append(HEADER_TIME_ZONE, it)
            }
        }
    }
}
