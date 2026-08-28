package br.com.codecacto.kmplib.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushPayloadTest {

    @Test
    fun readsCustomTitleAndBodyKeys() {
        val data = mapOf("title" to "Oi", "body" to "Tudo certo")
        assertEquals("Oi", PushPayload.title(data))
        assertEquals("Tudo certo", PushPayload.body(data))
    }

    @Test
    fun fallsBackToApsAlertKeys() {
        val data = mapOf("aps.alert.title" to "Alerta", "aps.alert.body" to "Detalhe")
        assertEquals("Alerta", PushPayload.title(data))
        assertEquals("Detalhe", PushPayload.body(data))
    }

    @Test
    fun customKeyWinsOverApsFallback() {
        val data = mapOf(
            "title" to "Custom",
            "aps.alert.title" to "Aps",
        )
        assertEquals("Custom", PushPayload.title(data))
    }

    @Test
    fun bodyAcceptsMessageKey() {
        assertEquals("Mensagem", PushPayload.body(mapOf("message" to "Mensagem")))
    }

    @Test
    fun blankAndMissingValuesYieldNull() {
        assertNull(PushPayload.title(mapOf("title" to "   ")))
        assertNull(PushPayload.body(emptyMap()))
    }
}
