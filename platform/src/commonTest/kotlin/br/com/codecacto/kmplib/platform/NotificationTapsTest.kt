package br.com.codecacto.kmplib.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Toque no corpo da notificação local (2.357.0): pendente até consumir, último vence, sem chave interna. */
class NotificationTapsTest {

    @AfterTest
    fun limpar() = NotificationTaps.clear()

    @Test
    fun `toque fica pendente ate o app consumir - app frio`() {
        val tap = NotificationTaps.deliver(7, mapOf("kmplib.date" to "2026-11-12", "nota" to "abc"))
        assertEquals(tap, NotificationTaps.pending.value)
        assertEquals("2026-11-12", tap.scheduledDate)
        NotificationTaps.consume(tap)
        assertNull(NotificationTaps.pending.value)
    }

    @Test
    fun `consumir um toque antigo nao apaga o novo`() {
        val antigo = NotificationTaps.deliver(1, emptyMap())
        val novo = NotificationTaps.deliver(1, emptyMap())
        assertNotEquals(antigo.sequence, novo.sequence)
        NotificationTaps.consume(antigo)
        assertEquals(novo, NotificationTaps.pending.value)
    }

    @Test
    fun `a marca interna da lib nunca chega ao app`() {
        val tap = NotificationTaps.deliver(3, mapOf(NotificationTaps.MARKER_KEY to "1", "x" to "y"))
        assertEquals(mapOf("x" to "y"), tap.data)
    }

    @Test
    fun `userInfo sem a marca nao e da lib`() {
        assertNull(NotificationTapRules.dataFromUserInfo(mapOf<Any?, Any?>("aps" to "push")))
    }

    @Test
    fun `userInfo com a marca vira data so com pares de texto`() {
        val data = NotificationTapRules.dataFromUserInfo(
            mapOf<Any?, Any?>(NotificationTaps.MARKER_KEY to "1", "kmplib.date" to "2026-11-12", "n" to 3),
        )
        assertEquals(mapOf("kmplib.date" to "2026-11-12"), data)
    }

    @Test
    fun `toString nao vaza o conteudo do data`() {
        val tap = NotificationTaps.deliver(9, mapOf("registro" to "segredo"))
        assertFalse("segredo" in tap.toString())
        assertTrue("registro" in tap.toString())
    }
}
