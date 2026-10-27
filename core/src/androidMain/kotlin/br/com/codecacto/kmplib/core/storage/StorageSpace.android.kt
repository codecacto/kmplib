package br.com.codecacto.kmplib.core.storage

import android.os.StatFs
import br.com.codecacto.kmplib.core.util.AppLogger

actual fun availableStorageBytes(): Long? = try {
    StatFs(BlobStoreHolder.requireContext().noBackupFilesDir.path).availableBytes
} catch (e: Exception) {
    AppLogger.w("StorageSpace", "espaço livre indisponível: ${e::class.simpleName}")
    null
}
