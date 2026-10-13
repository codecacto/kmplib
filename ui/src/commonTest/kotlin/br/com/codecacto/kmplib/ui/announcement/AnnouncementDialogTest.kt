@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class AnnouncementDialogTest {

    private class FakeClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val t0 = Instant.parse("2026-10-17T12:00:00Z")

    private fun a(
        id: String,
        kind: AnnouncementKind = AnnouncementKind.NEWS,
        frequency: AnnouncementFrequency = AnnouncementFrequency.ONCE,
        dismissible: Boolean = true,
        visibleFrom: Instant? = null,
        visibleUntil: Instant? = null,
        window: AnnouncementWindow? = null,
        presentation: AnnouncementPresentation = AnnouncementPresentation.MODAL,
    ) = Announcement(
        id = id, kind = kind, title = "T-$id", window = window, visibleFrom = visibleFrom,
        visibleUntil = visibleUntil, frequency = frequency, dismissible = dismissible, presentation = presentation,
    )

    private fun controller(store: AnnouncementStore, clock: Clock, session: AnnouncementDialogSession = AnnouncementDialogSession()) =
        AnnouncementDialogController(store, clock, session)

    // ─────────────── Fila ───────────────

    @Test
    fun fila_um_por_vez_por_prioridade_com_contador() = runTest {
        val c = controller(InMemoryAnnouncementStore(), FakeClock(t0))
        c.submit(listOf(a("n", AnnouncementKind.NEWS), a("u", AnnouncementKind.UPDATE), a("i", AnnouncementKind.INFO)))
        assertEquals("u", c.state.value.current?.id)
        assertEquals(1, c.state.value.position)
        assertEquals(3, c.state.value.total)
        assertTrue(c.state.value.showsCounter)
        c.dismiss("u")
        assertEquals("n", c.state.value.current?.id)
        assertEquals(2 to 3, c.state.value.position to c.state.value.total)
        c.dismiss("n")
        assertEquals("i", c.state.value.current?.id)
        assertEquals(3 to 3, c.state.value.position to c.state.value.total)
        c.dismiss("i")
        assertNull(c.state.value.current)
    }

    @Test
    fun um_so_nao_mostra_contador() = runTest {
        val c = controller(InMemoryAnnouncementStore(), FakeClock(t0))
        c.submit(listOf(a("x")))
        assertFalse(c.state.value.showsCounter)
    }

    @Test
    fun empate_de_tipo_vence_visibleFrom_mais_recente() {
        val q = selectAnnouncementDialogQueue(
            listOf(a("velho", visibleFrom = t0 - 3.days), a("novo", visibleFrom = t0 - 1.days), a("sem")),
            now = t0,
        )
        assertEquals(listOf("novo", "velho", "sem"), q.map { it.id })
    }

    @Test
    fun banner_nao_entra_na_fila_do_modal() = runTest {
        val c = controller(InMemoryAnnouncementStore(), FakeClock(t0))
        c.submit(listOf(a("b", presentation = AnnouncementPresentation.BANNER)))
        assertNull(c.state.value.current)
    }

    // ─────────────── Teto por sessão ───────────────

    @Test
    fun teto_de_3_por_sessao_o_excedente_fica_para_a_proxima() = runTest {
        val store = InMemoryAnnouncementStore()
        val session = AnnouncementDialogSession()
        val list = (1..5).map { a("a$it") }
        val c = controller(store, FakeClock(t0), session)
        c.submit(list)
        assertEquals(3, c.state.value.total)
        repeat(3) { c.dismiss(c.state.value.current!!.id) }
        assertNull(c.state.value.current)
        assertEquals(3, session.shownCount)

        // Outra tela na MESMA sessão não fura o teto.
        val outra = controller(store, FakeClock(t0), session)
        outra.submit(list)
        assertNull(outra.state.value.current)

        // Próxima sessão: os dois que sobraram.
        val proxima = controller(store, FakeClock(t0 + 1.hours))
        proxima.submit(list)
        assertEquals(2, proxima.state.value.total)
    }

    @Test
    fun teto_customizado_e_bloqueante_passa_por_cima() {
        val win = AnnouncementWindow(t0 - 1.hours, t0 + 1.hours)
        val q = selectAnnouncementDialogQueue(
            listOf(a("n1"), a("m", AnnouncementKind.MAINTENANCE, dismissible = false, window = win)),
            now = t0,
            shownThisSession = setOf("x", "y", "z"),
            maxPerSession = 3,
        )
        assertEquals(listOf("m"), q.map { it.id })
    }

    // ─────────────── Não fechável e janela ───────────────

    private val win = AnnouncementWindow(Instant.parse("2026-10-18T01:00:00Z"), Instant.parse("2026-10-18T05:00:00Z"))

    @Test
    fun nao_fechavel_so_bloqueia_durante_a_janela() {
        val m = a("m", AnnouncementKind.MAINTENANCE, dismissible = false, window = win)
        assertFalse(m.isBlockingAt(win.start - 1.minutes))
        assertTrue(m.isDialogDismissibleAt(win.start - 1.minutes))
        assertTrue(m.isBlockingAt(win.start))
        assertTrue(m.isBlockingAt(win.end - 1.minutes))
        assertFalse(m.isBlockingAt(win.end))
        assertFalse(a("d", dismissible = true, window = win).isBlockingAt(win.start))
    }

    @Test
    fun nao_fechavel_sem_janela_so_bloqueia_com_data_final() {
        assertFalse(a("x", dismissible = false).isBlockingAt(t0))
        assertTrue(a("x", dismissible = false, visibleUntil = t0 + 1.hours).isBlockingAt(t0))
        assertFalse(a("x", dismissible = false, visibleUntil = t0 + 1.hours).isBlockingAt(t0 + 2.hours))
    }

    @Test
    fun antes_da_janela_fecha_por_sessao_sem_gravar_e_volta_ao_comecar() = runTest {
        val store = InMemoryAnnouncementStore()
        val clock = FakeClock(win.start - 1.days)
        val session = AnnouncementDialogSession()
        val c = controller(store, clock, session)
        val m = a("m", AnnouncementKind.MAINTENANCE, dismissible = false, window = win)
        c.submit(listOf(m))
        assertEquals("m", c.state.value.current?.id)
        assertTrue(c.state.value.dismissible)
        c.dismiss("m")
        assertNull(c.state.value.current)
        assertNull(store.read("m").dismissedAt) // nada gravado

        // Próxima sessão, ainda antes: volta (fechável por sessão).
        val s2 = controller(store, clock)
        s2.submit(listOf(m))
        assertEquals("m", s2.state.value.current?.id)

        // A manutenção começa: volta na MESMA sessão, agora bloqueando.
        clock.instant = win.start + 1.minutes
        c.reevaluate()
        assertEquals("m", c.state.value.current?.id)
        assertFalse(c.state.value.dismissible)
        c.dismiss("m") // ignorado
        assertEquals("m", c.state.value.current?.id)

        // Terminou: some sozinho.
        clock.instant = win.end
        c.reevaluate()
        assertNull(c.state.value.current)
    }

    @Test
    fun bloqueante_vem_primeiro_e_ignora_frequencia_ja_vista() = runTest {
        val store = InMemoryAnnouncementStore()
        store.markSeen("m", t0 - 1.days)
        val clock = FakeClock(win.start + 10.minutes)
        val c = controller(store, clock)
        c.submit(listOf(a("u", AnnouncementKind.UPDATE), a("m", AnnouncementKind.INFO, dismissible = false, window = win)))
        assertEquals("m", c.state.value.current?.id)
    }

    @Test
    fun fechavel_grava_a_dispensa_e_always_nao() = runTest {
        val store = InMemoryAnnouncementStore()
        val c = controller(store, FakeClock(t0))
        c.submit(listOf(a("u", frequency = AnnouncementFrequency.UNTIL_DISMISSED), a("w", frequency = AnnouncementFrequency.ALWAYS)))
        c.dismiss("u")
        c.dismiss("w")
        assertEquals(t0, store.read("u").dismissedAt)
        assertNull(store.read("w").dismissedAt)
        assertEquals(t0, store.read("w").seenAt)
    }

    @Test
    fun duracao_da_janela() {
        assertEquals(AnnouncementDuration(4, 0), announcementDuration(win))
        assertEquals(AnnouncementDuration(1, 30), announcementDuration(AnnouncementWindow(t0, t0 + 90.minutes)))
        assertEquals(AnnouncementDuration(0, 1), announcementDuration(AnnouncementWindow(t0, t0)))
    }

    // ─────────────── Serialização dos campos novos ───────────────

    @Test
    fun le_image_e_presentation() {
        val list = decodeAnnouncements(
            """[{"id":"a","kind":"news","title":"T","image":{"url":"https://cdn.x/a.webp","alt":"Foto","focus":"top"},"presentation":"banner"}]""",
        )
        val img = list.single().image!!
        assertEquals("https://cdn.x/a.webp", img.url)
        assertEquals("Foto", img.alt)
        assertEquals(AnnouncementImageFocus.TOP, img.focus)
        assertEquals(AnnouncementPresentation.BANNER, list.single().presentation)
    }

    @Test
    fun ausentes_viram_default_modal_sem_foto() {
        val one = decodeAnnouncements("""[{"id":"a","kind":"news","title":"T"}]""").single()
        assertNull(one.image)
        assertEquals(AnnouncementPresentation.MODAL, one.presentation)
    }

    @Test
    fun foto_invalida_descarta_so_a_foto() {
        val list = decodeAnnouncements(
            """[
              {"id":"http","kind":"news","title":"T","image":{"url":"http://cdn.x/a.png"}},
              {"id":"js","kind":"news","title":"T","image":{"url":"javascript:alert(1)"}},
              {"id":"semurl","kind":"news","title":"T","image":{"alt":"x"}},
              {"id":"string","kind":"news","title":"T","image":"https://cdn.x/a.png"},
              {"id":"foco","kind":"news","title":"T","image":{"url":"https://cdn.x/a.png","focus":"left","alt":"  "},"presentation":"toast"}
            ]""",
        )
        assertEquals(listOf("http", "js", "semurl", "string", "foco"), list.map { it.id })
        assertNull(list[0].image)
        assertNull(list[1].image)
        assertNull(list[2].image)
        assertNull(list[3].image)
        assertEquals(AnnouncementImageFocus.CENTER, list[4].image!!.focus)
        assertNull(list[4].image!!.alt)
        assertEquals(AnnouncementPresentation.MODAL, list[4].presentation)
    }

    @Test
    fun ida_e_volta_preserva_os_campos_novos() {
        val original = Announcement(
            id = "a", kind = AnnouncementKind.UPDATE, title = "T",
            image = AnnouncementImage("https://cdn.x/a.webp", "alt", AnnouncementImageFocus.BOTTOM),
            presentation = AnnouncementPresentation.BANNER,
        )
        val json = encodeAnnouncements(listOf(original))
        assertTrue(json.contains("\"focus\":\"bottom\""), json)
        assertTrue(json.contains("\"presentation\":\"banner\""), json)
        assertEquals(listOf(original), decodeAnnouncements(json))
        val semNada = encodeAnnouncements(listOf(Announcement("b", AnnouncementKind.INFO, "T")))
        assertFalse(semNada.contains("image"), semNada)
        assertFalse(semNada.contains("presentation"), semNada)
    }

    @Test
    fun display_image_ignora_url_insegura_montada_no_codigo() {
        assertNull(Announcement("a", AnnouncementKind.INFO, "T", image = AnnouncementImage("http://x/a.png")).displayImage)
        assertTrue(isSecureAnnouncementImageUrl("https://cdn.codecacto.com.br/a.webp"))
        assertFalse(isSecureAnnouncementImageUrl("https://"))
        assertFalse(isSecureAnnouncementImageUrl("https://a b/c"))
    }

    // ─────────────── Contraste AA ───────────────

    private val accents = listOf(
        Color(0xFFF59E0B), Color(0xFF3B82F6), Color(0xFFEAB308), Color(0xFF16A34A),
        Color(0xFF7C3AED), Color(0xFF0F172A), Color(0xFFFFFFFF), Color(0xFFDC2626),
    )

    private data class Theme(val surface: Color, val onSurface: Color, val onSurfaceVariant: Color)

    private val themes = listOf(
        Theme(Color(0xFFFFFFFF), Color(0xFF1C1B1F), Color(0xFF49454F)),
        Theme(Color(0xFF121212), Color(0xFFE6E1E5), Color(0xFFCAC4D0)),
    )

    @Test
    fun cores_do_modal_atingem_aa_nos_temas_claro_e_escuro() {
        for (theme in themes) for (accent in accents) {
            val c = announcementDialogColors(accent, theme.surface, theme.onSurface, theme.onSurfaceVariant)
            val ctx = "accent=$accent surface=${theme.surface}"
            fun text(fg: Color, bg: Color, what: String) =
                assertTrue(ColorContrast.contrastRatio(fg, bg) >= ColorContrast.AA_TEXT, "$what $ctx")
            fun graphic(fg: Color, bg: Color, what: String) =
                assertTrue(ColorContrast.contrastRatio(fg, bg) >= ColorContrast.AA_GRAPHIC, "$what $ctx")
            text(c.content, c.card, "content")
            text(c.secondaryContent, c.card, "secondary")
            text(c.badgeContent, c.badge, "selo")
            text(c.windowContent, c.windowContainer, "janela")
            text(c.windowSecondary, c.windowContainer, "janela-secundario")
            graphic(c.windowAccent, c.windowContainer, "ponto em andamento")
            graphic(c.heroIcon, c.heroCircle, "icone da faixa")
            for (photo in listOf(Color.Black, Color.White, Color(0xFF808080))) {
                text(c.chipContent, ColorContrast.compositeOver(c.chip, 1f, photo), "chip sobre $photo")
            }
        }
    }
}
