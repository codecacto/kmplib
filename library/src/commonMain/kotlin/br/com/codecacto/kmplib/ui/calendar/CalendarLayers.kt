package br.com.codecacto.kmplib.ui.calendar

/* ─────────────────────────── camada de DESTINAÇÃO: lógica PURA (sem Compose) ───────────────────────────
 * "O que esta faixa É" (Aluguel · Clubinho · Social · Aula · Bloqueado), desenhado como FUNDO da grade,
 * com a ocupação por cima. Par web: `calendar/layers.ts` da weblib (`GAP-MA-W-01`) — mesmos nomes,
 * mesma semântica, de propósito.
 *
 * Aqui mora tudo o que não precisa de UI:
 *   1. RESOLVER a aparência (`kind` → rótulo/tom/textura), com override na faixa;
 *   2. DERIVAR a legenda do vocabulário declarado + do que aparece nas faixas;
 *   3. RESPONDER "qual é a destinação deste minuto?" (`layerAtMinute`);
 *   4. ACHATAR sobreposições em faixas disjuntas, para o desenho.
 *
 * O item 3 é o que faz a camada ter significado em vez de ser enfeite: é com ele que o consumidor
 * recusa amigavelmente uma reserva numa faixa que não é de aluguel. E ele compartilha a MESMA regra do
 * desenho ("a última vence"), porque o que a pessoa vê e o que o app decide não podem divergir
 * justamente na sobreposição — que é onde alguém já pensou no assunto.
 */

/** Tom default quando nem a faixa nem a legenda declaram. */
val DEFAULT_LAYER_TONE: LayerTone = LayerTone.Neutral

/** Textura default quando nem a faixa nem a legenda declaram. */
val DEFAULT_LAYER_PATTERN: LayerPattern = LayerPattern.Solid

/**
 * Aparência de um `kind` de destinação — fonte **única** de rótulo/tom/textura, usada pela grade **e**
 * pela legenda (é o que impede as duas de discordarem). Tudo opcional: o que faltar cai na escada de
 * resolução (ver [resolveLayerStyle]).
 */
data class ScheduleLayerStyle(
    /** Rótulo exibido (grade + legenda). Ausente ⇒ o próprio `kind`. */
    val label: String? = null,
    /** Tom semântico. Default [DEFAULT_LAYER_TONE]. */
    val tone: LayerTone? = null,
    /** Textura. Default [DEFAULT_LAYER_PATTERN]. */
    val pattern: LayerPattern? = null,
)

/**
 * Vocabulário de destinações: `kind` → aparência. A **ordem de declaração é a ordem da legenda**
 * (`mapOf`/`linkedMapOf` preservam a ordem).
 *
 * ```kotlin
 * val legenda: ScheduleLayerLegend = mapOf(
 *     "RENTAL" to ScheduleLayerStyle("Aluguel", LayerTone.Info),
 *     "CLUBINHO" to ScheduleLayerStyle("Clubinho", LayerTone.Success, LayerPattern.Dots),
 * )
 * ```
 */
typealias ScheduleLayerLegend = Map<String, ScheduleLayerStyle>

/** Aparência já resolvida de um `kind` — nada opcional, pronta para renderizar. */
data class ResolvedLayerStyle(
    val kind: String,
    val label: String,
    val tone: LayerTone,
    val pattern: LayerPattern,
)

/** Uma faixa de destinação já **achatada**: sem sobreposição com nenhuma outra. */
data class FlattenedLayer(
    val layer: ScheduleLayer,
    val range: MinuteRange,
)

/** Faixa de minutos-do-dia de uma camada; `null` se a duração for ≤ 0. */
fun layerRange(layer: ScheduleLayer): MinuteRange? {
    val range = toMinuteRange(layer.start, layer.end)
    return if (range.endMin > range.startMin) range else null
}

/**
 * Resolve a aparência de uma faixa: **a faixa vence a legenda, a legenda vence o default**.
 *
 * O rótulo termina no próprio `kind` — faixa sem rótulo e sem legenda ainda mostra algo identificável,
 * em vez de um retângulo colorido mudo.
 */
fun resolveLayerStyle(
    layer: ScheduleLayer,
    legend: ScheduleLayerLegend? = null,
): ResolvedLayerStyle {
    val fromLegend = legend?.get(layer.kind)
    return ResolvedLayerStyle(
        kind = layer.kind,
        label = layer.label ?: fromLegend?.label ?: layer.kind,
        tone = layer.tone ?: fromLegend?.tone ?: DEFAULT_LAYER_TONE,
        pattern = layer.pattern ?: fromLegend?.pattern ?: DEFAULT_LAYER_PATTERN,
    )
}

/**
 * Entradas da legenda a exibir. **Ordem = ordem de declaração da legenda** (o vocabulário do produto),
 * seguida dos `kind` que aparecem nas faixas mas **não** estão na legenda.
 *
 * Os dois pedaços são deliberados. Listar o vocabulário inteiro mantém a legenda **estável entre
 * dias** — ela não dança conforme o que por acaso está agendado hoje. E incluir o `kind` não declarado
 * (com a chave crua como rótulo) espelha o `onOrphanEvents` do [AppTimeGridScheduler]: destinação nova
 * vinda do backend aparece na legenda em vez de virar fundo pintado que ninguém sabe ler.
 */
fun layerLegendEntries(
    layers: List<ScheduleLayer> = emptyList(),
    legend: ScheduleLayerLegend? = null,
): List<ResolvedLayerStyle> {
    val out = ArrayList<ResolvedLayerStyle>()
    val seen = HashSet<String>()

    for ((kind, style) in legend.orEmpty()) {
        if (kind.isBlank() || !seen.add(kind)) continue
        out.add(
            ResolvedLayerStyle(
                kind = kind,
                label = style.label ?: kind,
                tone = style.tone ?: DEFAULT_LAYER_TONE,
                pattern = style.pattern ?: DEFAULT_LAYER_PATTERN,
            ),
        )
    }
    for (layer in layers) {
        if (layer.kind.isBlank() || !seen.add(layer.kind)) continue
        // Sem entrada na legenda: usa o que a própria faixa declarar (ou o default).
        out.add(resolveLayerStyle(layer, legend))
    }
    return out
}

/**
 * Achata as faixas de **um mesmo escopo de coluna** em trechos disjuntos: onde duas se cruzam, vence a
 * **última da lista** — a mesma convenção do [layerAtMinute] e do empilhamento do web.
 *
 * "A última vence" não é arbitrário: é o que permite empilhar *padrão semanal* e depois *exceção do
 * dia* na mesma lista e obter "terça é Aluguel, mas nesta terça das 14h às 16h é Bloqueado — chuva",
 * sem o app recortar faixas na mão. Trechos contíguos vencidos pela mesma faixa são reunidos, para não
 * virarem N retângulos com costura visível.
 *
 * **Por que o mobile achata e o web não precisa:** no web as faixas se sobrepõem no DOM e a de cima
 * cobre a de baixo. No Compose, duas superfícies translúcidas empilhadas **somam** opacidade — o trecho
 * comum sairia mais escuro que o resto, e a exceção do dia apareceria manchada sobre o padrão semanal.
 * Achatar antes de desenhar é o que mantém as duas plataformas visualmente iguais.
 *
 * Faixa de duração ≤ 0 é ignorada. Custo O(n²) em número de faixas — n aqui é a quantidade de
 * destinações de um dia (unidades), não de eventos.
 */
fun flattenLayers(layers: List<ScheduleLayer>): List<FlattenedLayer> {
    if (layers.isEmpty()) return emptyList()
    val ranges = layers.map { layerRange(it) }

    val cuts = HashSet<Int>()
    for (r in ranges) {
        if (r != null) {
            cuts.add(r.startMin)
            cuts.add(r.endMin)
        }
    }
    if (cuts.isEmpty()) return emptyList()
    val points = cuts.sorted()

    // (índice da faixa vencedora, início, fim) — acumulado para reunir trechos contíguos.
    val segments = ArrayList<IntArray>()
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        var winner = -1
        for (j in layers.indices) {
            val r = ranges[j] ?: continue
            if (r.startMin <= a && r.endMin >= b) winner = j
        }
        if (winner < 0) continue
        val last = segments.lastOrNull()
        if (last != null && last[0] == winner && last[2] == a) {
            last[2] = b
        } else {
            segments.add(intArrayOf(winner, a, b))
        }
    }
    return segments.map { FlattenedLayer(layers[it[0]], MinuteRange(it[1], it[2])) }
}

/**
 * Destinação vigente num minuto de uma coluna — a pergunta que o consumidor faz antes de abrir "nova
 * reserva" num clique de slot (ex.: *"esta faixa é Clubinho, aqui não se vende hora"*).
 *
 * Considera as faixas da própria coluna **e** as globais (`resourceId` nulo). Quando há mais de uma
 * cobrindo o minuto, vence a **última** da lista — mesma convenção de [flattenLayers] e do
 * empilhamento do web. Fronteira **aberta**: `[start, end)`, então 19:00 pertence à faixa que começa às
 * 19:00, não à que termina nela.
 *
 * @param columnId Coluna consultada. `null` = timeline única — aí **só** as faixas globais valem, pelo
 *   mesmo motivo do web: sem coluna não há como afirmar que a faixa de um recurso específico se aplica.
 */
fun layerAtMinute(
    layers: List<ScheduleLayer>?,
    columnId: String?,
    minute: Int,
): ScheduleLayer? {
    if (layers.isNullOrEmpty()) return null
    var found: ScheduleLayer? = null
    for (layer in layers) {
        if (layer.resourceId != null && layer.resourceId != columnId) continue
        val range = layerRange(layer) ?: continue
        if (minute >= range.startMin && minute < range.endMin) found = layer
    }
    return found
}

/**
 * Distribui as faixas entre as colunas. `resourceId` nulo cobre **todas** (regra do estabelecimento
 * inteiro); em coluna única ([isSingle]) tudo cai na coluna única, inclusive faixas com `resourceId` de
 * um recurso que não é coluna — numa timeline não existe "outra coluna" para onde mandá-las.
 *
 * Faixa cujo `resourceId` não casa com nenhuma coluna (fora do modo single) é **descartada**, sem
 * ruído: ao contrário de um evento órfão, que é um compromisso que sumiria da agenda, uma destinação
 * órfã é fundo de uma coluna que não está sendo exibida.
 */
fun layersByColumn(
    layers: List<ScheduleLayer>,
    columnIds: List<String>,
    isSingle: Boolean,
    singleColumnId: String,
): Map<String, List<ScheduleLayer>> {
    val map = LinkedHashMap<String, MutableList<ScheduleLayer>>()
    for (id in columnIds) map[id] = ArrayList()
    for (l in layers) {
        if (isSingle) {
            map[singleColumnId]?.add(l)
            continue
        }
        if (l.resourceId == null) {
            for (id in columnIds) map[id]?.add(l)
        } else {
            map[l.resourceId]?.add(l)
        }
    }
    return map
}

/**
 * Grupos de destinações que a pessoa **não consegue distinguir** na grade: mesmo tom **e** mesma
 * textura.
 *
 * É a verificação que dá dente à regra "estado nunca só por cor": com tom igual e textura igual, duas
 * destinações diferentes são o mesmo retângulo, e a legenda passa a mentir. Retorna os `kind` em grupos
 * (ordem estável de [entries]); lista vazia = tudo distinguível.
 *
 * O [AppTimeGridScheduler] usa isto para **avisar no log** em vez de deixar o defeito passar calado — a
 * correção é do app (declarar tom ou textura diferente), porque só ele conhece o vocabulário. É também
 * o que faz o caso "nenhuma legenda declarada" falhar alto: sem legenda, toda destinação sai neutra e
 * lisa, e o aviso aparece em vez de a grade parecer decorada.
 */
fun indistinguishableLayerKinds(entries: List<ResolvedLayerStyle>): List<List<String>> {
    val groups = LinkedHashMap<String, MutableList<String>>()
    for (e in entries) {
        groups.getOrPut("${e.tone.name}|${e.pattern.name}") { ArrayList() }.add(e.kind)
    }
    return groups.values.filter { it.size > 1 }.map { it.toList() }
}
