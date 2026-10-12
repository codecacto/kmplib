@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class AnnouncementControllerTest {

    private class FakeClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val t0 = Instant.parse("2026-10-18T12:00:00Z")

    private fun a(
        id: String,
        kind: AnnouncementKind = AnnouncementKind.NEWS,
        frequency: AnnouncementFrequency = AnnouncementFrequency.ONCE,
        dismissible: Boolean = true,
        visibleFrom: Instant? = null,
        window: AnnouncementWindow? = null,
    ) = Announcement(id, kind, "T-$id", window = window, visibleFrom = visibleFrom, frequency = frequency, dismissible = dismissible)

    @Test
    fun once_fica_a_sessao_inteira_e_nao_volta_na_seguinte() = runTest {
        val store = InMemoryAnnouncementStore()
        val list = listOf(a("x"))
        val s1 = AnnouncementController(store, FakeClock(t0))
        s1.submit(list)
        assertEquals("x", s1.current.value?.id)
        assertEquals(t0, store.read("x").seenAt)
        s1.submit(list) // a lista chega de novo (ex.: do servidor) na mesma sessão
        assertEquals("x", s1.current.value?.id)

        val s2 = AnnouncementController(store, FakeClock(t0 + 1.hours))
        s2.submit(list)
        assertNull(s2.current.value)
    }

    @Test
    fun until_dismissed_volta_ate_ser_dispensado() = runTest {
        val store = InMemoryAnnouncementStore()
        val list = listOf(a("x", frequency = AnnouncementFrequency.UNTIL_DISMISSED))
        AnnouncementController(store, FakeClock(t0)).submit(list)
        val s2 = AnnouncementController(store, FakeClock(t0))
        s2.submit(list)
        assertEquals("x", s2.current.value?.id)
        s2.dismiss("x")
        assertNull(s2.current.value)
        assertEquals(t0, store.read("x").dismissedAt)
        val s3 = AnnouncementController(store, FakeClock(t0))
        s3.submit(list)
        assertNull(s3.current.value)
    }

    @Test
    fun always_volta_em_toda_sessao_e_o_x_vale_so_para_a_sessao() = runTest {
        val store = InMemoryAnnouncementStore()
        val list = listOf(a("x", frequency = AnnouncementFrequency.ALWAYS))
        val s1 = AnnouncementController(store, FakeClock(t0))
        s1.submit(list)
        s1.dismiss("x")
        assertNull(s1.current.value)
        assertNull(store.read("x").dismissedAt)
        val s2 = AnnouncementController(store, FakeClock(t0))
        s2.submit(list)
        assertEquals("x", s2.current.value?.id)
    }

    @Test
    fun nao_dispensavel_ignora_o_dismiss() = runTest {
        val store = InMemoryAnnouncementStore()
        val c = AnnouncementController(store, FakeClock(t0))
        c.submit(listOf(a("x", dismissible = false)))
        c.dismiss("x")
        assertEquals("x", c.current.value?.id)
        assertNull(store.read("x").dismissedAt)
    }

    @Test
    fun dispensar_passa_ao_proximo_pela_prioridade() = runTest {
        val c = AnnouncementController(InMemoryAnnouncementStore(), FakeClock(t0))
        c.submit(listOf(a("news"), a("update", AnnouncementKind.UPDATE), a("maint", AnnouncementKind.MAINTENANCE)))
        assertEquals("maint", c.current.value?.id)
        c.dismiss("maint")
        assertEquals("update", c.current.value?.id)
        c.dismiss("update")
        assertEquals("news", c.current.value?.id)
        c.dismiss("news")
        assertNull(c.current.value)
        c.dismiss("inexistente") // não quebra
    }

    @Test
    fun relogio_avanca_e_reevaluate_troca_o_aviso() = runTest {
        val clock = FakeClock(t0)
        val c = AnnouncementController(InMemoryAnnouncementStore(), clock)
        val maint = a(
            "maint",
            AnnouncementKind.MAINTENANCE,
            frequency = AnnouncementFrequency.UNTIL_DISMISSED,
            visibleFrom = t0 + 1.hours,
            window = AnnouncementWindow(t0 + 2.hours, t0 + 3.hours),
        )
        c.submit(listOf(maint, a("news")))
        assertEquals("news", c.current.value?.id)
        assertEquals(t0 + 1.hours, c.nextChangeAt())
        clock.instant = t0 + 1.hours
        c.reevaluate()
        assertEquals("maint", c.current.value?.id)
        assertEquals(t0 + 1.hours, c.now.value)
        clock.instant = t0 + 3.hours
        c.reevaluate()
        assertEquals("news", c.current.value?.id) // a manutenção acabou; o ONCE visto nesta sessão continua
        assertNull(c.nextChangeAt())
    }

    @Test
    fun persistencia_nas_preferencias_da_lib_sobrevive_entre_instancias() = runTest {
        val prefs = InMemoryAppPreferences()
        val list = listOf(a("x"), a("y", frequency = AnnouncementFrequency.UNTIL_DISMISSED, kind = AnnouncementKind.UPDATE))

        val s1 = AnnouncementController(PreferencesAnnouncementStore(prefs), FakeClock(t0))
        s1.submit(list)
        assertEquals("y", s1.current.value?.id)
        s1.dismiss("y")
        assertEquals("x", s1.current.value?.id)

        val reopened = PreferencesAnnouncementStore(prefs)
        assertEquals(t0, reopened.read("y").dismissedAt)
        assertEquals(t0, reopened.read("x").seenAt)
        val s2 = AnnouncementController(reopened, FakeClock(t0 + 1.hours))
        s2.submit(list)
        assertNull(s2.current.value)

        reopened.forget("x")
        val s3 = AnnouncementController(reopened, FakeClock(t0 + 2.hours))
        s3.submit(list)
        assertEquals("x", s3.current.value?.id)
    }

    @Test
    fun visto_nao_e_sobrescrito_e_prefixo_isola_as_chaves() = runTest {
        val prefs = InMemoryAppPreferences()
        val store = PreferencesAnnouncementStore(prefs)
        store.markSeen("x", t0)
        store.markSeen("x", t0 + 5.hours)
        assertEquals(t0, store.read("x").seenAt)
        assertTrue(prefs.has("kmplib.announcement.x.seen"))
        assertEquals(AnnouncementRecord.NONE, PreferencesAnnouncementStore(prefs, keyPrefix = "outro.").read("x"))
    }
}
