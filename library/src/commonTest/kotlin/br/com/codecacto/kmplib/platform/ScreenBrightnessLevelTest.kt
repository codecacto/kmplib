package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenBrightnessLevelTest {

    @Test
    fun clamp_mantemValorDentroDaFaixa() {
        assertEquals(0f, ScreenBrightnessLevel.clamp(0f))
        assertEquals(0.37f, ScreenBrightnessLevel.clamp(0.37f))
        assertEquals(1f, ScreenBrightnessLevel.clamp(1f))
    }

    @Test
    fun clamp_cortaAcimaDoMaximo() {
        assertEquals(1f, ScreenBrightnessLevel.clamp(1.5f))
        assertEquals(1f, ScreenBrightnessLevel.clamp(Float.MAX_VALUE))
        assertEquals(1f, ScreenBrightnessLevel.clamp(Float.POSITIVE_INFINITY))
    }

    /** Número ruim vira "devolve ao sistema": forçar 0f apagaria a tela; forçar 1f queima bateria. */
    @Test
    fun clamp_negativoOuNaN_devolveOControleAoSistema() {
        assertEquals(ScreenBrightnessLevel.SYSTEM, ScreenBrightnessLevel.clamp(-0.2f))
        assertEquals(ScreenBrightnessLevel.SYSTEM, ScreenBrightnessLevel.clamp(Float.NaN))
        assertEquals(ScreenBrightnessLevel.SYSTEM, ScreenBrightnessLevel.clamp(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun isOverride_separaBrilhoDeVerdadeDasSentinelas() {
        assertTrue(ScreenBrightnessLevel.isOverride(0f))
        assertTrue(ScreenBrightnessLevel.isOverride(1f))
        assertFalse(ScreenBrightnessLevel.isOverride(ScreenBrightnessLevel.SYSTEM))
        assertFalse(ScreenBrightnessLevel.isOverride(ScreenBrightnessLevel.UNKNOWN))
        assertFalse(ScreenBrightnessLevel.isOverride(Float.NaN))
    }

    @Test
    fun percent_arredondaESinalizaAusenciaComMenosUm() {
        assertEquals(0, ScreenBrightnessLevel.percent(0f))
        assertEquals(50, ScreenBrightnessLevel.percent(0.5f))
        assertEquals(37, ScreenBrightnessLevel.percent(0.366f))
        assertEquals(100, ScreenBrightnessLevel.percent(1f))
        assertEquals(-1, ScreenBrightnessLevel.percent(ScreenBrightnessLevel.UNKNOWN))
    }
}
