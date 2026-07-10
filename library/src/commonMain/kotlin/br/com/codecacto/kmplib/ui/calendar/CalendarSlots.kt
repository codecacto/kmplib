package br.com.codecacto.kmplib.ui.calendar

/* ─────────────────────────── geração de slots (PURA) ───────────────────────────
 * Espelha `calendar/slots.ts` da weblib. O motor de disponibilidade REAL (interseção de expediente ×
 * folgas × agendamentos × lead time × combo contíguo) é server-side/no projeto (Meu Barbeiro §7/§8,
 * DP-01) — a lib NÃO decide disponibilidade de negócio. Este helper gera os slots CANDIDATOS de uma
 * janela por passo e os marca livre/ocupado a partir de faixas ocupadas + lead time. O `AppSlotPicker`
 * renderiza o resultado (do motor OU daqui).
 */

data class GenerateSlotsOptions(
    /** Início da janela (min-do-dia). */
    val startMin: Int,
    /** Fim da janela (min-do-dia). Só entra slot cujo FIM ≤ este. */
    val endMin: Int,
    /** Passo entre inícios de slot (ex.: `slot_step_min`, default 30). */
    val stepMin: Int,
    /** Duração do slot/atendimento (ex.: duração total do combo). Default = `stepMin`. */
    val slotMin: Int? = null,
    /** Faixas ocupadas (agendamentos + folgas) — slot que sobrepõe fica indisponível. */
    val busy: List<MinuteRange> = emptyList(),
    /** "Agora" em min-do-dia (p/ ocultar/desabilitar passado + aplicar lead time). `null` = ignora. */
    val nowMin: Int? = null,
    /** Antecedência mínima (min) a partir de `nowMin`. Default 0. */
    val leadMin: Int = 0,
    /** Motivo p/ slot no passado/lead. Default "Indisponível". */
    val pastReason: String = "Indisponível",
    /** Motivo p/ slot ocupado. Default "Ocupado". */
    val busyReason: String = "Ocupado",
)

/**
 * Gera os slots candidatos `[startMin, endMin)` por `stepMin`, cada um marcado `available` = (não
 * sobrepõe `busy`) E (início ≥ `nowMin + leadMin`). Puro e determinístico.
 */
fun generateTimeSlots(options: GenerateSlotsOptions): List<TimeSlot> {
    val stepMin = options.stepMin
    val slotMin = options.slotMin?.takeIf { it > 0 } ?: stepMin
    if (stepMin <= 0 || slotMin <= 0) return emptyList()

    val busy = options.busy
    val earliest = options.nowMin?.let { it + options.leadMin }

    val slots = ArrayList<TimeSlot>()
    var t = options.startMin
    while (t + slotMin <= options.endMin) {
        val range = MinuteRange(t, t + slotMin)
        val isPast = earliest != null && t < earliest
        val isBusy = busy.any { rangesOverlap(range, it) }
        val available = !isPast && !isBusy
        slots.add(
            TimeSlot(
                startMin = t,
                endMin = t + slotMin,
                available = available,
                reason = if (available) null else if (isPast) options.pastReason else options.busyReason,
            ),
        )
        t += stepMin
    }
    return slots
}

/** Só os slots livres (conveniência p/ contadores/"próxima vaga"). */
fun availableSlots(slots: List<TimeSlot>): List<TimeSlot> = slots.filter { it.available }
