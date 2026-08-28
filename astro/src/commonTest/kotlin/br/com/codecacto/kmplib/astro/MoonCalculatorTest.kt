package br.com.codecacto.kmplib.astro

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Precisão e contrato do cálculo lunar (Meeus, cap. 49/48).
 *
 * A validação NÃO é auto-referente: os instantes esperados vêm de fontes externas publicadas —
 * o Exemplo 49.a do próprio livro de Meeus e oito eclipses solares/lunares dos catálogos da NASA
 * (um eclipse só acontece EM uma lua nova/cheia, então o instante do máximo do eclipse ancora a
 * fase com precisão de minutos).
 */
class MoonCalculatorTest {

    private val saoPaulo = TimeZone.of("America/Sao_Paulo")

    // -----------------------------------------------------------------------------------
    // Precisão contra fontes externas
    // -----------------------------------------------------------------------------------

    @Test
    fun `lua nova de fevereiro de 1977 reproduz o Exemplo 49a de Meeus`() {
        // Meeus, Astronomical Algorithms (2ª ed.), Exemplo 49.a: k = -283 (lua nova de fev/1977),
        // resultado JDE = 2443192.65118 (= 1977 Fev 18, 3h37m42s TD).
        val jde = MeeusMoonPhases.ephemerisJulianDay(PrincipalMoonPhase.NEW, -283)
        assertTrue(
            abs(jde - 2443192.65118) < 2e-5,
            "JDE esperado 2443192.65118 (Exemplo 49.a de Meeus), foi $jde",
        )
    }

    @Test
    fun `instantes de lua nova conferem com eclipses solares publicados`() {
        // Eclipse total só ocorre na lua nova; instantes dos catálogos da NASA (UTC).
        assertPhaseInstant(PrincipalMoonPhase.NEW, "1999-08-11", "1999-08-11T11:09:00Z")
        assertPhaseInstant(PrincipalMoonPhase.NEW, "2017-08-21", "2017-08-21T18:30:00Z")
        assertPhaseInstant(PrincipalMoonPhase.NEW, "2024-04-08", "2024-04-08T18:21:00Z")
    }

    @Test
    fun `instantes de lua cheia conferem com eclipses lunares publicados`() {
        // Eclipse lunar só ocorre na lua cheia; instantes dos catálogos da NASA (UTC).
        assertPhaseInstant(PrincipalMoonPhase.FULL, "2000-01-21", "2000-01-21T04:40:00Z")
        assertPhaseInstant(PrincipalMoonPhase.FULL, "2018-07-27", "2018-07-27T20:20:00Z")
        assertPhaseInstant(PrincipalMoonPhase.FULL, "2019-01-21", "2019-01-21T05:16:00Z")
        assertPhaseInstant(PrincipalMoonPhase.FULL, "2022-05-16", "2022-05-16T04:14:00Z")
    }

    @Test
    fun `lua nova de referencia de 2000 bate com a efemeride publicada`() {
        assertPhaseInstant(PrincipalMoonPhase.NEW, "2000-01-06", "2000-01-06T18:14:00Z")
    }

    @Test
    fun `a aproximacao por sinodico medio erraria por horas — controle negativo`() {
        // Época de referência usada pelo atalho clássico: lua nova de 06/01/2000 18:14 UTC.
        val epoch = Instant.parse("2000-01-06T18:14:00Z")
        var worstMinutes = 0.0
        var event = MoonCalculator.nextNewMoon(Instant.parse("2020-01-01T00:00:00Z"))
        var k = 1
        repeat(40) {
            val naive = epoch + (MoonCalculator.MEAN_SYNODIC_MONTH_DAYS * lunationsBetween(epoch, event.instant)).days
            val diff = abs((event.instant - naive).inWholeSeconds) / 60.0
            if (diff > worstMinutes) worstMinutes = diff
            event = MoonCalculator.nextNewMoon(event.instant)
            k++
        }
        // Se o desvio fosse pequeno, o algoritmo caro não se justificaria. Ele não é: passa de 5 h.
        assertTrue(
            worstMinutes > 5 * 60,
            "esperado desvio > 5h do sinódico médio (justifica Meeus), maior foi ${worstMinutes.toInt()} min",
        )
    }

    // -----------------------------------------------------------------------------------
    // Fuso horário
    // -----------------------------------------------------------------------------------

    @Test
    fun `a data civil de uma lua nova depende do fuso`() {
        // 2026-03-19T01:23Z: ainda 18/03 no horário de Brasília (UTC-3).
        val event = MoonCalculator.nextNewMoon(Instant.parse("2026-03-01T00:00:00Z"))
        assertEquals(LocalDate(2026, 3, 19), event.dateIn(TimeZone.UTC))
        assertEquals(LocalDate(2026, 3, 18), event.dateIn(saoPaulo))
        assertNotEquals(event.dateIn(TimeZone.UTC), event.dateIn(saoPaulo))
    }

    @Test
    fun `phaseOn avalia o meio-dia local do fuso informado`() {
        val date = LocalDate(2026, 8, 12)
        val utc = MoonCalculator.phaseOn(date, TimeZone.UTC)
        val br = MoonCalculator.phaseOn(date, saoPaulo)
        assertEquals(12, utc.instant.toLocalDateTime(TimeZone.UTC).hour)
        assertEquals(12, br.instant.toLocalDateTime(saoPaulo).hour)
        // Mesmo dia civil, fusos diferentes ⇒ instantes diferentes ⇒ idades diferentes.
        assertNotEquals(utc.instant, br.instant)
        assertTrue(br.ageDays > utc.ageDays, "Brasília é 3h depois do UTC no mesmo meio-dia civil")
    }

    @Test
    fun `nextPhase a partir de uma data inclui uma ocorrencia no proprio dia`() {
        // A lua nova de 08/04/2024 ocorre às 18:21Z — pedindo "a partir do dia 08", é ela.
        val event = MoonCalculator.nextNewMoon(LocalDate(2024, 4, 8), TimeZone.UTC)
        assertEquals(LocalDate(2024, 4, 8), event.dateIn(TimeZone.UTC))
        assertEquals(0, event.daysFrom(LocalDate(2024, 4, 8), TimeZone.UTC))
    }

    @Test
    fun `daysFrom conta dias de calendario no fuso pedido`() {
        val event = MoonCalculator.nextNewMoon(Instant.parse("2026-03-01T00:00:00Z"))
        assertEquals(18, event.daysFrom(LocalDate(2026, 3, 1), TimeZone.UTC))
        assertEquals(17, event.daysFrom(LocalDate(2026, 3, 1), saoPaulo))
    }

    // -----------------------------------------------------------------------------------
    // Próxima / anterior / listas
    // -----------------------------------------------------------------------------------

    @Test
    fun `nextPhase e estritamente posterior e previousPhase estritamente anterior`() {
        val nova = MoonCalculator.nextNewMoon(Instant.parse("2024-04-01T00:00:00Z")).instant
        val depois = MoonCalculator.nextNewMoon(nova).instant
        val antes = MoonCalculator.previousPhase(PrincipalMoonPhase.NEW, nova).instant
        assertTrue(depois > nova, "próxima deve ser posterior")
        assertTrue(antes < nova, "anterior deve ser estritamente anterior")
        // Com inclusive = true, o próprio instante conta.
        assertEquals(nova, MoonCalculator.previousPhase(PrincipalMoonPhase.NEW, nova, inclusive = true).instant)
    }

    @Test
    fun `previousPhase de um instante no meio do ciclo devolve o marco que o abriu`() {
        val nova = MoonCalculator.nextNewMoon(Instant.parse("2026-01-01T00:00:00Z")).instant
        val meio = nova + 10.days
        assertEquals(nova, MoonCalculator.previousPhase(PrincipalMoonPhase.NEW, meio).instant)
    }

    @Test
    fun `nextPhases devolve N ocorrencias em ordem e com espacamento de mes sinodico real`() {
        val eventos = MoonCalculator.nextPhases(
            PrincipalMoonPhase.NEW,
            Instant.parse("2026-01-01T00:00:00Z"),
            count = 14,
        )
        assertEquals(14, eventos.size)
        eventos.zipWithNext().forEach { (a, b) ->
            val gapDays = (b.instant - a.instant).inWholeMinutes / 1440.0
            assertTrue(b.instant > a.instant, "ordem cronológica")
            assertTrue(
                gapDays in 29.18..29.94,
                "mês sinódico real fica entre ~29,27 e ~29,83 dias; foi $gapDays",
            )
        }
        // A 1ª da lista é exatamente o que nextPhase devolveria.
        assertEquals(
            MoonCalculator.nextNewMoon(Instant.parse("2026-01-01T00:00:00Z")).instant,
            eventos.first().instant,
        )
    }

    @Test
    fun `nextPhases com count zero ou negativo devolve lista vazia`() {
        val from = Instant.parse("2026-01-01T00:00:00Z")
        assertTrue(MoonCalculator.nextPhases(PrincipalMoonPhase.FULL, from, 0).isEmpty())
        assertTrue(MoonCalculator.nextPhases(PrincipalMoonPhase.FULL, from, -3).isEmpty())
    }

    @Test
    fun `phasesBetween devolve os quatro marcos do mes em ordem cronologica`() {
        val start = Instant.parse("2026-04-01T00:00:00Z")
        val end = Instant.parse("2026-04-30T23:59:59Z")
        val eventos = MoonCalculator.phasesBetween(start, end)
        assertTrue(eventos.size >= 4, "um mês contém ao menos os 4 marcos; vieram ${eventos.size}")
        assertEquals(eventos.sortedBy { it.instant }, eventos, "deve sair ordenado")
        assertEquals(
            PrincipalMoonPhase.entries.toSet(),
            eventos.map { it.phase }.toSet(),
            "as 4 fases principais devem aparecer",
        )
        eventos.forEach { assertTrue(it.instant > start && it.instant <= end) }
        assertTrue(MoonCalculator.phasesBetween(end, start).isEmpty(), "intervalo invertido = vazio")
    }

    // -----------------------------------------------------------------------------------
    // Virada de ano, passado e futuro
    // -----------------------------------------------------------------------------------

    @Test
    fun `virada de ano — a proxima fase cruza para o ano seguinte`() {
        val event = MoonCalculator.nextNewMoon(LocalDate(2025, 12, 31), TimeZone.UTC)
        val date = event.dateIn(TimeZone.UTC)
        assertEquals(2026, date.year)
        assertEquals(1, date.monthNumber)

        val anterior = MoonCalculator.previousPhase(PrincipalMoonPhase.NEW, LocalDate(2026, 1, 1), TimeZone.UTC)
        assertEquals(2025, anterior.dateIn(TimeZone.UTC).year)
        assertEquals(12, anterior.dateIn(TimeZone.UTC).monthNumber)
    }

    @Test
    fun `datas no passado distante continuam coerentes`() {
        // 1900: ΔT já vale ~ -3 s (ramo diferente do polinômio) e o algoritmo tem de continuar de pé.
        val eventos = MoonCalculator.nextPhases(
            PrincipalMoonPhase.NEW,
            Instant.parse("1900-01-01T00:00:00Z"),
            count = 13,
        )
        assertEquals(13, eventos.size)
        eventos.zipWithNext().forEach { (a, b) ->
            val gapDays = (b.instant - a.instant).inWholeMinutes / 1440.0
            assertTrue(gapDays in 29.18..29.94, "mês sinódico de 1900 fora da faixa: $gapDays")
        }
        val info = MoonCalculator.phaseAt(eventos.first().instant)
        assertEquals(MoonPhase.NEW, info.phase)
    }

    @Test
    fun `datas no futuro distante continuam coerentes`() {
        val eventos = MoonCalculator.nextPhases(
            PrincipalMoonPhase.FULL,
            Instant.parse("2100-06-01T00:00:00Z"),
            count = 13,
        )
        eventos.zipWithNext().forEach { (a, b) ->
            val gapDays = (b.instant - a.instant).inWholeMinutes / 1440.0
            assertTrue(gapDays in 29.18..29.94, "mês sinódico de 2100 fora da faixa: $gapDays")
        }
        val info = MoonCalculator.phaseAt(eventos.first().instant)
        assertEquals(MoonPhase.FULL, info.phase)
        assertTrue(info.illuminationPercent >= 99, "na cheia a iluminação é ~100%, foi ${info.illuminationPercent}")
    }

    // -----------------------------------------------------------------------------------
    // Estado da lua (fase, idade, iluminação)
    // -----------------------------------------------------------------------------------

    @Test
    fun `no instante da lua nova a fase e NEW e a iluminacao e praticamente zero`() {
        val nova = MoonCalculator.nextNewMoon(Instant.parse("2026-05-01T00:00:00Z")).instant
        val info = MoonCalculator.phaseAt(nova)
        assertEquals(MoonPhase.NEW, info.phase)
        assertTrue(info.illuminationPercent <= 1, "iluminação na nova = ${info.illuminationPercent}%")
        assertTrue(info.ageDays < 0.001, "idade na nova deve ser ~0, foi ${info.ageDays}")
        assertEquals(MoonPhaseGroup.NEW, info.group)
    }

    @Test
    fun `no instante da lua cheia a fase e FULL e a iluminacao e praticamente total`() {
        val cheia = MoonCalculator.nextFullMoon(Instant.parse("2026-05-01T00:00:00Z")).instant
        val info = MoonCalculator.phaseAt(cheia)
        assertEquals(MoonPhase.FULL, info.phase)
        assertTrue(info.illuminationPercent >= 99, "iluminação na cheia = ${info.illuminationPercent}%")
        assertTrue(abs(info.cycleFraction - 0.5) < 0.02, "cheia fica perto de meio ciclo: ${info.cycleFraction}")
    }

    @Test
    fun `nos quartos a iluminacao fica perto de metade do disco`() {
        listOf(PrincipalMoonPhase.FIRST_QUARTER, PrincipalMoonPhase.LAST_QUARTER).forEach { quarter ->
            val instant = MoonCalculator.nextPhase(quarter, Instant.parse("2026-05-01T00:00:00Z")).instant
            val info = MoonCalculator.phaseAt(instant)
            assertEquals(quarter.named, info.phase, "fase nomeada no marco $quarter")
            assertTrue(
                info.illuminationPercent in 45..55,
                "no $quarter a iluminação é ~50%, foi ${info.illuminationPercent}%",
            )
        }
    }

    @Test
    fun `idade e fracao do ciclo derivam das luas novas reais que cercam o instante`() {
        val instante = Instant.parse("2026-08-10T12:00:00Z")
        val info = MoonCalculator.phaseAt(instante)
        assertTrue(info.previousNewMoon <= instante, "a nova anterior precede o instante")
        assertTrue(info.nextNewMoon > instante, "a próxima nova sucede o instante")
        val esperadoDias = (instante - info.previousNewMoon).inWholeMilliseconds / 86_400_000.0
        assertTrue(abs(info.ageDays - esperadoDias) < 1e-6, "idade = tempo desde a nova anterior")
        assertTrue(info.cycleFraction in 0.0..1.0)
        assertTrue(info.cycleLengthDays in 29.18..29.94, "ciclo real = ${info.cycleLengthDays}")
    }

    @Test
    fun `a duracao do ciclo NAO e constante`() {
        val ciclos = MoonCalculator.nextPhases(
            PrincipalMoonPhase.NEW,
            Instant.parse("2026-01-01T00:00:00Z"),
            count = 14,
        ).zipWithNext().map { (a, b) -> (b.instant - a.instant).inWholeMinutes / 1440.0 }
        val menor = ciclos.min()
        val maior = ciclos.max()
        assertTrue(
            maior - menor > 0.2,
            "os ciclos variam de verdade (o atalho do sinódico médio ignora isso): $menor..$maior",
        )
    }

    @Test
    fun `iluminacao e fracao do ciclo ficam sempre dentro da faixa ao longo de um ciclo inteiro`() {
        var instante = Instant.parse("2026-06-01T00:00:00Z")
        val fim = instante + 31.days
        while (instante < fim) {
            val info = MoonCalculator.phaseAt(instante)
            assertTrue(info.illuminationFraction in 0.0..1.0, "iluminação fora da faixa em $instante")
            assertTrue(info.cycleFraction in 0.0..1.0, "fração fora da faixa em $instante")
            assertTrue(info.illuminationPercent in 0..100)
            instante += 6.hours
        }
    }

    @Test
    fun `a sequencia de fases nomeadas percorre as oito no ciclo`() {
        val nova = MoonCalculator.nextNewMoon(Instant.parse("2026-06-01T00:00:00Z")).instant
        val vistas = mutableSetOf<MoonPhase>()
        var instante = nova
        val fim = nova + 30.days
        while (instante < fim) {
            vistas += MoonCalculator.phaseAt(instante).phase
            instante += 6.hours
        }
        assertEquals(MoonPhase.entries.toSet(), vistas, "um ciclo completo passa pelas 8 fases")
    }

    @Test
    fun `namedPhaseOf mapeia a fracao do ciclo em fatias centradas nos marcos`() {
        assertEquals(MoonPhase.NEW, MoonCalculator.namedPhaseOf(0.0))
        assertEquals(MoonPhase.NEW, MoonCalculator.namedPhaseOf(0.999))
        assertEquals(MoonPhase.FIRST_QUARTER, MoonCalculator.namedPhaseOf(0.25))
        assertEquals(MoonPhase.FULL, MoonCalculator.namedPhaseOf(0.5))
        assertEquals(MoonPhase.LAST_QUARTER, MoonCalculator.namedPhaseOf(0.75))
        assertEquals(MoonPhase.WAXING_CRESCENT, MoonCalculator.namedPhaseOf(0.125))
        assertEquals(MoonPhase.WANING_CRESCENT, MoonCalculator.namedPhaseOf(0.875))
        // Cíclica: 1.25 é o mesmo ponto que 0.25.
        assertEquals(MoonCalculator.namedPhaseOf(0.25), MoonCalculator.namedPhaseOf(1.25))
        assertEquals(MoonCalculator.namedPhaseOf(0.25), MoonCalculator.namedPhaseOf(-0.75))
    }

    // -----------------------------------------------------------------------------------
    // ΔT
    // -----------------------------------------------------------------------------------

    @Test
    fun `deltaT bate com os valores publicados e nao salta nas fronteiras dos ramos`() {
        assertTrue(abs(AstroTime.deltaTSeconds(2000.0) - 63.86) < 0.1, "ΔT(2000) ≈ 63,86 s")
        assertTrue(abs(AstroTime.deltaTSeconds(1977.1) - 47.7) < 1.0, "ΔT(1977) ≈ 47,7 s")
        assertTrue(AstroTime.deltaTSeconds(2026.0) in 60.0..90.0, "ΔT(2026) na casa dos 70 s")

        // Fronteiras INTERNAS dos ramos de Espenak & Meeus: têm de emendar sem degrau.
        listOf(1700.0, 1800.0, 1860.0, 1900.0, 1920.0, 1941.0, 1961.0, 1986.0, 2005.0, 2050.0, 2150.0)
            .forEach { fronteira ->
                val antes = AstroTime.deltaTSeconds(fronteira - 0.001)
                val depois = AstroTime.deltaTSeconds(fronteira + 0.001)
                assertTrue(
                    abs(antes - depois) < 2.0,
                    "salto de ΔT em $fronteira: $antes vs $depois",
                )
            }
        // Antes de 1600 vale a parábola secular (a ΔT histórica é incerta em dezenas de segundos):
        // não se exige emenda perfeita, só ordem de grandeza coerente.
        assertTrue(AstroTime.deltaTSeconds(1600.0) in 100.0..140.0, "ΔT(1600) ≈ 120 s")
        assertTrue(AstroTime.deltaTSeconds(1500.0) > 100.0, "ΔT cresce para trás no tempo")
    }

    @Test
    fun `julian day e instante sao conversoes inversas`() {
        val instante = Instant.parse("2026-08-10T12:34:56Z")
        val jd = AstroTime.julianDay(instante)
        assertTrue(abs(AstroTime.julianDay(Instant.parse("2000-01-01T12:00:00Z")) - 2451545.0) < 1e-6)
        assertEquals(instante, AstroTime.instantOfJulianDay(jd))
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    private fun assertPhaseInstant(phase: PrincipalMoonPhase, fromDay: String, expectedUtc: String) {
        val event = MoonCalculator.nextPhase(phase, Instant.parse("${fromDay}T00:00:00Z"))
        val expected = Instant.parse(expectedUtc)
        val diff = (event.instant - expected).absoluteValue()
        assertTrue(
            diff <= 2.minutes,
            "$phase perto de $fromDay: esperado ~$expectedUtc, calculado ${event.instant} (diferença $diff)",
        )
    }

    private fun lunationsBetween(epoch: Instant, instant: Instant): Double {
        val days = (instant - epoch).inWholeSeconds / 86_400.0
        return kotlin.math.round(days / MoonCalculator.MEAN_SYNODIC_MONTH_DAYS)
    }
}

private fun kotlin.time.Duration.absoluteValue() = if (this < kotlin.time.Duration.ZERO) -this else this
