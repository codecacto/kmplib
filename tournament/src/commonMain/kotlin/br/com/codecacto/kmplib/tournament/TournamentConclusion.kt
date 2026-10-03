package br.com.codecacto.kmplib.tournament

/**
 * **Quando o torneio acabou e quem levou.**
 *
 * Regra pura, sem banco: recebe as fases reduzidas ao que importa e devolve o pódio. "O campeão é
 * quem venceu a final" e "o campeão é quem liderou a tabela" são regras do formato — a tela que as
 * refizesse por conta própria seria uma segunda fonte de verdade sobre o resultado.
 */
object TournamentConclusion {

    /** Um confronto reduzido ao que a conclusão precisa. BYE = um lado nulo, já concluído. */
    data class MatchSummary(
        val sideAId: Long?,
        val sideBId: Long?,
        val winnerId: Long?,
        val finished: Boolean,
    )

    data class PhaseSummary(
        val order: Int,
        val type: PhaseType,
        val matches: List<MatchSummary>,
    ) {
        val isFinished: Boolean get() = matches.isNotEmpty() && matches.all { it.finished }
    }

    /** Todas as fases fecharam — inclusive a chave que nasce depois dos grupos e o 3º lugar. */
    fun isComplete(phases: List<PhaseSummary>): Boolean = phases.isNotEmpty() && phases.all { it.isFinished }

    /** As fases de grupo fecharam: é o gatilho para montar a chave dos classificados. */
    fun groupsComplete(phases: List<PhaseSummary>): Boolean {
        val groups = phases.filter { it.type == PhaseType.GROUP }
        return groups.isNotEmpty() && groups.all { it.isFinished }
    }

    /**
     * O pódio, na ordem **campeão, vice, terceiro** — só até onde o formato produz essa informação.
     * Torneio em andamento devolve lista vazia (inclusive a final reaberta por um "desfazer").
     *
     * - **Todos contra todos**, e **grupos que terminam sem chave** (grupo único com 1 classificado):
     *   os três primeiros de [overallStandings].
     * - **Com chave**: campeão e vice da final. O **3º** existe só quando há disputa de 3º —
     *   o vencedor da fase [PhaseType.THIRD_PLACE]; ou, com [thirdPlaceMatch] e apenas 3
     *   participantes (uma semifinal é BYE), o único semifinalista derrotado. Sem disputa de 3º os dois
     *   semifinalistas derrotados param no mesmo ponto e nada os separa — escolher um seria inventar
     *   um resultado que ninguém jogou, e o pódio tem dois nomes.
     *
     * @param overallStandings ids na ordem da tabela, para os formatos decididos pela tabela.
     * @param thirdPlaceMatch o torneio foi configurado com disputa de 3º ([TournamentConfig.thirdPlaceMatch]).
     */
    fun podium(
        format: TournamentFormat,
        phases: List<PhaseSummary>,
        overallStandings: List<Long> = emptyList(),
        thirdPlaceMatch: Boolean = false,
    ): List<Long> {
        if (!isComplete(phases)) return emptyList()

        val knockouts = phases.filter { it.type == PhaseType.KNOCKOUT }
        return when (format) {
            TournamentFormat.ROUND_ROBIN -> overallStandings.take(PODIUM_PLACES)
            TournamentFormat.KNOCKOUT -> bracketPodium(knockouts, phases, thirdPlaceMatch)
            TournamentFormat.GROUPS_THEN_KNOCKOUT ->
                if (knockouts.isEmpty()) {
                    overallStandings.take(PODIUM_PLACES)
                } else {
                    bracketPodium(knockouts, phases, thirdPlaceMatch)
                }
        }
    }

    private fun bracketPodium(
        knockouts: List<PhaseSummary>,
        phases: List<PhaseSummary>,
        thirdPlaceMatch: Boolean,
    ): List<Long> {
        val byOrder = knockouts.sortedBy { it.order }
        val final = byOrder.lastOrNull()?.matches?.singleOrNull() ?: return emptyList()

        val champion = final.winnerId ?: return emptyList()
        val runnerUp = listOfNotNull(final.sideAId, final.sideBId).firstOrNull { it != champion }

        val third = phases.firstOrNull { it.type == PhaseType.THIRD_PLACE }
            ?.matches?.singleOrNull()?.winnerId
            ?: if (thirdPlaceMatch) loneSemifinalLoser(byOrder) else null

        return listOfNotNull(champion, runnerUp, third)
    }

    /** Com 3 participantes e 3º lugar ligado: uma semi é BYE, e o perdedor da outra é o 3º. */
    private fun loneSemifinalLoser(byOrder: List<PhaseSummary>): Long? {
        if (byOrder.size < 2) return null
        val semis = byOrder[byOrder.size - 2].matches
        if (semis.size != 2) return null
        val played = semis.filter { it.sideAId != null && it.sideBId != null }
        val byes = semis.count { (it.sideAId == null) != (it.sideBId == null) }
        if (played.size != 1 || byes != 1) return null
        val match = played.single()
        val winner = match.winnerId ?: return null
        return listOfNotNull(match.sideAId, match.sideBId).firstOrNull { it != winner }
    }

    private const val PODIUM_PLACES = 3
}
