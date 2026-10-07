package br.com.codecacto.kmplib.firebase.storage

// Modelos NEUTROS da fila de upload (2.260.0). Moravam no `kmplib-firebase`, e por causa deles o
// `kmplib-sync` (fila REST, `UploadProgressItem`) dependia do módulo Firebase inteiro — o que levava
// `firebase-analytics` a TODO app com banco local, inclusive app infantil sem Firebase. Vivem agora
// no `kmplib-core`; o PACOTE ficou o mesmo para nenhum import quebrar (`kmplib-firebase` continua
// expondo-os via `api(kmplib-core)`).

/**
 * Estado de um item individual da fila de upload.
 *
 * Modelo de UI puro (commonMain, serializável de fato apenas pelos campos primitivos) que o
 * `UploadProgressItem` (ui/components) renderiza. Reflete o ciclo de vida de um upload sobre
 * `StorageService.uploadBytesWithProgress` (kmplib-firebase).
 *
 * @property id identificador estável do item (ex.: UUID local). Use para `key` em listas.
 * @property fileName nome amigável exibido na UI.
 * @property fraction progresso 0.0..1.0 (clamped).
 * @property status estado atual.
 * @property downloadUrl preenchido quando [status] = [UploadStatus.COMPLETED].
 * @property errorMessage preenchido quando [status] = [UploadStatus.FAILED].
 */
data class UploadItem(
    val id: String,
    val fileName: String,
    val fraction: Float = 0f,
    val status: UploadStatus = UploadStatus.PENDING,
    val downloadUrl: String? = null,
    val errorMessage: String? = null,
) {
    val percent: Int get() = (fraction.coerceIn(0f, 1f) * 100).toInt()
    val isTerminal: Boolean get() = status == UploadStatus.COMPLETED || status == UploadStatus.FAILED
    val isFailed: Boolean get() = status == UploadStatus.FAILED
    val isUploading: Boolean get() = status == UploadStatus.UPLOADING
}

/** Estados possíveis de um [UploadItem]. */
enum class UploadStatus { PENDING, UPLOADING, COMPLETED, FAILED }

/**
 * Pedido de upload enfileirável. Os bytes ficam em memória até o upload — para muitas fotos,
 * comprima antes (via `ImageCompressor` (kmplib-platform)) e enfileire em lotes.
 */
data class UploadRequest(
    val id: String,
    val fileName: String,
    val path: String,
    val bytes: ByteArray,
    val mimeType: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UploadRequest) return false
        return id == other.id &&
            fileName == other.fileName &&
            path == other.path &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
