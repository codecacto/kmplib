package br.com.codecacto.kmplib.sync.direct

/**
 * O pouco de sistema de arquivos que a fila de envio direto precisa — e que o `BlobStore` (chave →
 * bytes, sem caminho) não dá: **mover** o arquivo preparado para dentro da fila, ler um **trecho**
 * dele (a parte) e gravar o trecho num arquivo próprio (o iOS só envia em segundo plano a partir de
 * arquivo).
 *
 * Nenhuma operação lança: falha vira `false`/`null`/`-1`. Interna: o app fala com a fila, não com isto.
 */
internal interface DirectUploadFiles {
    /** Tamanho em bytes, ou `-1` se não existe. */
    fun size(path: String): Long

    fun exists(path: String): Boolean = size(path) >= 0L

    /** Cria o diretório (e os pais). `true` se ele existe ao fim. */
    fun mkdirs(path: String): Boolean

    /** Nomes das entradas de [dir] (vazio se não existe). */
    fun list(dir: String): List<String>

    fun readText(path: String): String?

    /** Grava [text] por inteiro ou não grava nada (arquivo temporário + renomear). */
    fun writeTextAtomic(path: String, text: String): Boolean

    /** Move [from] para [to] (renomeia; entre volumes, copia e apaga). `true` se [to] ficou completo. */
    fun move(from: String, to: String): Boolean

    /** Copia [from] para [to]. */
    fun copy(from: String, to: String): Boolean

    /** Apaga arquivo ou diretório inteiro. `true` se não existe ao fim. */
    fun deleteRecursively(path: String): Boolean

    /** [length] bytes a partir de [offset] (posicionamento direto, sem ler o começo). */
    fun readRange(path: String, offset: Long, length: Long): ByteArray?

    /** Copia o trecho [offset]..[offset]+[length] de [source] para o arquivo [dest]. */
    fun writeRange(source: String, offset: Long, length: Long, dest: String): Boolean
}

/** A implementação da plataforma (`java.io` no Android, `NSFileManager`/`NSFileHandle` no iOS). */
internal expect fun platformDirectUploadFiles(): DirectUploadFiles

/**
 * Diretório privado da fila, **fora do backup**: `noBackupFilesDir/<nome>` no Android,
 * `Application Support/<nome>` com `NSURLIsExcludedFromBackupKey` no iOS. Vídeo do corpo de alguém
 * esperando para subir não vai para a nuvem pessoal de quem usa o aparelho. `null` sem contexto.
 */
internal expect fun platformDirectUploadRoot(directoryName: String): String?

internal fun joinPath(dir: String, name: String): String = dir.trimEnd('/') + "/" + name
