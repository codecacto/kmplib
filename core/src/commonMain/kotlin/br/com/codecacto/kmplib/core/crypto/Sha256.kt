package br.com.codecacto.kmplib.core.crypto

/**
 * **SHA-256 incremental** (2.312.0) — o resumo calculado aos pedaços, para conteúdo que não cabe (ou
 * não deve caber) na memória de uma vez: o arquivo de vídeo de 100 MB que o servidor confere byte a
 * byte depois do upload direto ao storage (RondAki §11.4), um corpo que chega em blocos.
 *
 * Implementação **da plataforma**, nunca SHA escrito à mão:
 * - **Android:** `java.security.MessageDigest.getInstance("SHA-256")`.
 * - **iOS:** CommonCrypto `CC_SHA256_Init` / `CC_SHA256_Update` / `CC_SHA256_Final`, com o contexto em
 *   memória nativa — por isso o [close].
 *
 * ```kotlin
 * Sha256Hasher().use { h ->
 *     h.update(cabecalho)
 *     h.update(corpo, offset = 0, length = lidos)
 *     val resumo = h.digest()          // 32 bytes; o hasher volta ao estado inicial
 * }
 * ```
 * Para um arquivo inteiro, prefira [sha256OfFile] (lê em blocos fora da thread principal).
 *
 * Não é thread-safe: um hasher por cálculo. Depois de [close], qualquer chamada lança
 * [IllegalStateException].
 */
class Sha256Hasher : AutoCloseable {
    private var engine: Sha256Engine? = newSha256Engine()

    private fun engine(): Sha256Engine = checkNotNull(engine) { "Sha256Hasher já fechado" }

    /** Acrescenta [length] bytes de [bytes] a partir de [offset]. `length = 0` não faz nada. */
    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        val e = engine()
        checkSha256Range(bytes.size, offset, length)
        if (length == 0) return
        e.update(bytes, offset, length)
    }

    /**
     * Fecha o cálculo e devolve os 32 bytes do resumo. O hasher **volta ao estado inicial** (como o
     * `MessageDigest.digest()`), pronto para outro cálculo.
     */
    fun digest(): ByteArray = engine().digest()

    /** Libera o contexto nativo (iOS). Idempotente. */
    override fun close() {
        val e = engine ?: return
        engine = null
        e.close()
    }
}

/** O motor da plataforma (`MessageDigest` / CommonCrypto). Faixa já validada por [Sha256Hasher]. */
internal interface Sha256Engine {
    fun update(bytes: ByteArray, offset: Int, length: Int)
    fun digest(): ByteArray
    fun close()
}

internal expect fun newSha256Engine(): Sha256Engine

/** Tamanho do resumo SHA-256, em bytes. */
const val SHA256_DIGEST_BYTES: Int = 32

/** Bloco default de leitura de [sha256OfFile]: 256 KiB (poucas chamadas de sistema, memória fixa). */
const val SHA256_FILE_BUFFER_BYTES: Int = 256 * 1024

/** SHA-256 de [bytes] inteiro (32 bytes). Para conteúdo grande, use [Sha256Hasher] ou [sha256OfFile]. */
fun sha256(bytes: ByteArray): ByteArray = Sha256Hasher().use { h ->
    h.update(bytes)
    h.digest()
}

/** SHA-256 de [bytes] em hexadecimal **minúsculo** (64 caracteres) — a forma que os contratos da fábrica pedem. */
fun sha256Hex(bytes: ByteArray): String = sha256(bytes).toLowerHex()

/** SHA-256 do texto em UTF-8, em hexadecimal minúsculo. */
fun sha256Hex(text: String): String = sha256Hex(text.encodeToByteArray())

/**
 * SHA-256 do **arquivo** em [path], lido em blocos de [bufferSize] bytes — a memória usada é a do
 * bloco, não a do arquivo. Roda fora da thread principal (`Dispatchers.IO` no Android,
 * `Dispatchers.Default` no iOS) e respeita cancelamento entre blocos.
 *
 * @return os 32 bytes, ou `null` se o arquivo não existe, não é arquivo regular ou não pôde ser lido
 *   até o fim. Nunca lança (exceto [kotlinx.coroutines.CancellationException]).
 */
suspend fun sha256OfFile(path: String, bufferSize: Int = SHA256_FILE_BUFFER_BYTES): ByteArray? {
    require(bufferSize > 0) { "bufferSize precisa ser positivo" }
    if (path.isBlank()) return null
    return platformSha256OfFile(path, bufferSize)
}

/** [sha256OfFile] em hexadecimal minúsculo (64 caracteres); `null` se o arquivo não pôde ser lido. */
suspend fun sha256HexOfFile(path: String, bufferSize: Int = SHA256_FILE_BUFFER_BYTES): String? =
    sha256OfFile(path, bufferSize)?.toLowerHex()

/** `true` se [value] é um SHA-256 em hexadecimal minúsculo (exatamente 64 de `0-9a-f`). */
fun isSha256Hex(value: String): Boolean =
    value.length == SHA256_DIGEST_BYTES * 2 && value.all { it in '0'..'9' || it in 'a'..'f' }

/** Bytes → hexadecimal minúsculo, dois dígitos por byte. */
fun ByteArray.toLowerHex(): String {
    val out = CharArray(size * 2)
    forEachIndexed { i, b ->
        val v = b.toInt() and 0xFF
        out[i * 2] = HEX[v ushr 4]
        out[i * 2 + 1] = HEX[v and 0x0F]
    }
    return out.concatToString()
}

private val HEX = "0123456789abcdef".toCharArray()

internal fun checkSha256Range(size: Int, offset: Int, length: Int) {
    require(offset >= 0 && length >= 0 && offset <= size && length <= size - offset) {
        "faixa inválida: offset=$offset length=$length size=$size"
    }
}

/** Leitura em blocos + hash, da plataforma. `null` = não leu o arquivo inteiro. */
internal expect suspend fun platformSha256OfFile(path: String, bufferSize: Int): ByteArray?
