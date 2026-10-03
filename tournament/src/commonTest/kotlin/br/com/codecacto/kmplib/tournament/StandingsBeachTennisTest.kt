package br.com.codecacto.kmplib.tournament

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * O regulamento de beach tennis (SESC/ITF): vitórias; empate de 2 → confronto direto; de 3+ →
 * saldo de sets → saldo de games → sorteio; e, se no meio do desempate de 3+ sobrarem 2, volta ao
 * confronto direto.
 */
class StandingsBeachTennisTest {

    private val rules = StandingsRules.BEACH_TENNIS

    private fun md3(a: Long, b: Long, vararg sets: SetScore) =
        MatchResult(a, b, MatchFormat.BEST_OF_THREE_MATCH_TIE_BREAK.outcomeOf(Score.Sets(*sets)), Score.Sets(*sets))

    @Test
    fun twoWayTieIsDecidedByHeadToHead() {
        val table = Standings.compute(
            participants(1, 2, 3, 4),
            listOf(
                setWin(1, 2, 6 to 4),
                setWin(2, 3, 6 to 0),
                setWin(2, 4, 6 to 0),
                setWin(3, 1, 7 to 5),
                setWin(1, 4, 6 to 4),
                setWin(4, 3, 6 to 4),
            ),
            rules,
        )
        assertEquals(listOf(1L, 2L, 4L, 3L), table.order())
        val p1 = table[0]
        val p2 = table[1]
        assertEquals(2, p1.points, "1 ponto por vitória")
        assertEquals(2, p1.wins)
        assertTrue(p2.gameDifference > p1.gameDifference, "o saldo de games aponta o contrário: decidiu o confronto direto")
        assertEquals(3, p1.played)
        assertEquals(2, p1.setsFor)
        assertEquals(1, p1.setsAgainst)
        assertEquals(17, p1.gamesFor)
        assertEquals(15, p1.gamesAgainst)
        assertEquals(1, p1.setDifference)
        assertEquals(2, p1.gameDifference)
        assertEquals(0, p1.draws)
        assertEquals(1, p1.losses)
    }

    /** 3 empatados: saldo de sets ANTES do de games, e sem confronto direto (ciclo). */
    @Test
    fun threeWayTieUsesSetThenGameDifference() {
        val table = Standings.compute(
            participants(1, 2, 3),
            listOf(
                md3(1, 2, SetScore(6, 4), SetScore(6, 4)),
                md3(2, 3, SetScore(6, 0), SetScore(0, 6), SetScore.matchTieBreak(10, 8)),
                md3(3, 1, SetScore(0, 6), SetScore(6, 4), SetScore.matchTieBreak(10, 8)),
            ),
            rules,
        )
        assertEquals(listOf(1, 1, 1), table.map { it.wins })
        assertEquals(listOf(1L, 3L, 2L), table.order())
        val p2 = table.first { it.participantId == 2L }
        val p3 = table.first { it.participantId == 3L }
        assertEquals(0, p3.setDifference)
        assertEquals(-1, p2.setDifference)
        assertTrue(p2.gameDifference > p3.gameDifference, "o saldo de games apontava o 2: decidiu o de sets")

        // Ciclo de 3 com saldo de sets igual: decide o saldo de games.
        val oneSet = Standings.compute(
            participants(1, 2, 3),
            listOf(setWin(1, 2, 6 to 0), setWin(2, 3, 6 to 4), setWin(3, 1, 6 to 3)),
            rules,
        )
        assertEquals(listOf(1L, 3L, 2L), oneSet.order())
    }

    /** Confronto direto entre 3 em ciclo não resolve nada — o próximo critério decide. */
    @Test
    fun headToHeadCycleOfThreeFallsThrough() {
        val withHeadToHead = rules.copy(
            tieBreakers = listOf(TieBreaker.HEAD_TO_HEAD, TieBreaker.SET_DIFFERENCE, TieBreaker.GAME_DIFFERENCE),
        )
        val table = Standings.compute(
            participants(1, 2, 3),
            listOf(setWin(1, 2, 6 to 0), setWin(2, 3, 6 to 4), setWin(3, 1, 6 to 3)),
            withHeadToHead,
        )
        assertEquals(listOf(1L, 3L, 2L), table.order(), "o ciclo empata no confronto direto; o saldo de games decide")

        // Tudo igual (ciclo de 6-4): sobra só o sorteio, na ordem sorteada.
        val allEqual = Standings.compute(
            listOf(TournamentParticipant(1, drawOrder = 2), TournamentParticipant(2, drawOrder = 0), TournamentParticipant(3, drawOrder = 1)),
            listOf(setWin(1, 2, 6 to 4), setWin(2, 3, 6 to 4), setWin(3, 1, 6 to 4)),
            withHeadToHead,
        )
        assertEquals(listOf(2L, 3L, 1L), allEqual.order())
    }

    /**
     * **A recursão**: 3 empatados em vitórias; o saldo de sets separa um e deixa DOIS empatados — e
     * esses dois voltam ao confronto direto, mesmo com o saldo de games apontando o contrário.
     */
    @Test
    fun threeWayTieReducedToTwoGoesBackToHeadToHead() {
        val results = listOf(
            md3(1, 2, SetScore(6, 4), SetScore(6, 4)),
            md3(2, 3, SetScore(6, 0), SetScore(6, 0)),
            md3(3, 1, SetScore(6, 4), SetScore(6, 4)),
            md3(1, 4, SetScore(7, 5), SetScore(7, 5)),
            md3(2, 4, SetScore(6, 0), SetScore(6, 0)),
            md3(3, 4, SetScore(6, 0), SetScore(4, 6), SetScore.matchTieBreak(10, 5)),
        )
        val people = participants(1, 2, 3, 4)
        val table = Standings.compute(people, results, rules)
        assertEquals(listOf(2, 2, 2, 0), table.map { it.wins })
        assertEquals(listOf(2, 2, 1, -5), table.map { it.setDifference })
        assertEquals(listOf(1L, 2L, 3L, 4L), table.order())
        assertTrue(table[1].gameDifference > table[0].gameDifference, "o 2 tinha saldo de games melhor")

        // Sem a recursão (critérios seguidos, sem volta ao confronto direto) o 2 passaria o 1.
        val noRecursion = rules.copy(twoWayTieBreakers = null)
        assertEquals(listOf(2L, 1L, 3L, 4L), Standings.compute(people, results, noRecursion).order())
    }

    @Test
    fun twoWayTieWithoutHeadToHeadPlayedFallsToSets() {
        // 1 e 2 ainda não se enfrentaram: o confronto direto não separa, decide o saldo.
        val table = Standings.compute(
            participants(1, 2, 3),
            listOf(setWin(1, 3, 6 to 4), setWin(2, 3, 6 to 0)),
            rules,
        )
        assertEquals(listOf(2L, 1L, 3L), table.order())
    }

    @Test
    fun walkoverCountsAsWinWithConventionalScore() {
        val format = MatchFormat.ONE_SET_OF_SIX
        val table = Standings.compute(
            participants(1, 2),
            listOf(format.walkover(1, 2, winner = Side.B)),
            rules,
        )
        assertEquals(listOf(2L, 1L), table.order())
        val winner = table.first()
        assertEquals(1, winner.wins)
        assertEquals(1, winner.setsFor)
        assertEquals(6, winner.gamesFor)
        assertEquals(0, winner.gamesAgainst)
    }

    /** SESC: quem desiste sai do grupo e os jogos dele são anulados — os já jogados também. */
    @Test
    fun withdrawalAnnulsAllMatches() {
        val results = listOf(
            setWin(4, 1, 6 to 0),
            setWin(1, 2, 6 to 3),
            setWin(2, 3, 6 to 4),
            setWin(1, 3, 6 to 2),
            MatchResult(2, 4, MatchOutcome.InProgress, Score.Sets(emptyList())),
        )
        val table = Standings.compute(participants(1, 2, 3, 4), results, rules, withdrawn = setOf(4))
        assertEquals(listOf(1L, 2L, 3L, 4L), table.order())
        val p1 = table.first()
        assertEquals(2, p1.played, "o jogo contra quem desistiu foi anulado")
        assertEquals(0, p1.losses)
        val out = table.last()
        assertTrue(out.withdrawn)
        assertEquals(0, out.played)
        assertEquals(0, out.points)

        // Com a política do futebol (derrota por W.O.), o jogo pendente vira 6-0 para o 2.
        val asLosses = rules.copy(
            withdrawalPolicy = WithdrawalPolicy.CountAsLosses(MatchFormat.ONE_SET_OF_SIX.walkoverScore(Side.A)),
        )
        val table2 = Standings.compute(participants(1, 2, 3, 4), results, asLosses, withdrawn = setOf(4))
        assertEquals(4L, table2.last().participantId, "quem desistiu vai para o fim mesmo com vitória")
        val p2 = table2.first { it.participantId == 2L }
        assertEquals(2, p2.wins)
        assertEquals(3 + 6 + 6, p2.gamesFor, "3 do jogo com o 1, 6 do jogo com o 3, 6 do W.O.")
        assertEquals(1, table2.first { it.participantId == 1L }.losses, "a derrota já jogada vale")
    }

    @Test
    fun rowRatiosAndPercentage() {
        val r = row(1, played = 3, points = 2, maxPoints = 3, setsFor = 4, setsAgainst = 2, gamesFor = 18, gamesAgainst = 0)
        assertEquals(2.0, r.setRatio)
        assertEquals(Double.POSITIVE_INFINITY, r.gameRatio)
        assertEquals(200.0 / 3, r.pointsPercentage)
        assertEquals(0.0, row(2, played = 0).pointsPercentage)
        assertEquals(0.0, row(2).setRatio, "0 ÷ 0 vale 0")

        assertEquals(0, Standings.compareRatio(12, 9, 8, 6), "12/9 = 8/6 exatamente")
        assertTrue(Standings.compareRatio(5, 0, 100, 1) > 0, "sem perdido e com ganho = infinito")
        assertEquals(0, Standings.compareRatio(5, 0, 1, 0), "infinito = infinito")
        assertTrue(Standings.compareRatio(0, 0, 1, 5) < 0, "0 ÷ 0 < 1 ÷ 5")
        assertEquals(0, Standings.compareRatio(0, 0, 0, 4))
    }

    /** Grupos de 3 e de 4 se comparam por MÉDIA, não por soma. */
    @Test
    fun acrossGroupsByAverageNotSum() {
        // 1º do grupo de 4: 3 vitórias, 18-6 em games. 1º do grupo de 3: 2 vitórias, 12-2.
        val fromFour = row(10, played = 3, points = 3, maxPoints = 3, setsFor = 3, setsAgainst = 0, gamesFor = 18, gamesAgainst = 6, drawOrder = 0)
        val fromThree = row(20, played = 2, points = 2, maxPoints = 2, setsFor = 2, setsAgainst = 0, gamesFor = 12, gamesAgainst = 2, drawOrder = 1)
        assertEquals(listOf(20L, 10L), Standings.rankAcrossGroups(listOf(fromFour, fromThree)).order())
        // Por soma o do grupo de 4 passaria (mais vitórias, mais games): é o que a média evita.
        assertEquals(
            listOf(10L, 20L),
            Standings.rankAcrossGroups(listOf(fromFour, fromThree), listOf(TieBreaker.WINS, TieBreaker.GAMES_WON)).order(),
        )
        // Aproveitamento primeiro: 2 de 3 perde para 2 de 2, mesmo com games melhores.
        val twoOfThree = row(30, played = 3, points = 2, maxPoints = 3, gamesFor = 30, gamesAgainst = 1, drawOrder = 0)
        val twoOfTwo = row(40, played = 2, points = 2, maxPoints = 2, gamesFor = 12, gamesAgainst = 10, drawOrder = 1)
        assertEquals(listOf(40L, 30L), Standings.rankAcrossGroups(listOf(twoOfThree, twoOfTwo)).order())
        // Confronto direto é ignorado entre grupos; empate total cai no sorteio.
        val a = row(1, drawOrder = 5)
        val b = row(2, drawOrder = 3)
        assertEquals(listOf(2L, 1L), Standings.rankAcrossGroups(listOf(a, b), listOf(TieBreaker.HEAD_TO_HEAD)).order())
        assertFailsWith<IllegalArgumentException> { Standings.rankAcrossGroups(listOf(a, a)) }
    }

    @Test
    fun everyCriterionOrders() {
        val x = row(1, played = 2, points = 1, maxPoints = 2, setsFor = 3, setsAgainst = 1, gamesFor = 20, gamesAgainst = 5, drawOrder = 1)
        val y = row(2, played = 2, points = 2, maxPoints = 2, setsFor = 2, setsAgainst = 2, gamesFor = 10, gamesAgainst = 10, drawOrder = 0)
        val expected = mapOf(
            TieBreaker.SET_DIFFERENCE to listOf(1L, 2L),
            TieBreaker.GAME_DIFFERENCE to listOf(1L, 2L),
            TieBreaker.SETS_WON to listOf(1L, 2L),
            TieBreaker.GAMES_WON to listOf(1L, 2L),
            TieBreaker.SET_RATIO to listOf(1L, 2L),
            TieBreaker.GAME_RATIO to listOf(1L, 2L),
            TieBreaker.POINTS_PERCENTAGE to listOf(2L, 1L),
            TieBreaker.DRAW_ORDER to listOf(2L, 1L),
            TieBreaker.WINS to listOf(2L, 1L), // empatam em 0 vitórias → sorteio
        )
        expected.forEach { (criterion, order) ->
            assertEquals(order, Standings.rankAcrossGroups(listOf(x, y), listOf(criterion)).order(), "$criterion")
        }
        val winner = x.copy(wins = 1)
        assertEquals(listOf(1L, 2L), Standings.rankAcrossGroups(listOf(winner, y), listOf(TieBreaker.WINS)).order())
    }

    @Test
    fun guards() {
        assertFailsWith<IllegalArgumentException> {
            Standings.compute(participants(1, 1), emptyList(), rules)
        }
        assertFailsWith<IllegalArgumentException> { StandingsRules(pointsPerWin = 0, tieBreakers = emptyList()) }
        // Jogo de alguém contra si mesmo não conta; tabela vazia funciona.
        assertTrue(Standings.compute(participants(1), listOf(setWin(1, 1, 6 to 0)), rules).single().played == 0)
        assertEquals(emptyList(), Standings.compute(emptyList(), emptyList(), rules))
    }
}
