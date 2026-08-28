package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes da LÓGICA PURA da camada de **destinação** (GAP-MA-M-01) — par mobile de `layers.test.ts` da
 * weblib. O que está coberto aqui é o que faz a camada ter significado em vez de ser enfeite:
 * sobreposição resolvida de forma determinística, a consulta (`layerAtMinute`) coincidindo com o
 * desenho, legenda derivada do vocabulário, e a garantia de que duas destinações não saem
 * indistinguíveis.
 */
class CalendarLayersTest {

    private fun dt(hh: Int, mm: Int = 0) = LocalDateTime(2026, 8, 14, hh, mm)

    private fun layer(
        id: String,
        kind: String,
        sh: Int,
        eh: Int,
        resourceId: String? = null,
        label: String? = null,
        tone: LayerTone? = null,
        pattern: LayerPattern? = null,
    ) = ScheduleLayer(
        id = id,
        start = dt(sh),
        end = dt(eh),
        kind = kind,
        resourceId = resourceId,
        label = label,
        tone = tone,
        pattern = pattern,
    )

    // ── flattenLayers: a exceção do dia cobre o padrão semanal ──

    @Test
    fun `sem sobreposicao devolve as faixas como estao`() {
        val flat = flattenLayers(
            listOf(layer("a", "RENTAL", 8, 19), layer("b", "CLUBINHO", 19, 22)),
        )
        assertEquals(2, flat.size)
        assertEquals(MinuteRange(480, 1140), flat[0].range)
        assertEquals("a", flat[0].layer.id)
        assertEquals(MinuteRange(1140, 1320), flat[1].range)
        assertEquals("b", flat[1].layer.id)
    }

    @Test
    fun `a ULTIMA faixa vence no trecho comum e recorta a anterior`() {
        // Padrão semanal (Aluguel 08–22) + exceção do dia (Bloqueado 14–16).
        val flat = flattenLayers(
            listOf(layer("semana", "RENTAL", 8, 22), layer("excecao", "BLOCKED", 14, 16)),
        )
        assertEquals(3, flat.size)
        assertEquals(listOf("semana", "excecao", "semana"), flat.map { it.layer.id })
        assertEquals(MinuteRange(480, 840), flat[0].range)
        assertEquals(MinuteRange(840, 960), flat[1].range)
        assertEquals(MinuteRange(960, 1320), flat[2].range)
    }

    @Test
    fun `a ordem importa - inverter troca quem vence`() {
        val flat = flattenLayers(
            listOf(layer("excecao", "BLOCKED", 14, 16), layer("semana", "RENTAL", 8, 22)),
        )
        assertEquals(1, flat.size)
        assertEquals("semana", flat[0].layer.id)
        assertEquals(MinuteRange(480, 1320), flat[0].range)
    }

    @Test
    fun `trechos contiguos da MESMA faixa sao reunidos em um so`() {
        // Sem a reunião, "semana" viraria 2 retângulos e apareceria uma costura na grade às 14h.
        val flat = flattenLayers(
            listOf(layer("semana", "RENTAL", 8, 22), layer("x", "AULA", 14, 14)),
        )
        assertEquals(1, flat.size)
        assertEquals(MinuteRange(480, 1320), flat[0].range)
    }

    @Test
    fun `faixa de duracao zero ou negativa e ignorada`() {
        assertTrue(flattenLayers(listOf(layer("a", "RENTAL", 10, 10))).isEmpty())
        val invertida = ScheduleLayer(id = "b", start = dt(12), end = dt(9), kind = "RENTAL")
        assertTrue(flattenLayers(listOf(invertida)).isEmpty())
    }

    @Test
    fun `lista vazia nao produz faixa`() {
        assertTrue(flattenLayers(emptyList()).isEmpty())
    }

    // ── layerAtMinute: a resposta ao toque é a MESMA coisa que está desenhada ──

    @Test
    fun `layerAtMinute devolve a destinacao vigente no minuto`() {
        val layers = listOf(layer("a", "RENTAL", 8, 19), layer("b", "CLUBINHO", 19, 22))
        assertEquals("RENTAL", layerAtMinute(layers, null, 600)?.kind)
        assertEquals("CLUBINHO", layerAtMinute(layers, null, 1200)?.kind)
    }

    @Test
    fun `layerAtMinute respeita a fronteira ABERTA no fim`() {
        val layers = listOf(layer("a", "RENTAL", 8, 19), layer("b", "CLUBINHO", 19, 22))
        // 19:00 pertence à faixa que COMEÇA às 19:00, não à que termina nela.
        assertEquals("CLUBINHO", layerAtMinute(layers, null, 1140)?.kind)
        assertNull(layerAtMinute(layers, null, 1320))
    }

    @Test
    fun `layerAtMinute concorda com flattenLayers na sobreposicao`() {
        // A garantia central: consulta e desenho não podem divergir onde há exceção.
        val layers = listOf(layer("semana", "RENTAL", 8, 22), layer("chuva", "BLOCKED", 14, 16))
        val flat = flattenLayers(layers)
        for (minute in 480 until 1320 step 5) {
            val esperado = flat.firstOrNull { minute >= it.range.startMin && minute < it.range.endMin }
            assertEquals(esperado?.layer?.id, layerAtMinute(layers, null, minute)?.id, "minuto $minute")
        }
    }

    @Test
    fun `layerAtMinute filtra por coluna e faixa sem recurso vale para todas`() {
        val layers = listOf(
            layer("geral", "RENTAL", 8, 22),
            layer("q2", "CLUBINHO", 19, 22, resourceId = "quadra-2"),
        )
        assertEquals("CLUBINHO", layerAtMinute(layers, "quadra-2", 1200)?.kind)
        assertEquals("RENTAL", layerAtMinute(layers, "quadra-1", 1200)?.kind)
    }

    @Test
    fun `layerAtMinute fora de qualquer faixa devolve nulo`() {
        val layers = listOf(layer("a", "RENTAL", 19, 22))
        assertNull(layerAtMinute(layers, null, 600))
        assertNull(layerAtMinute(emptyList(), null, 600))
    }

    // ── layersByColumn ──

    @Test
    fun `faixa sem recurso cobre todas as colunas e a com recurso so a dela`() {
        val byColumn = layersByColumn(
            layers = listOf(
                layer("geral", "RENTAL", 8, 22),
                layer("q2", "AULA", 19, 21, resourceId = "q2"),
            ),
            columnIds = listOf("q1", "q2"),
            isSingle = false,
            singleColumnId = "__single__",
        )
        assertEquals(listOf("geral"), byColumn["q1"]?.map { it.id })
        assertEquals(listOf("geral", "q2"), byColumn["q2"]?.map { it.id })
    }

    @Test
    fun `coluna unica recebe tudo inclusive faixa de recurso`() {
        val byColumn = layersByColumn(
            layers = listOf(layer("q2", "AULA", 19, 21, resourceId = "q2")),
            columnIds = listOf("__single__"),
            isSingle = true,
            singleColumnId = "__single__",
        )
        assertEquals(1, byColumn["__single__"]?.size)
    }

    @Test
    fun `faixa de recurso inexistente e descartada sem quebrar`() {
        val byColumn = layersByColumn(
            layers = listOf(layer("fantasma", "AULA", 19, 21, resourceId = "q9")),
            columnIds = listOf("q1"),
            isSingle = false,
            singleColumnId = "__single__",
        )
        assertEquals(emptyList(), byColumn["q1"])
    }

    // ── resolveLayerStyle: override na faixa → legenda → default ──

    @Test
    fun `estilo vem da legenda quando a faixa nao sobrescreve`() {
        val style = resolveLayerStyle(
            layer("a", "CLUBINHO", 19, 22),
            mapOf("CLUBINHO" to ScheduleLayerStyle("Clubinho", LayerTone.Success, LayerPattern.Dots)),
        )
        assertEquals("Clubinho", style.label)
        assertEquals(LayerTone.Success, style.tone)
        assertEquals(LayerPattern.Dots, style.pattern)
    }

    @Test
    fun `override na faixa vence a legenda`() {
        val style = resolveLayerStyle(
            layer("a", "CLUBINHO", 19, 22, label = "Clubinho (extra)", tone = LayerTone.Warning, pattern = LayerPattern.Hatch),
            mapOf("CLUBINHO" to ScheduleLayerStyle("Clubinho", LayerTone.Success, LayerPattern.Dots)),
        )
        assertEquals("Clubinho (extra)", style.label)
        assertEquals(LayerTone.Warning, style.tone)
        assertEquals(LayerPattern.Hatch, style.pattern)
    }

    @Test
    fun `sem legenda nem override o rotulo cai no kind e nada fica mudo`() {
        val style = resolveLayerStyle(layer("a", "CLUBINHO", 19, 22))
        assertEquals("CLUBINHO", style.label)
        assertEquals(LayerTone.Neutral, style.tone)
    }

    @Test
    fun `LayerTone tem Primary e Accent - e por isso NAO e o StatusTone dos selos`() {
        // Sem estes dois, uma destinação com a cor do produto (o "Social" do design) não sai.
        val style = resolveLayerStyle(
            layer("a", "SOCIAL", 8, 12),
            mapOf("SOCIAL" to ScheduleLayerStyle("Social", LayerTone.Primary)),
        )
        assertEquals(LayerTone.Primary, style.tone)
        assertTrue(LayerTone.entries.containsAll(listOf(LayerTone.Primary, LayerTone.Accent)))
    }

    // ── legenda derivada ──

    @Test
    fun `legenda lista o VOCABULARIO inteiro e nao so o que caiu na grade hoje`() {
        // Legenda estável entre dias: não some "Aula" porque hoje não há aula marcada.
        val entries = layerLegendEntries(
            layers = listOf(layer("a", "RENTAL", 8, 19), layer("b", "CLUBINHO", 19, 22)),
            legend = mapOf(
                "RENTAL" to ScheduleLayerStyle("Aluguel"),
                "CLUBINHO" to ScheduleLayerStyle("Clubinho"),
                "AULA" to ScheduleLayerStyle("Aula"),
            ),
        )
        assertEquals(listOf("RENTAL", "CLUBINHO", "AULA"), entries.map { it.kind })
    }

    @Test
    fun `legenda segue a ordem declarada e nao a ordem do dia`() {
        val entries = layerLegendEntries(
            layers = listOf(layer("b", "CLUBINHO", 8, 12), layer("a", "RENTAL", 12, 22)),
            legend = mapOf(
                "RENTAL" to ScheduleLayerStyle("Aluguel"),
                "CLUBINHO" to ScheduleLayerStyle("Clubinho"),
            ),
        )
        assertEquals(listOf("RENTAL", "CLUBINHO"), entries.map { it.kind })
    }

    @Test
    fun `kind presente mas nao declarado entra depois dos declarados`() {
        val entries = layerLegendEntries(
            layers = listOf(layer("x", "SURPRESA", 8, 12), layer("a", "RENTAL", 12, 22)),
            legend = mapOf("RENTAL" to ScheduleLayerStyle("Aluguel")),
        )
        assertEquals(listOf("RENTAL", "SURPRESA"), entries.map { it.kind })
        assertEquals("SURPRESA", entries[1].label)
    }

    @Test
    fun `kind repetido em varias faixas entra na legenda uma vez so`() {
        val entries = layerLegendEntries(
            layers = listOf(layer("a", "RENTAL", 8, 12), layer("b", "RENTAL", 12, 22)),
        )
        assertEquals(listOf("RENTAL"), entries.map { it.kind })
    }

    @Test
    fun `legenda ignora kind em branco`() {
        val entries = layerLegendEntries(
            layers = listOf(
                ScheduleLayer(id = "vazio", start = dt(8), end = dt(9), kind = "  "),
                layer("ok", "RENTAL", 8, 22),
            ),
        )
        assertEquals(listOf("RENTAL"), entries.map { it.kind })
    }

    @Test
    fun `legenda vazia quando nao ha faixa`() {
        assertTrue(layerLegendEntries(emptyList()).isEmpty())
    }

    // ── indistinguibilidade: a verificação que dá dente ao "nunca só por cor" ──

    @Test
    fun `mesmo tom e mesma textura sao acusados como indistinguiveis`() {
        val entries = layerLegendEntries(
            layers = listOf(layer("a", "RENTAL", 8, 12), layer("b", "BLOCKED", 12, 22)),
            legend = mapOf(
                "RENTAL" to ScheduleLayerStyle("Aluguel", LayerTone.Info, LayerPattern.Solid),
                "BLOCKED" to ScheduleLayerStyle("Bloqueado", LayerTone.Info, LayerPattern.Solid),
            ),
        )
        assertEquals(listOf(listOf("RENTAL", "BLOCKED")), indistinguishableLayerKinds(entries))
    }

    @Test
    fun `mesmo tom com texturas diferentes NAO e colisao`() {
        // É exatamente o caso que a camada existe para servir: paleta arbitrária do cliente, e a
        // textura carregando a distinção.
        val entries = layerLegendEntries(
            layers = listOf(layer("a", "RENTAL", 8, 12), layer("b", "BLOCKED", 12, 22)),
            legend = mapOf(
                "RENTAL" to ScheduleLayerStyle("Aluguel", LayerTone.Danger, LayerPattern.Solid),
                "BLOCKED" to ScheduleLayerStyle("Bloqueado", LayerTone.Danger, LayerPattern.Hatch),
            ),
        )
        assertTrue(indistinguishableLayerKinds(entries).isEmpty())
    }

    @Test
    fun `SEM legenda declarada tudo sai igual - e o aviso precisa acusar isso`() {
        // O default é neutro e liso nos dois lados (web e mobile). Não mascaramos isso com uma
        // textura "esperta": preferimos que o defeito apareça no log a que a grade pareça decorada.
        val entries = layerLegendEntries(
            layers = listOf(layer("a", "RENTAL", 8, 12), layer("b", "AULA", 12, 22)),
        )
        assertEquals(listOf(listOf("RENTAL", "AULA")), indistinguishableLayerKinds(entries))
    }

    @Test
    fun `o vocabulario do Minha Arena sai inteiramente distinguivel`() {
        // Cinco destinações do produto (§0.2 do design), com a legenda que ele declara.
        val legend = mapOf(
            "RENTAL" to ScheduleLayerStyle("Aluguel", LayerTone.Info, LayerPattern.Solid),
            "CLUBINHO" to ScheduleLayerStyle("Clubinho", LayerTone.Success, LayerPattern.Dots),
            "SOCIAL" to ScheduleLayerStyle("Social", LayerTone.Primary, LayerPattern.Solid),
            "AULA" to ScheduleLayerStyle("Aula", LayerTone.Warning, LayerPattern.Stripes),
            "BLOCKED" to ScheduleLayerStyle("Bloqueado", LayerTone.Neutral, LayerPattern.Hatch),
        )
        val layers = legend.keys.mapIndexed { i, kind -> layer("l" + i, kind, 8 + i, 9 + i) }
        val entries = layerLegendEntries(layers, legend)
        assertEquals(5, entries.size)
        assertTrue(indistinguishableLayerKinds(entries).isEmpty())
        assertEquals(listOf("Aluguel", "Clubinho", "Social", "Aula", "Bloqueado"), entries.map { it.label })
    }

    // ── recorte à janela: destinação é fundo, não estica a grade ──

    @Test
    fun `faixa que cobre o dia inteiro e recortada a janela visivel`() {
        // "Social o dia inteiro" não pode virar uma grade de 00:00–24:00 nem desenhar fora da coluna.
        val janela = BusinessWindow(480, 1320)
        assertEquals(MinuteRange(480, 1320), clipToWindow(MinuteRange(0, 1440), janela))
    }

    @Test
    fun `faixa inteiramente fora da janela nao aparece`() {
        val janela = BusinessWindow(480, 1320)
        assertNull(clipToWindow(MinuteRange(0, 480), janela))
        assertNull(clipToWindow(MinuteRange(1320, 1440), janela))
    }

    @Test
    fun `faixa dentro da janela passa intacta`() {
        val janela = BusinessWindow(480, 1320)
        assertEquals(MinuteRange(1140, 1320), clipToWindow(MinuteRange(1140, 1320), janela))
    }

    @Test
    fun `destinacao NAO expande a janela como um evento faria`() {
        // Contraste deliberado: evento expande (nada some da agenda), destinação não (é fundo).
        val janela = computeTimeWindow(ComputeWindowOptions(businessRanges = listOf(WorkRange(540, 1140))))
        assertEquals(BusinessWindow(480, 1200), janela)
        assertEquals(MinuteRange(480, 1200), clipToWindow(toMinuteRange(dt(0), dt(23)), janela))
    }

    // ── destinação ≠ indisponível ──

    @Test
    fun `destinacao e bloqueio sao camadas distintas e convivem`() {
        // Se destinação fosse "mais uma variante de bloqueio", este teste não existiria: a grade
        // voltaria a saber apenas que a faixa está indisponível, sem saber o que ela é.
        val layers = listOf(layer("a", "RENTAL", 8, 22, label = "Aluguel"))
        val block = ScheduleBlock(id = "almoco", start = dt(12), end = dt(13), label = "Almoço")
        assertEquals("Aluguel", resolveLayerStyle(layers[0]).label)
        assertEquals("RENTAL", layerAtMinute(layers, null, minutesOfDay(block.start))?.kind)
        assertNotEquals(ScheduleBlockVariant.Block.name, layers[0].kind)
    }
}
