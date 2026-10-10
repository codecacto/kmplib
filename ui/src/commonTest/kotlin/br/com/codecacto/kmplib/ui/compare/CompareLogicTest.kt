package br.com.codecacto.kmplib.ui.compare

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * As regras do comparador — espelho de `compare.logic.test.ts` da weblib (`GAP-VIT-K02`): os mesmos
 * casos, para as duas pontas não divergirem.
 */
class CompareLogicTest {

    private fun photo(id: String, view: String? = null, label: String? = null) =
        ComparePhoto(id = id, src = "https://cdn/$id.jpg", alt = "foto $id", view = view, label = label)

    private fun session(id: String, date: String, vararg photos: ComparePhoto) =
        CompareSession(id = id, date = date, photos = photos.toList())

    private val utc = TimeZone.UTC

    // ── Datas ──

    @Test
    fun dataCivilViraMeiaNoiteUtc() {
        val p = parseCompareDate("2026-03-12", utc)!!
        assertTrue(p.dateOnly)
        assertEquals(LocalDate(2026, 3, 12).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), p.epochMillis)
        // O fuso pedido NÃO muda a data civil.
        assertEquals(p, parseCompareDate("2026-03-12", TimeZone.of("America/Sao_Paulo")))
    }

    @Test
    fun recusaDataCivilImpossivelEAnoMenorQue100() {
        assertNull(parseCompareDate("2026-02-31", utc))
        assertNull(parseCompareDate("2026-13-01", utc))
        assertNull(parseCompareDate("0099-01-01", utc))
    }

    @Test
    fun isoComHoraEInstante_comZ_offset_eEspaco() {
        val z = parseCompareDate("2026-03-12T10:00:00Z", utc)!!
        assertFalse(z.dateOnly)
        assertEquals(parseCompareDate("2026-03-12T07:00:00-03:00", utc)!!.epochMillis, z.epochMillis)
        assertEquals(parseCompareDate("2026-03-12T07:00-0300", utc)!!.epochMillis, z.epochMillis)
        assertEquals(parseCompareDate("2026-03-12 10:00", utc)!!.epochMillis, z.epochMillis)
        // Sem offset: no fuso pedido.
        assertEquals(z.epochMillis, parseCompareDate("2026-03-12T07:00", TimeZone.of("America/Sao_Paulo"))!!.epochMillis)
    }

    @Test
    fun formatoLivreEVazioSaoNull() {
        assertNull(parseCompareDate("12 de março", utc))
        assertNull(parseCompareDate("", utc))
        assertNull(parseCompareDate(null, utc))
        assertNull(parseCompareDate("12/03/2026", utc))
    }

    @Test
    fun textoIlegivelVoltaComoVeioNaFormatacao() {
        assertEquals("ontem", formatCompareDate("ontem", utc))
    }

    // ── Sessões ──

    @Test
    fun ordenaDaMaisAntigaParaAMaisRecente() {
        val s = listOf(
            session("c", "2026-10-12", photo("3")),
            session("a", "2026-03-12", photo("1")),
            session("b", "2026-06-12", photo("2")),
        )
        assertEquals(listOf("a", "b", "c"), comparableSessions(s, utc).map { it.id })
    }

    @Test
    fun empateMantemOrdemEDataIlegivelVaiProFim() {
        val s = listOf(
            session("x", "lixo", photo("1")),
            session("b", "2026-03-12", photo("2")),
            session("a", "2026-03-12", photo("3")),
            session("y", "", photo("4")),
            session("z", "2026-01-01", photo("5")),
        )
        assertEquals(listOf("z", "b", "a", "x", "y"), comparableSessions(s, utc).map { it.id })
    }

    @Test
    fun sessaoSemFotoNaoEntra() {
        val s = listOf(session("a", "2026-03-12"), session("b", "2026-04-12", photo("1")))
        assertEquals(listOf("b"), comparableSessions(s, utc).map { it.id })
    }

    // ── Par ──

    @Test
    fun parInicialSaoAsDuasPontas() {
        assertEquals(ComparePair("a", "c"), defaultComparePair(listOf("a", "b", "c")))
        assertNull(defaultComparePair(listOf("a")))
        assertNull(defaultComparePair(emptyList()))
    }

    @Test
    fun normalizaValidoMesmaInstanciaEInvertidoDesvira() {
        val ids = listOf("a", "b", "c")
        val valido = ComparePair("a", "b")
        assertSame(valido, normalizeComparePair(ids, valido))
        assertEquals(ComparePair("a", "c"), normalizeComparePair(ids, ComparePair("c", "a")))
    }

    @Test
    fun normalizaIdQueSumiuRepetidoOuAusenteVoltaAoInicial() {
        val ids = listOf("a", "b", "c")
        assertEquals(ComparePair("a", "c"), normalizeComparePair(ids, ComparePair("a", "zz")))
        assertEquals(ComparePair("a", "c"), normalizeComparePair(ids, ComparePair("b", "b")))
        assertEquals(ComparePair("a", "c"), normalizeComparePair(ids, null))
        assertNull(normalizeComparePair(listOf("a"), ComparePair("a", "b")))
    }

    @Test
    fun toqueNumaDoParNaoMuda() {
        val ids = listOf("a", "b", "c", "d")
        val par = ComparePair("b", "c")
        assertSame(par, nextComparePair(ids, par, "b"))
        assertSame(par, nextComparePair(ids, par, "c"))
    }

    @Test
    fun anteriorViraAntesPosteriorViraDepois() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals(ComparePair("a", "c"), nextComparePair(ids, ComparePair("b", "c"), "a"))
        assertEquals(ComparePair("b", "d"), nextComparePair(ids, ComparePair("b", "c"), "d"))
    }

    @Test
    fun entreAsDuasTrocaAPontaMaisProximaEmpateTrocaODepois() {
        val ids = listOf("a", "b", "c", "d", "e")
        assertEquals(ComparePair("b", "e"), nextComparePair(ids, ComparePair("a", "e"), "b"))
        assertEquals(ComparePair("a", "d"), nextComparePair(ids, ComparePair("a", "e"), "d"))
        // c está a 2 de cada ponta: troca o depois.
        assertEquals(ComparePair("a", "c"), nextComparePair(ids, ComparePair("a", "e"), "c"))
    }

    @Test
    fun idDesconhecidoNaoMuda() {
        val par = ComparePair("a", "b")
        assertSame(par, nextComparePair(listOf("a", "b"), par, "zz"))
        assertSame(par, nextComparePair(listOf("x", "y"), par, "x"))
    }

    @Test
    fun oAntesNuncaFicaDepoisDoDepois() {
        val ids = listOf("a", "b", "c", "d", "e", "f")
        var par = ComparePair("a", "f")
        for (clicked in listOf("e", "b", "d", "c", "a", "f", "b", "e")) {
            par = nextComparePair(ids, par, clicked)
            assertTrue(ids.indexOf(par.before) < ids.indexOf(par.after), "par invertido: $par")
        }
    }

    // ── Vistas ──

    @Test
    fun chaveDeCasamentoViewLabelPosicao() {
        assertEquals("frente", photoViewKey(photo("1", view = "frente", label = "Frente"), 0))
        assertEquals("Perfil", photoViewKey(photo("1", label = " Perfil "), 0))
        assertEquals("#3", photoViewKey(photo("1", view = " "), 2))
    }

    @Test
    fun uniaoDasSessoesNaOrdemEmQueAparecem() {
        val s = listOf(
            session("a", "2026-01-01", photo("1", view = "frente", label = "Frente"), photo("2")),
            session("b", "2026-02-01", photo("3", view = "costas"), photo("4", view = "frente")),
        )
        val views = compareViews(s) { "Foto $it" }
        assertEquals(listOf("frente", "#2", "costas"), views.map { it.key })
        assertEquals(listOf("Frente", "Foto 2", "costas"), views.map { it.label })
    }

    @Test
    fun photoForViewAchaAPrimeiraEToleraAusentes() {
        val s = session("a", "2026-01-01", photo("1", view = "frente"), photo("2", view = "frente"))
        assertEquals("1", photoForView(s, "frente")?.id)
        assertNull(photoForView(s, "costas"))
        assertNull(photoForView(null, "frente"))
        assertNull(photoForView(s, null))
    }

    @Test
    fun vistaEfetivaAPedidaSeOParTemSenaoAPrimeiraPresente() {
        val a = session("a", "2026-01-01", photo("1", view = "frente"))
        val b = session("b", "2026-02-01", photo("2", view = "frente"), photo("3", view = "costas"))
        val c = session("c", "2026-03-01", photo("4", view = "perfil"))
        val views = compareViews(listOf(a, b, c)) { "Foto $it" }
        assertEquals("costas", resolveCompareView(views, listOf(a, b), "costas")) // só de um lado basta
        assertEquals("frente", resolveCompareView(views, listOf(a, b), "perfil")) // nenhum dos dois tem
        assertEquals("frente", resolveCompareView(views, listOf(a, b), null))
        assertNull(resolveCompareView(views, listOf(null, null), "frente"))
    }

    // ── Divisória ──

    @Test
    fun clampPrendeEm0a100ENaoFinitoVoltaAoMeio() {
        assertEquals(0f, clampComparePosition(-10f))
        assertEquals(100f, clampComparePosition(250f))
        assertEquals(37.5f, clampComparePosition(37.5f))
        assertEquals(50f, clampComparePosition(Float.NaN))
        assertEquals(50f, clampComparePosition(Float.POSITIVE_INFINITY))
    }

    @Test
    fun posicaoDoDedoPercentualPresoNasBordasSemLarguraNull() {
        assertEquals(25f, comparePositionFromPointer(100f, 400f))
        assertEquals(0f, comparePositionFromPointer(-30f, 400f))
        assertEquals(100f, comparePositionFromPointer(999f, 400f))
        assertNull(comparePositionFromPointer(10f, 0f))
        assertNull(comparePositionFromPointer(10f, Float.NaN))
    }

    @Test
    fun parteVisivelSomaCem() {
        assertEquals(40 to 60, compareVisibleShares(40f))
        assertEquals(34 to 66, compareVisibleShares(33.6f))
        assertEquals(100 to 0, compareVisibleShares(120f))
    }

    @Test
    fun intencaoDoGesto() {
        assertEquals(CompareDragIntent.UNDECIDED, compareDragIntent(3f, -4f))
        assertEquals(CompareDragIntent.HORIZONTAL, compareDragIntent(10f, 4f))
        assertEquals(CompareDragIntent.VERTICAL, compareDragIntent(2f, -12f))
        assertEquals(CompareDragIntent.HORIZONTAL, compareDragIntent(-8f, 8f)) // empate = arrasto
        assertEquals(CompareDragIntent.UNDECIDED, compareDragIntent(10f, 0f, slop = 20f))
    }

    @Test
    fun estadoPrendeAPosicao() {
        val s = PhotoCompareState(initialPosition = 140f)
        assertEquals(100f, s.position)
        s.position = -5f
        assertEquals(0f, s.position)
        s.position = Float.NaN
        assertEquals(50f, s.position)
    }

    // ── Zoom sincronizado ──

    @Test
    fun zoomPrendeEscalaEDeslocamentoAosLimites() {
        val t = applyCompareTransform(CompareTransform.Identity, zoom = 2f, panX = 1000f, panY = -1000f, width = 300f, height = 400f)
        assertEquals(2f, t.scale)
        assertEquals(150f, t.offsetX) // (2-1)*300/2
        assertEquals(-200f, t.offsetY) // -(2-1)*400/2
        val max = applyCompareTransform(t, zoom = 100f, panX = 0f, panY = 0f, width = 300f, height = 400f)
        assertEquals(COMPARE_MAX_SCALE, max.scale)
    }

    @Test
    fun voltarAEscala1ZeraODeslocamento() {
        val t = CompareTransform(2f, 100f, 50f)
        assertEquals(CompareTransform.Identity, applyCompareTransform(t, 0.1f, 10f, 10f, 300f, 400f))
        assertFalse(CompareTransform.Identity.isZoomed)
        assertTrue(t.isZoomed)
    }

    @Test
    fun gestoInvalidoNaoQuebraOZoom() {
        val t = CompareTransform(2f, 0f, 0f)
        assertEquals(t, applyCompareTransform(t, Float.NaN, Float.NaN, Float.POSITIVE_INFINITY, 300f, 400f))
    }

    @Test
    fun duploToqueAlterna() {
        val ampliado = toggleCompareZoom(CompareTransform.Identity)
        assertEquals(COMPARE_DOUBLE_TAP_SCALE, ampliado.scale)
        assertEquals(CompareTransform.Identity, toggleCompareZoom(CompareTransform(3f, 40f, 40f)))
    }

    @Test
    fun deslocamentoMaximoEmEscala1EZero() {
        assertEquals(0f to 0f, compareMaxOffset(1f, 300f, 400f))
        assertEquals(0f to 0f, compareMaxOffset(0.5f, 300f, 400f))
    }

    // ── Quadro ──

    @Test
    fun quadroDeProporcaoFixaPresoPelaLarguraEPelaAltura() {
        assertEquals(300f to 400f, compareFrameSize(3f / 4f, 300f, 1000f))
        // Tablet: a altura máxima limita a LARGURA, e a proporção continua 3:4.
        assertEquals(375f to 500f, compareFrameSize(3f / 4f, 1200f, 500f))
        // Sem teto de altura (tela cheia sem limite): só a largura.
        assertEquals(300f to 400f, compareFrameSize(3f / 4f, 300f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun proporcaoInvalidaVoltaAo3por4() {
        assertEquals(compareFrameSize(DEFAULT_COMPARE_ASPECT_RATIO, 300f, 1000f), compareFrameSize(0f, 300f, 1000f))
        assertEquals(compareFrameSize(DEFAULT_COMPARE_ASPECT_RATIO, 300f, 1000f), compareFrameSize(Float.NaN, 300f, 1000f))
    }

    // ── Contrato e textos ──

    @Test
    fun jsonDaWeblibServeSemTraducao() {
        val json = """
            [{"id":"s1","date":"2026-03-12","label":"Sessão 1","description":"4 fotos",
              "photos":[{"id":"p1","src":"/v1/fotos/p1","alt":"Frente","view":"frente","label":"Frente"}]},
             {"id":"s2","date":"2026-10-12T09:30:00-03:00","photos":[{"id":"p2","src":"/v1/fotos/p2","alt":"Frente","view":"frente","takenAt":"2026-10-11"}]}]
        """.trimIndent()
        val sessions = Json { ignoreUnknownKeys = true }.decodeFromString<List<CompareSession>>(json)
        assertEquals(2, sessions.size)
        assertEquals("frente", sessions[1].photos[0].view)
        assertEquals("2026-10-11", sessions[1].photos[0].takenAt)
        assertNull(sessions[1].label)
    }

    @Test
    fun toStringNaoVazaUrlNemDescricao() {
        val p = ComparePhoto("p1", "https://cdn/x.jpg?X-Amz-Signature=segredo", "Frente")
        assertFalse("segredo" in p.toString())
        val s = CompareSession("s1", "2026-03-12", description = "78 kg", photos = listOf(p))
        assertFalse("78 kg" in s.toString())
    }

    @Test
    fun templateSubstituiArgumentos() {
        assertEquals("Comparando a com b", formatCompareTemplate("Comparando %1\$s com %2\$s", "a", "b"))
        assertEquals("40% antes, 60% depois", formatCompareTemplate("%1\$d% antes, %2\$d% depois", 40, 60))
    }
}
