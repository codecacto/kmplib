package br.com.codecacto.kmplib.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Padrões funcionais, quiet zone e geometria — as partes do símbolo que **não** dependem do conteúdo.
 *
 * A quiet zone tem teste próprio porque é o esquecimento clássico: sem ela o símbolo é estruturalmente
 * perfeito e **muitos leitores não decodificam**, com o defeito aparecendo só no aparelho de quem usa.
 */
class QrStructureTest {

    private fun qr(text: String = "CODECACTO", ec: QrErrorCorrection = QrErrorCorrection.M) =
        encodeQrOrNull(text, ec)!!

    @Test
    fun `quiet zone de 4 modulos existe, e claro em toda a volta`() {
        val code = qr()
        assertEquals(QrCode.QUIET_ZONE, code.quietZone)
        assertEquals(4, code.quietZone, "o padrão exige 4 módulos")
        assertEquals(code.symbolSize + 8, code.size)

        for (i in 0 until code.size) {
            for (band in 0 until code.quietZone) {
                assertTrue(!code.isDark(i, band), "quiet zone superior suja em ($i, $band)")
                assertTrue(!code.isDark(i, code.size - 1 - band), "quiet zone inferior suja")
                assertTrue(!code.isDark(band, i), "quiet zone esquerda suja")
                assertTrue(!code.isDark(code.size - 1 - band, i), "quiet zone direita suja")
            }
        }
    }

    @Test
    fun `quiet zone menor que o minimo do padrao e elevada, nunca aceita`() {
        // "Economizar" margem é o caminho conhecido para o QR que só lê em alguns aparelhos.
        assertEquals(4, encodeQrOrNull("CODECACTO", quietZone = 0)!!.quietZone)
        assertEquals(4, encodeQrOrNull("CODECACTO", quietZone = 2)!!.quietZone)
        assertEquals(8, encodeQrOrNull("CODECACTO", quietZone = 8)!!.quietZone)
    }

    @Test
    fun `os tres padroes de localizacao estao nos cantos certos`() {
        val code = qr()
        val q = code.quietZone
        val last = code.symbolSize - 1

        // Cada finder é 7×7: anel escuro, anel claro, núcleo 3×3 escuro.
        for ((originX, originY) in listOf(0 to 0, last - 6 to 0, 0 to last - 6)) {
            for (dy in 0..6) {
                for (dx in 0..6) {
                    val distance = maxOf(kotlin.math.abs(dx - 3), kotlin.math.abs(dy - 3))
                    val expected = distance != 2
                    assertEquals(
                        expected,
                        code.isDark(q + originX + dx, q + originY + dy),
                        "finder errado em ($originX+$dx, $originY+$dy)",
                    )
                }
            }
        }
    }

    @Test
    fun `separadores em volta dos finders sao claros`() {
        val code = qr()
        val q = code.quietZone
        val last = code.symbolSize - 1

        for (i in 0..7) {
            assertTrue(!code.isDark(q + i, q + 7), "separador do finder superior esquerdo")
            assertTrue(!code.isDark(q + 7, q + i), "separador do finder superior esquerdo")
            assertTrue(!code.isDark(q + last - i, q + 7), "separador do finder superior direito")
            assertTrue(!code.isDark(q + i, q + last - 7), "separador do finder inferior esquerdo")
        }
    }

    @Test
    fun `padrao de sincronismo alterna na linha e na coluna 6`() {
        val code = qr()
        val q = code.quietZone
        // Entre os separadores (8 .. size-9) o sincronismo alterna, começando escuro em índice par.
        for (i in 8..code.symbolSize - 9) {
            assertEquals(i % 2 == 0, code.isDark(q + i, q + 6), "sincronismo horizontal em $i")
            assertEquals(i % 2 == 0, code.isDark(q + 6, q + i), "sincronismo vertical em $i")
        }
    }

    @Test
    fun `o modulo escuro fixo esta presente`() {
        // (8, size-8) é sempre escuro — outro item que, esquecido, gera símbolo que não lê.
        for (version in listOf(1, 2, 7, 10)) {
            val code = encodeQrOrNull("A".repeat(4), minVersion = version, maxVersion = version)!!
            val q = code.quietZone
            assertTrue(
                code.isDark(q + 8, q + code.symbolSize - 8),
                "módulo escuro fixo ausente na versão $version",
            )
        }
    }

    @Test
    fun `padroes de alinhamento aparecem a partir da versao 2, no lugar previsto`() {
        // Versão 1 não tem alinhamento; da 2 em diante, sim.
        assertEquals(0, QrTables.alignmentPatternPositions(1).size)
        assertEquals(intArrayOf(6, 18).toList(), QrTables.alignmentPatternPositions(2).toList())
        assertEquals(intArrayOf(6, 22).toList(), QrTables.alignmentPatternPositions(3).toList())
        assertEquals(intArrayOf(6, 22, 38).toList(), QrTables.alignmentPatternPositions(7).toList())

        // O centro do alinhamento é escuro e o anel em volta é claro (versão 2, centro em 18,18).
        val code = encodeQrOrNull("A".repeat(20), minVersion = 2, maxVersion = 2)!!
        val q = code.quietZone
        assertTrue(code.isDark(q + 18, q + 18), "centro do alinhamento")
        assertTrue(!code.isDark(q + 17, q + 18), "anel claro do alinhamento")
        assertTrue(code.isDark(q + 16, q + 18), "anel escuro do alinhamento")
    }

    @Test
    fun `a versao 32 usa o passo excepcional da regra de alinhamento`() {
        // A fórmula geral falha na versão 32; o padrão fixa passo 26. Sem esse caso especial, os
        // alinhamentos saem deslocados e o símbolo grande não lê.
        val positions = QrTables.alignmentPatternPositions(32)
        assertEquals(6, positions.first())
        assertEquals(QrCode.symbolSizeOf(32) - 7, positions.last())
        val steps = positions.toList().zipWithNext { a, b -> b - a }
        assertTrue(steps.drop(1).all { it == 26 }, "passo esperado 26, foi $steps")
    }

    @Test
    fun `tamanho do simbolo segue a formula da versao`() {
        assertEquals(21, QrCode.symbolSizeOf(1))
        assertEquals(25, QrCode.symbolSizeOf(2))
        assertEquals(177, QrCode.symbolSizeOf(40))
        for (version in 1..40) {
            assertEquals(version * 4 + 17, QrCode.symbolSizeOf(version))
        }
    }

    @Test
    fun `informacao de versao existe da versao 7 em diante e o BCH bate`() {
        // Valores conhecidos do padrão (Tabela D.1): versão 7 = 0x07C94, versão 40 = 0x28C69.
        assertEquals(0x07C94, QrMatrixBuilder.versionBits(7))
        assertEquals(0x28C69, QrMatrixBuilder.versionBits(40))
    }

    @Test
    fun `informacao de formato bate com a Tabela C1 publicada do padrao`() {
        // Os 32 valores da Tabela C.1 (nível × máscara). É aqui que a ordem dos bits de nível
        // aparece: L = 01, M = 00, Q = 11, H = 10 — NÃO o ordinal do enum.
        val published = mapOf(
            QrErrorCorrection.L to listOf(0x77C4, 0x72F3, 0x7DAA, 0x789D, 0x662F, 0x6318, 0x6C41, 0x6976),
            QrErrorCorrection.M to listOf(0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0),
            QrErrorCorrection.Q to listOf(0x355F, 0x3068, 0x3F31, 0x3A06, 0x24B4, 0x2183, 0x2EDA, 0x2BED),
            QrErrorCorrection.H to listOf(0x1689, 0x13BE, 0x1CE7, 0x19D0, 0x0762, 0x0255, 0x0D0C, 0x083B),
        )
        for ((level, expected) in published) {
            for (mask in 0..7) {
                assertEquals(
                    expected[mask],
                    QrMatrixBuilder.formatBits(level, mask),
                    "formato divergiu em $level / máscara $mask",
                )
            }
        }
        // Os 32 são distintos entre si (é o que dá ao leitor margem para corrigir o formato).
        val all = published.values.flatten()
        assertEquals(32, all.toSet().size)
    }

    @Test
    fun `toMatrix devolve copia, nao a matriz interna`() {
        val code = qr()
        val matrix = code.toMatrix()
        val before = code.isDark(code.quietZone, code.quietZone)
        matrix[code.quietZone][code.quietZone] = !before
        assertEquals(before, code.isDark(code.quietZone, code.quietZone), "o QrCode é imutável")
    }

    @Test
    fun `isDark fora da matriz devolve falso em vez de lancar`() {
        val code = qr()
        assertTrue(!code.isDark(-1, 0))
        assertTrue(!code.isDark(0, -1))
        assertTrue(!code.isDark(code.size, 0))
        assertTrue(!code.isDark(0, code.size))
    }
}
