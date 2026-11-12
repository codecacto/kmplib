package br.com.codecacto.kmplib.analytics

/**
 * O que o Umami precisa saber do aparelho — e nada além: sistema, versão do sistema, se é telefone ou
 * tablet e a área de tela em dp/pontos (a mesma unidade que o script do site manda em CSS px). Sem
 * modelo, sem fabricante, sem identificador.
 *
 * @property os nome do sistema no vocabulário do Umami (`"Android OS"`, `"iOS"`) — é o mesmo texto que
 *   ele grava quando lê o User-Agent de um navegador, então app e site somam no mesmo filtro.
 * @property osVersion versão do sistema (`"14"`, `"18.1"`), só para o User-Agent.
 * @property tablet `true` em tablet (largura mínima ≥ 600 dp no Android; iPad no iOS).
 * @property screen `"<largura>x<altura>"` em dp/pontos, retrato ou paisagem como estiver; `null` se a
 *   plataforma não disse.
 */
data class AnalyticsDevice(
    val os: String,
    val osVersion: String?,
    val tablet: Boolean,
    val screen: String?,
) {
    /** O `device` do Umami: `"tablet"` ou `"mobile"`. */
    val deviceType: String get() = if (tablet) "tablet" else "mobile"

    companion object {
        const val OS_ANDROID: String = "Android OS"
        const val OS_IOS: String = "iOS"
    }
}

/** O aparelho atual. Nunca lança: o que não der para ler sai `null`. */
internal expect fun currentAnalyticsDevice(): AnalyticsDevice

/**
 * Registra os dois momentos em que a fila deve andar: o app VEIO ao primeiro plano (reenviar o que
 * ficou sem rede) e FOI para o segundo plano (esvaziar antes que o sistema congele o processo).
 * Devolve `false` quando não foi possível registrar (a fila continua andando a cada evento).
 */
internal expect fun registerAnalyticsLifecycle(onForeground: () -> Unit, onBackground: () -> Unit): Boolean

/** Marca do app no User-Agent. Junto do `Mozilla/5.0 (…)` é o que faz o Umami aceitar o envio. */
const val ANALYTICS_UA_PRODUCT: String = "CodeCactoApp"

/**
 * O User-Agent do envio ao Umami.
 *
 * **Por que existe:** o Umami descarta (com 200 e `{"beep":"boop"}`, sem gravar nada) toda requisição
 * cujo User-Agent a biblioteca `isbot` reconhece como robô — e o UA padrão dos clientes HTTP cai nela:
 * `okhttp/4.12.0` e `Ktor client` são descartados, testado contra o `metricas.codecacto.com.br` em
 * 12/nov/2026. Um UA no formato de navegador móvel (`Mozilla/5.0 (Linux; Android 14; Mobile) …`)
 * passa, e a marca [ANALYTICS_UA_PRODUCT] + versão do app no fim diz de onde veio.
 *
 * Sem modelo de aparelho de propósito: o UA entra no hash da sessão do Umami e no log do servidor;
 * versão do sistema e do app bastam.
 *
 * ```
 * Android telefone  Mozilla/5.0 (Linux; Android 14; Mobile) CodeCactoApp/1.4.2
 * Android tablet    Mozilla/5.0 (Linux; Android 14; Tablet) CodeCactoApp/1.4.2
 * iPhone            Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) CodeCactoApp/1.4.2
 * iPad              Mozilla/5.0 (iPad; CPU OS 18_1 like Mac OS X) CodeCactoApp/1.4.2
 * ```
 */
fun analyticsUserAgent(device: AnalyticsDevice, appVersion: String?): String {
    val versao = cleanToken(appVersion).ifEmpty { "0" }
    val so = cleanToken(device.osVersion)
    val plataforma = if (device.os == AnalyticsDevice.OS_IOS) {
        val v = so.replace('.', '_').ifEmpty { "0" }
        if (device.tablet) "iPad; CPU OS $v like Mac OS X" else "iPhone; CPU iPhone OS $v like Mac OS X"
    } else {
        val v = so.ifEmpty { "0" }
        "Linux; Android $v; ${if (device.tablet) "Tablet" else "Mobile"}"
    }
    return "Mozilla/5.0 ($plataforma) $ANALYTICS_UA_PRODUCT/$versao"
}

private fun cleanToken(value: String?): String =
    value.orEmpty().trim().filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }.take(32)
