package br.com.codecacto.kmplib.ui.components

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VideoRangeReadingTest {

    private val dados = ByteArray(1000) { (it % 251).toByte() }

    /** Fonte em memória, já "posicionada" em [inicio]; [porLeitura] limita cada read (leitura curta). */
    private fun fonte(inicio: Int = 0, porLeitura: Int = Int.MAX_VALUE): RangeSource {
        var pos = inicio
        return RangeSource { destino, max ->
            if (pos >= dados.size) return@RangeSource -1
            val n = minOf(max, porLeitura, dados.size - pos)
            dados.copyInto(destino, 0, pos, pos + n)
            pos += n
            n
        }
    }

    private suspend fun coletar(source: RangeSource, length: Long?, chunk: Int): Pair<ByteArray, List<Int>> {
        val saida = mutableListOf<Byte>()
        val contagens = mutableListOf<Int>()
        val total = streamVideoRange(source, length, chunk) { bytes, count ->
            contagens += count
            for (i in 0 until count) saida += bytes[i]
        }
        assertEquals(saida.size.toLong(), total)
        return saida.toByteArray() to contagens
    }

    @Test
    fun ateOFimEntregaTudoEmPedacos() = runTest {
        val (bytes, contagens) = coletar(fonte(), null, 256)
        assertContentEquals(dados, bytes)
        assertEquals(listOf(256, 256, 256, 232), contagens)
    }

    @Test
    fun faixaLimitadaTruncaOUltimoPedaco() = runTest {
        val (bytes, contagens) = coletar(fonte(inicio = 100), 300, 128)
        assertContentEquals(dados.copyOfRange(100, 400), bytes)
        assertEquals(listOf(128, 128, 44), contagens)
    }

    @Test
    fun faixaQuePassaDoFimEntregaSoOQueExiste() = runTest {
        val (bytes, _) = coletar(fonte(inicio = 900), 500, 64)
        assertContentEquals(dados.copyOfRange(900, 1000), bytes)
    }

    @Test
    fun leituraCurtaNaoPerdeBytes() = runTest {
        val (bytes, _) = coletar(fonte(inicio = 10, porLeitura = 7), 95, 32)
        assertContentEquals(dados.copyOfRange(10, 105), bytes)
    }

    @Test
    fun comprimentoZeroNaoLeNada() = runTest {
        var leu = false
        val total = streamVideoRange({ _, _ -> leu = true; 1 }, 0L, 16) { _, _ -> error("não devia") }
        assertEquals(0L, total)
        assertEquals(false, leu)
    }

    @Test
    fun deslocamentoAlemDoFimDevolveZero() = runTest {
        val (bytes, contagens) = coletar(fonte(inicio = 5000), null, 64)
        assertEquals(0, bytes.size)
        assertEquals(emptyList(), contagens)
    }

    @Test
    fun pedidoNuncaPassaDoChunkNemDaFaixa() = runTest {
        val pedidos = mutableListOf<Int>()
        val base = fonte()
        streamVideoRange({ b, max -> pedidos += max; base.read(b, max) }, 70L, 32) { _, _ -> }
        assertEquals(listOf(32, 32, 6), pedidos)
    }

    @Test
    fun recusaFaixaInvalida() {
        assertFailsWith<IllegalArgumentException> { requireValidVideoRange(-1, null, 10) }
        assertFailsWith<IllegalArgumentException> { requireValidVideoRange(0, -5, 10) }
        assertFailsWith<IllegalArgumentException> { requireValidVideoRange(0, null, 0) }
        requireValidVideoRange(0, 0, 1)
        requireValidVideoRange(10, null, 1)
    }

    // ---- skip verificado ------------------------------------------------------------------------

    @Test
    fun skipQueAndaInteiro() {
        var pos = 0L
        val pulou = skipVideoBytesExactly(500, { n -> pos += n; n }, { error("não devia ler") })
        assertEquals(500, pulou)
        assertEquals(500, pos)
    }

    @Test
    fun skipQueDevolveZeroSemFimLeUmByteEContinua() {
        // Provedor por pipe: skip anda no máximo 10 por vez, e às vezes devolve 0.
        var pos = 0L
        var chamada = 0
        val pulou = skipVideoBytesExactly(
            count = 55,
            skip = { n -> chamada++; if (chamada % 2 == 0) 0 else minOf(n, 10).also { pos += it } },
            readByte = { pos++; 1 },
        )
        assertEquals(55, pulou)
        assertEquals(55, pos)
    }

    @Test
    fun skipQueChegaAoFimDevolveMenos() {
        var pos = 0L
        val tamanho = 30L
        val pulou = skipVideoBytesExactly(
            count = 100,
            skip = { n -> val a = minOf(n, tamanho - pos); pos += a; a },
            readByte = { if (pos < tamanho) { pos++; 1 } else -1 },
        )
        assertEquals(30, pulou)
    }
}
