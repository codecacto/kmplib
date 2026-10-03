package br.com.codecacto.kmplib.tournament

/** Como os classificados dos grupos viram cabeças de chave. */
sealed interface KnockoutSeeding {

    /**
     * Pela ordem dos grupos: 1º do A, 1º do B, …, depois 2º do A, 2º do B, … (o TorneioDePenalti).
     */
    data object GroupOrder : KnockoutSeeding

    /**
     * Por **campanha**: dentro de cada colocação (todos os 1ºs, depois todos os 2ºs…), quem fez a
     * melhor campanha é a cabeça melhor — regulamento SESC: "cabeças de chave = melhores campanhas
     * entre os 1ºs colocados". Os 1ºs continuam todos à frente dos 2ºs.
     *
     * Grupos de tamanhos diferentes (3 e 4) se comparam por **média/aproveitamento**, não por soma —
     * ver [Standings.rankAcrossGroups]. [TieBreaker.HEAD_TO_HEAD] é ignorado.
     */
    data class ByCampaign(val criteria: List<TieBreaker> = DEFAULT_CRITERIA) : KnockoutSeeding {
        companion object {
            /** Aproveitamento → média de sets → média de games → sorteio. */
            val DEFAULT_CRITERIA: List<TieBreaker> = listOf(
                TieBreaker.POINTS_PERCENTAGE,
                TieBreaker.SET_RATIO,
                TieBreaker.GAME_RATIO,
                TieBreaker.DRAW_ORDER,
            )
        }
    }
}

/** Quem sai dos grupos para a chave, e em que ordem de cabeça. */
object Qualifiers {

    /**
     * A ordem em que os classificados entram na chave — **já semeada, pronta para
     * [TournamentGenerator.knockoutFromQualifiers]**.
     *
     * A lista de **cabeças** é a colocação: todos os 1ºs, depois todos os 2ºs, e assim por diante
     * (dentro de cada colocação, na ordem dos grupos ou da campanha — [seeding]). Quem posiciona na
     * chave é [Knockout.seedOrder]: com 4 classificados sai `1ºA × 2ºB` e `1ºB × 2ºA`; com 8, a 1ª e
     * a 2ª cabeças em metades opostas, que só se encontram na **final**. O BYE é da semeadura: as
     * vagas que faltam para a potência de 2 caem como adversárias das melhores cabeças.
     *
     * O que esta função ainda decide é o **cruzamento de grupos**: quando a semeadura poria dois do
     * MESMO grupo frente a frente na estreia (3 grupos com 2 classificados juntava 1ºC × 2ºC), os
     * adversários são trocados entre dois pares. A troca mexe **só no lado B** de pares cheios: quem é
     * cabeça de confronto e quem recebe BYE não muda. **Grupo único** é a exceção que fica de pé — não
     * há adversário de fora para trocar.
     *
     * Quem **desistiu** ([StandingsRow.withdrawn]) não se classifica: a vaga vai para o seguinte.
     *
     * @param groupTables a tabela de cada grupo, na ordem dos grupos (saída de [Standings.compute]).
     */
    fun forKnockout(
        groupTables: List<List<StandingsRow>>,
        qualifiersPerGroup: Int,
        seeding: KnockoutSeeding = KnockoutSeeding.GroupOrder,
    ): List<Long> {
        require(qualifiersPerGroup >= 1) { "ao menos 1 classificado por grupo (recebeu $qualifiersPerGroup)" }
        val eligible = groupTables.map { table -> table.filterNot { it.withdrawn } }
        val groupOf = buildMap {
            eligible.forEachIndexed { group, table -> table.forEach { put(it.participantId, group) } }
        }

        val seeds = buildList {
            for (placement in 0 until qualifiersPerGroup) {
                val tier = eligible.mapNotNull { it.getOrNull(placement) }
                val ordered = when (seeding) {
                    KnockoutSeeding.GroupOrder -> tier
                    is KnockoutSeeding.ByCampaign -> Standings.rankAcrossGroups(tier, seeding.criteria)
                }
                ordered.forEach { add(Seed(it.participantId, groupOf.getValue(it.participantId))) }
            }
        }
        // Com 0 ou 1 classificado não há chave a semear — quem lidera já é o campeão.
        if (seeds.size < 2) return seeds.map { it.id }

        val pairs = Knockout.firstRoundPairs(Knockout.bracketSize(seeds.size))
        return crossGroups(seeds, pairs).map { it.id }
    }

    private data class Seed(val id: Long, val group: Int)

    /**
     * Desfaz reencontros de mesmo grupo na estreia, trocando os adversários entre dois pares.
     *
     * Trabalha sobre a lista **indexada por cabeça** (posição `c−1` = cabeça `c`); [pairs] são os
     * confrontos de estreia em termos de cabeça. Só pares **cheios** entram (par com cabeça virtual é
     * BYE, decisão da semeadura) e só o **lado B** é trocado. A troca é aceita apenas quando resolve
     * os DOIS pares; sem isso ela só mudaria o reencontro de lugar.
     */
    private fun crossGroups(seeds: List<Seed>, pairs: List<Pair<Int, Int>>): List<Seed> {
        val bySeed = seeds.toMutableList()
        val full = pairs.filter { (_, sideB) -> sideB <= bySeed.size }

        for ((sideA, sideB) in full) {
            val a = bySeed[sideA - 1]
            val b = bySeed[sideB - 1]
            if (a.group != b.group) continue

            val target = full.firstOrNull { (otherA, otherB) ->
                otherA != sideA &&
                    bySeed[otherA - 1].group != b.group &&
                    bySeed[otherB - 1].group != a.group
            } ?: continue

            val otherB = target.second
            val swapped = bySeed[sideB - 1]
            bySeed[sideB - 1] = bySeed[otherB - 1]
            bySeed[otherB - 1] = swapped
        }
        return bySeed
    }
}
