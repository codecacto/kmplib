package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShareIcsTest {

    private class Espiao : ShareHandler {
        var bytes: ByteArray? = null
        var nome: String? = null
        var mime: String? = null
        var titulo: String? = null
        override fun shareText(text: String, title: String) = Unit
        override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) = Unit
        override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) {
            bytes = fileBytes; nome = fileName; mime = mimeType; titulo = title
        }
    }

    @Test
    fun entregaUtf8ComMimeDeCalendario() {
        val espiao = Espiao()
        espiao.shareIcs("BEGIN:VCALENDAR\r\nSUMMARY:Água\r\nEND:VCALENDAR\r\n", "lua-nova.ics", "Agenda")
        assertContentEquals("BEGIN:VCALENDAR\r\nSUMMARY:Água\r\nEND:VCALENDAR\r\n".encodeToByteArray(), espiao.bytes)
        assertEquals("lua-nova.ics", espiao.nome)
        assertEquals("text/calendar", espiao.mime)
        assertEquals("Agenda", espiao.titulo)
    }

    @Test
    fun nomeGanhaExtensao() {
        assertEquals("ciclo.ics", icsFileName("ciclo"))
        assertEquals("CICLO.ICS", icsFileName("CICLO.ICS"))
        assertEquals("evento.ics", icsFileName("  "))
    }

    @Test
    fun recusaCalendarioVazio() {
        assertFailsWith<IllegalArgumentException> { Espiao().shareIcs(" ") }
    }
}
