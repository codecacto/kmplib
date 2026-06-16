package br.com.codecacto.kmplib.monetization.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuotaExceededTest {

    @Test
    fun `parse 402 body valido`() {
        val body = """{"feature":"recibos","limite":5,"contagem":5,"upgrade_url":"https://x/up"}"""
        val q = parseQuotaExceeded(body)
        assertEquals("recibos", q?.feature)
        assertEquals(5L, q?.limite)
        assertEquals(5L, q?.contagem)
        assertEquals("https://x/up", q?.upgradeUrl)
    }

    @Test
    fun `parse tolera campos desconhecidos`() {
        val body = """{"feature":"os","limite":3,"contagem":4,"extra":"ignorar","nested":{"a":1}}"""
        val q = parseQuotaExceeded(body)
        assertEquals("os", q?.feature)
        assertEquals(3L, q?.limite)
        assertNull(q?.upgradeUrl)
    }

    @Test
    fun `parse retorna null para body invalido`() {
        assertNull(parseQuotaExceeded("not json"))
        assertNull(parseQuotaExceeded(""))
        assertNull(parseQuotaExceeded(null))
    }

    @Test
    fun `parse retorna null quando feature ausente`() {
        // sem `feature` (campo obrigatorio) -> falha de deserializacao -> null
        assertNull(parseQuotaExceeded("""{"limite":5,"contagem":5}"""))
    }

    @Test
    fun `upgradeUrl opcional ausente fica null`() {
        val q = parseQuotaExceeded("""{"feature":"f","limite":1,"contagem":1}""")
        assertEquals("f", q?.feature)
        assertNull(q?.upgradeUrl)
    }

    @Test
    fun `toUsageSnapshot converte para snapshot esgotado`() {
        val q = QuotaExceeded(feature = "recibos", limite = 5, contagem = 5)
        val s = q.toUsageSnapshot()
        assertEquals("recibos", s.feature)
        assertEquals(5L, s.limite)
        assertEquals(5L, s.contagem)
        assertEquals(true, s.isExhausted)
        assertEquals(0L, s.remaining)
    }
}
