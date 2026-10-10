package br.com.codecacto.kmplib.ui.components

/**
 * O núcleo comum da leitura por faixa de [PickedVideo] — puro, sem API de plataforma, e por isso
 * coberto em `commonTest`. As duas plataformas só abrem o arquivo, posicionam e entregam uma
 * [RangeSource]; o limite de bytes, o último pedaço truncado e a contagem moram aqui, uma vez.
 */

/**
 * De onde os bytes saem, já posicionado no deslocamento pedido.
 *
 * `read` preenche `buffer[0 until maxBytes]` e devolve quantos bytes escreveu; **`<= 0` é fim do
 * arquivo** (o `InputStream` devolve `-1`, o `NSFileHandle` devolve `NSData` vazio).
 */
internal fun interface RangeSource {
    fun read(buffer: ByteArray, maxBytes: Int): Int
}

/** As mesmas recusas nas duas plataformas, antes de abrir qualquer arquivo. */
internal fun requireValidVideoRange(offset: Long, length: Long?, chunkSize: Int) {
    require(offset >= 0) { "offset não pode ser negativo: $offset" }
    require(length == null || length >= 0) { "length não pode ser negativo: $length" }
    require(chunkSize > 0) { "chunkSize precisa ser positivo: $chunkSize" }
}

/**
 * Lê de [source] até [length] bytes (ou até o fim, com `null`), em pedaços de no máximo
 * [chunkSize], reaproveitando **um** buffer. Devolve o total entregue a [onChunk].
 *
 * O último pedaço sai com `count` menor que o buffer quando a faixa termina no meio dele — é a
 * razão de existir do `count`.
 */
internal suspend fun streamVideoRange(
    source: RangeSource,
    length: Long?,
    chunkSize: Int,
    onChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
): Long {
    if (length == 0L) return 0L
    val buffer = ByteArray(chunkSize)
    var entregue = 0L
    while (length == null || entregue < length) {
        val pedir = if (length == null) chunkSize else minOf(chunkSize.toLong(), length - entregue).toInt()
        val lidos = source.read(buffer, pedir)
        if (lidos <= 0) break
        onChunk(buffer, lidos)
        entregue += lidos
    }
    return entregue
}

/**
 * Pula exatamente [count] bytes de um fluxo que **não** se posiciona, e devolve quantos pulou.
 *
 * `InputStream.skip` pode devolver `0` sem estar no fim (o contrato permite, e provedor de conteúdo
 * por pipe faz isso) — confiar nele num laço trava ou, pior, sai antes e entrega bytes do lugar
 * errado. Quando o pulo não anda, um byte é lido para distinguir "não andou" de "acabou".
 *
 * @param skip o `InputStream.skip(n)`.
 * @param readByte o `InputStream.read()` — `-1` no fim.
 * @return [count] quando deu; menos que isso só se o arquivo acabou antes.
 */
internal fun skipVideoBytesExactly(count: Long, skip: (Long) -> Long, readByte: () -> Int): Long {
    var pulados = 0L
    while (pulados < count) {
        val andou = skip(count - pulados)
        if (andou > 0) {
            pulados += andou
            continue
        }
        if (readByte() < 0) break
        pulados += 1
    }
    return pulados
}
