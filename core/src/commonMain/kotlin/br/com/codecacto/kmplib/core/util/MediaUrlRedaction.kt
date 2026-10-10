package br.com.codecacto.kmplib.core.util

/**
 * A forma de uma URL de mídia que pode ir para log, `toString` e mensagem de erro (2.277.1).
 *
 * URL de vídeo, legenda, foto e download costuma ser **pré-assinada** (S3/CloudFront/GCS): a
 * assinatura, o prazo e às vezes a credencial viajam na **query** (`X-Amz-Signature`, `Expires`,
 * `token`…) ou no fragmento. Quem tem a URL inteira tem o arquivo até ela vencer — e o log do app
 * vai para o logcat, para o console do Xcode e, pelo `CrashReporter`, para o GlitchTip.
 *
 * Fica **esquema · host (com porta) · caminho** — o que basta para dizer "o app pediu o arquivo
 * errado" ou "está batendo no host errado". Sai a query, o fragmento e a parte de usuário/senha da
 * autoridade (`https://user:senha@host`). Texto que não tem a forma de URL (`esquema:` na frente)
 * **não vai para o log de jeito nenhum**: devolve [REDACTED_MEDIA_URL] — sem esquema não há como
 * saber onde começa a parte secreta.
 *
 * ```kotlin
 * redactMediaUrl("https://cdn.x.com/aulas/1.m3u8?X-Amz-Signature=abc") // "https://cdn.x.com/aulas/1.m3u8"
 * redactMediaUrl("lixo sem esquema?token=1")                            // "[url omitida]"
 * ```
 */
@KmpLibCoreInternalApi
fun redactMediaUrl(url: String?): String {
    if (url.isNullOrBlank()) return REDACTED_MEDIA_URL
    val texto = url.trim()
    val match = MEDIA_URL_SHAPE.matchEntire(texto.substringBefore('#').substringBefore('?'))
        ?: return REDACTED_MEDIA_URL
    val esquema = match.groupValues[1]
    val barras = match.groupValues[2]
    val autoridade = match.groupValues[3].substringAfterLast('@')
    val caminho = match.groupValues[4]
    if (barras.isEmpty() && caminho.isEmpty()) return REDACTED_MEDIA_URL
    if (barras.isNotEmpty() && autoridade.isEmpty() && caminho.isEmpty()) return REDACTED_MEDIA_URL
    return "$esquema:$barras$autoridade$caminho"
}

/**
 * Aplica [redactMediaUrl] a **toda URL dentro de um texto livre** — a mensagem de uma exceção, que
 * a pilha de rede (Ktor, Media3, Foundation) costuma escrever com a URL inteira dentro
 * (`"Client request(GET https://…?X-Amz-Signature=…) invalid: 403"`).
 *
 * Só URLs com `esquema://` são reconhecidas no meio do texto; o resto passa igual.
 */
@KmpLibCoreInternalApi
fun redactMediaUrlsIn(text: String?): String {
    if (text.isNullOrEmpty()) return ""
    return URL_IN_TEXT.replace(text) { redactMediaUrl(it.value) }
}

/** O que sai no lugar de uma URL que não dá para reduzir com segurança. */
@KmpLibCoreInternalApi
const val REDACTED_MEDIA_URL: String = "[url omitida]"

// esquema RFC 3986 · "//" opcional · autoridade · caminho (já sem query e fragmento).
private val MEDIA_URL_SHAPE = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):(//)?([^/]*)(/.*)?$""")

// URL no meio de um texto: termina em espaço, aspas, parêntese/colchete de fechamento ou ">".
private val URL_IN_TEXT = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://[^\s"'<>)\]]+""")
