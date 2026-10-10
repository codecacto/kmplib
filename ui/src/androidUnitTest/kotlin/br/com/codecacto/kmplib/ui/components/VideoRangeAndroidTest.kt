package br.com.codecacto.kmplib.ui.components

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O caminho Android da leitura por faixa, com arquivo e fluxo reais (sem `Context`). */
class VideoRangeAndroidTest {

    private val dados = ByteArray(300_000) { (it * 31 % 256).toByte() }
    private val arquivo = File.createTempFile("kmplib-video", ".mp4").apply { writeBytes(dados) }

    @AfterTest
    fun limpar() {
        arquivo.delete()
    }

    private suspend fun ler(
        offset: Long,
        length: Long?,
        seekable: Boolean,
        stream: () -> InputStream = { FileInputStream(arquivo) },
        onStreamOpened: () -> Unit = {},
    ): Pair<ByteArray, Long> {
        val saida = ByteArrayOutputStream()
        val total = readVideoRangeFrom(
            offset = offset,
            length = length,
            chunkSize = 64 * 1024,
            openSeekable = { if (seekable) FileInputStream(arquivo) else null },
            openStream = { onStreamOpened(); stream() },
            onChunk = { bytes, count -> saida.write(bytes, 0, count) },
        )
        return saida.toByteArray() to total
    }

    @Test
    fun descritorPosicionavelSaltaSemAbrirOFluxo() = runTest {
        var abriuFluxo = false
        val (bytes, total) = ler(100_000, 50_000, seekable = true, onStreamOpened = { abriuFluxo = true })
        assertContentEquals(dados.copyOfRange(100_000, 150_000), bytes)
        assertEquals(50_000, total)
        assertFalse(abriuFluxo)
    }

    @Test
    fun descritorPosicionavelAteOFim() = runTest {
        val (bytes, _) = ler(250_001, null, seekable = true)
        assertContentEquals(dados.copyOfRange(250_001, dados.size), bytes)
    }

    @Test
    fun offsetZeroUsaOFluxoComum() = runTest {
        var abriuFluxo = false
        val (bytes, _) = ler(0, null, seekable = true, onStreamOpened = { abriuFluxo = true })
        assertContentEquals(dados, bytes)
        assertTrue(abriuFluxo)
    }

    @Test
    fun semDescritorCaiNoSkip() = runTest {
        val (bytes, total) = ler(123_456, 10_000, seekable = false)
        assertContentEquals(dados.copyOfRange(123_456, 133_456), bytes)
        assertEquals(10_000, total)
    }

    @Test
    fun skipQueNaoAndaAindaPosicionaCerto() = runTest {
        // Provedor por pipe que nunca anda no skip: a lib lê byte a byte até o deslocamento.
        val teimoso = { object : ByteArrayInputStream(dados) { override fun skip(n: Long) = 0L } }
        val (bytes, _) = ler(5_000, 1_000, seekable = false, stream = teimoso)
        assertContentEquals(dados.copyOfRange(5_000, 6_000), bytes)
    }

    @Test
    fun deslocamentoAlemDoFimNaoEntregaNada() = runTest {
        val (porDescritor, t1) = ler(dados.size + 10L, null, seekable = true)
        val (porSkip, t2) = ler(dados.size + 10L, null, seekable = false)
        assertEquals(0, porDescritor.size)
        assertEquals(0, porSkip.size)
        assertEquals(0L, t1 + t2)
    }

    @Test
    fun comprimentoZeroNaoAbreNada() = runTest {
        var abriu = false
        val total = readVideoRangeFrom(
            offset = 10,
            length = 0,
            chunkSize = 1024,
            openSeekable = { abriu = true; null },
            openStream = { abriu = true; ByteArrayInputStream(dados) },
            onChunk = { _, _ -> },
        )
        assertEquals(0L, total)
        assertFalse(abriu)
    }
}
