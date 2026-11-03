package br.com.codecacto.kmplib.platform

/**
 * A forma de uma URL que pode ir para o LOG (2.340.0): só **esquema + host** (`https://pagamento.exemplo.com`).
 * Caminho, query e fragmento ficam de fora — link de onboarding de gateway, redefinição de senha e
 * convite levam o token ali, e o log do aparelho (e o que vai ao GlitchTip) não é lugar de segredo.
 * Esquema sem autoridade (`mailto:`, `tel:`, `sms:`, `whatsapp:`) vira só o esquema (`tel:`): o resto é
 * dado pessoal. Texto que não parece URL vira `"(url inválida)"`.
 */
internal fun urlForLog(url: String): String {
    val texto = url.trim()
    val esquema = SCHEME.find(texto)?.groupValues?.get(1)?.lowercase() ?: return INVALID_URL_FOR_LOG
    val resto = texto.substring(esquema.length + 1)
    if (!resto.startsWith("//")) return "$esquema:"
    val autoridade = resto.removePrefix("//").takeWhile { it != '/' && it != '?' && it != '#' }
    // Credencial embutida (`usuario:senha@host`) também não sai.
    val host = autoridade.substringAfterLast('@').lowercase()
    return if (host.isEmpty()) "$esquema:" else "$esquema://$host"
}

internal const val INVALID_URL_FOR_LOG: String = "(url inválida)"

// RFC 3986 §3.1: ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) seguido de ':'.
private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
