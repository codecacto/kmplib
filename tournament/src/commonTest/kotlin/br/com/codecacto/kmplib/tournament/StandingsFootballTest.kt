package br.com.codecacto.kmplib.tournament

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O regulamento do futebol/pênalti — o que o TorneioDePenalti usa hoje. A promessa é **tabela
 * idêntica** à da `Classificacao` do app: ele vai migrar e não pode mudar a posição de ninguém.
 */
class StandingsFootballTest {

    private val rules = StandingsRules.FOOTBALL

    /** Os casos do `VerificacaoDoMotorTest`, cada critério isolado do seguinte. */
    @Test
    fun pointsHeadToHeadAndGoalDifference() {
        val four = participants(1, 2, 3, 4)

        // (a) P1 vence tudo (9 pts). P2 goleia P3 por 9x0 mas perde para P1 e P4: PONTOS vencem
        //     saldo, e CONFRONTO DIRETO vence saldo (P4 sobe sobre P2 com saldo 0 contra +7).
        val table = Standings.compute(
            four,
            listOf(win(1, 2), win(1, 3), win(1, 4), win(2, 3, Score.Points(9, 0)), win(4, 2)),
            rules,
        )
        assertEquals(listOf(1L, 4L, 2L, 3L), table.order())
        assertEquals(9, table.first { it.participantId == 1L }.points)
        assertEquals(listOf(1, 2, 3, 4), table.map { it.position })
        val p2 = table.first { it.participantId == 2L }
        val p4 = table.first { it.participantId == 4L }
        assertEquals(3, p2.points)
        assertEquals(3, p4.points)
        assertTrue(p2.gameDifference > p4.gameDifference, "o saldo aponta o contrário do confronto direto")
        assertEquals(0, p2.setsFor + p2.setsAgainst, "placar por pontos não tem sets")
        assertEquals(9, p2.gamesFor, "gols marcados = games")

        // (b) Ciclo de três (1→2→3→1): o mini-campeonato empata em tudo, e o SALDO decide.
        val cycle = Standings.compute(
            participants(1, 2, 3),
            listOf(win(1, 2), win(2, 3), win(3, 1, Score.Points(5, 0))),
            rules,
        )
        assertEquals(listOf(3, 3, 3), cycle.map { it.points })
        assertEquals(3L, cycle.first().participantId)

        // (c) Empate: 1 ponto para cada, e a coluna conta.
        val drawn = Standings.compute(participants(1, 2), listOf(draw(1, 2)), rules)
        assertTrue(drawn.all { it.points == 1 && it.draws == 1 && it.played == 1 })

        // (d) Em andamento e jogo com quem não é da tabela não contam.
        val ignored = Standings.compute(
            participants(1, 2),
            listOf(MatchResult(1, 2, MatchOutcome.InProgress, Score.Points.ZERO), win(1, 99)),
            rules,
        )
        assertTrue(ignored.all { it.played == 0 })
        assertEquals(listOf(1L, 2L), ignored.order(), "sem jogo, decide a ordem do sorteio")
    }

    /**
     * O mini-campeonato é entre **todos** os empatados em pontos, numa passada só — não é recursivo.
     * 1, 2, 3 e 4 empatam em 6 pontos; o mini-campeonato entre os quatro separa {1,2} (6) de {3,4}
     * (3). Dentro de cada bloco quem decide é o SALDO, mesmo o 2 tendo vencido o 1 e o 3 tendo
     * vencido o 4 — é o comportamento do TorneioDePenalti. Com a regra recursiva do beach tennis
     * (empate de dois → confronto direto), os mesmos jogos dão a ordem oposta dentro dos blocos.
     */
    @Test
    fun headToHeadIsASinglePassMiniLeague() {
        val results = listOf(
            win(2, 1),
            win(1, 3, Score.Points(5, 0)),
            win(1, 4),
            win(2, 3),
            win(4, 2),
            win(3, 4),
            win(3, 5),
            win(4, 6, Score.Points(5, 0)),
        )
        val people = participants(1, 2, 3, 4, 5, 6)
        val table = Standings.compute(people, results, rules)
        assertEquals(listOf(6, 6, 6, 6, 0, 0), table.map { it.points })
        assertEquals(listOf(1L, 2L, 4L, 3L, 5L, 6L), table.order())

        val recursive = rules.copy(twoWayTieBreakers = listOf(TieBreaker.HEAD_TO_HEAD, TieBreaker.GAME_DIFFERENCE))
        assertEquals(listOf(2L, 1L, 3L, 4L, 5L, 6L), Standings.compute(people, results, recursive).order())
    }

    /**
     * **A prova da migração**: o algoritmo antigo do TorneioDePenalti, reescrito aqui como oráculo,
     * contra o motor novo em milhares de tabelas aleatórias com muito empate (placares de 0 a 2).
     */
    @Test
    fun identicalToTheLegacyTorneioDePenaltiTable() {
        val random = Random(2026)
        var tables = 0
        repeat(3000) {
            val n = random.nextInt(2, 9)
            val people = List(n) { TournamentParticipant(id = (it + 1).toLong() * 7, drawOrder = it) }.shuffled(random)
            val results = buildList {
                for (i in 0 until n) for (j in i + 1 until n) {
                    if (random.nextInt(6) == 0) continue // jogo ainda não cadastrado
                    val a = people[i].id
                    val b = people[j].id
                    val outcome = when (random.nextInt(5)) {
                        0, 1 -> MatchOutcome.Decided(Side.A)
                        2, 3 -> MatchOutcome.Decided(Side.B)
                        else -> if (random.nextInt(4) == 0) MatchOutcome.InProgress else MatchOutcome.Draw
                    }
                    add(MatchResult(a, b, outcome, Score.Points(random.nextInt(3), random.nextInt(3))))
                }
            }
            val expected = legacyTable(people, results)
            val actual = Standings.compute(people, results, rules)
            assertEquals(expected.map { it.first }, actual.order(), "tabela $it divergiu do TorneioDePenalti")
            assertEquals(expected.map { it.second }, actual.map { it.points }, "pontos divergiram na tabela $it")
            assertEquals(expected.map { it.third }, actual.map { it.gameDifference }, "saldo divergiu na tabela $it")
            tables++
        }
        assertEquals(3000, tables)
    }

    /** Porte literal de `Classificacao.calcular` do TorneioDePenalti (pré-2.248.0). */
    private fun legacyTable(people: List<TournamentParticipant>, results: List<MatchResult>): List<Triple<Long, Int, Int>> {
        val eligible = people.map { it.id }.toSet()
        val finished = results.filter { it.outcome.isFinished && it.sideAId in eligible && it.sideBId in eligible }
        val points = people.associate { it.id to 0 }.toMutableMap()
        val balance = people.associate { it.id to 0 }.toMutableMap()
        finished.forEach { r ->
            val s = r.score as Score.Points
            balance[r.sideAId] = balance.getValue(r.sideAId) + s.a - s.b
            balance[r.sideBId] = balance.getValue(r.sideBId) + s.b - s.a
            when (val o = r.outcome) {
                is MatchOutcome.Decided -> {
                    val w = if (o.winner == Side.A) r.sideAId else r.sideBId
                    points[w] = points.getValue(w) + 3
                }
                MatchOutcome.Draw -> {
                    points[r.sideAId] = points.getValue(r.sideAId) + 1
                    points[r.sideBId] = points.getValue(r.sideBId) + 1
                }
                else -> Unit
            }
        }
        val order = people.associate { it.id to it.drawOrder }
        val h2h = people.associate { p ->
            val tied = people.map { it.id }.filter { points[it] == points[p.id] }.toSet()
            var mini = 0
            if (tied.size >= 2) {
                finished.forEach { r ->
                    if (r.sideAId !in tied || r.sideBId !in tied) return@forEach
                    val side = when (p.id) {
                        r.sideAId -> Side.A
                        r.sideBId -> Side.B
                        else -> return@forEach
                    }
                    mini += when (val o = r.outcome) {
                        is MatchOutcome.Decided -> if (o.winner == side) 3 else 0
                        MatchOutcome.Draw -> 1
                        else -> 0
                    }
                }
            }
            p.id to mini
        }
        return people.map { it.id }
            .sortedWith(
                compareByDescending<Long> { points.getValue(it) }
                    .thenByDescending { h2h.getValue(it) }
                    .thenByDescending { balance.getValue(it) }
                    .thenBy { order.getValue(it) },
            )
            .map { Triple(it, points.getValue(it), balance.getValue(it)) }
    }

    @Test
    fun withdrawalCountsRemainingMatchesAsLosses() {
        // O 3 desistiu depois de vencer o 1. O jogo dele com o 2 (pendente) vira W.O. 3-0 para o 2.
        val table = Standings.compute(
            participants(1, 2, 3),
            listOf(
                win(3, 1, Score.Points(2, 0)),
                MatchResult(2, 3, MatchOutcome.InProgress, Score.Points.ZERO),
                win(1, 2),
            ),
            rules,
            withdrawn = setOf(3),
        )
        assertEquals(listOf(1L, 2L, 3L), table.order(), "quem desistiu vai para o fim")
        val p2 = table.first { it.participantId == 2L }
        assertEquals(3, p2.points, "o W.O. vale vitória")
        assertEquals(3, p2.gamesFor, "com o placar convencionado de 3-0")
        val p3 = table.last()
        assertTrue(p3.withdrawn)
        assertEquals(3, p3.points, "o jogo já jogado continua valendo")
        assertEquals(1, p3.losses)
        val p1 = table.first()
        assertEquals(3, p1.points)
        assertEquals(1, p1.losses, "a derrota para quem desistiu continua valendo")
    }
}
