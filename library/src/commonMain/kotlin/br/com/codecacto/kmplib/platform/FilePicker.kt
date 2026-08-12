package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable

/**
 * Dados de um arquivo selecionado
 */
data class FileData(
    val name: String,
    val mimeType: String,
    val data: ByteArray,
    val size: Long,
    val extension: String = name.substringAfterLast('.', "")
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isPdf: Boolean
        get() = mimeType == "application/pdf"

    val isDocument: Boolean
        get() = mimeType.startsWith("application/") &&
                (mimeType.contains("document") ||
                 mimeType.contains("pdf") ||
                 mimeType.contains("msword") ||
                 mimeType.contains("officedocument"))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as FileData
        if (name != other.name) return false
        if (mimeType != other.mimeType) return false
        if (!data.contentEquals(other.data)) return false
        if (size != other.size) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + size.hashCode()
        return result
    }
}

/**
 * Desfecho **tipado** da seleção de arquivo (kmplib 2.105.0).
 *
 * Existe porque o contrato anterior (`FileData?`) só sabia dizer "veio" ou "não veio", e o "não veio"
 * juntava três coisas que pedem UI diferente: **desistência** do usuário (silêncio), **arquivo grande
 * demais** (mensagem com o limite) e **arquivo ilegível** (mensagem de erro). Pior: sem teto, a lib
 * lia o arquivo inteiro para a memória — escolher um vídeo de centenas de MB **derrubava o app** antes
 * de o consumidor ver um byte, e o `catch (Exception)` não pega `OutOfMemoryError`.
 */
sealed interface FilePickResult {
    /** O usuário escolheu um arquivo dentro do limite e a lib conseguiu lê-lo. */
    data class Picked(val file: FileData) : FilePickResult

    /** O usuário fechou o seletor. **Não é erro** — não mostre mensagem. */
    data object Cancelled : FilePickResult

    /**
     * O arquivo passa do teto e **não foi materializado**.
     *
     * @param sizeBytes tamanho informado pelo provedor; `-1` quando ele não informa (nesse caso a lib
     *   descobriu o excesso lendo com teto, e abortou a leitura — nunca carregou o arquivo inteiro).
     */
    data class TooLarge(val name: String, val sizeBytes: Long, val maxBytes: Long) : FilePickResult

    /** O arquivo existe mas não pôde ser lido. */
    data class Failed(val name: String?, val reason: FilePickFailure) : FilePickResult
}

/** Motivo de [FilePickResult.Failed]. */
enum class FilePickFailure {
    /** Provedor devolveu erro, permissão expirou, arquivo removido no meio, etc. */
    Unreadable,

    /**
     * Memória não bastou mesmo dentro do teto (aparelho fraco, arquivo perto do limite).
     * A lib captura o `OutOfMemoryError` como última linha — o teto é a primeira.
     */
    OutOfMemory,
}

/**
 * Teto default de leitura (25 MiB).
 *
 * Generoso para o que os apps do ecossistema realmente selecionam (PDF de nota, JSON de backup, foto)
 * e pequeno o suficiente para não derrubar aparelho de entrada. Quem precisa de mais (vídeo, por
 * exemplo) passa `maxBytes` explícito e assume o custo de memória.
 */
const val DEFAULT_MAX_FILE_BYTES: Long = 25L * 1024L * 1024L

/**
 * Seletor de arquivos multiplataforma (contrato antigo).
 *
 * Mantido para compatibilidade: entrega `null` para **qualquer** desfecho que não seja sucesso. Um
 * arquivo acima de [DEFAULT_MAX_FILE_BYTES] é **recusado** (chega como `null`, não mais como crash) —
 * se a sua tela precisa dizer *por que* não deu, use a sobrecarga com [FilePickResult].
 */
@Composable
fun rememberFilePicker(
    onFilePicked: (FileData?) -> Unit
): () -> Unit = rememberFilePicker(
    mimeTypes = listOf("*/*"),
    onFilePicked = onFilePicked
)

/**
 * Seletor de arquivos multiplataforma (contrato antigo, com filtro de MIME).
 *
 * Ver [rememberFilePicker] com [FilePickResult] para o desfecho tipado.
 */
@Composable
fun rememberFilePicker(
    mimeTypes: List<String>,
    onFilePicked: (FileData?) -> Unit
): () -> Unit = rememberFilePicker(
    mimeTypes = mimeTypes,
    maxBytes = DEFAULT_MAX_FILE_BYTES,
) { resultado ->
    onFilePicked((resultado as? FilePickResult.Picked)?.file)
}

/**
 * Seletor de arquivos multiplataforma com **teto de tamanho** e desfecho tipado.
 *
 * O teto é conferido **antes** de materializar o arquivo: com o tamanho informado pelo provedor,
 * nem se abre o arquivo; sem essa informação (provedor que não a expõe), a leitura acontece **com
 * teto** e é abortada ao passar do limite. Em nenhum caminho a lib carrega o arquivo inteiro para
 * descobrir depois que ele não cabia.
 *
 * ```kotlin
 * val escolher = rememberFilePicker(
 *     mimeTypes = listOf("application/json"),
 *     maxBytes = 2L * 1024 * 1024,
 * ) { resultado ->
 *     when (resultado) {
 *         is FilePickResult.Picked -> vm.importar(resultado.file)
 *         is FilePickResult.TooLarge -> toast("Arquivo maior que o limite.")
 *         is FilePickResult.Failed -> toast("Não foi possível ler o arquivo.")
 *         FilePickResult.Cancelled -> Unit // desistiu: silêncio
 *     }
 * }
 * ```
 *
 * @param maxBytes teto de leitura. Valor `<= 0` cai em [DEFAULT_MAX_FILE_BYTES] — "sem limite" não é
 *   opção oferecida de propósito: era exatamente o comportamento que derrubava o app.
 */
@Composable
expect fun rememberFilePicker(
    mimeTypes: List<String>,
    maxBytes: Long,
    onResult: (FilePickResult) -> Unit
): () -> Unit

/** Normaliza o teto: valor inútil (`<= 0`) cai no default em vez de virar "sem limite". */
internal fun resolveMaxFileBytes(maxBytes: Long): Long =
    if (maxBytes <= 0L) DEFAULT_MAX_FILE_BYTES else maxBytes

/**
 * `true` quando o tamanho **conhecido** passa do teto. Tamanho desconhecido (`< 0`) devolve `false`:
 * quem decide nesse caso é a leitura com teto ([BoundedByteAccumulator]).
 */
internal fun exceedsFilePickLimit(sizeBytes: Long, maxBytes: Long): Boolean =
    sizeBytes >= 0L && sizeBytes > maxBytes

/**
 * Acumulador de bytes **com teto**, usado quando o provedor não informa o tamanho.
 *
 * Para de copiar assim que o total passaria de [limit] e marca [exceeded]; o buffer nunca cresce além
 * do teto. É o que transforma "escolhi um arquivo de 800 MB" de crash em [FilePickResult.TooLarge].
 */
internal class BoundedByteAccumulator(private val limit: Long) {

    private var buffer = ByteArray(minOf(limit, INITIAL_CAPACITY).toInt().coerceAtLeast(1))
    private var size = 0

    /** `true` quando o conteúdo passou do teto — o resultado parcial deve ser descartado. */
    var exceeded: Boolean = false
        private set

    val bytesRead: Int get() = size

    /** @return `false` quando não vale a pena continuar lendo (teto estourado). */
    fun append(chunk: ByteArray, count: Int): Boolean {
        if (exceeded) return false
        if (count <= 0) return true
        if (size.toLong() + count > limit) {
            exceeded = true
            return false
        }
        ensureCapacity(size + count)
        chunk.copyInto(buffer, destinationOffset = size, startIndex = 0, endIndex = count)
        size += count
        return true
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var nova = buffer.size.toLong().coerceAtLeast(INITIAL_CAPACITY)
        while (nova < required) nova *= 2
        // O teto pode ser maior que Int.MAX_VALUE (o chamador escolheu); o array, não.
        val capacidade = minOf(nova, limit)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
            .coerceAtLeast(required)
        buffer = buffer.copyOf(capacidade)
    }

    private companion object {
        const val INITIAL_CAPACITY = 64L * 1024L
    }
}
