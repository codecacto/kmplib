package br.com.codecacto.kmplib.sync.direct

import android.net.ConnectivityManager
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.SyncDatabaseHolder
import java.io.File
import java.io.RandomAccessFile

internal actual fun platformDirectUploadFiles(): DirectUploadFiles = JavaDirectUploadFiles

/** `noBackupFilesDir/<nome>` — fora do Auto Backup e da transferência entre aparelhos, sem regra de manifesto. */
internal actual fun platformDirectUploadRoot(directoryName: String): String? {
    val context = SyncDatabaseHolder.contextOrNull() ?: return null
    val dir = File(context.noBackupFilesDir, directoryName)
    dir.mkdirs()
    return dir.absolutePath
}

internal actual fun platformIsActiveNetworkMetered(): Boolean? {
    val context = SyncDatabaseHolder.contextOrNull() ?: return null
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    return runCatching { cm.isActiveNetworkMetered }.getOrNull()
}

/** `java.io` puro: roda no aparelho e na JVM dos testes. */
internal object JavaDirectUploadFiles : DirectUploadFiles {
    private const val TAG = "DirectUploadFiles"
    private const val BUFFER = 64 * 1024

    override fun size(path: String): Long {
        val f = File(path)
        return if (f.isFile) f.length() else -1L
    }

    override fun mkdirs(path: String): Boolean {
        val f = File(path)
        return f.isDirectory || f.mkdirs() || f.isDirectory
    }

    override fun list(dir: String): List<String> = File(dir).list()?.toList().orEmpty()

    override fun readText(path: String): String? = runCatching {
        File(path).takeIf { it.isFile }?.readText(Charsets.UTF_8)
    }.getOrNull()

    override fun writeTextAtomic(path: String, text: String): Boolean = runCatching {
        val destino = File(path)
        destino.parentFile?.mkdirs()
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        tmp.outputStream().use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(destino)) {
            // renameTo não sobrescreve em alguns sistemas de arquivos: apaga e tenta de novo.
            destino.delete()
            if (!tmp.renameTo(destino)) {
                tmp.delete()
                return false
            }
        }
        true
    }.onFailure { AppLogger.w(TAG, "escrita atômica falhou: ${it::class.simpleName}") }.getOrDefault(false)

    override fun move(from: String, to: String): Boolean {
        val origem = File(from)
        val destino = File(to)
        if (!origem.isFile) return false
        destino.parentFile?.mkdirs()
        destino.delete()
        // cacheDir e noBackupFilesDir ficam no mesmo volume interno: o caso normal é um rename.
        if (origem.renameTo(destino)) return true
        if (!copy(from, to)) return false
        if (!origem.delete()) AppLogger.w(TAG, "arquivo copiado para a fila, mas o original não saiu")
        return true
    }

    override fun copy(from: String, to: String): Boolean = runCatching {
        val origem = File(from)
        if (!origem.isFile) return false
        val destino = File(to)
        destino.parentFile?.mkdirs()
        origem.inputStream().use { i -> destino.outputStream().use { o -> i.copyTo(o, BUFFER); o.fd.sync() } }
        destino.length() == origem.length()
    }.onFailure { AppLogger.w(TAG, "cópia falhou: ${it::class.simpleName}"); runCatching { File(to).delete() } }
        .getOrDefault(false)

    override fun deleteRecursively(path: String): Boolean {
        val f = File(path)
        if (!f.exists()) return true
        return f.deleteRecursively() || !f.exists()
    }

    override fun readRange(path: String, offset: Long, length: Long): ByteArray? {
        if (offset < 0 || length <= 0 || length > Int.MAX_VALUE) return null
        return runCatching {
            RandomAccessFile(path, "r").use { raf ->
                if (offset + length > raf.length()) return null
                raf.seek(offset)
                val bytes = ByteArray(length.toInt())
                raf.readFully(bytes)
                bytes
            }
        }.getOrNull()
    }

    override fun writeRange(source: String, offset: Long, length: Long, dest: String): Boolean = runCatching {
        if (offset < 0 || length <= 0) return false
        RandomAccessFile(source, "r").use { raf ->
            if (offset + length > raf.length()) return false
            raf.seek(offset)
            val destino = File(dest)
            destino.parentFile?.mkdirs()
            destino.outputStream().use { out ->
                val buf = ByteArray(BUFFER)
                var falta = length
                while (falta > 0) {
                    val n = raf.read(buf, 0, minOf(buf.size.toLong(), falta).toInt())
                    if (n <= 0) return false
                    out.write(buf, 0, n)
                    falta -= n
                }
                out.fd.sync()
            }
        }
        true
    }.getOrDefault(false)
}
