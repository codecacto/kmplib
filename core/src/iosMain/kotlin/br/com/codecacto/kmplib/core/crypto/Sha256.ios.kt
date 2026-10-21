@file:OptIn(ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.core.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread

/**
 * CommonCrypto incremental. O `CC_SHA256_CTX` mora em memória nativa (`nativeHeap`), liberada no
 * `close` do [Sha256Hasher] — o `use {}` garante.
 */
internal actual fun newSha256Engine(): Sha256Engine = CommonCryptoSha256()

private class CommonCryptoSha256 : Sha256Engine {
    private val ctx: CC_SHA256_CTX = nativeHeap.alloc<CC_SHA256_CTX>().also { CC_SHA256_Init(it.ptr) }

    override fun update(bytes: ByteArray, offset: Int, length: Int) {
        bytes.usePinned { pinned ->
            CC_SHA256_Update(ctx.ptr, pinned.addressOf(offset), length.convert())
        }
    }

    override fun digest(): ByteArray {
        val out = UByteArray(CC_SHA256_DIGEST_LENGTH)
        out.usePinned { pinned -> CC_SHA256_Final(pinned.addressOf(0), ctx.ptr) }
        // Mesmo contrato do MessageDigest: depois do resumo, o hasher recomeça do zero.
        CC_SHA256_Init(ctx.ptr)
        return out.toByteArray()
    }

    override fun close() {
        nativeHeap.free(ctx)
    }
}

internal actual suspend fun platformSha256OfFile(path: String, bufferSize: Int): ByteArray? =
    withContext(Dispatchers.Default) {
        val file = fopen(path, "rb") ?: return@withContext null
        try {
            Sha256Hasher().use { hasher ->
                val buffer = ByteArray(bufferSize)
                while (true) {
                    ensureActive()
                    val n = buffer.usePinned { pinned ->
                        fread(pinned.addressOf(0), 1.convert(), bufferSize.convert(), file)
                    }.toInt()
                    if (n > 0) hasher.update(buffer, 0, n)
                    if (n < bufferSize) {
                        // Fim do arquivo OU erro de leitura: só o fim vale como resumo.
                        if (ferror(file) != 0) return@withContext null
                        break
                    }
                }
                hasher.digest()
            }
        } finally {
            fclose(file)
        }
    }
