package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSliderDefaultsTest {

    @Test
    fun degrausSaoOsPontosINTERMEDIARIOS_naoAsPosicoes() {
        // 0..10 de 1 em 1 = 11 posições = 9 pontos entre os extremos.
        assertEquals(9, AppSliderDefaults.stepsFor(0f..10f, increment = 1f))
    }

    @Test
    fun incrementoFracionario() {
        // 0,5 Hz a 10 Hz, de 0,5 em 0,5 = 19 intervalos = 18 pontos intermediários.
        assertEquals(18, AppSliderDefaults.stepsFor(0.5f..10f, increment = 0.5f))
    }

    @Test
    fun incrementoIgualAFaixa_naoTemPontoIntermediario() {
        assertEquals(0, AppSliderDefaults.stepsFor(0f..1f, increment = 1f))
    }

    @Test
    fun incrementoInvalido_viraFaixaContinua() {
        assertEquals(0, AppSliderDefaults.stepsFor(0f..10f, increment = 0f))
        assertEquals(0, AppSliderDefaults.stepsFor(0f..10f, increment = -2f))
    }

    @Test
    fun faixaDegenerada_viraFaixaContinua() {
        assertEquals(0, AppSliderDefaults.stepsFor(5f..5f, increment = 1f))
    }

    @Test
    fun incrementoMaiorQueAFaixa_naoDevolveNegativo() {
        assertEquals(0, AppSliderDefaults.stepsFor(0f..1f, increment = 5f))
    }
}
