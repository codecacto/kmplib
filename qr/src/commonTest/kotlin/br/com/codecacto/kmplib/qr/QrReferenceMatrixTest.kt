package br.com.codecacto.kmplib.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * O teste que sustenta o encoder: a matriz gerada aqui tem de ser **idêntica**, módulo a módulo, à de
 * uma implementação **independente** (`node-qrcode` 1.5.4 — ver [QrReferenceVectors]).
 *
 * Por que isso importa mais que qualquer teste interno: um encoder de QR escrito à mão erra
 * **silenciosamente** — o símbolo sai bonito e alguns leitores recusam. Comparar contra terceiro cobre
 * de uma vez o bitstream, o padding `0xEC`/`0x11`, o Reed-Solomon em GF(256), o intercalamento de
 * blocos (curtos e longos), os padrões funcionais, o módulo escuro fixo, a informação de formato
 * (BCH 15,5), a de versão (BCH 18,6) e a aplicação da máscara.
 *
 * A máscara é **forçada** nos vetores, isolando a estrutura da heurística de escolha (que é medida em
 * `QrMaskChoiceTest`).
 *
 * **O que este teste NÃO prova:** que um iPhone ou um Android real decodifica a imagem na tela. Isso
 * depende de câmera, contraste, brilho e distância, e é validação de device — passo do fundador, como
 * a `assembleDebug`.
 */
class QrReferenceMatrixTest {

    @Test
    fun `matriz identica a implementacao independente em todos os vetores`() {
        for (vector in QrReferenceVectors.vectors) {
            val result = encodeQr(
                text = vector.text,
                errorCorrection = vector.level,
                forcedMask = vector.mask,
            )
            val qr = assertIs<QrEncodeResult.Success>(result, "falhou ao codificar ${vector.name}").qrCode

            assertEquals(vector.version, qr.version, "versão divergiu em ${vector.name}")
            assertEquals(vector.size, qr.symbolSize, "tamanho divergiu em ${vector.name}")
            assertEquals(vector.mask, qr.mask, "máscara divergiu em ${vector.name}")

            val flat = flatten(qr)
            assertEquals(
                vector.darkCount,
                flat.count { it == '1' },
                "quantidade de módulos escuros divergiu em ${vector.name}",
            )
            assertEquals(
                vector.fingerprint,
                QrReferenceVectors.fingerprintOf(flat),
                "impressão digital da matriz divergiu em ${vector.name}",
            )
        }
    }

    @Test
    fun `matriz completa bate modulo a modulo nos vetores com grade publicada`() {
        val withRows = QrReferenceVectors.vectors.filter { it.rows.isNotEmpty() }
        assertTrue(withRows.size >= 4, "esperado ao menos 4 vetores com matriz completa")

        for (vector in withRows) {
            val qr = encodeQrOrNull(
                text = vector.text,
                errorCorrection = vector.level,
                forcedMask = vector.mask,
            )!!

            for (y in 0 until vector.size) {
                for (x in 0 until vector.size) {
                    val expected = vector.rows[y][x] == '1'
                    val actual = qr.isDark(x + qr.quietZone, y + qr.quietZone)
                    assertEquals(
                        expected,
                        actual,
                        "módulo divergente em ${vector.name} @ ($x, $y)",
                    )
                }
            }
        }
    }

    @Test
    fun `os vetores cobrem versao pequena, com informacao de versao e multi-bloco`() {
        // A cobertura só vale se os vetores exercitarem os caminhos que costumam quebrar.
        val versions = QrReferenceVectors.vectors.map { it.version }
        assertTrue(versions.any { it == 1 }, "falta versão 1")
        assertTrue(versions.any { it >= 7 }, "falta versão >= 7 (informação de versão BCH 18,6)")
        assertTrue(versions.any { it >= 14 }, "falta versão com blocos curtos e longos")

        val modes = QrReferenceVectors.vectors.map { vector ->
            encodeQrOrNull(vector.text, vector.level, forcedMask = vector.mask)!!.mode
        }
        assertTrue(QrMode.Numeric in modes && QrMode.Alphanumeric in modes && QrMode.Byte in modes)

        val levels = QrReferenceVectors.vectors.map { it.level }.toSet()
        assertEquals(4, levels.size, "os quatro níveis de correção têm de estar cobertos")
    }

    private fun flatten(qr: QrCode): String = buildString(qr.symbolSize * qr.symbolSize) {
        for (y in 0 until qr.symbolSize) {
            for (x in 0 until qr.symbolSize) {
                append(if (qr.isDark(x + qr.quietZone, y + qr.quietZone)) '1' else '0')
            }
        }
    }
}
