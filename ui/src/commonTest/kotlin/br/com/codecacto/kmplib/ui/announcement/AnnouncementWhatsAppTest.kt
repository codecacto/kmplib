package br.com.codecacto.kmplib.ui.announcement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnouncementWhatsAppTest {

    @Test
    fun `reconhece os tres hosts do WhatsApp em https`() {
        assertTrue(isWhatsAppActionUrl("https://wa.me/5565998030240"))
        assertTrue(isWhatsAppActionUrl("  https://WA.ME/5565998030240  "))
        assertTrue(isWhatsAppActionUrl("https://api.whatsapp.com/send?phone=5565998030240"))
        assertTrue(isWhatsAppActionUrl("https://web.whatsapp.com/send?phone=1"))
    }

    @Test
    fun `nao reconhece outro esquema nem outro host nem texto`() {
        assertFalse(isWhatsAppActionUrl("http://wa.me/5565998030240"))
        assertFalse(isWhatsAppActionUrl("whatsapp://send?phone=5565998030240"))
        assertFalse(isWhatsAppActionUrl("https://wa.me.evil.com/5565998030240"))
        assertFalse(isWhatsAppActionUrl("https://evil.com/?u=https://wa.me/1"))
        assertFalse(isWhatsAppActionUrl(null))
        assertFalse(isWhatsAppActionUrl(""))
    }

    @Test
    fun `numero brasileiro sai formatado`() {
        val n = whatsAppNumberFromUrl("https://wa.me/5565998030240?text=Ol%C3%A1")
        assertEquals("5565998030240", n?.digits)
        assertEquals("(65) 99803-0240", n?.display)
        assertEquals("(11) 3333-4444", whatsAppNumberFromUrl("https://wa.me/551133334444")?.display)
    }

    @Test
    fun `api whatsapp le o phone decodificado e outro pais sai com mais`() {
        assertEquals("(65) 99803-0240", whatsAppNumberFromUrl("https://api.whatsapp.com/send?text=oi&phone=%2B5565998030240")?.display)
        assertEquals("+351912345678", whatsAppNumberFromUrl("https://api.whatsapp.com/send?phone=351912345678")?.display)
    }

    @Test
    fun `sem numero ou fora de 8 a 15 algarismos devolve null`() {
        assertNull(whatsAppNumberFromUrl("https://wa.me/"))
        assertNull(whatsAppNumberFromUrl("https://wa.me/1234567"))
        assertNull(whatsAppNumberFromUrl("https://wa.me/1234567890123456"))
        assertNull(whatsAppNumberFromUrl("https://api.whatsapp.com/send?text=oi"))
        assertNull(whatsAppNumberFromUrl("https://example.com/5565998030240"))
    }

    @Test
    fun `numero do comunicado so quando a acao abre o WhatsApp`() {
        val base = Announcement(id = "w", kind = AnnouncementKind.WELCOME, title = "Oi")
        assertNull(base.whatsAppNumber)
        val comAcao = base.copy(action = AnnouncementAction("Falar no WhatsApp", "https://wa.me/5565998030240"))
        assertEquals("(65) 99803-0240", comAcao.whatsAppNumber?.display)
        assertNull(base.copy(action = AnnouncementAction("Site", "https://codecacto.com.br")).whatsAppNumber)
    }

    @Test
    fun `welcome e reconhecido no fio e tem prioridade logo depois da manutencao`() {
        val list = decodeAnnouncements("""[{"id":"w","kind":"welcome","title":"Oi"}]""")
        assertEquals(AnnouncementKind.WELCOME, list.single().kind)
        assertTrue(AnnouncementKind.MAINTENANCE.announcementPriority > AnnouncementKind.WELCOME.announcementPriority)
        assertTrue(AnnouncementKind.WELCOME.announcementPriority > AnnouncementKind.UPDATE.announcementPriority)
    }
}
