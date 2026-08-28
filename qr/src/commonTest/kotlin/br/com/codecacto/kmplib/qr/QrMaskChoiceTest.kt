package br.com.codecacto.kmplib.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A **escolha** da máscara: a de menor penalidade entre as 8, pelas 4 regras do padrão.
 *
 * Separado de `QrReferenceMatrixTest` (que força a máscara) porque aqui a comparação com terceiros
 * tem um limite legítimo: a regra 4 do padrão admite duas leituras, e implementações populares usam a
 * variante `|ceil(p/5) − 10|`, que cobra penalidade já em 55,0% de módulos escuros — a Tabela 11 do
 * ISO considera 45%–55% equilibrado. A kmplib segue a tabela.
 *
 * Consequência prática, e é por isso que a divergência é aceitável: **penalidade é heurística de
 * qualidade, não de correção**. Qualquer uma das 8 máscaras produz símbolo válido e decodificável; a
 * escolha só afeta o quão "limpo" o símbolo fica para a câmera.
 */
class QrMaskChoiceTest {

    @Test
    fun `a mascara escolhida e a de menor penalidade entre as oito`() {
        val texts = listOf(
            "01234567",
            "HELLO WORLD",
            "https://codecacto.com.br",
            "Cofre do Café — 12 plaquinhas",
            "x".repeat(120),
        )

        for (text in texts) {
            for (level in QrErrorCorrection.entries) {
                val automatic = encodeQrOrNull(text, level)!!
                val penalties = (0 until QrMask.COUNT).map { mask ->
                    val forced = encodeQrOrNull(text, level, forcedMask = mask)!!
                    mask to penaltyOf(forced)
                }
                val best = penalties.minBy { it.second }
                assertEquals(
                    best.second,
                    penaltyOf(automatic),
                    "máscara automática não tem a menor penalidade em '$text' / $level",
                )
                // Empate é possível; a lib escolhe a de menor índice, o que a torna determinística.
                val tiedLowest = penalties.filter { it.second == best.second }.minOf { it.first }
                assertEquals(tiedLowest, automatic.mask, "desempate deve ser pelo menor índice")
            }
        }
    }

    @Test
    fun `versao escolhida coincide com a implementacao independente em todos os casos`() {
        // A versão NÃO é heurística: sai da tabela de capacidade do padrão. Divergir aqui seria
        // defeito de tabela, e é o que este teste protege.
        for (choice in QrReferenceVectors.autoChoices) {
            val qr = encodeQrOrNull(choice.text, choice.level)!!
            assertEquals(choice.version, qr.version, "versão divergiu em ${choice.name}")
        }
    }

    @Test
    fun `a escolha de mascara concorda com a referencia na maioria dos casos`() {
        // Mede a concordância em vez de exigi-la: divergência vinda da regra 4 é esperada e inofensiva
        // (ver KDoc da classe). O teste falha se a concordância despencar — sinal de que alguma das
        // outras três regras foi quebrada, e essas NÃO admitem duas leituras.
        val total = QrReferenceVectors.autoChoices.size
        val agreements = QrReferenceVectors.autoChoices.count { choice ->
            encodeQrOrNull(choice.text, choice.level)!!.mask == choice.mask
        }
        assertTrue(
            agreements * 2 >= total,
            "concordância de máscara caiu para $agreements/$total — suspeitar das regras 1 a 3",
        )
    }

    @Test
    fun `mascara forcada e respeitada e valor invalido e recusado`() {
        for (mask in 0 until QrMask.COUNT) {
            assertEquals(mask, encodeQrOrNull("CODECACTO", forcedMask = mask)!!.mask)
        }
        assertFailsWithIllegalArgument { encodeQr("CODECACTO", forcedMask = 8) }
        assertFailsWithIllegalArgument { encodeQr("CODECACTO", forcedMask = -1) }
    }

    private fun penaltyOf(qr: QrCode): Int {
        val size = qr.symbolSize
        val dark = BooleanArray(size * size) { index ->
            val x = index % size
            val y = index / size
            qr.isDark(x + qr.quietZone, y + qr.quietZone)
        }
        return QrMask.penalty(dark, size)
    }

    private inline fun assertFailsWithIllegalArgument(block: () -> Unit) {
        val threw = try {
            block()
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        assertTrue(threw, "esperado IllegalArgumentException")
    }
}
