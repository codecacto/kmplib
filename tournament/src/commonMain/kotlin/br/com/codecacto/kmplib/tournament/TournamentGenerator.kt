package br.com.codecacto.kmplib.tournament

/**
 * Parâmetros que o organizador escolhe ao criar o torneio.
 *
 * @property groupSize tamanho-alvo do grupo (3 ou 4 no beach tennis). Os tamanhos reais nunca
 *   diferem em mais de um ([Groups.split]).
 * @property qualifiersPerGroup quantos de cada grupo vão à chave.
 * @property singleGroupBelow com menos inscritos que isto, grupo único (SESC: `6`). `null` = sem a regra.
 * @property thirdPlaceMatch disputa de 3º lugar na chave. Default `false` — é o comportamento do
 *   TorneioDePenalti, que não disputa 3º.
 * @property doubleRoundRobin turno e returno no todos-contra-todos (e dentro dos grupos).
 */
data class TournamentConfig(
    val format: TournamentFormat,
    val groupSize: Int = Groups.DEFAULT_GROUP_SIZE,
    val qualifiersPerGroup: Int = Groups.DEFAULT_QUALIFIERS_PER_GROUP,
    val singleGroupBelow: Int? = null,
    val thirdPlaceMatch: Boolean = false,
    val doubleRoundRobin: Boolean = false,
) {
    init {
        require(groupSize >= 2) { "um grupo precisa de ao menos 2 participantes (recebeu $groupSize)" }
        require(qualifiersPerGroup >= 1) { "ao menos 1 classificado por grupo (recebeu $qualifiersPerGroup)" }
        require(singleGroupBelow == null || singleGroupBelow >= 2) {
            "singleGroupBelow precisa ser ≥ 2 (recebeu $singleGroupBelow)"
        }
    }
}

/**
 * Sorteia e monta o torneio — o ponto de entrada dos 3 formatos.
 *
 * **Nada aqui sabe qual esporte está rodando.** Chave, grupos e rodadas são idênticos no pênalti e
 * no beach tennis; o que muda é como cada confronto se resolve, e isso é do app ou do [MatchFormat].
 */
object TournamentGenerator {

    /**
     * @param participantIds os inscritos, em qualquer ordem — o sorteio acontece AQUI, uma vez, e a
     *   posição sorteada vira [TournamentParticipant.drawOrder] (persista-a).
     */
    fun generate(
        participantIds: List<Long>,
        config: TournamentConfig,
        random: RandomSource = SystemRandomSource,
    ): TournamentPlan {
        require(participantIds.size >= 2) {
            "um torneio precisa de ao menos 2 participantes (recebeu ${participantIds.size})"
        }
        require(participantIds.toSet().size == participantIds.size) { "participante repetido na inscrição" }

        val drawn = participantIds.shuffledBy(random)
            .mapIndexed { position, id -> TournamentParticipant(id = id, drawOrder = position) }

        return when (config.format) {
            TournamentFormat.KNOCKOUT -> knockoutPlan(drawn, config)
            TournamentFormat.ROUND_ROBIN -> roundRobinPlan(drawn, config)
            TournamentFormat.GROUPS_THEN_KNOCKOUT -> groupsPlan(drawn, config)
        }
    }

    /**
     * A chave dos classificados, montada quando a fase de grupos fecha.
     *
     * Recebe os classificados **na ordem de cabeça** — quem a calcula, com o cruzamento de grupos,
     * é [Qualifiers.forKnockout] — e aplica a semeadura de [Knockout.buildBracket].
     */
    fun knockoutFromQualifiers(
        qualifiersInSeedOrder: List<Long>,
        firstPhaseOrder: Int,
        thirdPlaceMatch: Boolean = false,
    ): KnockoutBracket = Knockout.buildBracket(qualifiersInSeedOrder, firstPhaseOrder, thirdPlaceMatch)

    private fun knockoutPlan(drawn: List<TournamentParticipant>, config: TournamentConfig): TournamentPlan {
        // A ordem sorteada É a ordem de cabeça: sem grupos não há mérito prévio.
        val bracket = Knockout.buildBracket(drawn.map { it.id }, thirdPlaceMatch = config.thirdPlaceMatch)
        return TournamentPlan(
            format = TournamentFormat.KNOCKOUT,
            participants = drawn,
            groups = emptyList(),
            phases = bracket.phases,
            bracketSize = bracket.size,
        )
    }

    private fun roundRobinPlan(drawn: List<TournamentParticipant>, config: TournamentConfig): TournamentPlan {
        val matches = RoundRobin.schedule(drawn.map { it.id }, config.doubleRoundRobin)
        val phases = matches.groupBy { it.round }
            .entries.sortedBy { it.key }
            .map { (round, inRound) ->
                PlannedPhase(
                    order = round - 1,
                    type = PhaseType.ROUND,
                    roundNumber = round,
                    matches = inRound.mapIndexed { index, m -> PlannedMatch(index, m.sideAId, m.sideBId) },
                )
            }
        return TournamentPlan(
            format = TournamentFormat.ROUND_ROBIN,
            participants = drawn,
            groups = emptyList(),
            phases = phases,
            bracketSize = 0,
        )
    }

    private fun groupsPlan(drawn: List<TournamentParticipant>, config: TournamentConfig): TournamentPlan {
        val split = Groups.split(drawn.map { it.id }, config.groupSize, config.singleGroupBelow)
        val groupOf = buildMap { split.forEachIndexed { index, ids -> ids.forEach { put(it, index) } } }

        val groups = split.mapIndexed { index, ids -> PlannedGroup(index = index, participantIds = ids) }

        // Uma fase por grupo, com todo o round-robin daquele grupo dentro.
        val phases = groups.map { group ->
            PlannedPhase(
                order = group.index,
                type = PhaseType.GROUP,
                groupIndex = group.index,
                matches = RoundRobin.schedule(group.participantIds, config.doubleRoundRobin)
                    .mapIndexed { index, m -> PlannedMatch(index, m.sideAId, m.sideBId) },
            )
        }

        // A chave NÃO nasce agora: quem entra nela só se sabe quando os grupos fecham. O tamanho,
        // sim, já é previsível — é ele que a tela usa para dizer "os 2 primeiros vão às quartas".
        val qualifiers = groups.sumOf { minOf(config.qualifiersPerGroup, it.participantIds.size) }

        return TournamentPlan(
            format = TournamentFormat.GROUPS_THEN_KNOCKOUT,
            participants = drawn.map { it.copy(groupIndex = groupOf[it.id]) },
            groups = groups,
            phases = phases,
            bracketSize = if (qualifiers >= 2) Knockout.bracketSize(qualifiers) else 0,
        )
    }
}
