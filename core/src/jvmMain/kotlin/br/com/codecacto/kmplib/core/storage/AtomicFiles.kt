package br.com.codecacto.kmplib.core.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Gravação DURÁVEL e atômica de arquivo no desktop (2.353.0): escreve num temporário da MESMA pasta,
 * força os bytes ao disco (`FileChannel.force(true)` — `FlushFileBuffers` no Windows, `fsync` no
 * POSIX) e troca pelo destino com `ATOMIC_MOVE`. Quem lê depois de um corte de energia vê o arquivo
 * antigo inteiro ou o novo inteiro, nunca um pedaço. Só retorna com o dado no disco; falha LANÇA.
 *
 * Pública por ser a primitiva que os módulos de cima (o cofre do `kmplib-auth`) também usam — não é
 * convite para o app gravar na pasta da lib.
 */
@Throws(IOException::class)
fun writeFileDurably(target: Path, bytes: ByteArray) {
    val dir = target.toAbsolutePath().parent ?: throw IOException("destino sem pasta")
    Files.createDirectories(dir)
    val temp = Files.createTempFile(dir, "~" + target.fileName.toString(), ".tmp")
    try {
        FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { ch ->
            val buf = ByteBuffer.wrap(bytes)
            while (buf.hasRemaining()) ch.write(buf)
            ch.force(true)
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
        forceDirectory(dir)
    } finally {
        Files.deleteIfExists(temp)
    }
}

/**
 * Apaga [target] de forma durável (a entrada do diretório também vai ao disco onde o SO permite).
 * `true` se havia arquivo. Falha LANÇA — um logout que não chegou ao disco ressuscitaria.
 */
@Throws(IOException::class)
fun deleteFileDurably(target: Path): Boolean {
    val existed = Files.deleteIfExists(target)
    if (existed) target.toAbsolutePath().parent?.let(::forceDirectory)
    return existed
}

/**
 * `fsync` do diretório (POSIX), para o rename/delete sobreviver a queda de energia. No Windows não
 * se abre diretório como canal — o NTFS registra a operação no journal, e aqui não há o que fazer.
 */
private fun forceDirectory(dir: Path) {
    if (System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")) return
    runCatching { FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) } }
}
