@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.*
import platform.posix.memcpy

internal actual fun platformDirectUploadFiles(): DirectUploadFiles = IosDirectUploadFiles

/**
 * `Application Support/<nome>`, marcado com `NSURLIsExcludedFromBackupKey` (cobre tudo dentro dele).
 * Não é `Caches` (o sistema purga sob pressão — o vídeo sumiria da fila em silêncio) nem `tmp`.
 */
internal actual fun platformDirectUploadRoot(directoryName: String): String? {
    val raiz = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: return null
    val dir = "$raiz/$directoryName"
    if (!IosDirectUploadFiles.mkdirs(dir)) return null
    val ok = NSURL.fileURLWithPath(dir, isDirectory = true)
        .setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)
    if (!ok) AppLogger.e("DirectUploadFiles", "não foi possível tirar a fila de envio do backup do iCloud")
    return dir
}

/** O iOS não expõe "rede tarifada agora" de forma síncrona; o transporte aplica o "só Wi-Fi" por requisição. */
internal actual fun platformIsActiveNetworkMetered(): Boolean? = null

/**
 * `NSFileManager` + `NSFileHandle` com as APIs que devolvem `NSError` (iOS 13+). As antigas
 * (`readDataOfLength`, `seekToFileOffset`) sinalizam falha com `NSException`, que o Kotlin/Native não
 * captura — derrubariam o app no meio do envio.
 */
internal object IosDirectUploadFiles : DirectUploadFiles {
    private const val TAG = "DirectUploadFiles"
    private const val BUFFER: Long = 256L * 1024
    private val fm get() = NSFileManager.defaultManager

    private fun isDirectory(path: String): Boolean =
        fm.attributesOfItemAtPath(path, null)?.get(NSFileType) == NSFileTypeDirectory

    override fun size(path: String): Long {
        val attrs = fm.attributesOfItemAtPath(path, null) ?: return -1L
        if (attrs[NSFileType] == NSFileTypeDirectory) return -1L
        return (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: -1L
    }

    override fun mkdirs(path: String): Boolean {
        if (isDirectory(path)) return true
        return fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null) || isDirectory(path)
    }

    override fun list(dir: String): List<String> =
        fm.contentsOfDirectoryAtPath(dir, null)?.filterIsInstance<String>().orEmpty()

    override fun readText(path: String): String? =
        if (size(path) < 0L) null else NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

    override fun writeTextAtomic(path: String, text: String): Boolean {
        val bytes = text.encodeToByteArray()
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        // atomically = true: grava num temporário e renomeia — nunca meio JSON no lugar do estado.
        return data.writeToFile(path, atomically = true)
    }

    override fun move(from: String, to: String): Boolean {
        if (size(from) < 0L) return false
        if (fm.fileExistsAtPath(to)) fm.removeItemAtPath(to, null)
        // tmp e Application Support ficam no mesmo volume: é um rename, sem copiar o vídeo.
        if (fm.moveItemAtPath(from, toPath = to, error = null)) return true
        if (!copy(from, to)) return false
        if (!fm.removeItemAtPath(from, null)) AppLogger.w(TAG, "arquivo copiado para a fila, mas o original não saiu")
        return true
    }

    override fun copy(from: String, to: String): Boolean {
        if (size(from) < 0L) return false
        if (fm.fileExistsAtPath(to)) fm.removeItemAtPath(to, null)
        val ok = fm.copyItemAtPath(from, toPath = to, error = null)
        return ok && size(to) == size(from)
    }

    override fun deleteRecursively(path: String): Boolean {
        if (!fm.fileExistsAtPath(path)) return true
        return fm.removeItemAtPath(path, null) || !fm.fileExistsAtPath(path)
    }

    override fun readRange(path: String, offset: Long, length: Long): ByteArray? {
        if (offset < 0 || length <= 0 || length > Int.MAX_VALUE) return null
        if (offset + length > size(path)) return null
        val handle = abrirLeitura(path) ?: return null
        try {
            if (!posicionar(handle, offset)) return null
            val out = ByteArray(length.toInt())
            var lidos = 0L
            while (lidos < length) {
                val dados = lerAte(handle, minOf(BUFFER, length - lidos)) ?: return null
                val n = dados.length.toLong()
                if (n <= 0L) return null
                out.usePinned { memcpy(it.addressOf(lidos.toInt()), dados.bytes, dados.length) }
                lidos += n
            }
            return out
        } finally {
            fechar(handle)
        }
    }

    override fun writeRange(source: String, offset: Long, length: Long, dest: String): Boolean {
        if (offset < 0 || length <= 0 || offset + length > size(source)) return false
        if (fm.fileExistsAtPath(dest)) fm.removeItemAtPath(dest, null)
        if (!fm.createFileAtPath(dest, contents = null, attributes = null)) return false
        val leitura = abrirLeitura(source) ?: return false
        val escrita = memScoped {
            val erro = alloc<ObjCObjectVar<NSError?>>()
            NSFileHandle.fileHandleForWritingToURL(NSURL.fileURLWithPath(dest), error = erro.ptr)
        }
        if (escrita == null) {
            fechar(leitura)
            return false
        }
        var ok = false
        try {
            if (!posicionar(leitura, offset)) return false
            var falta = length
            while (falta > 0) {
                val dados = lerAte(leitura, minOf(BUFFER, falta)) ?: return false
                val n = dados.length.toLong()
                if (n <= 0L) return false
                val escreveu = memScoped {
                    val erro = alloc<ObjCObjectVar<NSError?>>()
                    escrita.writeData(dados, error = erro.ptr)
                }
                if (!escreveu) return false
                falta -= n
            }
            ok = true
            return true
        } finally {
            fechar(leitura)
            fechar(escrita)
            if (!ok) fm.removeItemAtPath(dest, null)
        }
    }

    private fun abrirLeitura(path: String): NSFileHandle? = memScoped {
        val erro = alloc<ObjCObjectVar<NSError?>>()
        NSFileHandle.fileHandleForReadingFromURL(NSURL.fileURLWithPath(path), error = erro.ptr)
    }

    private fun posicionar(handle: NSFileHandle, offset: Long): Boolean = memScoped {
        val erro = alloc<ObjCObjectVar<NSError?>>()
        handle.seekToOffset(offset.toULong(), error = erro.ptr)
    }

    private fun lerAte(handle: NSFileHandle, max: Long): NSData? = memScoped {
        val erro = alloc<ObjCObjectVar<NSError?>>()
        val dados = handle.readDataUpToLength(max.toULong(), error = erro.ptr)
        if (dados == null && erro.value != null) null else dados
    }

    private fun fechar(handle: NSFileHandle) {
        memScoped {
            val erro = alloc<ObjCObjectVar<NSError?>>()
            handle.closeAndReturnError(erro.ptr)
        }
    }
}
