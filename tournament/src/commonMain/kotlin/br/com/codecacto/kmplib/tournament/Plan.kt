package br.com.codecacto.kmplib.tournament

/**
 * O que o sorteio produziu, antes de virar linha de banco.
 *
 * O plano é **puro**: nada aqui conhece Room, SQLDelight, Koin ou Compose. Quem persiste é o app,
 * que traduz o plano nas suas fases, grupos e confrontos — e é essa fronteira que permite conferir as
 * invariantes do motor fora de um aparelho.
 */
data class TournamentPlan(
    val format: TournamentFormat,
    /** Participantes na ordem sorteada, com [TournamentParticipant.drawOrder] e grupo preenchidos. */
    val participants: List<TournamentParticipant>,
    val groups: List<PlannedGroup>,
    /**
     * As fases que já nascem com o sorteio. Em [TournamentFormat.GROUPS_THEN_KNOCKOUT] são só os
     * grupos: a chave nasce quando eles fecham ([TournamentGenerator.knockoutFromQualifiers]).
     */
    val phases: List<PlannedPhase>,
    /** Tamanho da chave eliminatória (potência de 2). `0` quando o formato não tem mata-mata. */
    val bracketSize: Int,
)

/** Um grupo do plano. **Sem nome**: o grupo é o [index] (0 = "A", montado na UI). */
data class PlannedGroup(
    val index: Int,
    val participantIds: List<Long>,
)

data class PlannedPhase(
    val order: Int,
    val type: PhaseType,
    /** Preenchido só em [PhaseType.GROUP]. 0-based. */
    val groupIndex: Int? = null,
    /** Preenchido só em [PhaseType.ROUND]. 1-based. */
    val roundNumber: Int? = null,
    val matches: List<PlannedMatch>,
) {
    /** O que a UI precisa para escrever o nome da fase. Nenhum texto sai daqui. */
    val identity: PhaseIdentity
        get() = PhaseIdentity(
            type = type,
            matchCount = matches.size,
            groupIndex = groupIndex,
            roundNumber = roundNumber,
        )
}

/**
 * Um confronto do plano.
 *
 * As duas formas de "lado vazio" são coisas diferentes e não podem ser confundidas:
 * - **BYE** ([isBye] `true`): [sideAId] preenchido, [sideBId] nulo. O confronto já nasce decidido
 *   — quem está no lado A passa direto.
 * - **Vaga a definir** ([isPending] `true`, os dois nulos): espera o vencedor (ou, no 3º lugar, o
 *   perdedor) de outra fase.
 */
data class PlannedMatch(
    val index: Int,
    val sideAId: Long?,
    val sideBId: Long?,
    val isBye: Boolean = false,
) {
    val isPending: Boolean get() = sideAId == null && sideBId == null
}

/** Uma chave eliminatória montada: o tamanho (potência de 2) e as fases, da estreia à final. */
data class KnockoutBracket(
    val size: Int,
    /** As eliminatórias, da estreia à final, e por último a de 3º lugar quando houver. */
    val phases: List<PlannedPhase>,
) {
    /** A fase de disputa de 3º lugar, se a chave foi montada com ela. */
    val thirdPlace: PlannedPhase? get() = phases.firstOrNull { it.type == PhaseType.THIRD_PLACE }
}
