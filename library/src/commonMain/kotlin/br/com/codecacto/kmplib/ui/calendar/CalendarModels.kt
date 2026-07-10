package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDateTime

/* ─────────────────────────── modelo de dados (domínio-agnóstico) ───────────────────────────
 * Par mobile de `@codecacto/weblib/calendar`. O calendário NUNCA conhece "agendamento"/"barbeiro":
 * recebe eventos e recursos genéricos — o Influencer consome em modo recurso-único (1 coluna/timeline)
 * e o Meu Barbeiro em modo N-recursos (uma coluna por profissional), o MESMO componente. Cor/rótulo de
 * status ficam no consumidor (via `eventColors`/`renderEvent`), nunca hardcoded na lib.
 *
 * Paridade com a weblib: os nomes e a semântica (`ScheduleEvent`, `ScheduleResource`, `ScheduleBlock`,
 * `BusinessWindow`, `CalendarViewMode`, `TimeSlot`, `WorkRange`, `MinuteRange`) são espelhados. A
 * diferença de plataforma: o web recebe `Date | ISO string` e coage por parede local; o mobile recebe
 * **`kotlinx.datetime.LocalDateTime`** (parede local por construção, sem fuso) — decisão deliberada para
 * fazer a aritmética do grid só em `LocalDateTime`/`LocalTime` e NUNCA em `Instant` (typealias de
 * `kotlin.time.Instant` no kotlinx-datetime 0.7.x, raiz do incidente de release R8). O boundary de API
 * (ISO string → `LocalDateTime`) é do app (ou use `parseCalendarDateTime`).
 */

/** Uma faixa de minutos-do-dia (`[startMin, endMin)`). `endMin` pode passar de 1440 (cruza meia-noite). */
data class MinuteRange(val startMin: Int, val endMin: Int)

/** Faixa de expediente/disponibilidade em minutos-do-dia. Ex.: 08:00–19:00 = `WorkRange(480, 1140)`. */
data class WorkRange(val startMin: Int, val endMin: Int)

/** Janela horária visível da grade (minutos-do-dia). */
data class BusinessWindow(val startMin: Int, val endMin: Int)

/** Um recurso (coluna) da grade — ex.: um profissional, uma sala. */
data class ScheduleResource(
    /** Chave estável — casa com `ScheduleEvent.resourceId`. */
    val id: String,
    /** Rótulo exibido no cabeçalho da coluna. */
    val label: String,
    /** URL de avatar/foto opcional (ex.: foto do profissional). */
    val avatar: String? = null,
    /** Subtítulo opcional (ex.: especialidade). */
    val subtitle: String? = null,
    /**
     * Faixas de minutos-do-dia em que o recurso está disponível (expediente do profissional, com
     * intervalos). Fora delas a coluna é sombreada (fora-de-expediente). Vazio ⇒ sem sombreado.
     */
    val availableRanges: List<WorkRange> = emptyList(),
)

/**
 * Um evento posicionável (ex.: um agendamento). `start`/`end` são parede local (`LocalDateTime`). A
 * posição é por **minuto real** (`top`/`height` derivam de início e duração), não por balde-de-hora.
 */
data class ScheduleEvent(
    val id: String,
    /** Início (parede local). */
    val start: LocalDateTime,
    /** Fim (parede local). Se ≤ início, o bloco usa a altura mínima. */
    val end: LocalDateTime,
    /** Coluna/recurso a que pertence (default de distribuição). */
    val resourceId: String? = null,
    /** Texto principal do bloco (ex.: nome do cliente). */
    val label: String? = null,
    /** Linha secundária (ex.: serviço). */
    val description: String? = null,
    /** Chave de status do domínio (agnóstica) — o consumidor mapeia status→cor em `eventColors`. */
    val status: String? = null,
    /** Buffer (min) após o fim — desenhado como "rabo" esmaecido. */
    val bufferMin: Int = 0,
    /** Rótulo livre de tipo/kind (ex.: origem). */
    val kind: String? = null,
)

/** Tipo de camada de bloqueio na grade. */
enum class ScheduleBlockVariant {
    /** Só sombreado (fora-de-expediente). */
    OffHours,

    /** Hachurado (folga/almoço/férias/feriado/fechamento). */
    Block,
}

/** Uma faixa de indisponibilidade (almoço, folga, feriado, fechamento). */
data class ScheduleBlock(
    val id: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** Coluna alvo; `null` = cobre TODAS as colunas (fechamento do estabelecimento). */
    val resourceId: String? = null,
    /** `Block` = hachurado (folga/almoço); `OffHours` = só sombreado. Default `Block`. */
    val variant: ScheduleBlockVariant = ScheduleBlockVariant.Block,
    /** Rótulo curto (ex.: "Almoço"). */
    val label: String? = null,
)

/** Modo de visão do calendário. */
enum class CalendarViewMode {
    Day,
    Week,
    Month,
}

/** Um slot de horário (saída do motor de disponibilidade OU de `generateTimeSlots`). */
data class TimeSlot(
    /** Início em minutos-do-dia. */
    val startMin: Int,
    /** Fim em minutos-do-dia (opcional). */
    val endMin: Int? = null,
    /** Livre para escolha. */
    val available: Boolean,
    /** Rótulo custom; default "HH:mm" derivado de `startMin`. */
    val label: String? = null,
    /** Motivo da indisponibilidade (ex.: "passou", "ocupado") — a11y/tooltip. */
    val reason: String? = null,
)
