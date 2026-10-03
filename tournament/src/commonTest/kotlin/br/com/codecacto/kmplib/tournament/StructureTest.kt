package br.com.codecacto.kmplib.tournament

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Invariantes de estrutura — portadas do `VerificacaoDoMotorTest` do TorneioDePenalti. O motor é
 * matemática pura cujas invariantes quebram em silêncio: uma chave fora da potência de 2, um
 * confronto com dois BYEs, alguém jogando duas vezes na mesma rodada produzem um torneio plausível
 * e errado, que o organizador só descobre no campo.
 */
class StructureTest {

    // ── Mata-mata ────────────────────────────────────────────────────────────

    @Test
    fun knockoutFrom2To32() {
        for (n in 2..32) {
            val plan = TournamentGenerator.generate(ids(n), TournamentConfig(TournamentFormat.KNOCKOUT), fixedRandom(n))
            val first = plan.phases.first()
            val slots = first.matches.flatMap { listOfNotNull(it.sideAId, it.sideBId) }
            val byes = first.matches.count { it.isBye }

            assertTrue(isPowerOfTwo(plan.bracketSize), "n=$n: chave ${plan.bracketSize} não é potência de 2")
            assertTrue(plan.bracketSize >= n && plan.bracketSize < n * 2, "n=$n: chave ${plan.bracketSize} fora do mínimo")
            assertEquals(n, slots.size, "n=$n: participante a mais ou a menos na estreia")
            assertEquals(n, slots.toSet().size, "n=$n: participante repetido na estreia")
            assertEquals(plan.bracketSize - n, byes, "n=$n: nº de BYEs")
            assertEquals(0, first.matches.count { it.isPending }, "n=$n: confronto com DOIS BYEs")
            assertEquals(plan.bracketSize / 2, first.matches.size, "n=$n: confrontos da estreia")
            assertEquals(1, plan.phases.last().identity.matchCount, "n=$n: a última fase não é uma final")
            assertTrue(plan.phases.all { it.type == PhaseType.KNOCKOUT }, "n=$n: sem 3º lugar por default")
            plan.phases.zipWithNext { before, after ->
                assertEquals(before.matches.size / 2, after.matches.size, "n=$n: a fase ${after.order} não tem metade")
                assertEquals(before.order + 1, after.order, "n=$n: ordem das fases não é contínua")
                assertTrue(after.matches.all { it.isPending && !it.isBye }, "n=$n: vaga a definir ≠ BYE")
            }
            assertEquals(n, plan.participants.map { it.drawOrder }.toSet().size, "n=$n: ordem de sorteio repetida")
        }
    }

    @Test
    fun seedOrderCanonical() {
        assertEquals(listOf(1), Knockout.seedOrder(1))
        assertEquals(listOf(1, 2), Knockout.seedOrder(2))
        assertEquals(listOf(1, 4, 2, 3), Knockout.seedOrder(4))
        assertEquals(listOf(1, 8, 4, 5, 2, 7, 3, 6), Knockout.seedOrder(8))
        assertEquals(listOf(1, 16, 8, 9, 4, 13, 5, 12, 2, 15, 7, 10, 3, 14, 6, 11), Knockout.seedOrder(16))
        assertEquals(
            listOf(
                1, 32, 16, 17, 8, 25, 9, 24, 4, 29, 13, 20, 5, 28, 12, 21,
                2, 31, 15, 18, 7, 26, 10, 23, 3, 30, 14, 19, 6, 27, 11, 22,
            ),
            Knockout.seedOrder(32),
        )
        for (size in listOf(2, 4, 8, 16, 32)) {
            assertEquals((1..size).toList(), Knockout.seedOrder(size).sorted(), "chave $size: cabeça faltando")
            val pairs = Knockout.firstRoundPairs(size)
            pairs.forEachIndexed { index, (a, b) ->
                assertEquals(size + 1, a + b, "chave $size: confronto $index não é i × T+1−i")
                assertTrue(a < b, "chave $size: cabeça pior no lado A")
            }
            assertEquals(0, pairs.indexOfFirst { it.first == 1 })
            val withTwo = pairs.indexOfFirst { it.first == 2 || it.second == 2 }
            assertTrue(size == 2 || withTwo >= pairs.size / 2, "chave $size: cabeças 1 e 2 na mesma metade")
        }
        assertFailsWith<IllegalArgumentException> { Knockout.seedOrder(6) }
        assertFailsWith<IllegalArgumentException> { Knockout.firstRoundPairs(1) }
    }

    /** Simulação "a cabeça melhor sempre vence": de 2 a 32, a final é sempre 1 × 2. */
    @Test
    fun topTwoSeedsOnlyMeetInTheFinal() {
        for (n in 2..32) {
            val bracket = Knockout.buildBracket(ids(n))
            var matches: List<Pair<Long, Long?>> = bracket.phases.first().matches.map { it.sideAId!! to it.sideBId }
            bracket.phases.drop(1).forEach { phase ->
                val winners = matches.map { (a, b) -> if (b == null) a else minOf(a, b) }
                val sideA = arrayOfNulls<Long>(phase.matches.size)
                val sideB = arrayOfNulls<Long>(phase.matches.size)
                winners.forEachIndexed { index, winner ->
                    val destination = Advancement.winnerDestination(index)
                    when (destination.side) {
                        Side.A -> sideA[destination.matchIndex] = winner
                        Side.B -> sideB[destination.matchIndex] = winner
                    }
                }
                matches = phase.matches.indices.map { sideA[it]!! to sideB[it] }
            }
            assertEquals(listOf(1L to 2L), matches, "n=$n: as duas melhores cabeças se cruzaram antes da final")
        }
    }

    @Test
    fun byeGoesToTheBestSeeds() {
        // 6 numa chave de 8: passam direto as cabeças 1 e 2.
        val bracket = Knockout.buildBracket(listOf(11L, 12, 13, 14, 15, 16))
        assertEquals(8, bracket.size)
        assertEquals(listOf(11L, 12L), bracket.phases.first().matches.filter { it.isBye }.map { it.sideAId })
        assertFailsWith<IllegalArgumentException> { Knockout.buildBracket(listOf(1L)) }
        assertFailsWith<IllegalArgumentException> { Knockout.buildBracket(listOf(1L, 1L)) }
        assertEquals(2, Knockout.bracketSize(0))
        assertEquals(2, Knockout.bracketSize(2))
        assertEquals(4, Knockout.bracketSize(3))
        assertEquals(32, Knockout.bracketSize(17))
    }

    @Test
    fun thirdPlaceMatch() {
        for (n in 4..32) {
            val plan = TournamentGenerator.generate(
                ids(n),
                TournamentConfig(TournamentFormat.KNOCKOUT, thirdPlaceMatch = true),
                fixedRandom(n),
            )
            val third = plan.phases.last()
            assertEquals(PhaseType.THIRD_PLACE, third.type, "n=$n: 3º lugar tem de ser a última fase")
            assertEquals(1, third.matches.size)
            assertTrue(third.matches.single().isPending)
            val final = plan.phases.last { it.type == PhaseType.KNOCKOUT }
            assertEquals(final.order + 1, third.order, "n=$n: 3º lugar numerado depois da final")
            assertEquals(1, final.matches.size)
        }
        // Com 3 não há dois semifinalistas derrotados: a fase não nasce.
        val three = Knockout.buildBracket(ids(3), thirdPlaceMatch = true)
        assertNull(three.thirdPlace)
        // Com 2 é só a final.
        assertNull(Knockout.buildBracket(ids(2), thirdPlaceMatch = true).thirdPlace)
        assertEquals(PhaseType.THIRD_PLACE, Knockout.buildBracket(ids(4), thirdPlaceMatch = true).thirdPlace?.type)

        assertEquals(Advancement.Destination(0, Side.A), Advancement.thirdPlaceDestination(0))
        assertEquals(Advancement.Destination(0, Side.B), Advancement.thirdPlaceDestination(1))
        assertFailsWith<IllegalArgumentException> { Advancement.thirdPlaceDestination(2) }
    }

    @Test
    fun advancementCrossesNeighbours() {
        assertEquals(Advancement.Destination(0, Side.A), Advancement.winnerDestination(0))
        assertEquals(Advancement.Destination(0, Side.B), Advancement.winnerDestination(1))
        assertEquals(Advancement.Destination(3, Side.A), Advancement.winnerDestination(6))
        assertEquals(Advancement.Destination(3, Side.B), Advancement.winnerDestination(7))
    }

    // ── Todos contra todos ───────────────────────────────────────────────────

    @Test
    fun roundRobinFrom2To32() {
        for (n in 2..32) {
            val plan = TournamentGenerator.generate(ids(n), TournamentConfig(TournamentFormat.ROUND_ROBIN), fixedRandom(n))
            val matches = plan.phases.flatMap { phase ->
                phase.matches.map { Triple(phase.roundNumber!!, it.sideAId!!, it.sideBId!!) }
            }
            val expected = n * (n - 1) / 2
            assertEquals(expected, matches.size, "n=$n: total de confrontos")
            val pairs = matches.map { (_, a, b) -> if (a < b) a to b else b to a }
            assertEquals(expected, pairs.toSet().size, "n=$n: confronto repetido no round-robin")

            val rounds = if (n % 2 == 0) n - 1 else n
            assertEquals(rounds, plan.phases.size, "n=$n: nº de rodadas fora do mínimo")
            assertEquals(rounds, RoundRobin.roundCount(n))
            assertEquals(expected, RoundRobin.matchCount(n))
            assertEquals(0, plan.bracketSize)
            assertTrue(plan.phases.all { it.type == PhaseType.ROUND && it.identity.roundNumber == it.roundNumber })

            plan.phases.forEach { phase ->
                val onField = phase.matches.flatMap { listOf(it.sideAId, it.sideBId) }
                assertEquals(onField.size, onField.toSet().size, "n=$n: alguém joga duas vezes na rodada ${phase.roundNumber}")
            }
            val restsPerParticipant = if (n % 2 == 0) 0 else 1
            plan.participants.forEach { p ->
                val rests = plan.phases.count { phase -> phase.matches.none { it.sideAId == p.id || it.sideBId == p.id } }
                assertEquals(restsPerParticipant, rests, "n=$n: ${p.id} folgou $rests vez(es)")
            }
        }
    }

    @Test
    fun doubleRoundRobin() {
        val legs = RoundRobin.schedule(ids(5), doubleRound = true)
        assertEquals(20, legs.size)
        assertEquals(10, RoundRobin.roundCount(5, doubleRound = true))
        assertEquals(20, RoundRobin.matchCount(5, doubleRound = true))
        val ordered = legs.map { it.sideAId to it.sideBId }
        assertEquals(20, ordered.toSet().size, "turno e returno invertem os lados")
        assertEquals((1..10).toList(), legs.map { it.round }.distinct().sorted(), "returno nas rodadas 6 a 10")
        assertEquals(10, legs.count { it.round <= 5 }, "o turno inteiro antes do returno")

        val plan = TournamentGenerator.generate(
            ids(4),
            TournamentConfig(TournamentFormat.ROUND_ROBIN, doubleRoundRobin = true),
            fixedRandom(1),
        )
        assertEquals(6, plan.phases.size)
        assertEquals(12, plan.phases.sumOf { it.matches.size })

        assertEquals(emptyList(), RoundRobin.schedule(listOf(1L)))
        assertEquals(0, RoundRobin.roundCount(1))
        assertEquals(0, RoundRobin.matchCount(1))
        assertFailsWith<IllegalArgumentException> { RoundRobin.schedule(listOf(1L, RoundRobin.REST)) }
    }

    // ── Grupos + mata-mata ───────────────────────────────────────────────────

    @Test
    fun groupsThenKnockoutFrom4To32() {
        for (groupSize in listOf(3, 4)) {
            for (n in 4..32) {
                val config = TournamentConfig(TournamentFormat.GROUPS_THEN_KNOCKOUT, groupSize = groupSize)
                val plan = TournamentGenerator.generate(ids(n), config, fixedRandom(n))
                val inGroups = plan.groups.flatMap { it.participantIds }
                assertEquals(n, inGroups.size, "n=$n: participante fora de grupo (ou em dois)")
                assertEquals(n, inGroups.toSet().size, "n=$n: participante repetido entre grupos")
                plan.groups.forEach { assertTrue(it.participantIds.size >= 2, "n=$n: grupo ${it.index} com < 2") }
                val sizes = plan.groups.map { it.participantIds.size }
                assertTrue(sizes.max() - sizes.min() <= 1, "n=$n: grupos desbalanceados $sizes")
                assertTrue(plan.groups.size <= n / 2, "n=$n: grupos demais")
                plan.participants.forEach { p ->
                    assertEquals(plan.groups.single { p.id in it.participantIds }.index, p.groupIndex)
                }

                plan.phases.forEachIndexed { index, phase ->
                    assertEquals(PhaseType.GROUP, phase.type)
                    assertEquals(index, phase.identity.groupIndex)
                    val size = plan.groups[index].participantIds.size
                    assertEquals(size * (size - 1) / 2, phase.matches.size, "n=$n: grupo $index não é todos-contra-todos")
                    val pairs = phase.matches.map { val a = it.sideAId!!; val b = it.sideBId!!; if (a < b) a to b else b to a }
                    assertEquals(pairs.size, pairs.toSet().size)
                }

                val qualifiers = buildList {
                    for (placement in 0 until config.qualifiersPerGroup) {
                        plan.groups.forEach { it.participantIds.getOrNull(placement)?.let(::add) }
                    }
                }
                assertEquals(plan.bracketSize, Knockout.bracketSize(qualifiers.size), "n=$n: previsão da chave")
                val bracket = TournamentGenerator.knockoutFromQualifiers(qualifiers, firstPhaseOrder = plan.phases.size)
                assertEquals(plan.phases.size, bracket.phases.first().order)
                assertEquals(0, bracket.phases.first().matches.count { it.isPending }, "n=$n: dois BYEs")
                assertEquals(
                    qualifiers.size,
                    bracket.phases.first().matches.flatMap { listOfNotNull(it.sideAId, it.sideBId) }.toSet().size,
                )
            }
        }
    }

    @Test
    fun groupsSplitBalancedAndSingleGroupBelow() {
        assertEquals(listOf(4, 3, 3), Groups.split(ids(10), groupSize = 4).map { it.size })
        assertEquals(listOf(3, 3), Groups.split(ids(6), groupSize = 3).map { it.size })
        // 5 em grupos de 4: nunca um "grupo" de 1 — sai 3 e 2.
        assertEquals(listOf(3, 2), Groups.split(ids(5), groupSize = 4).map { it.size })
        // Alternado: 1º→A, 2º→B, 3º→A…
        assertEquals(listOf(listOf(1L, 3L), listOf(2L, 4L)), Groups.split(ids(4), groupSize = 2))
        // Regra SESC: < 6 duplas = grupo único.
        assertEquals(listOf(ids(5)), Groups.split(ids(5), groupSize = 3, singleGroupBelow = 6))
        assertEquals(2, Groups.split(ids(6), groupSize = 3, singleGroupBelow = 6).size)
        assertEquals(listOf(listOf(1L)), Groups.split(listOf(1L)))
        assertFailsWith<IllegalArgumentException> { Groups.split(ids(4), groupSize = 1) }

        val plan = TournamentGenerator.generate(
            ids(5),
            TournamentConfig(TournamentFormat.GROUPS_THEN_KNOCKOUT, groupSize = 3, singleGroupBelow = 6),
            fixedRandom(5),
        )
        assertEquals(1, plan.groups.size)
        assertEquals(10, plan.phases.single().matches.size)
        assertEquals(2, plan.bracketSize, "2 classificados do grupo único = final")

        // Grupo único com 1 classificado: sem chave.
        val single = TournamentGenerator.generate(
            ids(4),
            TournamentConfig(TournamentFormat.GROUPS_THEN_KNOCKOUT, qualifiersPerGroup = 1, singleGroupBelow = 6),
        )
        assertEquals(0, single.bracketSize)
    }

    @Test
    fun configAndGeneratorGuards() {
        assertFailsWith<IllegalArgumentException> { TournamentConfig(TournamentFormat.KNOCKOUT, groupSize = 1) }
        assertFailsWith<IllegalArgumentException> { TournamentConfig(TournamentFormat.KNOCKOUT, qualifiersPerGroup = 0) }
        assertFailsWith<IllegalArgumentException> { TournamentConfig(TournamentFormat.KNOCKOUT, singleGroupBelow = 1) }
        assertFailsWith<IllegalArgumentException> {
            TournamentGenerator.generate(listOf(1L), TournamentConfig(TournamentFormat.KNOCKOUT))
        }
        assertFailsWith<IllegalArgumentException> {
            TournamentGenerator.generate(listOf(1L, 1L), TournamentConfig(TournamentFormat.KNOCKOUT))
        }
    }

    @Test
    fun drawIsReproducibleAndAPermutation() {
        val first = TournamentGenerator.generate(ids(16), TournamentConfig(TournamentFormat.KNOCKOUT), fixedRandom(7))
        val again = TournamentGenerator.generate(ids(16), TournamentConfig(TournamentFormat.KNOCKOUT), fixedRandom(7))
        assertEquals(first, again, "mesma semente, mesmo sorteio")
        assertEquals(ids(16).toSet(), first.participants.map { it.id }.toSet())
        assertEquals((0 until 16).toList(), first.participants.map { it.drawOrder })

        val list = (1..50).toList()
        repeat(20) { seed ->
            val shuffled = list.shuffledBy(fixedRandom(seed))
            assertEquals(list.sorted(), shuffled.sorted(), "Fisher-Yates perdeu ou duplicou item")
        }
        // A fonte padrão também é uma permutação.
        assertEquals(list, list.shuffledBy().sorted())
        assertEquals(list, list.shuffledBy(SystemRandomSource).sorted())
        assertEquals(emptyList(), emptyList<Int>().shuffledBy())
    }

    @Test
    fun phaseIdentityCarriesNoText() {
        val phase = PlannedPhase(order = 3, type = PhaseType.GROUP, groupIndex = 2, matches = List(6) { PlannedMatch(it, 1, 2) })
        assertEquals(PhaseIdentity(PhaseType.GROUP, matchCount = 6, groupIndex = 2), phase.identity)
        val bye = PlannedMatch(0, 1L, null, isBye = true)
        assertTrue(bye.isBye && !bye.isPending)
    }
}
