package br.com.codecacto.kmplib.announcement

import br.com.codecacto.kmplib.modalevents.ModalEventType
import br.com.codecacto.kmplib.ui.announcement.Announcement
import br.com.codecacto.kmplib.ui.announcement.AnnouncementEvent
import br.com.codecacto.kmplib.ui.announcement.AnnouncementEventListener
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementEventsReporterTest {

    private val recorded = mutableListOf<Pair<ModalEventType, String>>()
    private val reporter = AnnouncementEventsReporter { t, id -> recorded += t to id }

    private fun aviso(id: String) = Announcement(id = id, kind = br.com.codecacto.kmplib.ui.announcement.AnnouncementKind.INFO, title = "Manutenção")

    @Test
    fun soOQueVeioDoServidor() {
        val l = reporter.listener(remoteIds = setOf("srv-1"))
        l.onEvent(aviso("srv-1"), AnnouncementEvent.ACTION_CLICK)
        l.onEvent(aviso("local"), AnnouncementEvent.ACTION_CLICK)
        assertEquals(listOf(ModalEventType.ANNOUNCEMENT_ACTION_CLICK to "srv-1"), recorded)
    }

    @Test
    fun shownUmaVezPorSessao() {
        val l = reporter.listener(remoteIds = setOf("srv-1"))
        repeat(3) { l.onEvent(aviso("srv-1"), AnnouncementEvent.SHOWN) }
        assertEquals(1, recorded.count { it.first == ModalEventType.ANNOUNCEMENT_SHOWN })
    }

    @Test
    fun mapeiaOsQuatroEventos() {
        val l = reporter.listener(remoteIds = setOf("a"))
        AnnouncementEvent.entries.forEach { l.onEvent(aviso("a"), it) }
        assertEquals(
            listOf(
                ModalEventType.ANNOUNCEMENT_SHOWN,
                ModalEventType.ANNOUNCEMENT_ACTION_CLICK,
                ModalEventType.ANNOUNCEMENT_DISMISS_GOTIT,
                ModalEventType.ANNOUNCEMENT_DISMISS_CLOSE,
            ),
            recorded.map { it.first },
        )
    }

    @Test
    fun repassaTudoAoOuvinteDoApp() {
        val doApp = mutableListOf<String>()
        val l = reporter.listener(setOf("srv"), AnnouncementEventListener { a, _ -> doApp += a.id })
        l.onEvent(aviso("local"), AnnouncementEvent.SHOWN)
        l.onEvent(aviso("srv"), AnnouncementEvent.SHOWN)
        assertEquals(listOf("local", "srv"), doApp)
        assertEquals(1, recorded.size)
    }
}
