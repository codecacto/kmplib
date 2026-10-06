package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomNavLabelFitTest {

    // Medida fake: cada caractere ocupa 0,6 × o tamanho da fonte (px = sp, densidade 1).
    private fun widthOf(label: String, sp: Float) = label.length * sp * 0.6f

    private fun fitFor(label: String, itemWidthPx: Float) =
        bottomNavLabelFit(maxSp = 12f, minSp = 10f, stepSp = 0.5f) { sp -> widthOf(label, sp) <= itemWidthPx }

    @Test
    fun `rotulo que cabe fica na fonte do tema sem reticencia`() {
        assertEquals(BottomNavLabelFit(12f, false), fitFor("Pátio", itemWidthPx = 90f))
    }

    @Test
    fun `Configuracoes em item estreito reduz a fonte em vez de cortar`() {
        // 13 letras: 12sp = 93,6 > 90; 11,5sp = 89,7 → cabe. Era o corte do iPhone 17 Pro.
        assertEquals(BottomNavLabelFit(11.5f, false), fitFor("Configurações", itemWidthPx = 90f))
    }

    @Test
    fun `item de 360dp com 4 abas desce ate o piso e ainda cabe`() {
        // 13 × 0,6 × 10 = 78 ≤ 80 → cabe no piso, sem reticência.
        val fit = fitFor("Configurações", itemWidthPx = 80f)
        assertEquals(10f, fit.fontSizeSp)
        assertFalse(fit.ellipsized)
    }

    @Test
    fun `abaixo do piso fica no piso e so entao usa reticencia`() {
        val fit = fitFor("Configurações avançadas", itemWidthPx = 80f)
        assertEquals(10f, fit.fontSizeSp)
        assertTrue(fit.ellipsized)
    }

    @Test
    fun `teto igual ou abaixo do piso devolve o piso`() {
        assertEquals(BottomNavLabelFit(10f, false), bottomNavLabelFit(9f, 10f, 0.5f) { true })
        assertEquals(BottomNavLabelFit(10f, true), bottomNavLabelFit(10f, 10f, 0.5f) { false })
    }

    @Test
    fun `passo zero e recusado`() {
        assertFailsWith<IllegalArgumentException> { bottomNavLabelFit(12f, 10f, 0f) { true } }
    }

    @Test
    fun `piso padrao fica na faixa legivel de rotulo`() {
        val piso = BottomNavDefaults.LabelMinFontSize.value
        assertTrue(piso in 10f..11f)
    }
}
