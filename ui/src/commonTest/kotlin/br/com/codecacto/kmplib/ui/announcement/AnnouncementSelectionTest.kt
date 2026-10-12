@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class AnnouncementSelectionTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    private fun a(
        id: String,
        kind: AnnouncementKind = AnnouncementKind.INFO,
        frequency: AnnouncementFrequency = AnnouncementFrequency.ONCE,
        visibleFrom: Instant? = null,
        visibleUntil: Instant? = null,
        window: AnnouncementWindow? = null,
        dismissible: Boolean = true,
        title: String = "T-$id",
    ) = Announcement(id, kind, title, window = window, visibleFrom = visibleFrom, visibleUntil = visibleUntil, frequency = frequency, dismissible = dismissible)

    @Test
    fun prioridade_maintenance_update_crossplatform_news_info() {
        val list = listOf(
            a("info", AnnouncementKind.INFO),
            a("news", AnnouncementKind.NEWS),
            a("cross", AnnouncementKind.CROSS_PLATFORM),
            a("update", AnnouncementKind.UPDATE),
            a("maint", AnnouncementKind.MAINTENANCE),
        )
        val order = mutableListOf<String>()
        val dismissed = mutableSetOf<String>()
        while (true) {
            val pick = selectAnnouncement(list, now, dismissedThisSession = dismissed) ?: break
            order += pick.id
            dismissed += pick.id
        }
        assertEquals(listOf("maint", "update", "cross", "news", "info"), order)
    }

    @Test
    fun empate_de_tipo_vale_a_ordem_da_lista_e_id_repetido_vale_o_primeiro() {
        val list = listOf(a("b", AnnouncementKind.NEWS), a("a", AnnouncementKind.NEWS), a("b", AnnouncementKind.MAINTENANCE))
        assertEquals("b", selectAnnouncement(list, now)!!.id)
        assertEquals(AnnouncementKind.NEWS, selectAnnouncement(list, now)!!.kind)
    }

    @Test
    fun janela_de_visibilidade_inclui_inicio_e_exclui_fim() {
        val x = a("x", visibleFrom = now, visibleUntil = now + 1.hours)
        assertTrue(x.isVisibleAt(now))
        assertFalse(x.isVisibleAt(now - 1.hours))
        assertFalse(x.isVisibleAt(now + 1.hours))
        assertNull(selectAnnouncement(listOf(x), now + 2.hours))
    }

    @Test
    fun sem_visible_until_o_aviso_some_ao_fim_da_janela_do_evento() {
        val w = AnnouncementWindow(now + 1.hours, now + 3.hours)
        val x = a("m", AnnouncementKind.MAINTENANCE, window = w)
        assertEquals(now + 3.hours, x.effectiveVisibleUntil)
        assertTrue(x.isVisibleAt(now))
        assertTrue(x.isVisibleAt(now + 2.hours))
        assertFalse(x.isVisibleAt(now + 3.hours))
        // visibleUntil explícito vence a janela
        assertEquals(now + 10.hours, x.copy(visibleUntil = now + 10.hours).effectiveVisibleUntil)
        // janela invertida não vira prazo
        assertNull(a("i", window = AnnouncementWindow(now + 3.hours, now + 1.hours)).effectiveVisibleUntil)
    }

    @Test
    fun aviso_fora_da_janela_cede_a_vez_ao_de_menor_prioridade() {
        val list = listOf(
            a("maint", AnnouncementKind.MAINTENANCE, visibleFrom = now + 1.hours),
            a("news", AnnouncementKind.NEWS),
        )
        assertEquals("news", selectAnnouncement(list, now)!!.id)
        assertEquals("maint", selectAnnouncement(list, now + 1.hours)!!.id)
    }

    @Test
    fun frequencia_once() {
        val x = a("x", frequency = AnnouncementFrequency.ONCE)
        assertTrue(x.isPending(AnnouncementRecord.NONE))
        // visto em sessão anterior: não volta
        assertFalse(x.isPending(AnnouncementRecord(seenAt = now)))
        // visto nesta sessão: continua até o fim dela
        assertTrue(x.isPending(AnnouncementRecord(seenAt = now), shownThisSession = setOf("x")))
        // dispensado: some
        assertFalse(x.isPending(AnnouncementRecord.NONE, dismissedThisSession = setOf("x")))
        assertFalse(x.isPending(AnnouncementRecord(dismissedAt = now), shownThisSession = setOf("x")))
    }

    @Test
    fun frequencia_until_dismissed() {
        val x = a("x", frequency = AnnouncementFrequency.UNTIL_DISMISSED)
        assertTrue(x.isPending(AnnouncementRecord(seenAt = now)))
        assertFalse(x.isPending(AnnouncementRecord(seenAt = now, dismissedAt = now)))
    }

    @Test
    fun frequencia_always_ignora_historico_e_so_some_na_sessao() {
        val x = a("x", frequency = AnnouncementFrequency.ALWAYS)
        assertTrue(x.isPending(AnnouncementRecord(seenAt = now, dismissedAt = now)))
        assertFalse(x.isPending(AnnouncementRecord.NONE, dismissedThisSession = setOf("x")))
    }

    @Test
    fun nao_dispensavel_ignora_dispensa() {
        val x = a("x", frequency = AnnouncementFrequency.UNTIL_DISMISSED, dismissible = false)
        assertTrue(x.isPending(AnnouncementRecord(dismissedAt = now), dismissedThisSession = setOf("x")))
    }

    @Test
    fun titulo_em_branco_nao_aparece() {
        assertNull(selectAnnouncement(listOf(a("x", title = " ")), now))
    }

    @Test
    fun proxima_mudanca_e_o_menor_instante_futuro() {
        val list = listOf(
            a("a", visibleFrom = now - 1.hours, visibleUntil = now + 5.hours),
            a("b", window = AnnouncementWindow(now + 2.hours, now + 4.hours)),
            a("c", visibleFrom = now + 3.hours),
        )
        assertEquals(now + 2.hours, nextAnnouncementChange(list, now))
        assertEquals(now + 3.hours, nextAnnouncementChange(list, now + 2.hours))
        assertNull(nextAnnouncementChange(list, now + 5.hours))
        assertNull(nextAnnouncementChange(emptyList(), now))
    }

    @Test
    fun descricao_da_janela_no_fuso_do_aparelho() {
        val sp = TimeZone.of("America/Sao_Paulo")
        // 18/10 22:00 → 19/10 02:00 em São Paulo (UTC-3)
        val w = AnnouncementWindow(Instant.parse("2026-10-19T01:00:00Z"), Instant.parse("2026-10-19T05:00:00Z"))
        val before = describeAnnouncementWindow(w, now, sp)
        assertEquals(AnnouncementWindowPhase.UPCOMING, before.phase)
        assertEquals(22, before.start.hour)
        assertEquals(18, before.start.day)
        assertEquals(2, before.end.hour)
        assertFalse(before.sameDay)
        assertTrue(before.sameYearAsNow)
        assertEquals(AnnouncementWindowPhase.ONGOING, describeAnnouncementWindow(w, Instant.parse("2026-10-19T02:00:00Z"), sp).phase)
        assertEquals(AnnouncementWindowPhase.ENDED, describeAnnouncementWindow(w, Instant.parse("2026-10-19T05:00:00Z"), sp).phase)
        // em UTC a mesma janela cabe num dia só
        assertTrue(describeAnnouncementWindow(w, now, TimeZone.UTC).sameDay)
        // virada de ano
        val nye = AnnouncementWindow(Instant.parse("2027-01-02T10:00:00Z"), Instant.parse("2027-01-02T11:00:00Z"))
        assertFalse(describeAnnouncementWindow(nye, now, TimeZone.UTC).sameYearAsNow)
    }
}
