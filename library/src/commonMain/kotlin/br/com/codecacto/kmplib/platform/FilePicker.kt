package br.com.codecacto.kmplib.platform

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
 * Seletor de arquivos multiplataforma
 *
 * Nota: FilePicker ainda esta em desenvolvimento.
 * As implementacoes Android e iOS serao adicionadas em breve.
 */
