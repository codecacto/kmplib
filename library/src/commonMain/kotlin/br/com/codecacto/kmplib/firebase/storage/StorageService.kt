package br.com.codecacto.kmplib.firebase.storage

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storage
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Serviço de Firebase Storage para download e exclusão de arquivos.
 *
 * Uso:
 * ```kotlin
 * val storageService = StorageService()
 *
 * // Download URL
 * val url = storageService.getDownloadUrl("users/$userId/avatar.jpg")
 *
 * // Deletar arquivo
 * storageService.deleteFile("users/$userId/avatar.jpg")
 * ```
 */
class StorageService {

    private val storage: FirebaseStorage = Firebase.storage

    companion object {
        private const val TAG = "StorageService"
    }

    // ========================
    // UPLOAD
    // ========================

    /**
     * Envia bytes para um caminho no Storage e retorna a URL de download.
     */
    suspend fun uploadBytes(
        path: String,
        bytes: ByteArray,
        mimeType: String? = null
    ): Result<String> {
        return try {
            val reference = storage.reference.child(path)
            reference.putData(bytes.toFirebaseData())
            val downloadUrl = reference.getDownloadUrl()
            val mimeSuffix = mimeType?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            AppLogger.d(TAG, "Arquivo enviado: $path$mimeSuffix")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao enviar: $path", e)
            Result.failure(mapStorageException(e))
        }
    }

    /**
     * Envia bytes emitindo progresso em um [Flow].
     *
     * Emite [UploadProgress.Started], depois (limitação atual do GitLive 2.1.0
     * que não expõe progresso reativo) um [UploadProgress.Uploading] indicando
     * que o upload está em andamento, e por fim [UploadProgress.Completed] ou
     * [UploadProgress.Failed].
     *
     * Quando a versão GitLive evoluir para expor progresso real, este método
     * passará a emitir [UploadProgress.Uploading] periodicamente sem mudar a API.
     *
     * Uso:
     * ```
     * storageService.uploadBytesWithProgress(path, bytes).collect { progress ->
     *     when (progress) {
     *         is UploadProgress.Started -> setState { copy(isUploading = true) }
     *         is UploadProgress.Uploading -> setState { copy(percent = progress.percent) }
     *         is UploadProgress.Completed -> setState { copy(downloadUrl = progress.downloadUrl) }
     *         is UploadProgress.Failed -> setState { copy(error = progress.cause.message) }
     *     }
     * }
     * ```
     */
    fun uploadBytesWithProgress(
        path: String,
        bytes: ByteArray,
        mimeType: String? = null
    ): Flow<UploadProgress> = flow {
        val total = bytes.size.toLong()
        emit(UploadProgress.Started(total))
        try {
            val reference = storage.reference.child(path)
            emit(UploadProgress.Uploading(transferred = 0L, total = total))
            reference.putData(bytes.toFirebaseData())
            val downloadUrl = reference.getDownloadUrl()
            val mimeSuffix = mimeType?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            AppLogger.d(TAG, "Arquivo enviado: $path$mimeSuffix")
            emit(UploadProgress.Uploading(transferred = total, total = total))
            emit(UploadProgress.Completed(downloadUrl = downloadUrl, totalBytes = total))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao enviar: $path", e)
            emit(UploadProgress.Failed(cause = mapStorageException(e)))
        }
    }

    // ========================
    // DOWNLOAD URL
    // ========================

    /**
     * Obtém a URL de download de um arquivo.
     */
    suspend fun getDownloadUrl(path: String): Result<String> {
        return try {
            val url = storage.reference.child(path).getDownloadUrl()
            Result.success(url)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao obter URL: $path", e)
            Result.failure(mapStorageException(e))
        }
    }

    // ========================
    // DELETE
    // ========================

    /**
     * Exclui um arquivo.
     */
    suspend fun deleteFile(path: String): Result<Unit> {
        return try {
            storage.reference.child(path).delete()
            AppLogger.d(TAG, "Arquivo excluído: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao excluir: $path", e)
            Result.failure(mapStorageException(e))
        }
    }

    /**
     * Exclui múltiplos arquivos.
     * @param paths Lista de caminhos dos arquivos a serem excluídos
     * @return Result com DeleteFilesResult contendo contagem de sucessos e falhas
     */
    suspend fun deleteFiles(paths: List<String>): Result<DeleteFilesResult> {
        var successCount = 0
        val failedPaths = mutableListOf<String>()

        paths.forEach { path ->
            deleteFile(path)
                .onSuccess { successCount++ }
                .onFailure { failedPaths.add(path) }
        }

        val result = DeleteFilesResult(
            successCount = successCount,
            failedCount = failedPaths.size,
            failedPaths = failedPaths
        )

        return if (failedPaths.isEmpty()) {
            Result.success(result)
        } else if (successCount == 0) {
            Result.failure(StorageException.Unknown("Falha ao excluir todos os arquivos"))
        } else {
            // Retorna sucesso parcial com informações das falhas
            Result.success(result)
        }
    }

    // ========================
    // HELPERS
    // ========================

    /**
     * Verifica se um arquivo existe.
     */
    suspend fun exists(path: String): Boolean {
        return try {
            storage.reference.child(path).getMetadata()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtém referência para um caminho no Storage.
     * Use para operações mais avançadas ou platform-specific.
     */
    fun reference(path: String) = storage.reference.child(path)

    /**
     * Obtém tipo MIME baseado na extensão do arquivo.
     */
    fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".").lowercase()) {
            // Imagens
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"

            // Documentos
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"

            // Vídeos
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"

            // Áudio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"

            // Compactados
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"

            // JSON
            "json" -> "application/json"

            else -> "application/octet-stream"
        }
    }

    private fun mapStorageException(e: Exception): StorageException {
        val message = e.message?.lowercase() ?: ""

        return when {
            "not found" in message || "object does not exist" in message ->
                StorageException.FileNotFound("Arquivo não encontrado")

            "unauthorized" in message || "permission" in message ->
                StorageException.Unauthorized("Sem permissão para acessar o arquivo")

            "canceled" in message || "cancelled" in message ->
                StorageException.Cancelled("Operação cancelada")

            "quota" in message ->
                StorageException.QuotaExceeded("Limite de armazenamento excedido")

            "network" in message || "connection" in message ->
                StorageException.NetworkError("Erro de conexão")

            else -> StorageException.Unknown(e.message ?: "Erro desconhecido")
        }
    }
}

/**
 * Estados de progresso de um upload de bytes.
 *
 * Usado por [StorageService.uploadBytesWithProgress].
 */
sealed class UploadProgress {
    /** Upload iniciado. Antes de qualquer transferência. */
    data class Started(val totalBytes: Long) : UploadProgress()

    /**
     * Upload em andamento.
     *
     * Nota: na versão atual do GitLive Firebase Storage 2.1.0, [transferred]
     * pode ser apenas 0 (início) ou [total] (fim) — sem progresso intermediário
     * real. Quando a lib evoluir, este campo passará a refletir o progresso
     * verdadeiro sem mudança de API.
     */
    data class Uploading(val transferred: Long, val total: Long) : UploadProgress() {
        /** Percentual 0.0..1.0 (clamped). */
        val fraction: Float
            get() = if (total > 0) (transferred.toFloat() / total).coerceIn(0f, 1f) else 0f

        /** Percentual 0..100 inteiro. */
        val percent: Int get() = (fraction * 100).toInt()
    }

    /** Upload finalizado com sucesso. */
    data class Completed(val downloadUrl: String, val totalBytes: Long) : UploadProgress()

    /** Upload falhou. */
    data class Failed(val cause: Throwable) : UploadProgress()
}

/**
 * Resultado da exclusão de múltiplos arquivos.
 */
data class DeleteFilesResult(
    val successCount: Int,
    val failedCount: Int,
    val failedPaths: List<String>
) {
    val isCompleteSuccess: Boolean get() = failedCount == 0
    val isPartialSuccess: Boolean get() = successCount > 0 && failedCount > 0
    val isCompleteFailure: Boolean get() = successCount == 0 && failedCount > 0
}

/**
 * Exceções de Storage.
 */
sealed class StorageException(message: String) : Exception(message) {
    data class FileNotFound(override val message: String) : StorageException(message)
    data class Unauthorized(override val message: String) : StorageException(message)
    data class Cancelled(override val message: String) : StorageException(message)
    data class QuotaExceeded(override val message: String) : StorageException(message)
    data class NetworkError(override val message: String) : StorageException(message)
    data class Unknown(override val message: String) : StorageException(message)
}
