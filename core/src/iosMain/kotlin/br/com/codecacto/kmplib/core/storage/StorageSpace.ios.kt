package br.com.codecacto.kmplib.core.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey

@OptIn(ExperimentalForeignApi::class)
actual fun availableStorageBytes(): Long? {
    val casa = NSHomeDirectory()
    val chave = NSURLVolumeAvailableCapacityForImportantUsageKey
    if (chave != null) {
        val valores = NSURL.fileURLWithPath(casa).resourceValuesForKeys(listOf(chave), null)
        (valores?.get(chave) as? NSNumber)?.longLongValue?.takeIf { it > 0 }?.let { return it }
    }
    val atributos = NSFileManager.defaultManager.attributesOfFileSystemForPath(casa, null) ?: return null
    return (atributos[NSFileSystemFreeSize] as? NSNumber)?.longLongValue
}
