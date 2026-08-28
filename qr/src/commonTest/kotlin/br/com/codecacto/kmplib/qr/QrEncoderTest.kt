package br.com.codecacto.kmplib.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato do encoder: escolha de modo, escolha de versão, limites e o que acontece quando o conteúdo
 * **não cabe** (que é estado esperado de produto, não bug).
 */
class QrEncoderTest {

    @Test
    fun `modo numerico e escolhido para digitos, e e mais economico que byte`() {
        val digits = "1".repeat(100)
        val qr = encodeQrOrNull(digits, QrErrorCorrection.M)!!
        assertEquals(QrMode.Numeric, qr.mode)

        // A economia é o motivo de existir o modo: em byte, os mesmos 100 dígitos exigiriam
        // uma versão maior. Se um dia o modo deixar de ser escolhido, este teste mostra o custo.
        val numericBits = qrCodeFits(digits, QrErrorCorrection.M).requiredBits
        assertTrue(numericBits < 100 * 8, "100 dígitos deveriam ocupar ~334 bits, foi $numericBits")
    }

    @Test
    fun `modo alfanumerico e escolhido para maiuscula e simbolos do conjunto`() {
        val qr = encodeQrOrNull("HELLO WORLD 123 \$%*+-./:", QrErrorCorrection.M)!!
        assertEquals(QrMode.Alphanumeric, qr.mode)
    }

    @Test
    fun `minuscula e acento caem no modo byte com UTF-8`() {
        assertEquals(QrMode.Byte, encodeQrOrNull("hello")!!.mode)
        assertEquals(QrMode.Byte, encodeQrOrNull("Café")!!.mode)

        // "é" são DOIS bytes em UTF-8: o contador de caracteres conta bytes, não caracteres.
        val oneChar = qrCodeFits("a", QrErrorCorrection.M)
        val accented = qrCodeFits("é", QrErrorCorrection.M)
        assertEquals(oneChar.requiredBits + 8, accented.requiredBits)
    }

    @Test
    fun `versao escolhida e a menor que couber`() {
        // Versão 1-L cabe 152 bits de dados; um texto curto tem de ficar na versão 1.
        assertEquals(1, encodeQrOrNull("OI", QrErrorCorrection.L)!!.version)
        // Conteúdo maior sobe de versão sozinho.
        assertTrue(encodeQrOrNull("x".repeat(200), QrErrorCorrection.L)!!.version > 1)
        // Nível mais alto gasta mais espaço com redundância ⇒ mesma carga, versão maior ou igual.
        val atL = encodeQrOrNull("x".repeat(200), QrErrorCorrection.L)!!.version
        val atH = encodeQrOrNull("x".repeat(200), QrErrorCorrection.H)!!.version
        assertTrue(atH > atL, "H deveria exigir versão maior que L para a mesma carga")
    }

    @Test
    fun `minVersion mantem o tamanho estavel entre conteudos diferentes`() {
        val a = encodeQrOrNull("A", minVersion = 5)!!
        val b = encodeQrOrNull("conteudo um pouco maior", minVersion = 5)!!
        assertEquals(5, a.version)
        assertEquals(5, b.version)
        assertEquals(a.size, b.size, "com minVersion fixa, o QR não muda de tamanho na tela")
    }

    @Test
    fun `conteudo que nao cabe devolve TooLong com os numeros, sem lancar`() {
        // Versão 40-H cabe ~1273 bytes; 5000 caracteres não cabem em nível nenhum.
        val result = encodeQr("z".repeat(5000), QrErrorCorrection.H)
        val tooLong = assertIs<QrEncodeResult.TooLong>(result)

        assertTrue(tooLong.requiredBits > tooLong.capacityBits)
        assertEquals(40, tooLong.maxVersion)
        assertEquals(QrErrorCorrection.H, tooLong.errorCorrection)
        assertNull(result.qrCodeOrNull)
        assertNull(encodeQrOrNull("z".repeat(5000), QrErrorCorrection.H))
    }

    @Test
    fun `maxVersion baixo faz o conteudo medio nao caber - o gatilho do fallback`() {
        // É exatamente o caso do produto: um cofre grande não entra num QR pequeno o suficiente para
        // ser lido de tela; a tela precisa oferecer arquivo.
        val payload = "p".repeat(400)
        assertNull(encodeQrOrNull(payload, QrErrorCorrection.L, maxVersion = 5))
        assertNotNull(encodeQrOrNull(payload, QrErrorCorrection.L, maxVersion = 20))
    }

    @Test
    fun `texto vazio gera o menor simbolo valido em vez de erro`() {
        val qr = encodeQrOrNull("", QrErrorCorrection.M)!!
        assertEquals(1, qr.version)
        assertEquals(21, qr.symbolSize)
        assertEquals(29, qr.size) // 21 + 4 + 4
    }

    @Test
    fun `argumento invalido de programacao lanca, ao contrario de conteudo grande`() {
        // Distinção deliberada: erro de quem chama estoura; limite de capacidade é resultado tipado.
        assertFails { encodeQr("A", minVersion = 0) }
        assertFails { encodeQr("A", maxVersion = 41) }
        assertFails { encodeQr("A", minVersion = 10, maxVersion = 5) }
        assertFails { encodeQr("A", quietZone = -1) }
        assertFails { QrCode.symbolSizeOf(0) }
        assertFails { QrCode.symbolSizeOf(41) }
    }

    @Test
    fun `codificacao e deterministica`() {
        val a = encodeQrOrNull("confere-qr:v1:cofre", QrErrorCorrection.L)!!
        val b = encodeQrOrNull("confere-qr:v1:cofre", QrErrorCorrection.L)!!
        assertEquals(a.version, b.version)
        assertEquals(a.mask, b.mask)
        for (y in 0 until a.size) {
            for (x in 0 until a.size) {
                assertEquals(a.isDark(x, y), b.isDark(x, y), "divergiu em ($x, $y)")
            }
        }
    }

    @Test
    fun `conteudos diferentes geram matrizes diferentes`() {
        val a = encodeQrOrNull("CONFERE QR A", QrErrorCorrection.M)!!
        val b = encodeQrOrNull("CONFERE QR B", QrErrorCorrection.M)!!
        val differs = (0 until a.size).any { y ->
            (0 until a.size).any { x -> a.isDark(x, y) != b.isDark(x, y) }
        }
        assertTrue(differs)
    }

    @Test
    fun `debug string tem o tamanho da matriz e serve para inspecao`() {
        val qr = encodeQrOrNull("A")!!
        val lines = qr.toDebugString(dark = "#", light = ".").split("\n")
        assertEquals(qr.size, lines.size)
        assertTrue(lines.all { it.length == qr.size })
        // A primeira linha é quiet zone: toda clara.
        assertTrue(lines.first().all { it == '.' })
    }

    @Test
    fun `payload no limite exato da versao cabe`() {
        // A borda que costuma quebrar: conteúdo que ocupa TODA a capacidade de dados.
        for (level in QrErrorCorrection.entries) {
            val capacity = qrByteCapacity(4, level)
            val payload = "x".repeat(capacity)
            val qr = encodeQrOrNull(payload, level, maxVersion = 4)
            assertNotNull(qr, "capacidade declarada ($capacity bytes) deveria caber em 4-$level")
            assertEquals(4, qr.version)

            // E um byte a mais NÃO cabe: prova que a capacidade declarada é exata, não conservadora.
            assertNull(
                encodeQrOrNull("x".repeat(capacity + 1), level, maxVersion = 4),
                "um byte além da capacidade não deveria caber em 4-$level",
            )
        }
    }

    private inline fun assertFails(block: () -> Unit) {
        val threw = try {
            block()
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        assertTrue(threw, "esperado IllegalArgumentException")
    }
}
