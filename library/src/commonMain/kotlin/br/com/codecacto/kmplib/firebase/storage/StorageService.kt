package br.com.codecacto.kmplib.firebase.storage

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storage
import br.com.codecacto.kmplib.core.util.AppLogger

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
