package br.com.codecacto.kmplib.core.storage

import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import java.nio.file.Files
import java.nio.file.Path

/** Desktop: espaço utilizável do volume da pasta de dados do app (`FileStore.usableSpace`). */
actual fun availableStorageBytes(): Long? = try {
    var dir: Path? = DesktopAppContext.appDataDirectory().toAbsolutePath()
    while (dir != null && !Files.exists(dir)) dir = dir.parent
    dir?.let { Files.getFileStore(it).usableSpace }
} catch (e: Exception) {
    AppLogger.w("StorageSpace", "espaço livre indisponível: ${e::class.simpleName}")
    null
}
