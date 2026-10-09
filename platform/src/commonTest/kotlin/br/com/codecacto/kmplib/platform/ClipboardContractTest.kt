package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipboardContractTest {

    /** Dublê mínimo: prova que a assinatura aceita as chamadas antigas e as novas. */
    private class Memoria : Clipboard {
        var texto: String? = null
        var rotulo: String? = null
        var sensivel: Boolean? = null
        override fun copy(text: String, label: String, sensitive: Boolean) {
            texto = text; rotulo = label; sensivel = sensitive
        }
        override fun hasText(): Boolean = texto != null
        override fun readText(): String? = texto
    }

    @Test
    fun legacyCalls_stillCompile_andDefaultToNotSensitive() {
        val c = Memoria()
        c.copy("chave")
        assertEquals("Texto", c.rotulo)
        assertEquals(false, c.sensivel)
        c.copy("chave", "Chave Pix")
        assertEquals("Chave Pix", c.rotulo)
        assertEquals(false, c.sensivel)
    }

    @Test
    fun sensitiveCopy_byName() {
        val c = Memoria()
        c.copy("Xk9#2", sensitive = true)
        assertEquals(true, c.sensivel)
        assertEquals("Texto", c.rotulo)
    }

    @Test
    fun readText_andHasText() {
        val c = Memoria()
        assertFalse(c.hasText())
        assertNull(c.readText())
        c.copy("CUPOM10")
        assertTrue(c.hasText())
        assertEquals("CUPOM10", c.readText())
    }

    @Test
    fun sensitiveExpiration_isTwoMinutes() {
        assertEquals(120L, SENSITIVE_CLIP_EXPIRATION_SECONDS)
    }
}
