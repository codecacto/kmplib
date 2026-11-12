package br.com.codecacto.kmplib.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prova de origem do toque na notificação local (2.357.1): a `Activity` de abertura é exportada, então
 * só o `Intent` com o HMAC que a lib gravou vira [NotificationTap].
 */
class NotificationTapProofTest {

    private val chave = ByteArray(32) { it.toByte() }
    private val outraChave = ByteArray(32) { (it + 1).toByte() }
    private val data = mapOf("kmplib.date" to "2026-11-12", "nota" to "abc")

    @AfterTest
    fun limpar() = NotificationTaps.clear()

    private fun raw(id: Int = 7, d: Map<String, String> = data, proof: ByteArray?, fromHistory: Boolean = false) =
        NotificationTapIntents.RawTap(id, d, proof, fromHistory)

    @Test
    fun `toque legitimo com prova valida e entregue`() {
        val prova = NotificationTapProof.sign(chave, 7, data)
        assertTrue(NotificationTapIntents.accept(raw(proof = prova), chave))
        val tap = assertNotNull(NotificationTaps.pending.value)
        assertEquals(7, tap.notificationId)
        assertEquals(data, tap.data)
    }

    @Test
    fun `intent forjado sem prova e descartado`() {
        assertFalse(NotificationTapIntents.accept(raw(proof = null), chave))
        assertFalse(NotificationTapIntents.accept(raw(proof = ByteArray(0)), chave))
        assertNull(NotificationTaps.pending.value)
    }

    @Test
    fun `prova adulterada e descartada`() {
        val prova = NotificationTapProof.sign(chave, 7, data)
        val bitTrocado = prova.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(NotificationTapIntents.accept(raw(proof = bitTrocado), chave))
        // Prova verdadeira, mas data trocado por quem forjou.
        assertFalse(NotificationTapIntents.accept(raw(d = data + ("nota" to "outra"), proof = prova), chave))
        assertFalse(NotificationTapIntents.accept(raw(d = data + ("rota" to "https://x"), proof = prova), chave))
        // Prova verdadeira, outro id.
        assertFalse(NotificationTapIntents.accept(raw(id = 8, proof = prova), chave))
        // Prova feita com outra chave (outra instalação).
        assertFalse(NotificationTapIntents.accept(raw(proof = NotificationTapProof.sign(outraChave, 7, data)), chave))
        // Prova truncada.
        assertFalse(NotificationTapIntents.accept(raw(proof = prova.copyOf(16)), chave))
        assertNull(NotificationTaps.pending.value)
    }

    @Test
    fun `sem chave disponivel nada e entregue`() {
        val prova = NotificationTapProof.sign(chave, 7, data)
        assertFalse(NotificationTapIntents.accept(raw(proof = prova), null))
        assertNull(NotificationTaps.pending.value)
    }

    @Test
    fun `aberto pelos recentes nao entrega de novo mesmo com prova`() {
        val prova = NotificationTapProof.sign(chave, 7, data)
        assertFalse(NotificationTapIntents.accept(raw(proof = prova, fromHistory = true), chave))
        assertNull(NotificationTaps.pending.value)
    }

    @Test
    fun `bundle invalido nao lanca e descarta`() {
        assertNull(NotificationTapIntents.readSafely { throw RuntimeException("BadParcelableException simulada") })
        assertNull(NotificationTapIntents.readSafely { throw ClassNotFoundException("com.outro.App\$Parcelavel") })
        assertNull(NotificationTaps.pending.value)
    }

    @Test
    fun `leitura normal passa adiante`() {
        val r = raw(proof = null)
        assertEquals(r, NotificationTapIntents.readSafely { r })
    }

    @Test
    fun `codificacao canonica nao depende da ordem e nao confunde fronteiras`() {
        val a = NotificationTapProof.canonical(1, linkedMapOf("a" to "1", "b" to "2"))
        val b = NotificationTapProof.canonical(1, linkedMapOf("b" to "2", "a" to "1"))
        assertTrue(a.contentEquals(b))
        val c = NotificationTapProof.canonical(1, mapOf("ab" to "c"))
        val d = NotificationTapProof.canonical(1, mapOf("a" to "bc"))
        assertFalse(c.contentEquals(d))
    }

    @Test
    fun `verify confere a propria assinatura`() {
        assertTrue(NotificationTapProof.verify(chave, 3, emptyMap(), NotificationTapProof.sign(chave, 3, emptyMap())))
    }
}
