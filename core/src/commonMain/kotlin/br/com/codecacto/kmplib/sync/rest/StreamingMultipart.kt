package br.com.codecacto.kmplib.sync.rest

import kotlinx.io.RawSource

/**
 * Uma parte binária de um corpo `multipart/form-data` (2.356.0): ou os bytes já em memória
 * ([MultipartPart]) ou um arquivo lido em pedaços durante o envio ([StreamingMultipartPart]).
 *
 * É o tipo que [DomainApiClient.postMultipartStreaming]/[DomainApiClient.putMultipartStreaming]
 * aceitam, e permite misturar as duas formas no mesmo request (ex.: o vídeo em fluxo + a capa JPEG
 * em bytes).
 */
sealed interface MultipartBodyPart {
    /** Nome do campo do formulário (`arquivo`, `file`, `full`…). */
    val fieldName: String

    /** Nome do arquivo no `Content-Disposition` (escapado como o navegador escapa). */
    val fileName: String

    /** `Content-Type` da parte. */
    val mimeType: String
}

/**
 * Parte multipart **em fluxo** (2.356.0): o conteúdo é lido em pedaços, enquanto sobe — nunca vai
 * inteiro para a memória. É o caminho para vídeo, PDF grande e todo arquivo que o app não precisa ter
 * em `ByteArray` (um vídeo de 40 MB em `ByteArray` é 40 MB de heap a mais, e `OutOfMemoryError` não é
 * `Exception`: o `try/catch` da tela não pega).
 *
 * Por baixo é o `InputProvider` do Ktor 3 com um `kotlinx.io.Source` — a forma oficial de corpo
 * multipart em fluxo. [open] é chamado **a cada envio**: a repetição depois de um 401 (refresh do
 * token) abre o arquivo de novo e manda tudo desde o começo, como tem de ser. Cada fonte aberta é
 * fechada pelo Ktor ao terminar a parte.
 *
 * Para arquivo do aparelho, use [fromLocalFile] — ou as extensões prontas sobre o que os seletores da
 * kmplib devolvem: `PickedVideo.toMultipartPart(...)` (`kmplib-ui`) e `PickedFile.toMultipartPart(...)`
 * (`kmplib-platform`).
 *
 * @property sizeBytes tamanho exato do conteúdo, quando conhecido: vira o `Content-Length` do request.
 *   `null` = desconhecido, e o corpo sobe em `chunked`. **Nunca passe um tamanho aproximado** — se o
 *   arquivo tiver um byte a mais ou a menos, o servidor recebe um corpo inválido.
 * @param open abre uma fonte NOVA, posicionada no início, a cada chamada.
 */
class StreamingMultipartPart(
    override val fieldName: String,
    override val fileName: String,
    override val mimeType: String,
    val sizeBytes: Long?,
    private val open: () -> RawSource,
) : MultipartBodyPart {

    init {
        require(sizeBytes == null || sizeBytes >= 0L) { "sizeBytes não pode ser negativo" }
    }

    /** Abre uma fonte nova, no início do conteúdo. Quem chama fecha. */
    fun openSource(): RawSource = open()

    /** Sem o nome do arquivo — "cnh-joao.pdf" é dado pessoal e não vai para log/GlitchTip. */
    override fun toString(): String =
        "StreamingMultipartPart(fieldName=$fieldName, mimeType=$mimeType, sizeBytes=$sizeBytes)"

    companion object {
        /**
         * Parte em fluxo a partir de um arquivo do aparelho.
         *
         * @param reference o que os seletores e gravadores da kmplib devolvem:
         *   - **Android:** URI `content://` (galeria, seletor de documentos, `FileProvider` da câmera),
         *     `file://` ou caminho absoluto. `content://` é lido pelo `ContentResolver` (exige
         *     `initKmpLibCore`/`KmpLib.init` antes).
         *   - **iOS:** caminho absoluto ou URL `file://`.
         *   - **Desktop:** caminho absoluto ou URL `file:`.
         * @param sizeBytes tamanho exato se o app já sabe; `null` = a lib pergunta ao sistema (se o
         *   sistema não souber, sobe em `chunked`).
         *
         * A existência do arquivo **não** é conferida aqui: se ele sumir antes do envio, o envio
         * falha com erro de transporte ([DomainResult.Error] com `code = -1`), nunca com exceção.
         */
        fun fromLocalFile(
            fieldName: String,
            reference: String,
            fileName: String,
            mimeType: String,
            sizeBytes: Long? = null,
        ): StreamingMultipartPart = StreamingMultipartPart(
            fieldName = fieldName,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes?.takeIf { it >= 0L } ?: localFileSizeOrNull(reference),
        ) { openLocalFileSource(reference) }
    }
}

/**
 * Abre um arquivo local para leitura em fluxo: URI `content://`/`file://` ou caminho absoluto
 * (ver [StreamingMultipartPart.fromLocalFile]). Lança `IOException` se não abrir.
 */
internal expect fun openLocalFileSource(reference: String): RawSource

/** Tamanho exato do arquivo local; `null` quando o sistema não informa ou não abre. */
internal expect fun localFileSizeOrNull(reference: String): Long?
