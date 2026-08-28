package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenBrightnessStateTest {

    @Test
    fun estadoInicial_semOverrideESemLeitura() {
        val state = ScreenBrightnessState.UNKNOWN
        assertFalse(state.isOverridden)
        assertEquals(ScreenBrightnessLevel.UNKNOWN, state.effective)
    }

    @Test
    fun comOverride_oEfetivoEODoApp() {
        val state = ScreenBrightnessState(overrideLevel = 1f, systemLevel = 0.4f)
        assertTrue(state.isOverridden)
        assertEquals(1f, state.effective)
    }

    @Test
    fun semOverride_oEfetivoEODoSistema() {
        val state = ScreenBrightnessState(systemLevel = 0.4f)
        assertFalse(state.isOverridden)
        assertEquals(0.4f, state.effective)
    }

    /** `0f` é "no mínimo", não "sem override" — quem confunde os dois some com o modo noturno. */
    @Test
    fun brilhoZero_eOverrideValido() {
        val state = ScreenBrightnessState(overrideLevel = 0f, systemLevel = 0.8f)
        assertTrue(state.isOverridden)
        assertEquals(0f, state.effective)
    }
}
