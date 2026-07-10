package br.com.codecacto.kmplib.ui.calendar

/* ─────────────────────────── layout PURO da grade temporal (sem Compose) ───────────────────────────
 * Espelha `calendar/layout.ts` da weblib. Três responsabilidades, 100% testáveis:
 *   1. derivar a JANELA horária dos dados (business hours + eventos), não fixa 7–22;
 *   2. POSICIONAR um bloco por minuto real (top/height proporcionais a início/duração);
 *   3. empacotar sobreposições em LANES (colunas paralelas dentro da coluna do recurso).
 * A saída é em unidades abstratas (o consumidor Compose multiplica por `dp/min`).
 */

/** Opções para derivar a janela horária visível. */
data class ComputeWindowOptions(
    /** Faixas de expediente do dia (min open → max close). Vazio ⇒ usa `fallback`. */
    val businessRanges: List<WorkRange> = emptyList(),
    /** Eventos a garantir visíveis (a janela cresce para nunca escondê-los). */
    val events: List<ScheduleEvent> = emptyList(),
    /** Bloqueios a garantir visíveis. */
    val blocks: List<ScheduleBlock> = emptyList(),
    /** Janela default quando não há business hours nem dados. Default 08:00–20:00. */
    val fallback: BusinessWindow = DEFAULT_WINDOW_FALLBACK,
    /** Folga (min) aplicada antes/depois do expediente. Default 30. */
    val paddingMin: Int = 30,
    /** Passo para "arredondar" as bordas da janela (start p/ baixo, end p/ cima). Default 60. */
    val snapMin: Int = 60,
)

/** Janela default 08:00–20:00. */
val DEFAULT_WINDOW_FALLBACK: BusinessWindow = BusinessWindow(startMin = 480, endMin = 1200)

private fun collectRanges(options: ComputeWindowOptions): List<MinuteRange> {
    val out = ArrayList<MinuteRange>()
    for (e in options.events) {
        val r = toMinuteRange(e.start, e.end)
        out.add(MinuteRange(r.startMin, maxOf(r.endMin, r.startMin + 1)))
    }
    for (b in options.blocks) {
        out.add(toMinuteRange(b.start, b.end))
    }
    return out
}

/**
 * Deriva a janela horária visível **dos dados** (substitui o `7..22` fixo do Influencer):
 * min(open) → max(close) do expediente, com folga e snap, **expandida** para conter todo
 * evento/bloqueio (nada some — o análogo por-minuto do `gridHour` defensivo).
 */
fun computeTimeWindow(options: ComputeWindowOptions = ComputeWindowOptions()): BusinessWindow {
    val padding = options.paddingMin
    val snap = options.snapMin
    val business = options.businessRanges

    var start: Int
    var end: Int

    if (business.isNotEmpty()) {
        start = business.minOf { it.startMin }
        end = business.maxOf { it.endMin }
        start = floorToStep(start - padding, snap)
        end = ceilToStep(end + padding, snap)
    } else {
        start = options.fallback.startMin
        end = options.fallback.endMin
    }

    // Expande para conter todo evento/bloqueio (sem snap: cola no dado para não cortar).
    for (r in collectRanges(options)) {
        if (r.startMin < start) start = r.startMin
        if (r.endMin > end) end = r.endMin
    }

    start = maxOf(0, start)
    if (end <= start) end = minOf(MINUTES_IN_DAY, start + snap)
    return BusinessWindow(startMin = start, endMin = end)
}

/** Posição/altura absolutas de uma faixa dentro da janela (unidades abstratas). */
data class BlockBox(val top: Float, val height: Float)

/**
 * Posiciona uma faixa por **minuto real**: `top = (início − janela.start) × unitPerMinute`,
 * `height = duração × unitPerMinute` (nunca menor que `minHeight`). Recortada à janela no topo
 * (top ≥ 0) preservando a altura visível.
 */
fun positionInWindow(
    range: MinuteRange,
    window: BusinessWindow,
    unitPerMinute: Float,
    minHeight: Float = 0f,
): BlockBox {
    val rawTop = (range.startMin - window.startMin) * unitPerMinute
    val rawHeight = (range.endMin - range.startMin) * unitPerMinute
    val top = maxOf(0f, rawTop)
    // Se o topo foi cortado (evento começa antes da janela), desconta da altura.
    val clipped = rawHeight - (top - rawTop)
    val height = maxOf(minHeight, clipped)
    return BlockBox(top = top, height = height)
}

/** Item de evento com sua faixa e posição na lane (coluna paralela de sobreposição). */
data class LaidOutEvent<T>(
    val event: T,
    val range: MinuteRange,
    /** Índice da lane (0-based) dentro do cluster de sobreposição. */
    val laneIndex: Int,
    /** Total de lanes do cluster (largura do bloco = 1/laneCount). */
    val laneCount: Int,
)

/**
 * Empacota eventos em **lanes** (algoritmo padrão de calendário): agrupa por clusters de sobreposição
 * transitiva; dentro do cluster, cada evento cai na primeira lane livre (cuja última faixa termina ≤
 * início do evento). Todo evento do cluster recebe o mesmo `laneCount` → largura = `1/laneCount`,
 * `left = laneIndex/laneCount`. Determinístico: ordena por início asc, fim asc, `id` (estável).
 */
fun <T> packLanes(
    events: List<T>,
    getRange: (T) -> MinuteRange?,
    id: (T) -> String,
): List<LaidOutEvent<T>> {
    data class WithRange(val event: T, val range: MinuteRange)

    val withRange = ArrayList<WithRange>()
    for (event in events) {
        val range = getRange(event)
        if (range != null) withRange.add(WithRange(event, range))
    }
    withRange.sortWith(
        compareBy({ it.range.startMin }, { it.range.endMin }, { id(it.event) }),
    )

    val result = ArrayList<LaidOutEvent<T>>()
    var cluster = ArrayList<WithRange>()
    var clusterLanes = ArrayList<Int>() // último endMin por lane, no cluster corrente
    var clusterMaxEnd = Int.MIN_VALUE
    val laneOf = HashMap<String, Int>()

    fun flushCluster() {
        val laneCount = maxOf(1, clusterLanes.size)
        for (item in cluster) {
            result.add(
                LaidOutEvent(
                    event = item.event,
                    range = item.range,
                    laneIndex = laneOf[id(item.event)] ?: 0,
                    laneCount = laneCount,
                ),
            )
        }
        cluster = ArrayList()
        clusterLanes = ArrayList()
        clusterMaxEnd = Int.MIN_VALUE
        laneOf.clear()
    }

    for (item in withRange) {
        // Novo cluster quando o evento começa em/depois do fim de tudo que veio antes.
        if (cluster.isNotEmpty() && item.range.startMin >= clusterMaxEnd) {
            flushCluster()
        }
        // Primeira lane cuja última faixa terminou ≤ início deste evento.
        var lane = clusterLanes.indexOfFirst { it <= item.range.startMin }
        if (lane == -1) {
            lane = clusterLanes.size
            clusterLanes.add(item.range.endMin)
        } else {
            clusterLanes[lane] = item.range.endMin
        }
        laneOf[id(item.event)] = lane
        cluster.add(item)
        clusterMaxEnd = maxOf(clusterMaxEnd, item.range.endMin)
    }
    if (cluster.isNotEmpty()) flushCluster()

    return result
}

/** Açúcar para eventos `ScheduleEvent` (usa `id`/`start`/`end` diretos). */
fun packEventLanes(events: List<ScheduleEvent>): List<LaidOutEvent<ScheduleEvent>> =
    packLanes(events, getRange = { toMinuteRange(it.start, it.end) }, id = { it.id })

/** Faixas de FORA do expediente dentro da janela (para sombrear). Complemento de `availableRanges`. */
fun offHoursRanges(
    window: BusinessWindow,
    availableRanges: List<WorkRange>?,
): List<MinuteRange> {
    if (availableRanges.isNullOrEmpty()) {
        // Sem info de disponibilidade ⇒ nada sombreado (não assume fechado).
        return emptyList()
    }
    val sorted = availableRanges
        .map {
            MinuteRange(
                startMin = maxOf(it.startMin, window.startMin),
                endMin = minOf(it.endMin, window.endMin),
            )
        }
        .filter { it.endMin > it.startMin }
        .sortedBy { it.startMin }

    val out = ArrayList<MinuteRange>()
    var cursor = window.startMin
    for (r in sorted) {
        if (r.startMin > cursor) out.add(MinuteRange(cursor, r.startMin))
        cursor = maxOf(cursor, r.endMin)
    }
    if (cursor < window.endMin) out.add(MinuteRange(cursor, window.endMin))
    return out
}

/**
 * Colunas que recebem a **linha do "agora"** — regra pura **recurso×dia** do [AppTimeGridScheduler]:
 * - [hasNow] `false` (sem "agora" dentro da janela visível) ⇒ **nenhuma** coluna;
 * - [nowColumnId] `null` (colunas = **recursos do mesmo dia**, vários profissionais) ⇒ **todas** as
 *   colunas (o horário "agora" vale para todos);
 * - [nowColumnId] presente (colunas = **dias**, visão Semana) ⇒ **só** a coluna cujo id casa (a de
 *   hoje). Id inexistente ⇒ lista vazia (evita a linha se repetir falsamente nos 7 dias).
 *
 * Determinístico e sem Compose — base testável do desenho da linha.
 */
fun nowLineColumnIds(
    columnIds: List<String>,
    nowColumnId: String?,
    hasNow: Boolean,
): List<String> {
    if (!hasNow) return emptyList()
    if (nowColumnId == null) return columnIds
    return columnIds.filter { it == nowColumnId }
}

/** Marcas de hora (rótulos do eixo à esquerda) da janela, no passo dado. */
fun hourTicks(window: BusinessWindow, stepMin: Int = 60): List<Int> {
    val step = if (stepMin > 0) stepMin else 60
    val first = ceilToStep(window.startMin, step)
    val ticks = ArrayList<Int>()
    var t = first
    while (t <= window.endMin) {
        ticks.add(t)
        t += step
    }
    return ticks
}
