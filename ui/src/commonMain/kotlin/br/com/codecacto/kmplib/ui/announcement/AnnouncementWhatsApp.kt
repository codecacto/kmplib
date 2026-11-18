package br.com.codecacto.kmplib.ui.announcement

/*
 * Botão de WhatsApp do comunicado (2.371.0) — par de `isWhatsAppActionUrl`/`whatsAppNumberFromUrl` da
 * weblib (≥ 0.328.0, `src/announcements/logic.ts`). Puro: sem Compose, testado em `commonTest`.
 *
 * Quando a ação do comunicado abre o WhatsApp, o modal mostra o NÚMERO em destaque (inteiro, numa linha,
 * com "Copiar") e desenha o botão no verde-petróleo da marca com o ícone oficial. É o caso das
 * boas-vindas do 1º acesso, que trazem o WhatsApp do suporte.
 */

private val WHATSAPP_HOSTS = setOf("wa.me", "api.whatsapp.com", "web.whatsapp.com")
private val BR_PHONE = Regex("""^55(\d{2})(\d{4,5})(\d{4})$""")

/**
 * O número de um link de WhatsApp.
 *
 * @property digits só algarismos, com o DDI (`5565998030240`).
 * @property display como se mostra: Brasil (55 + DDD + 8/9 dígitos) `(65) 99803-0240`; outro país
 *   `+<dígitos>`.
 */
data class AnnouncementWhatsAppNumber(val digits: String, val display: String)

/**
 * O link abre o WhatsApp? `https://wa.me/…`, `https://api.whatsapp.com/…` ou `https://web.whatsapp.com/…`
 * (host sem diferença de caixa). Outro esquema (`http`, `whatsapp://`) não conta.
 */
fun isWhatsAppActionUrl(url: String?): Boolean = parseHttpsUrl(url)?.host in WHATSAPP_HOSTS

/**
 * O número de um link de WhatsApp: `wa.me/<dígitos>` ou `api.whatsapp.com/send?phone=<dígitos>`.
 * `null` quando o link não é de WhatsApp ou não traz número de 8 a 15 algarismos.
 */
fun whatsAppNumberFromUrl(url: String?): AnnouncementWhatsAppNumber? {
    val parsed = parseHttpsUrl(url) ?: return null
    if (parsed.host !in WHATSAPP_HOSTS) return null
    val raw = if (parsed.host == "wa.me") parsed.path.removePrefix("/") else queryParameter(parsed.query, "phone").orEmpty()
    val digits = raw.filter { it in '0'..'9' }
    if (digits.length !in 8..15) return null
    val br = BR_PHONE.matchEntire(digits)
    val display = if (br != null) {
        val (ddd, first, last) = br.destructured
        "($ddd) $first-$last"
    } else {
        "+$digits"
    }
    return AnnouncementWhatsAppNumber(digits = digits, display = display)
}

/** A ação do comunicado, se ela abre o WhatsApp (rótulo e URL preenchidos). */
val Announcement.whatsAppAction: AnnouncementAction?
    get() = action?.takeIf { it.label.isNotBlank() && isWhatsAppActionUrl(it.url) }

/** O número do WhatsApp da ação do comunicado, para o bloco "Copiar" do modal; `null` sem número. */
val Announcement.whatsAppNumber: AnnouncementWhatsAppNumber?
    get() = whatsAppAction?.let { whatsAppNumberFromUrl(it.url) }

private class HttpsUrlParts(val host: String, val path: String, val query: String)

/** `https://host[:porta]/caminho?consulta#fragmento` — só o que interessa aqui; `null` se não for https. */
private fun parseHttpsUrl(url: String?): HttpsUrlParts? {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    if (!trimmed.startsWith("https://", ignoreCase = true)) return null
    val rest = trimmed.substring("https://".length).substringBefore('#')
    val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
    val authority = rest.substring(0, authorityEnd).substringAfterLast('@')
    val host = authority.substringBefore(':').lowercase()
    if (host.isBlank()) return null
    val afterAuthority = rest.substring(authorityEnd)
    val path = afterAuthority.substringBefore('?')
    val query = if ('?' in afterAuthority) afterAuthority.substringAfter('?') else ""
    return HttpsUrlParts(host = host, path = path, query = query)
}

/** Valor do parâmetro [name] na consulta, decodificado como no `URLSearchParams` (`+` = espaço, `%XX`). */
private fun queryParameter(query: String, name: String): String? =
    query.split('&').firstNotNullOfOrNull { pair ->
        val key = decodeQueryComponent(pair.substringBefore('='))
        if (key == name) decodeQueryComponent(pair.substringAfter('=', "")) else null
    }

private fun decodeQueryComponent(value: String): String {
    val out = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        val hex = if (c == '%' && i + 2 < value.length + 1 && i + 3 <= value.length) value.substring(i + 1, i + 3) else null
        when {
            c == '+' -> {
                out += ' '.code.toByte()
                i++
            }
            hex != null && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> {
                out += hex.toInt(16).toByte()
                i += 3
            }
            else -> {
                c.toString().encodeToByteArray().forEach { out += it }
                i++
            }
        }
    }
    return out.toByteArray().decodeToString()
}
