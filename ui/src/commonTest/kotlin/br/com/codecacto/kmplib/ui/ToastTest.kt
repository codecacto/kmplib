package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.components.ToastData
import br.com.codecacto.kmplib.ui.components.ToastType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes para Toast system
 */
class ToastTest {

    @Test
    fun `ToastType has all variants`() {
        val types = ToastType.entries
        assertEquals(4, types.size)
        assertTrue(ToastType.SUCCESS in types)
        assertTrue(ToastType.ERROR in types)
        assertTrue(ToastType.WARNING in types)
        assertTrue(ToastType.INFO in types)
    }

    @Test
    fun `ToastData default type is INFO`() {
        val toast = ToastData(message = "Test")
        assertEquals(ToastType.INFO, toast.type)
    }

    @Test
    fun `ToastData default duration is 3000ms`() {
        val toast = ToastData(message = "Test")
        assertEquals(3000L, toast.duration)
    }

    @Test
    fun `ToastData can be created with custom values`() {
        val toast = ToastData(
            message = "Custom message",
            type = ToastType.ERROR,
            duration = 5000L
        )
        assertEquals("Custom message", toast.message)
        assertEquals(ToastType.ERROR, toast.type)
        assertEquals(5000L, toast.duration)
    }
}
