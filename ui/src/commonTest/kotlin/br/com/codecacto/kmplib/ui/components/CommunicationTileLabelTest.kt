package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommunicationTileLabelTest {

    // Medida fake: cada caractere ocupa 0,6 × o tamanho da fonte (px = sp, densidade 1).
    private fun widthOf(word: String, sp: Float) = word.length * sp * 0.6f

    private fun fitFor(label: String, widthPx: Float, maxSp: Float = 16f) =
        largestFittingFontSize(maxSp = maxSp, minSp = 12f, stepSp = 1f) { sp ->
            communicationTileWords(label).all { widthOf(it, sp) <= widthPx }
        }

    @Test
    fun `palavra que cabe mantem a fonte do tema`() {
        // "Sim" a 16sp mede 28,8 — cabe em 100.
        assertEquals(16f, fitFor("Sim", widthPx = 100f))
    }

    @Test
    fun `palavra longa demais reduz a fonte ate caber inteira`() {
        // "Sentimentos" (11 letras) a 16sp = 105,6 > 100; a 15sp = 99 => cabe. Nunca "Sentiment / os".
        assertEquals(15f, fitFor("Sentimentos", widthPx = 100f))
    }

    @Test
    fun `frase de varias palavras decide pela palavra MAIS longa e nao pela frase`() {
        // A frase inteira nao cabe numa linha, mas cada palavra cabe a 16sp: quebra ENTRE palavras.
        assertEquals(16f, fitFor("Quero beber agua", widthPx = 60f))
        assertEquals(listOf("Quero", "beber", "agua"), communicationTileWords("  Quero  beber agua "))
    }

    @Test
    fun `nada cabe fica no piso legivel`() {
        assertEquals(12f, fitFor("Anticonstitucionalissimamente", widthPx = 50f))
    }

    @Test
    fun `teto abaixo do piso devolve o piso`() {
        assertEquals(12f, largestFittingFontSize(maxSp = 10f, minSp = 12f, stepSp = 1f) { true })
    }

    @Test
    fun `passo zero e recusado`() {
        assertFailsWith<IllegalArgumentException> {
            largestFittingFontSize(maxSp = 16f, minSp = 12f, stepSp = 0f) { true }
        }
    }

    @Test
    fun `rotulo vazio nao tem palavra`() {
        assertEquals(emptyList(), communicationTileWords("   "))
    }
}
