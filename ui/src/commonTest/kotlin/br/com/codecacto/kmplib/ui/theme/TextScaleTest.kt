@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package br.com.codecacto.kmplib.ui.theme

import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextScaleTest {

    @Test
    fun `fatores sao os do sistema da familia 17 19 21`() {
        assertEquals(1f, TextScale.STANDARD.factor)
        assertEquals(19f, TextScale.LARGE.factor * 17f, 0.0001f)
        assertEquals(21f, TextScale.LARGEST.factor * 17f, 0.0001f)
    }

    @Test
    fun `valores gravados sao estaveis`() {
        assertEquals(listOf("standard", "large", "largest"), TextScale.entries.map { it.storageValue })
    }

    @Test
    fun `fromStorage tolera lixo`() {
        assertEquals(TextScale.LARGE, TextScale.fromStorage(" LARGE "))
        assertEquals(TextScale.STANDARD, TextScale.fromStorage(null))
        assertEquals(TextScale.STANDARD, TextScale.fromStorage("gigante"))
    }

    @Test
    fun `degraus saturam nas pontas`() {
        assertEquals(TextScale.STANDARD, TextScale.STANDARD.smaller())
        assertEquals(TextScale.LARGEST, TextScale.LARGEST.larger())
        assertEquals(TextScale.LARGE, TextScale.STANDARD.larger())
        assertFalse(TextScale.STANDARD.canDecrease)
        assertFalse(TextScale.LARGEST.canIncrease)
        assertEquals(listOf(1, 2, 3), TextScale.entries.map { it.step })
        assertEquals(3, TextScale.STEPS)
    }

    @Test
    fun `controlador carrega o gravado e grava a escolha`() = runTest {
        val prefs = InMemoryAppPreferences().apply { setString(TextScaleController.DEFAULT_KEY, "largest") }
        val controller = TextScaleController(prefs, backgroundScope)
        assertEquals(TextScale.STANDARD, controller.scale.value)
        runCurrent()
        assertEquals(TextScale.LARGEST, controller.scale.value)
        assertTrue(controller.isLoaded.value)
        controller.decrease()
        runCurrent()
        assertEquals(TextScale.LARGE, controller.scale.value)
        assertEquals("large", prefs.getString(TextScaleController.DEFAULT_KEY, ""))
        controller.increase(); controller.increase()
        runCurrent()
        assertEquals(TextScale.LARGEST, controller.currentScale)
    }

    @Test
    fun `escolha antes da leitura vence a leitura`() = runTest {
        val prefs = InMemoryAppPreferences().apply { setString(TextScaleController.DEFAULT_KEY, "largest") }
        val controller = TextScaleController(prefs, backgroundScope)
        controller.setScale(TextScale.LARGE)
        runCurrent()
        assertEquals(TextScale.LARGE, controller.scale.value)
    }
}
