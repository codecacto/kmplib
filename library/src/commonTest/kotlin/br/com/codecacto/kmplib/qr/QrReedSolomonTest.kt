package br.com.codecacto.kmplib.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reed-Solomon em GF(256) e o fluxo de bits — as duas camadas que, erradas, produzem um símbolo
 * estruturalmente perfeito que **nenhum leitor decodifica**.
 *
 * Os vetores vêm do próprio padrão / de material de referência publicado, não da nossa implementação.
 */
class QrReedSolomonTest {

    @Test
    fun `o corpo finito usa o polinomio primitivo do QR`() {
        assertEquals(0x11D, QrReedSolomon.PRIMITIVE_POLYNOMIAL)

        // Propriedades do corpo: neutro, absorvente e um produto conhecido.
        assertEquals(0, QrReedSolomon.multiply(0, 123))
        assertEquals(0, QrReedSolomon.multiply(123, 0))
        assertEquals(123, QrReedSolomon.multiply(1, 123))
        // α⁴ · α⁴ = α⁸ = 0x1D (a redução pelo polinômio primitivo é o que este caso prova).
        assertEquals(0x1D, QrReedSolomon.multiply(0x10, 0x10))
        // Comutatividade.
        for (a in listOf(2, 7, 33, 200)) {
            for (b in listOf(3, 11, 99, 255)) {
                assertEquals(QrReedSolomon.multiply(a, b), QrReedSolomon.multiply(b, a))
            }
        }
    }

    @Test
    fun `polinomio gerador bate com os expoentes publicados do padrao`() {
        // O padrão publica os coeficientes do gerador como **expoentes de α** (α⁸⁷, α²²⁹, …), e a
        // implementação trabalha com os **valores** do corpo. O teste faz a conversão explicitamente,
        // para comparar com o dado publicado em vez de com a nossa própria saída.
        assertEquals(
            listOf(87, 229, 146, 149, 238, 102, 21).map { alphaPower(it) },
            QrReedSolomon.generatorPolynomial(7).toList(),
            "gerador de grau 7 (versão 1-L)",
        )
        assertEquals(
            listOf(251, 67, 46, 61, 118, 70, 64, 94, 32, 45).map { alphaPower(it) },
            QrReedSolomon.generatorPolynomial(10).toList(),
            "gerador de grau 10 (versão 1-M)",
        )
        // Grau 2: x² + α²⁵x + α³.
        assertEquals(
            listOf(25, 1).map { alphaPower(it) },
            QrReedSolomon.generatorPolynomial(2).toList(),
        )

        for (degree in 1..30) {
            assertEquals(degree, QrReedSolomon.generatorPolynomial(degree).size)
        }
    }

    /** α^[power] no corpo, por multiplicação repetida — usa só a operação já validada acima. */
    private fun alphaPower(power: Int): Int {
        var result = 1
        repeat(power % 255) { result = QrReedSolomon.multiply(result, 0x02) }
        return result
    }

    @Test
    fun `codewords de EC batem com o exemplo classico do padrao`() {
        // Exemplo amplamente publicado (ISO/IEC 18004 e material derivado): a mensagem "01234567" em
        // versão 1-M produz os codewords de dados abaixo, e a EC esperada é conhecida.
        val data = byteArrayOf(
            0x10, 0x20, 0x0C, 0x56, 0x61, 0x80.toByte(), 0xEC.toByte(), 0x11,
            0xEC.toByte(), 0x11, 0xEC.toByte(), 0x11, 0xEC.toByte(), 0x11, 0xEC.toByte(), 0x11,
        )
        val ecc = QrReedSolomon.errorCorrectionCodewords(data, 10)
        assertEquals(
            listOf(0xA5, 0x24, 0xD4, 0xC1, 0xED, 0x36, 0xC7, 0x87, 0x2C, 0x55),
            ecc.map { it.toInt() and 0xFF },
        )
    }

    @Test
    fun `EC de tamanho pedido, e mensagem diferente muda a EC`() {
        val a = QrReedSolomon.errorCorrectionCodewords(byteArrayOf(1, 2, 3), 7)
        val b = QrReedSolomon.errorCorrectionCodewords(byteArrayOf(1, 2, 4), 7)
        assertEquals(7, a.size)
        assertEquals(7, b.size)
        assertTrue(!a.contentEquals(b), "mensagens diferentes não podem gerar a mesma EC")
    }

    @Test
    fun `bitstream do exemplo do padrao bate byte a byte`() {
        // "01234567" em modo numérico, versão 1-M: 4 bits de modo (0001) + 10 bits de contador (8) +
        // os dígitos em grupos de 3 → e o fecho com terminador, alinhamento e padding EC/11.
        val buffer = QrBitBuffer()
        val segment = QrSegment.of("01234567")
        assertEquals(QrMode.Numeric, segment.mode)
        buffer.appendBits(segment.mode.modeBits, 4)
        buffer.appendBits(segment.characterCount, segment.mode.characterCountBits(1))
        buffer.appendAll(segment.data)

        val codewords = buffer.toDataCodewords(16)
        assertEquals(
            listOf(0x10, 0x20, 0x0C, 0x56, 0x61, 0x80, 0xEC, 0x11, 0xEC, 0x11, 0xEC, 0x11, 0xEC, 0x11, 0xEC, 0x11),
            codewords.map { it.toInt() and 0xFF },
            "bitstream/padding divergiu do exemplo do padrão",
        )
    }

    @Test
    fun `padding alterna EC e 11 exatamente nessa ordem, depois do terminador`() {
        val buffer = QrBitBuffer()
        buffer.appendBits(0, 8) // um codeword de dados
        val codewords = buffer.toDataCodewords(5)
        // O segundo byte zero NÃO é dado: é o terminador de 4 bits + o alinhamento de byte. Só a
        // partir dele começa o preenchimento alternado. Confundir os dois é como se produz um
        // codeword deslocado — símbolo perfeito que não decodifica.
        assertEquals(
            listOf(0x00, 0x00, 0xEC, 0x11, 0xEC),
            codewords.map { it.toInt() and 0xFF },
        )
    }

    @Test
    fun `terminador nao estoura a capacidade quando o fluxo ja esta cheio`() {
        // Borda real: dados que preenchem a capacidade exata. O terminador de 4 bits não cabe, e o
        // padrão manda encurtá-lo — não lançar nem sobrescrever dado.
        val buffer = QrBitBuffer()
        repeat(16) { buffer.appendBits(0xFF, 8) }
        val codewords = buffer.toDataCodewords(16)
        assertEquals(16, codewords.size)
        assertTrue(codewords.all { it == 0xFF.toByte() }, "dados no limite não podem ser alterados")
    }

    @Test
    fun `segmento alfanumerico usa 11 bits por par e 6 no resto`() {
        val pair = QrSegment.alphanumeric("AB")
        assertEquals(11, pair.data.bitLength)
        val odd = QrSegment.alphanumeric("ABC")
        assertEquals(17, odd.data.bitLength)
        // "HELLO WORLD" = 11 caracteres ⇒ 5 pares + 1 ⇒ 5*11 + 6 = 61 bits.
        assertEquals(61, QrSegment.alphanumeric("HELLO WORLD").data.bitLength)
    }

    @Test
    fun `segmento numerico usa 10, 7 ou 4 bits conforme o resto`() {
        assertEquals(10, QrSegment.numeric("123").data.bitLength)
        assertEquals(17, QrSegment.numeric("12345").data.bitLength) // 10 + 7
        assertEquals(14, QrSegment.numeric("1234").data.bitLength) // 10 + 4
        assertEquals(4, QrSegment.numeric("1").data.bitLength)
        // Zeros à esquerda não podem encurtar o grupo.
        assertEquals(10, QrSegment.numeric("001").data.bitLength)
    }

    @Test
    fun `contador de caracteres tem o tamanho da faixa de versao`() {
        // Faixas do padrão: 1–9, 10–26, 27–40. Errar isto gera QR que ninguém decodifica.
        assertEquals(10, QrMode.Numeric.characterCountBits(9))
        assertEquals(12, QrMode.Numeric.characterCountBits(10))
        assertEquals(14, QrMode.Numeric.characterCountBits(27))
        assertEquals(9, QrMode.Alphanumeric.characterCountBits(1))
        assertEquals(11, QrMode.Alphanumeric.characterCountBits(26))
        assertEquals(13, QrMode.Alphanumeric.characterCountBits(40))
        assertEquals(8, QrMode.Byte.characterCountBits(9))
        assertEquals(16, QrMode.Byte.characterCountBits(10))
        assertEquals(16, QrMode.Byte.characterCountBits(40))
    }

    @Test
    fun `conjunto alfanumerico tem os 45 caracteres do padrao, na ordem`() {
        assertEquals(45, QrSegment.ALPHANUMERIC_CHARSET.length)
        assertEquals(0, QrSegment.ALPHANUMERIC_CHARSET.indexOf('0'))
        assertEquals(10, QrSegment.ALPHANUMERIC_CHARSET.indexOf('A'))
        assertEquals(35, QrSegment.ALPHANUMERIC_CHARSET.indexOf('Z'))
        assertEquals(36, QrSegment.ALPHANUMERIC_CHARSET.indexOf(' '))
        assertEquals(44, QrSegment.ALPHANUMERIC_CHARSET.indexOf(':'))
        // Minúscula NÃO está no conjunto — é o que joga o texto comum para o modo byte.
        assertEquals(-1, QrSegment.ALPHANUMERIC_CHARSET.indexOf('a'))
    }
}
