package br.com.codecacto.kmplib.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

internal actual fun newSha256Engine(): Sha256Engine = object : Sha256Engine {
    private val md = MessageDigest.getInstance("SHA-256")
    override fun update(bytes: ByteArray, offset: Int, length: Int) = md.update(bytes, offset, length)
    override fun digest(): ByteArray = md.digest()
    override fun close() = Unit
}

internal actual suspend fun platformSha256OfFile(path: String, bufferSize: Int): ByteArray? =
    withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) return@withContext null
        val esperado = file.length()
        runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            var lidos = 0L
            FileInputStream(file).use { input ->
                val buffer = ByteArray(bufferSize)
                while (true) {
                    ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n > 0) {
                        md.update(buffer, 0, n)
                        lidos += n
                    }
                }
            }
            // Arquivo que encolheu/cresceu durante a leitura não tem um resumo que valha.
            if (lidos != esperado) null else md.digest()
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
