package br.com.codecacto.kmplib.tournament

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A semeadura dos classificados — portado do `VerificacaoDoMotorTest` (ordem dos grupos) e estendido
 * com a semeadura por campanha (SESC) e a desistência.
 */
class QualifiersTest {

    private fun groupOf(tables: List<List<StandingsRow>>): Map<Long, Int> = buildMap {
        tables.forEachIndexed { index, table -> table.forEach { put(it.participantId, index) } }
    }

    private fun check(
        title: String,
        tables: List<List<StandingsRow>>,
        perGroup: Int,
        expected: List<Long>,
        expectedByes: List<Long>,
    ) {
        val group = groupOf(tables)
        val order = Qualifiers.forKnockout(tables, perGroup)
        assertEquals(expected, order, "$title: ordem de entrada")
        val first = TournamentGenerator.knockoutFromQualifiers(order, firstPhaseOrder = 1).phases.first()
        first.matches.filter { !it.isBye }.forEach {
            assertFalse(group.getValue(it.sideAId!!) == group.getValue(it.sideBId!!), "$title: reencontro de grupo")
        }
        assertEquals(expectedByes, first.matches.filter { it.isBye }.map { it.sideAId }, "$title: o BYE mudou de dono")
        val slots = first.matches.flatMap { listOfNotNull(it.sideAId, it.sideBId) }
        assertEquals(order.toSet(), slots.toSet())
        assertEquals(order.size, slots.size)
    }

    @Test
    fun groupOrderSeedingCrossesGroups() {
        check("2 grupos x 1", listOf(table(10, 11), table(20, 21)), 1, listOf(10L, 20L), emptyList())
        check("2 grupos x 2", listOf(table(10, 11), table(20, 21)), 2, listOf(10L, 20L, 11L, 21L), emptyList())
        // O único caso em que a semeadura sozinha juntaria 1ºC × 2ºC: o cruzamento troca o lado B.
        check(
            "3 grupos x 2",
            listOf(table(10, 11), table(20, 21), table(30, 31)),
            2,
            listOf(10L, 20L, 30L, 11L, 31L, 21L),
            listOf(10L, 20L),
        )
        check(
            "4 grupos x 2",
            listOf(table(10, 11), table(20, 21), table(30, 31), table(40, 41)),
            2,
            listOf(10L, 20L, 30L, 40L, 11L, 21L, 31L, 41L),
            emptyList(),
        )
        check(
            "3 grupos x 3",
            listOf(table(10, 11, 12), table(20, 21, 22), table(30, 31, 32)),
            3,
            listOf(10L, 20L, 30L, 11L, 21L, 31L, 12L, 22L, 32L),
            listOf(10L, 11L, 21L, 20L, 12L, 30L, 31L),
        )
        check(
            "6 grupos x 2",
            listOf(table(10, 11), table(20, 21), table(30, 31), table(40, 41), table(50, 51), table(60, 61)),
            2,
            listOf(10L, 20L, 30L, 40L, 50L, 60L, 11L, 21L, 31L, 41L, 51L, 61L),
            listOf(10L, 40L, 20L, 30L),
        )
        assertEquals(listOf(10L), Qualifiers.forKnockout(listOf(table(10, 11)), 1), "grupo único com 1 = o líder")
        assertFailsWith<IllegalArgumentException> { Qualifiers.forKnockout(listOf(table(10, 11)), 0) }
    }

    /** Varredura de 1 a 8 grupos × 1 a 4 classificados: BYE sempre nas melhores cabeças. */
    @Test
    fun sweepGroupOrder() = sweep { tables, perGroup -> Qualifiers.forKnockout(tables, perGroup) }

    /** A mesma varredura com semeadura por campanha e campanhas sorteadas: nenhuma regressão. */
    @Test
    fun sweepByCampaign() {
        val random = Random(48)
        sweep(repetitions = 20) { tables, perGroup ->
            val scrambled = tables.map { table ->
                table.map { it.copy(points = random.nextInt(4), maxPoints = 3, gamesFor = random.nextInt(20), gamesAgainst = random.nextInt(20)) }
            }
            Qualifiers.forKnockout(scrambled, perGroup, KnockoutSeeding.ByCampaign())
        }
    }

    private fun sweep(repetitions: Int = 1, seed: (List<List<StandingsRow>>, Int) -> List<Long>) {
        var singleGroupReencounters = 0
        repeat(repetitions) {
            for (groups in 1..8) for (perGroup in 1..4) {
                val tables = List(groups) { g -> table(*LongArray(perGroup) { (g * 10 + it + 1).toLong() }) }
                val group = groupOf(tables)
                val order = seed(tables, perGroup)
                assertEquals(groups * perGroup, order.size)
                if (order.size < 2) continue
                val bracket = TournamentGenerator.knockoutFromQualifiers(order, firstPhaseOrder = 1)
                val first = bracket.phases.first()
                val reencounters = first.matches.count {
                    !it.isBye && group.getValue(it.sideAId!!) == group.getValue(it.sideBId!!)
                }
                if (groups == 1) {
                    if (reencounters > 0) singleGroupReencounters++
                } else {
                    assertEquals(0, reencounters, "$groups grupos x $perGroup: reencontro de grupo na estreia")
                }
                val byeSeeds = first.matches.filter { it.isBye }.map { order.indexOf(it.sideAId) + 1 }.sorted()
                assertEquals((1..(bracket.size - order.size)).toList(), byeSeeds, "$groups x $perGroup: BYE fora das melhores")
                assertEquals(0, first.matches.count { it.isPending })
                // Os 1ºs colocados são as primeiras cabeças.
                assertEquals(
                    tables.map { it.first().participantId }.toSet(),
                    order.take(groups).toSet(),
                    "$groups x $perGroup: as cabeças têm de ser os 1ºs",
                )
            }
        }
        assertTrue(singleGroupReencounters > 0, "a varredura deveria encontrar o caso de grupo único")
    }

    /** SESC: cabeças = melhores campanhas entre os 1ºs, grupos de 3 e de 4 comparados por média. */
    @Test
    fun byCampaignSeedsBestFirstsByAverage() {
        // Grupo A (4): o 1º fez 3-0 com 18-9. Grupo B (3): o 1º fez 2-0 com 12-3. Grupo C (4): 2-1.
        val groupA = listOf(
            row(10, 1, played = 3, points = 3, maxPoints = 3, setsFor = 3, gamesFor = 18, gamesAgainst = 9),
            row(11, 2, played = 3, points = 2, maxPoints = 3, setsFor = 2, setsAgainst = 1, gamesFor = 15, gamesAgainst = 12),
        )
        val groupB = listOf(
            row(20, 1, played = 2, points = 2, maxPoints = 2, setsFor = 2, gamesFor = 12, gamesAgainst = 3),
            row(21, 2, played = 2, points = 1, maxPoints = 2, setsFor = 1, setsAgainst = 1, gamesFor = 9, gamesAgainst = 9),
        )
        val groupC = listOf(
            row(30, 1, played = 3, points = 2, maxPoints = 3, setsFor = 2, setsAgainst = 1, gamesFor = 16, gamesAgainst = 10),
            row(31, 2, played = 3, points = 2, maxPoints = 3, setsFor = 2, setsAgainst = 1, gamesFor = 14, gamesAgainst = 13),
        )
        val tables = listOf(groupA, groupB, groupC)
        val byCampaign = Qualifiers.forKnockout(tables, 2, KnockoutSeeding.ByCampaign())
        // Cabeça 1 = o do grupo de 3 (média de games 4,0 contra 2,0), não o do A com mais games.
        // Os 2ºs também por campanha: 11 e 31 empatam em aproveitamento (2 de 3) e média de sets
        // (2,0); a média de games separa (15/12 = 1,25 contra 14/13 ≈ 1,08). O 21 (1 de 2) fecha.
        assertEquals(listOf(20L, 10L, 30L, 11L, 31L, 21L), byCampaign)
        // Pela ordem dos grupos a cabeça 1 seria o 10.
        assertEquals(10L, Qualifiers.forKnockout(tables, 2).first())
        val first = TournamentGenerator.knockoutFromQualifiers(byCampaign, 0).phases.first()
        assertEquals(listOf(20L, 10L), first.matches.filter { it.isBye }.map { it.sideAId }, "BYE nas 2 melhores campanhas")
    }

    @Test
    fun withdrawnNeverQualifies() {
        val tables = listOf(
            listOf(row(10, 1), row(11, 2), row(12, 3, withdrawn = true)),
            listOf(row(20, 1), row(21, 2, withdrawn = true), row(22, 3)),
        )
        assertEquals(listOf(10L, 20L, 11L, 22L), Qualifiers.forKnockout(tables, 2))
    }
}
