package br.com.codecacto.kmplib.tournament

/** Divisão em grupos para o formato [TournamentFormat.GROUPS_THEN_KNOCKOUT]. */
object Groups {

    /** Grupos de 4 — o padrão do futebol amador e do beach tennis. */
    const val DEFAULT_GROUP_SIZE: Int = 4

    /** 2 classificados por grupo, qualquer que seja o tamanho do grupo. */
    const val DEFAULT_QUALIFIERS_PER_GROUP: Int = 2

    /**
     * Distribui os participantes **já sorteados** em grupos o mais equilibrados possível.
     *
     * A distribuição é alternada (1º→A, 2º→B, 3º→A, 4º→B…), e não em blocos: fatiar a lista em
     * pedaços de [groupSize] deixa o último grupo com o resto, e com 5 participantes em grupos de 4
     * isso produz um "grupo" de uma pessoa, sem confronto nenhum. Alternando, **os tamanhos nunca
     * diferem em mais de um** (10 em grupos de 4 → 4-3-3).
     *
     * O número de grupos é limitado a `total / 2`: um grupo precisa de ao menos dois nomes.
     *
     * @param singleGroupBelow com menos inscritos que isto, **grupo único** (regulamento SESC de
     *   beach tennis: `< 6` duplas = um grupo só). `null` = sem a regra.
     */
    fun split(
        participantIds: List<Long>,
        groupSize: Int = DEFAULT_GROUP_SIZE,
        singleGroupBelow: Int? = null,
    ): List<List<Long>> {
        require(groupSize >= 2) { "um grupo precisa de ao menos 2 participantes (recebeu $groupSize)" }
        if (participantIds.size < 2) return listOf(participantIds)
        if (singleGroupBelow != null && participantIds.size < singleGroupBelow) return listOf(participantIds)

        val wanted = (participantIds.size + groupSize - 1) / groupSize
        val count = wanted.coerceIn(1, participantIds.size / 2)

        val groups = List(count) { mutableListOf<Long>() }
        participantIds.forEachIndexed { position, id -> groups[position % count].add(id) }
        return groups.map { it.toList() }
    }

    // "Grupo A" NÃO se monta aqui: é palavra, e palavra é da UI. O grupo é o índice (PhaseIdentity).
}
