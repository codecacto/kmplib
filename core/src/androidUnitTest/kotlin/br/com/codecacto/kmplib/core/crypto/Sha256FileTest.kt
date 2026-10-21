package br.com.codecacto.kmplib.core.crypto

import kotlinx.coroutines.test.runTest
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Arquivo de verdade no disco da JVM: leitura em blocos dá o mesmo resumo do `MessageDigest` direto. */
class Sha256FileTest {

    @Test
    fun arquivoEmBlocosIgualAoResumoDireto() = runTest {
        val arquivo = File.createTempFile("kmplib-sha", ".bin")
        try {
            val dados = ByteArray(3 * SHA256_FILE_BUFFER_BYTES + 17) { (it % 253).toByte() }
            arquivo.writeBytes(dados)
            val esperado = MessageDigest.getInstance("SHA-256").digest(dados).toLowerHex()
            assertEquals(esperado, sha256HexOfFile(arquivo.absolutePath))
            assertEquals(esperado, sha256HexOfFile(arquivo.absolutePath, bufferSize = 1000))
        } finally {
            arquivo.delete()
        }
    }

    @Test
    fun arquivoVazioEDiretorio() = runTest {
        val vazio = File.createTempFile("kmplib-sha", ".bin")
        try {
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256HexOfFile(vazio.absolutePath))
            assertNull(sha256HexOfFile(vazio.parentFile!!.absolutePath), "diretório não é arquivo")
        } finally {
            vazio.delete()
        }
    }
}
