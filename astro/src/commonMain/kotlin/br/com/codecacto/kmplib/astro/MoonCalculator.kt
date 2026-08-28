package br.com.codecacto.kmplib.astro

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

/**
 * Efemérides lunares **100% offline** — fase, idade, iluminação e os **instantes exatos** das 4
 * fases principais (nova, quarto crescente, cheia, quarto minguante).
 *
 * Implementa **Meeus, _Astronomical Algorithms_ (2ª ed.)**: capítulo 49 para os instantes das fases
 * (com todos os termos periódicos e as correções planetárias) e capítulos 47/48 para a fração
 * iluminada, com conversão TT→UTC por ΔT (Espenak & Meeus). Erro típico de **segundos**, não de
 * horas. Sem rede, sem serviço externo, sem dado embarcado além dos coeficientes.
 *
 * ### Instante ou data?
 * Fase da lua é um **instante**, e a data civil dele **depende do fuso**: uma lua nova às 02:30 UTC
 * cai no dia anterior em Brasília. Por isso a API é escrita sobre [Instant], e todo overload que
 * fala em [LocalDate] exige o [TimeZone] explicitamente (default = fuso do aparelho). Quem ancora um
 * cronograma numa fase deve guardar o **instante**, e converter para data só na hora de exibir.
 *
 * ### Uso típico
 * ```kotlin
 * // Estado da lua hoje (fuso do aparelho)
 * val hoje = MoonCalculator.phaseOn(LocalDate(2026, 8, 10))
 * hoje.phase                 // MoonPhase.WAXING_GIBBOUS
 * hoje.illuminationPercent   // 87
 *
 * // Âncora de um protocolo: a próxima lua nova
 * val nova = MoonCalculator.nextPhase(PrincipalMoonPhase.NEW, Clock.System.now())
 * nova.instant               // 2026-08-12T17:37:00Z
 * nova.dateIn(TimeZone.currentSystemDefault())   // 2026-08-12
 *
 * // As 6 próximas luas cheias
 * MoonCalculator.nextPhases(PrincipalMoonPhase.FULL, Clock.System.now(), count = 6)
 * ```
 */
object MoonCalculator {

    /**
     * Duração MÉDIA do mês sinódico (nova→nova), em dias.
     *
     * Exposto só como referência/rótulo: **este valor não é usado como verdade** em nenhum cálculo
     * de fase. A duração real de cada ciclo varia entre ~29,27 e ~29,83 dias e sai dos instantes
     * calculados (ver [MoonPhaseInfo.cycleLengthDays]).
     */
    const val MEAN_SYNODIC_MONTH_DAYS: Double = MeeusMoonPhases.MEAN_SYNODIC_MONTH

    /** Limite de segurança da varredura de lunações (nunca alcançado em uso normal). */
    private const val MAX_LUNATION_SCAN = 8

    // ---------------------------------------------------------------------------------------
    // Estado da lua
    // ---------------------------------------------------------------------------------------

    /** Estado da lua no [instant] dado (fase, idade, iluminação e as luas novas que cercam o ciclo). */
    fun phaseAt(instant: Instant): MoonPhaseInfo {
        val previousNew = previousPhase(PrincipalMoonPhase.NEW, instant, inclusive = true).instant
        val nextNew = nextPhase(PrincipalMoonPhase.NEW, instant).instant

        val cycleMillis = (nextNew - previousNew).inWholeMilliseconds.toDouble()
        val elapsedMillis = (instant - previousNew).inWholeMilliseconds.toDouble()
        val fraction = if (cycleMillis > 0.0) (elapsedMillis / cycleMillis).coerceIn(0.0, 1.0) else 0.0

        return MoonPhaseInfo(
            instant = instant,
            phase = namedPhaseOf(fraction),
            ageDays = elapsedMillis / 86_400_000.0,
            cycleFraction = fraction,
            illuminationFraction = MoonIllumination.fractionAt(instant),
            previousNewMoon = previousNew,
            nextNewMoon = nextNew,
        )
    }

    /**
     * Estado da lua num DIA civil, avaliado ao **meio-dia local** — a convenção de calendário lunar
     * (o meio-dia é o ponto do dia que melhor representa "a lua deste dia", já que a fase muda
     * continuamente e a meia-noite favoreceria arbitrariamente o dia anterior).
     */
    fun phaseOn(
        date: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): MoonPhaseInfo = phaseAt(LocalDateTime(date, LocalTime(12, 0)).toInstant(timeZone))

    /** Fase nomeada a partir da posição no ciclo (`0.0`..`1.0`) — 8 fatias CENTRADAS nos marcos. */
    fun namedPhaseOf(cycleFraction: Double): MoonPhase {
        val f = ((cycleFraction % 1.0) + 1.0) % 1.0
        val slice = 1.0 / 16.0
        return when {
            f < slice * 1 -> MoonPhase.NEW
            f < slice * 3 -> MoonPhase.WAXING_CRESCENT
            f < slice * 5 -> MoonPhase.FIRST_QUARTER
            f < slice * 7 -> MoonPhase.WAXING_GIBBOUS
            f < slice * 9 -> MoonPhase.FULL
            f < slice * 11 -> MoonPhase.WANING_GIBBOUS
            f < slice * 13 -> MoonPhase.LAST_QUARTER
            f < slice * 15 -> MoonPhase.WANING_CRESCENT
            else -> MoonPhase.NEW
        }
    }

    // ---------------------------------------------------------------------------------------
    // Próxima / anterior ocorrência de uma fase principal
    // ---------------------------------------------------------------------------------------

    /**
     * Primeira ocorrência de [phase] **estritamente depois** de [from].
     *
     * É a função que ancora cronograma: `nextPhase(NEW, agora).instant` é o marco exato do início do
     * ciclo, com precisão de segundos.
     */
    fun nextPhase(phase: PrincipalMoonPhase, from: Instant): MoonPhaseEvent {
        var lunation = MeeusMoonPhases.approximateLunation(phase, from) - 1
        repeat(MAX_LUNATION_SCAN) {
            val instant = MeeusMoonPhases.instantOf(phase, lunation)
            if (instant > from) return MoonPhaseEvent(phase, instant)
            lunation++
        }
        // Inalcançável: a estimativa de lunação erra por no máximo ±1.
        return MoonPhaseEvent(phase, MeeusMoonPhases.instantOf(phase, lunation))
    }

    /**
     * Primeira ocorrência de [phase] a partir do **início do dia** [from] no fuso dado — ou seja, se
     * a fase ocorre em algum momento do próprio dia [from], é ela que volta.
     */
    fun nextPhase(
        phase: PrincipalMoonPhase,
        from: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): MoonPhaseEvent = nextPhase(phase, from.atStartOfDayIn(timeZone))

    /**
     * Última ocorrência de [phase] **antes** de [from] (com [inclusive] `true`, aceita também uma
     * ocorrência exatamente em [from]).
     */
    fun previousPhase(
        phase: PrincipalMoonPhase,
        from: Instant,
        inclusive: Boolean = false,
    ): MoonPhaseEvent {
        var lunation = MeeusMoonPhases.approximateLunation(phase, from) + 1
        repeat(MAX_LUNATION_SCAN) {
            val instant = MeeusMoonPhases.instantOf(phase, lunation)
            if (instant < from || (inclusive && instant == from)) return MoonPhaseEvent(phase, instant)
            lunation--
        }
        return MoonPhaseEvent(phase, MeeusMoonPhases.instantOf(phase, lunation))
    }

    /** Última ocorrência de [phase] antes do **início do dia** [from] no fuso dado. */
    fun previousPhase(
        phase: PrincipalMoonPhase,
        from: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): MoonPhaseEvent = previousPhase(phase, from.atStartOfDayIn(timeZone))

    /**
     * As [count] próximas ocorrências de [phase] depois de [from], em ordem cronológica.
     *
     * Serve ao planejamento de ciclos futuros ("as 6 próximas luas novas") sem o app ter de somar
     * 29,53 dias na mão — soma que acumula erro a cada iteração.
     */
    fun nextPhases(phase: PrincipalMoonPhase, from: Instant, count: Int): List<MoonPhaseEvent> {
        if (count <= 0) return emptyList()
        val first = nextPhase(phase, from)
        val firstLunation = MeeusMoonPhases.approximateLunation(phase, first.instant).let { approx ->
            // Reancora na lunação que produziu `first`, tolerando o ±1 da estimativa.
            (approx - 1..approx + 1).firstOrNull { MeeusMoonPhases.instantOf(phase, it) == first.instant }
                ?: approx
        }
        return (0 until count).map { offset ->
            if (offset == 0) first
            else MoonPhaseEvent(phase, MeeusMoonPhases.instantOf(phase, firstLunation + offset))
        }
    }

    /** As [count] próximas ocorrências de [phase] a partir do início do dia [from]. */
    fun nextPhases(
        phase: PrincipalMoonPhase,
        from: LocalDate,
        count: Int,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<MoonPhaseEvent> = nextPhases(phase, from.atStartOfDayIn(timeZone), count)

    /**
     * TODAS as ocorrências das 4 fases principais no intervalo `(start, end]`, em ordem cronológica —
     * o que um calendário mensal precisa para marcar os quatro marcos do mês de uma vez.
     */
    fun phasesBetween(start: Instant, end: Instant): List<MoonPhaseEvent> {
        if (end <= start) return emptyList()
        val events = mutableListOf<MoonPhaseEvent>()
        PrincipalMoonPhase.entries.forEach { phase ->
            var event = nextPhase(phase, start)
            while (event.instant <= end) {
                events += event
                event = nextPhase(phase, event.instant)
            }
        }
        return events.sortedBy { it.instant }
    }

    // ---------------------------------------------------------------------------------------
    // Atalhos de leitura (açúcar sobre nextPhase/previousPhase)
    // ---------------------------------------------------------------------------------------

    /** Próxima lua nova depois de [from]. */
    fun nextNewMoon(from: Instant): MoonPhaseEvent = nextPhase(PrincipalMoonPhase.NEW, from)

    /** Próxima lua nova a partir do início do dia [from]. */
    fun nextNewMoon(
        from: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): MoonPhaseEvent = nextPhase(PrincipalMoonPhase.NEW, from, timeZone)

    /** Próxima lua cheia depois de [from]. */
    fun nextFullMoon(from: Instant): MoonPhaseEvent = nextPhase(PrincipalMoonPhase.FULL, from)

    /** Próxima lua cheia a partir do início do dia [from]. */
    fun nextFullMoon(
        from: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): MoonPhaseEvent = nextPhase(PrincipalMoonPhase.FULL, from, timeZone)
}
