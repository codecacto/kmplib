package br.com.codecacto.kmplib.pdf.viewer

/** De onde o PDF vem. */
sealed interface PdfSource {

    /** Bytes que o app já tem em mãos — por exemplo, o resultado de um dos geradores da lib. */
    data class Bytes(val bytes: ByteArray, val id: String) : PdfSource {
        // `ByteArray` compara por identidade; sem isto, um `remember(source)` recarregaria o
        // documento a cada recomposição que recriasse a data class.
        override fun equals(other: Any?): Boolean = other is Bytes && other.id == id
        override fun hashCode(): Int = id.hashCode()
    }

    /** Arquivo já no disco do aparelho. Caminho absoluto. */
    data class LocalFile(val path: String) : PdfSource

    /**
     * Arquivo remoto. É baixado uma vez e guardado; nas próximas aberturas sai do disco.
     *
     * @param cacheId a identidade no cache. `null` = derivada da URL por [pdfCacheIdFor] — que
     *   **ignora a query de propósito**, para URL assinada não virar um item novo a cada abertura.
     *   Preencha à mão quando o app já tem um id estável (o id da aula, do material).
     */
    data class Url(val url: String, val cacheId: String? = null) : PdfSource
}

/**
 * A identidade de um PDF remoto no cache.
 *
 * **A query fica de fora, e é o ponto todo desta função.** Material de curso vem por URL assinada
 * (`…/apostila.pdf?token=…&expires=…`), e o token muda a cada abertura: com a URL inteira na chave,
 * o cache erraria **sempre** e o aluno rebaixaria a apostila toda vez — inclusive sem sinal, onde
 * simplesmente não abriria.
 *
 * O resultado é um id válido para o `BlobStore` (só `A-Za-z0-9._-`): um hash FNV-1a do caminho, que
 * é o que distingue dois arquivos, mais um trecho do nome, que é o que torna o diretório legível
 * quando alguém precisa olhar.
 */
fun pdfCacheIdFor(url: String): String {
    val semQuery = url.substringBefore('?').substringBefore('#')
    val nome = semQuery.substringAfterLast('/')
        .take(NOME_NO_ID)
        .map { if (it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '_' || it == '-') it else '_' }
        .joinToString("")
    return "pdf-${fnv1a(semQuery)}${if (nome.isEmpty()) "" else "-$nome"}"
}

/**
 * FNV-1a de 32 bits em hexadecimal.
 *
 * Não é criptográfico e não precisa ser: aqui ele só separa dois caminhos diferentes dentro do
 * cache de um aparelho. É implementado à mão porque `commonMain` não tem `hashCode` estável entre
 * plataformas — e uma chave de cache que muda de valor entre Android e iOS é uma chave que não
 * serve para nada.
 */
private fun fnv1a(texto: String): String {
    var hash = 0x811C9DC5u
    for (byte in texto.encodeToByteArray()) {
        hash = hash xor (byte.toUInt() and 0xFFu)
        hash *= 0x01000193u
    }
    return hash.toString(16).padStart(8, '0')
}

private const val NOME_NO_ID = 40
