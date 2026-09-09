@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.video.download

import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSURL
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey

/**
 * O espaço livre "para conteúdo que o usuário pediu".
 *
 * A chave é [NSURLVolumeAvailableCapacityForImportantUsageKey], e não o `NSFileSystemFreeSize`
 * cru — é o que a Apple manda usar para download pedido pelo usuário. Ela conta o que o sistema
 * consegue liberar apagando cache purgável de outros apps, e por isso costuma ser **maior** que a
 * capacidade livre bruta: perguntar pela bruta faria o app recusar uma aula que caberia sem
 * problema num iPhone que só parece cheio.
 *
 * O `NSFileSystemFreeSize` fica como reserva para o caso de a chave não vir (volume que não a
 * suporta).
 */
actual fun availableStorageBytes(): Long {
    val diretorio = mediaDownloadsDirectory() ?: return 0L
    val url = NSURL.fileURLWithPath(diretorio)
    val valores = runCatching {
        url.resourceValuesForKeys(listOf(NSURLVolumeAvailableCapacityForImportantUsageKey), null)
    }.getOrNull()
    val importante = valores?.get(NSURLVolumeAvailableCapacityForImportantUsageKey) as? Number
    if (importante != null && importante.toLong() > 0L) return importante.toLong()

    val atributos = runCatching {
        NSFileManager.defaultManager.attributesOfFileSystemForPath(diretorio, null)
    }.getOrNull()
    return (atributos?.get(NSFileSystemFreeSize) as? Number)?.toLong() ?: 0L
}
