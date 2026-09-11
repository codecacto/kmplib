package br.com.codecacto.kmplib.platform

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol

/**
 * Link **rastreável** para compartilhar o app (ou um conteúdo dele) — o `ShareHandler` leva o texto,
 * este objeto garante que o link que vai junto **chega com origem**.
 *
 * Existe por uma regra do ecossistema: todo canal tem UTM, e todo lead/visita chega ao
 * `central.contact` dizendo de onde veio (`docs/30`, invariante 6). "Compartilhar" é o único canal
 * que cresce com o uso — e sem UTM ele cai no balde "direto" do analytics, indistinguível de quem
 * digitou o endereço. Cada app montando a query string à mão é como o `utm_campaign` nasce em
 * cinco grafias (`MeuApp`, `meu_app`, `meu-app `…) e o relatório vira cinco linhas.
 *
 * O link aponta para o **site do produto** (todo projeto tem um) — é ele que decide a loja pela
 * plataforma de quem abre, e que continua funcionando no desktop, onde link direto de loja não
 * serve. Parâmetros gravados:
 *
 * | parâmetro | valor |
 * |---|---|
 * | `utm_source` | [SOURCE] = `app` |
 * | `utm_medium` | [MEDIUM] = `share` |
 * | `utm_campaign` | o [campaign] — **o slug do projeto** (`AppConfig.projectSlug` na casca) |
 * | `utm_content` | opcional, por tela/conteúdo (`menu`, `resultado`, `piada`…) |
 *
 * ```kotlin
 * val link = AppShareLink(landingUrl = AppConfig.siteBaseUrl, campaign = AppConfig.projectSlug)
 * link.url(content = "menu")
 * // https://meu-app.codecacto.com.br?utm_source=app&utm_medium=share&utm_campaign=meu-app&utm_content=menu
 * link.urlFor("https://meu-app.codecacto.com.br/receita/42", content = "receita")
 * ```
 *
 * **Normalização:** `utm_campaign` e `utm_content` saem em **minúsculas**, sem espaço nas pontas e
 * com espaço interno virando `-`. O analytics diferencia caixa ("Menu" e "menu" viram duas linhas),
 * então quem normaliza é a lib, uma vez, e não cada tela. Parâmetros que o link já tinha são
 * preservados; `utm_*` que já estavam nele são **substituídos** (reenviar um link recebido não pode
 * carregar a origem de quem o mandou antes).
 *
 * @property landingUrl URL absoluta `http(s)` do site do produto (ou da página que decide a loja).
 * @property campaign slug do projeto. Não pode ser vazio.
 * @throws IllegalArgumentException se [landingUrl] não for `http(s)` absoluta ou [campaign] for vazio.
 */
data class AppShareLink(
    val landingUrl: String,
    val campaign: String,
) {
    init {
        requireShareableUrl(landingUrl)
        require(normalizeUtmValue(campaign).isNotEmpty()) { "campaign (slug do projeto) não pode ser vazio" }
    }

    /** O [landingUrl] com a UTM de compartilhamento; [content] opcional identifica a tela. */
    fun url(content: String? = null): String = urlFor(landingUrl, content)

    /**
     * Uma página **específica** do site (o conteúdo que a pessoa está compartilhando) com a mesma
     * UTM. Use quando o site tem a página daquele conteúdo; senão, [url].
     *
     * @throws IllegalArgumentException se [pageUrl] não for `http(s)` absoluta.
     */
    fun urlFor(pageUrl: String, content: String? = null): String =
        appendShareUtm(pageUrl, campaign, content)

    companion object {
        /** `utm_source` de todo compartilhamento feito de dentro de um app. */
        const val SOURCE: String = "app"

        /** `utm_medium` de todo compartilhamento feito de dentro de um app. */
        const val MEDIUM: String = "share"
    }
}

/**
 * Acrescenta a UTM de compartilhamento a [url] — a função pura por trás de [AppShareLink].
 *
 * Preserva caminho, query existente e fragmento; **substitui** `utm_*` que já estivessem no link;
 * omite `utm_content` quando [content] é nulo ou em branco (e remove um `utm_content` antigo, pelo
 * mesmo motivo da substituição). Codificação da query é do `URLBuilder` do Ktor.
 *
 * @throws IllegalArgumentException se [url] não for `http(s)` absoluta ou [campaign] for vazio.
 */
fun appendShareUtm(
    url: String,
    campaign: String,
    content: String? = null,
    source: String = AppShareLink.SOURCE,
    medium: String = AppShareLink.MEDIUM,
): String {
    requireShareableUrl(url)
    val normalizedCampaign = normalizeUtmValue(campaign)
    require(normalizedCampaign.isNotEmpty()) { "campaign (slug do projeto) não pode ser vazio" }
    val normalizedContent = content?.let(::normalizeUtmValue).orEmpty()

    val builder = URLBuilder(url.trim())
    builder.parameters.apply {
        set("utm_source", normalizeUtmValue(source))
        set("utm_medium", normalizeUtmValue(medium))
        set("utm_campaign", normalizedCampaign)
        if (normalizedContent.isEmpty()) remove("utm_content") else set("utm_content", normalizedContent)
    }
    return builder.buildString()
}

/**
 * Texto único que vai ao share sheet: a mensagem e, na linha seguinte, o link.
 *
 * **Um texto só, e não "texto + URL" em itens separados**, de propósito: com itens separados cada
 * app de destino escolhe o que aproveita, e há os que ficam só com um dos dois — perder o link é
 * perder a atribuição, perder a mensagem é mandar uma URL crua. Numa string única todo app recebe
 * os dois e detecta o link sozinho para montar a prévia (WhatsApp, Mensagens, Telegram). Mensagem em
 * branco = só o link.
 */
fun composeShareText(message: String, url: String): String {
    val msg = message.trim()
    val link = url.trim()
    return if (msg.isEmpty()) link else "$msg\n$link"
}

/** Minúsculas, sem espaço nas pontas, espaço interno → `-` (ver [AppShareLink]). */
internal fun normalizeUtmValue(value: String): String =
    value.trim().lowercase().replace(WHITESPACE, "-")

private val WHITESPACE = Regex("\\s+")

private fun requireShareableUrl(url: String) {
    val trimmed = url.trim()
    val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
    require(scheme == "https" || scheme == "http") { "link de compartilhamento precisa ser http(s) absoluto: '$url'" }
    val host = runCatching { URLBuilder(trimmed) }.getOrNull()
        ?.takeIf { it.protocol == URLProtocol.HTTPS || it.protocol == URLProtocol.HTTP }
        ?.host
        .orEmpty()
    require(host.isNotBlank()) { "link de compartilhamento sem host: '$url'" }
}
