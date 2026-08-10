package br.com.codecacto.kmplib.astro

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * Estado da lua num instante: qual fase, quanto do ciclo já passou, quanta luz aparece.
 *
 * Todos os campos derivam dos instantes REAIS das luas novas que cercam [instant] — não de um mês
 * sinódico médio. Por isso [ageDays] e [cycleFraction] podem discordar levemente de uma regra de
 * três sobre 29,53 dias: o ciclo corrente tem a duração que tem.
 */
data class MoonPhaseInfo(
    /** Instante avaliado. */
    val instant: Instant,
    /** Fase nomeada (uma das 8). */
    val phase: MoonPhase,
    /** Idade da lua: dias decorridos desde a lua nova anterior (0 ≤ idade < duração do ciclo). */
    val ageDays: Double,
    /** Posição no ciclo corrente, `0.0` (lua nova anterior) a `1.0` (próxima lua nova). */
    val cycleFraction: Double,
    /** Fração do disco iluminada, `0.0` (nova) a `1.0` (cheia). */
    val illuminationFraction: Double,
    /** Instante exato da lua nova que ABRE o ciclo corrente. */
    val previousNewMoon: Instant,
    /** Instante exato da lua nova que FECHA o ciclo corrente. */
    val nextNewMoon: Instant,
) {
    /** Iluminação em percentual inteiro (0..100), para exibição. */
    val illuminationPercent: Int
        get() = (illuminationFraction * 100.0).roundToInt().coerceIn(0, 100)

    /** Duração real do ciclo corrente, em dias (≈ 29,27 a 29,83 — nunca constante). */
    val cycleLengthDays: Double
        get() = (nextNewMoon - previousNewMoon).inWholeMilliseconds / 86_400_000.0

    /** `true` ganhando luz, `false` perdendo, `null` nos picos (nova/cheia) — ver [MoonPhase.isWaxing]. */
    val isWaxing: Boolean?
        get() = phase.isWaxing

    /** Agrupamento amplo da fase. */
    val group: MoonPhaseGroup
        get() = phase.group
}

/**
 * Ocorrência de uma das 4 fases principais num **instante exato**.
 *
 * O instante é a verdade; a **data civil depende do fuso** e por isso não é campo, e sim
 * [dateIn]/[dateTimeIn]. Uma lua nova às 02:30 UTC cai no dia anterior no horário de Brasília —
 * guardar "a data" sem o fuso é exatamente como se erra o começo de um cronograma por um dia.
 */
data class MoonPhaseEvent(
    val phase: PrincipalMoonPhase,
    val instant: Instant,
) {
    /** Data civil da ocorrência no fuso informado. */
    fun dateIn(timeZone: TimeZone): LocalDate = instant.toLocalDateTime(timeZone).date

    /** Data e hora civis da ocorrência no fuso informado. */
    fun dateTimeIn(timeZone: TimeZone): LocalDateTime = instant.toLocalDateTime(timeZone)

    /**
     * Dias inteiros de calendário entre [from] e a data local da ocorrência (0 = é hoje).
     * Use para "faltam N dias", que é contagem de **dias de calendário**, não de 24 h.
     */
    fun daysFrom(from: LocalDate, timeZone: TimeZone): Int = from.daysUntil(dateIn(timeZone))
}
