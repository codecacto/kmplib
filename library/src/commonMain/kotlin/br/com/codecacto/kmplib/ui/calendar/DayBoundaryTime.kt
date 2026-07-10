package br.com.codecacto.kmplib.ui.calendar

/* ─────────────────────────── minuto-do-dia com FIM DE DIA ("24:00") ───────────────────────────
 * Núcleo PURO e testável para horário de FAIXA (expediente / disponibilidade), onde o **fim** de uma
 * faixa pode ser a própria meia-noite.
 *
 * Por que "24:00" existe — e por que NÃO é `LocalTime`:
 *   Um salão que fecha à meia-noite tem o expediente 18:00 → **24:00**. Modelado como "minuto do dia"
 *   (`Int`, 0..1440), `1440` = fim do dia = "24:00". `kotlinx.datetime.LocalTime` NÃO representa 24:00
 *   (só 00:00..23:59): usá-lo apagaria o fim de dia e o slot das 23:30 nunca seria oferecido (o bug real
 *   do Meu Barbeiro, D-20). Por isso a fronteira do dia trafega como minuto-do-dia (`Int`) ou "HH:mm"
 *   `String` com "24:00" válido — NUNCA `LocalTime`.
 *
 * "24:00" só faz sentido como **FIM** de faixa. Como **início** é inválido (uma faixa que começa no fim
 * do dia é vazia). Isso NÃO é confiado ao consumidor: [parseDayMinute] e [dayTimeOptions] recebem um
 * [DayTimeRole]; no papel [DayTimeRole.Start] o "24:00" é rejeitado/ausente por construção.
 *
 * Distinto de [parseTimeOfDay]/[formatTimeOfDay] (em `CalendarTime.kt`), que espelham a weblib e
 * rejeitam/espelham 24:00 (0..1439) — aqueles servem à aritmética do grid; estes, à edição de faixa.
 */

/** Minuto do dia que representa o **fim do dia** / meia-noite ("24:00") como fim de faixa. */
const val END_OF_DAY_MINUTE: Int = MINUTES_IN_DAY // 1440

/**
 * Papel de um campo de horário numa faixa. Torna explícito (na API, não no consumidor) que o fim do dia
 * ("24:00") só é aceito como [End]; como [Start] é sempre inválido.
 */
enum class DayTimeRole {
    /** Início da faixa: 0..1439. "24:00" é rejeitado (início no fim do dia = faixa vazia). */
    Start,

    /** Fim da faixa: 0..1440. "24:00" (= [END_OF_DAY_MINUTE]) é válido (salão que fecha à meia-noite). */
    End,
}

/**
 * Formata minuto-do-dia 0..1440 como "HH:mm". `1440` (e acima) vira **"24:00"** — NÃO espelha para
 * "00:00" (é o fim do dia, não o começo). Valores 0..1439 delegam a [formatTimeOfDay]; negativos → "00:00".
 */
fun formatDayMinute(minutes: Int): String {
    if (minutes >= END_OF_DAY_MINUTE) return "24:00"
    return formatTimeOfDay(minutes)
}

/**
 * Converte "HH:mm" para minuto-do-dia conforme o [role]:
 *  - "24:00" → [END_OF_DAY_MINUTE] se [role] == [DayTimeRole.End]; **`null`** se [DayTimeRole.Start].
 *  - demais valores → 0..1439 via [parseTimeOfDay] (`null` se inválido).
 *
 * Assim "24:00 como início" é impossível pela própria API.
 */
fun parseDayMinute(value: String, role: DayTimeRole): Int? {
    val trimmed = value.trim()
    if (trimmed == "24:00") return if (role == DayTimeRole.End) END_OF_DAY_MINUTE else null
    return parseTimeOfDay(trimmed)
}

/**
 * Opções "HH:mm" de um campo de horário de faixa, de "00:00" ao limite do [role] (inclusive), passo
 * [stepMin] (clampado 5..60). [DayTimeRole.End] inclui **"24:00"**; [DayTimeRole.Start] vai só até 23:xx
 * (24:00 é inválido como início). Pura/testável — alimenta o dropdown do `AppDayTimePicker`.
 */
fun dayTimeOptions(role: DayTimeRole, stepMin: Int = 30): List<String> {
    val step = stepMin.coerceIn(5, 60)
    val last = if (role == DayTimeRole.End) END_OF_DAY_MINUTE else END_OF_DAY_MINUTE - 1
    val out = ArrayList<String>()
    var m = 0
    while (m <= last) {
        out.add(formatDayMinute(m))
        m += step
    }
    return out
}
