package br.com.codecacto.kmplib.platform.links

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncomingLinksTest {

    @AfterTest
    fun limpar() = IncomingLinks.reset()

    @Test
    fun `aceita https e guarda aparado`() {
        assertTrue(IncomingLinks.deliver("  https://mirassolconectado.com.br/parceiros/1 "))
        assertEquals("https://mirassolconectado.com.br/parceiros/1", IncomingLinks.pending.value)
    }

    @Test
    fun `recusa o que nao e http - esquema do login do Google, texto e nulo`() {
        assertFalse(IncomingLinks.deliver("com.googleusercontent.apps.123:/oauth2redirect?code=x"))
        assertFalse(IncomingLinks.deliver("parceiros/1"))
        assertFalse(IncomingLinks.deliver(null))
        assertFalse(IncomingLinks.deliver("https://"))
        assertNull(IncomingLinks.pending.value)
    }

    @Test
    fun `o mais recente vence`() {
        IncomingLinks.deliver("https://a.com/1")
        IncomingLinks.deliver("https://a.com/2")
        assertEquals("https://a.com/2", IncomingLinks.pending.value)
    }

    @Test
    fun `consumir o anterior nao apaga um link novo`() {
        IncomingLinks.deliver("https://a.com/1")
        IncomingLinks.deliver("https://a.com/2")
        IncomingLinks.consume("https://a.com/1")
        assertEquals("https://a.com/2", IncomingLinks.pending.value)
        IncomingLinks.consume("https://a.com/2")
        assertNull(IncomingLinks.pending.value)
    }

    @Test
    fun `belongsTo compara host sem caixa e com www equivalente`() {
        val hosts = setOf("mirassolconectado.com.br")
        assertTrue(IncomingLinks.belongsTo("https://MirassolConectado.com.br/parceiros/1", hosts))
        assertTrue(IncomingLinks.belongsTo("https://www.mirassolconectado.com.br/parceiros/1", hosts))
        assertFalse(IncomingLinks.belongsTo("https://mirassolconectado.com.br.evil.com/parceiros/1", hosts))
        assertFalse(IncomingLinks.belongsTo("https://outro.com/parceiros/1", hosts))
    }
}
