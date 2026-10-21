package br.com.codecacto.kmplib.core.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vetores do FIPS 180-4 / NIST CSHA (mesmos nas duas plataformas). */
class Sha256Test {

    @Test
    fun vetoresConhecidos() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun incrementalIgualAoDeUmaVezEmQualquerCorte() {
        val dados = ByteArray(10_007) { (it * 31 % 256).toByte() }
        val inteiro = sha256(dados)
        for (corte in listOf(0, 1, 63, 64, 65, 5_000, 10_007)) {
            val h = Sha256Hasher()
            h.update(dados, 0, corte)
            h.update(dados, corte, dados.size - corte)
            assertContentEquals(inteiro, h.digest(), "corte em $corte")
            h.close()
        }
    }

    @Test
    fun umMilhaoDeAsEmBlocos() {
        val bloco = ByteArray(1_000) { 'a'.code.toByte() }
        val resumo = Sha256Hasher().use { h ->
            repeat(1_000) { h.update(bloco) }
            h.digest()
        }
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", resumo.toLowerHex())
    }

    @Test
    fun digestRecomecaDoZero() {
        Sha256Hasher().use { h ->
            h.update("abc".encodeToByteArray())
            h.digest()
            h.update("abc".encodeToByteArray())
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h.digest().toLowerHex())
        }
    }

    @Test
    fun faixaInvalidaEUsoDepoisDeFecharLancam() {
        val h = Sha256Hasher()
        assertFailsWith<IllegalArgumentException> { h.update(ByteArray(4), 3, 2) }
        assertFailsWith<IllegalArgumentException> { h.update(ByteArray(4), -1, 1) }
        h.update(ByteArray(4), 4, 0) // vazio no fim é válido
        h.close()
        h.close() // idempotente
        assertFailsWith<IllegalStateException> { h.update(ByteArray(1)) }
        assertFailsWith<IllegalStateException> { h.digest() }
    }

    @Test
    fun hexEValidacao() {
        assertEquals("00ff10", byteArrayOf(0, -1, 16).toLowerHex())
        assertTrue(isSha256Hex(sha256Hex("x")))
        assertFalse(isSha256Hex(sha256Hex("x").uppercase()))
        assertFalse(isSha256Hex("abc"))
        assertEquals(SHA256_DIGEST_BYTES, sha256("x".encodeToByteArray()).size)
    }

    @Test
    fun arquivoInexistenteOuCaminhoVazioDevolveNull() = runTest {
        assertNull(sha256OfFile(""))
        assertNull(sha256HexOfFile("/nao/existe/kmplib-${sha256Hex("z")}"))
        assertFailsWith<IllegalArgumentException> { sha256OfFile("/x", bufferSize = 0) }
    }
}
