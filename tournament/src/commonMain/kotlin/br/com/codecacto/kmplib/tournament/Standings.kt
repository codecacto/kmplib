package br.com.codecacto.kmplib.tournament

/**
 * Um confronto, do jeito que a tabela precisa dele: quem, como terminou e o placar.
 *
 * Repare no que **não** tem aqui: nem cobrança, nem gol, nem esporte. A classificação consome
 * [MatchOutcome] e [Score] — e por isso funciona igual no pênalti e no beach tennis.
 */
data class MatchResult(
    val sideAId: Long,
    val sideBId: Long,
    val outcome: MatchOutcome,
    val score: Score,
)

/**
 * Critério de desempate. A classificação os aplica **na ordem da lista** ([StandingsRules]).
 *
 * "Média" ([SET_RATIO], [GAME_RATIO]) é **ganhos ÷ perdidos** — o critério do regulamento de beach
 * tennis para comparar campanhas de grupos de tamanhos diferentes, onde somar favoreceria quem jogou
 * mais. A comparação é **exata** (produto cruzado em inteiros), nunca por `Double`. Sem nada perdido
 * e algo ganho, a média é infinita (a melhor possível); `0 ÷ 0` vale 0.
 */
enum class TieBreaker {
    /**
     * Confronto direto: os pontos ([StandingsRules]) feitos **só nos jogos entre os empatados**. Com
     * dois é "quem ganhou o jogo entre os dois"; com três ou mais é o mini-campeonato entre eles — e
     * num ciclo (A>B, B>C, C>A) todos empatam nele, então ele não resolve nada e o próximo critério
     * decide. Não se aplica entre grupos ([Standings.rankAcrossGroups] o ignora).
     */
    HEAD_TO_HEAD,

    /** Saldo de sets (`0` para placar por pontos). */
    SET_DIFFERENCE,

    /** Saldo de games — no placar por pontos, saldo de gols/pontos. */
    GAME_DIFFERENCE,

    /** Sets ganhos. */
    SETS_WON,

    /** Games ganhos (gols/pontos marcados no placar por pontos). */
    GAMES_WON,

    /** Média de sets: ganhos ÷ perdidos. */
    SET_RATIO,

    /** Média de games: ganhos ÷ perdidos. */
    GAME_RATIO,

    /** Aproveitamento: pontos ÷ pontos disputados. Compara grupos de tamanhos diferentes. */
    POINTS_PERCENTAGE,

    /** Número de vitórias (inclui W.O.). */
    WINS,

    /**
     * Ordem do sorteio ([TournamentParticipant.drawOrder]) — quem foi sorteado antes fica na frente.
     * É sempre o **último recurso implícito**: mesmo fora da lista, ele fecha qualquer empate que
     * sobrar, para a tabela nunca depender da ordem de chegada dos dados.
     */
    DRAW_ORDER,
}

/** O que fazer com os jogos de quem **desistiu do torneio** (saiu do grupo). */
sealed interface WithdrawalPolicy {

    /**
     * **Anula** todos os jogos de quem desistiu — os já jogados e os que faltavam (regulamento SESC:
     * "os jogos dela são descartados"). A tabela dos demais fica como se ele nunca tivesse estado no
     * grupo.
     */
    data object AnnulMatches : WithdrawalPolicy

    /**
     * Os jogos que já foram jogados **valem**; os que ainda estavam por jogar ([MatchOutcome.InProgress]
     * no que o app passar) contam como **derrota por W.O.** de quem desistiu, com o placar
     * convencionado.
     *
     * @property walkoverScoreForSideA o placar do W.O. com o **vencedor no lado A** — espelhado
     *   sozinho quando o vencedor é o B. Raquete: `MatchFormat.walkoverScore(Side.A)`; futebol:
     *   `Score.Points(3, 0)`.
     */
    data class CountAsLosses(val walkoverScoreForSideA: Score) : WithdrawalPolicy
}

/**
 * As regras da tabela: quanto vale cada resultado e como se desempata.
 *
 * O empate em **pontos** é resolvido por [tieBreakers], critério a critério: cada um divide os
 * empatados em blocos, e cada bloco que continua empatado segue para o **próximo** critério.
 *
 * Com [twoWayTieBreakers] preenchido, **todo** empate de exatamente dois — o inicial, ou o que
 * sobrar no meio de um desempate de três ou mais — passa a ser resolvido por essa lista. É a regra
 * recursiva do beach tennis: "empate de 2 → confronto direto; de 3+ → saldo de sets → saldo de
 * games → sorteio; se no meio sobrarem 2, volta ao confronto direto". Com `null`, a mesma lista vale
 * para qualquer tamanho de empate (é o caso do futebol).
 *
 * Use os presets — [BEACH_TENNIS] e [FOOTBALL] — ou monte a sua.
 */
data class StandingsRules(
    val pointsPerWin: Int = 3,
    val pointsPerDraw: Int = 1,
    val pointsPerLoss: Int = 0,
    val tieBreakers: List<TieBreaker>,
    val twoWayTieBreakers: List<TieBreaker>? = null,
    val withdrawalPolicy: WithdrawalPolicy = WithdrawalPolicy.AnnulMatches,
) {
    init {
        require(pointsPerWin > 0) { "a vitória precisa valer ponto (recebeu $pointsPerWin)" }
    }

    companion object {
        /**
         * **Beach tennis (regulamento SESC/ITF).** Ordena por vitórias (1 ponto cada; raquete não
         * empata). Empate de **2** → confronto direto; de **3+** → saldo de sets → saldo de games →
         * sorteio, voltando ao confronto direto quando sobrarem 2. Desistência **anula** os jogos.
         */
        val BEACH_TENNIS = StandingsRules(
            pointsPerWin = 1,
            pointsPerDraw = 0,
            pointsPerLoss = 0,
            tieBreakers = listOf(TieBreaker.SET_DIFFERENCE, TieBreaker.GAME_DIFFERENCE, TieBreaker.DRAW_ORDER),
            twoWayTieBreakers = listOf(
                TieBreaker.HEAD_TO_HEAD,
                TieBreaker.SET_DIFFERENCE,
                TieBreaker.GAME_DIFFERENCE,
                TieBreaker.DRAW_ORDER,
            ),
            withdrawalPolicy = WithdrawalPolicy.AnnulMatches,
        )

        /**
         * **Futebol / pênalti** (o regulamento do TorneioDePenalti): vitória 3, empate 1, derrota 0 →
         * confronto direto como mini-campeonato entre **todos** os empatados em pontos → saldo →
         * ordem do sorteio. Desistência conta os jogos que faltavam como derrota por `3-0`.
         */
        val FOOTBALL = StandingsRules(
            pointsPerWin = 3,
            pointsPerDraw = 1,
            pointsPerLoss = 0,
            tieBreakers = listOf(TieBreaker.HEAD_TO_HEAD, TieBreaker.GAME_DIFFERENCE, TieBreaker.DRAW_ORDER),
            twoWayTieBreakers = null,
            withdrawalPolicy = WithdrawalPolicy.CountAsLosses(Score.Points(3, 0)),
        )
    }
}

/**
 * Uma linha da tabela.
 *
 * No placar por pontos (`Score.Points`), "games" são os gols/pontos e os sets ficam em zero.
 *
 * @property maxPoints pontos disputados (`played × pointsPerWin`) — a base do aproveitamento.
 * @property withdrawn desistiu do torneio: vai para o fim da tabela e nunca se classifica.
 */
data class StandingsRow(
    val position: Int,
    val participantId: Long,
    val drawOrder: Int,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val points: Int,
    val maxPoints: Int,
    val setsFor: Int,
    val setsAgainst: Int,
    val gamesFor: Int,
    val gamesAgainst: Int,
    val withdrawn: Boolean = false,
) {
    val setDifference: Int get() = setsFor - setsAgainst
    val gameDifference: Int get() = gamesFor - gamesAgainst

    /** Média de sets (ganhos ÷ perdidos); [Double.POSITIVE_INFINITY] sem set perdido e com algum ganho. */
    val setRatio: Double get() = ratioOf(setsFor, setsAgainst)

    /** Média de games (ganhos ÷ perdidos); [Double.POSITIVE_INFINITY] sem game perdido e com algum ganho. */
    val gameRatio: Double get() = ratioOf(gamesFor, gamesAgainst)

    /** Aproveitamento de 0 a 100. */
    val pointsPercentage: Double get() = if (maxPoints == 0) 0.0 else points * 100.0 / maxPoints

    private fun ratioOf(won: Int, lost: Int): Double = when {
        lost > 0 -> won.toDouble() / lost
        won > 0 -> Double.POSITIVE_INFINITY
        else -> 0.0
    }
}

/**
 * Tabela de classificação de um grupo ou do todos-contra-todos.
 *
 * Só pontua o que **terminou** entre dois participantes **desta tabela**: confronto em andamento,
 * BYE e jogo de outro grupo não entram.
 */
object Standings {

    /**
     * @param participants os participantes desta tabela (o [TournamentParticipant.drawOrder] é o
     *   último desempate).
     * @param results os confrontos — pode vir a lista inteira do torneio; o que não é desta tabela é
     *   ignorado. Com [WithdrawalPolicy.CountAsLosses], passe também os jogos **pendentes** (como
     *   [MatchOutcome.InProgress]) para que os de quem desistiu virem W.O.
     * @param withdrawn quem desistiu do torneio; tratado pela [StandingsRules.withdrawalPolicy] e
     *   posto no fim da tabela.
     */
    fun compute(
        participants: List<TournamentParticipant>,
        results: List<MatchResult>,
        rules: StandingsRules,
        withdrawn: Set<Long> = emptySet(),
    ): List<StandingsRow> {
        val ids = participants.map { it.id }
        require(ids.toSet().size == ids.size) { "participante repetido na tabela" }
        val eligible = ids.toSet()

        val inTable = results.filter {
            it.sideAId != it.sideBId && it.sideAId in eligible && it.sideBId in eligible
        }
        val counted = applyWithdrawal(inTable, rules.withdrawalPolicy, withdrawn).filter { it.outcome.isFinished }

        val tallies = participants.associate { it.id to Tally(it.drawOrder) }
        counted.forEach { result ->
            tallies.getValue(result.sideAId).register(result, Side.A, rules)
            tallies.getValue(result.sideBId).register(result, Side.B, rules)
        }

        val rows = tallies.mapValues { (id, tally) -> tally.toRow(id, withdrawn = id in withdrawn) }
        val ranking = Ranking(rows, counted, rules)

        val active = ids.filter { it !in withdrawn }
        val out = ids.filter { it in withdrawn }
        val ordered = ranking.rank(active) + ranking.rank(out)

        return ordered.mapIndexed { index, id -> rows.getValue(id).copy(position = index + 1) }
    }

    /**
     * Ordena linhas de **grupos diferentes** — "melhores campanhas entre os 1ºs", "melhores 2ºs".
     *
     * Entre grupos de tamanhos diferentes, compare por **média e aproveitamento**
     * ([TieBreaker.POINTS_PERCENTAGE], [TieBreaker.SET_RATIO], [TieBreaker.GAME_RATIO]), nunca por
     * soma: o 1º de um grupo de 4 jogou um jogo a mais que o de um grupo de 3. [TieBreaker.HEAD_TO_HEAD]
     * é ignorado (quem é de grupos diferentes não se enfrentou) e a ordem do sorteio fecha o que sobrar.
     *
     * Devolve as linhas na nova ordem, **sem** mexer em [StandingsRow.position] (que é a do grupo).
     */
    fun rankAcrossGroups(
        rows: List<StandingsRow>,
        criteria: List<TieBreaker> = KnockoutSeeding.ByCampaign.DEFAULT_CRITERIA,
    ): List<StandingsRow> {
        val byId = rows.associateBy { it.participantId }
        require(byId.size == rows.size) { "participante repetido na comparação entre grupos" }
        var blocks: List<List<Long>> = listOf(rows.map { it.participantId })
        criteria.filter { it != TieBreaker.HEAD_TO_HEAD }.forEach { criterion ->
            blocks = blocks.flatMap { block -> partition(block, criterion, byId, headToHead = emptyMap()) }
        }
        return blocks.flatMap { block -> block.sortedBy { byId.getValue(it).drawOrder } }.map { byId.getValue(it) }
    }

    private fun applyWithdrawal(
        results: List<MatchResult>,
        policy: WithdrawalPolicy,
        withdrawn: Set<Long>,
    ): List<MatchResult> {
        if (withdrawn.isEmpty()) return results
        return when (policy) {
            WithdrawalPolicy.AnnulMatches -> results.filter { it.sideAId !in withdrawn && it.sideBId !in withdrawn }
            is WithdrawalPolicy.CountAsLosses -> results.map { result ->
                val aOut = result.sideAId in withdrawn
                val bOut = result.sideBId in withdrawn
                if (result.outcome.isFinished || aOut == bOut) {
                    result
                } else {
                    val winner = if (aOut) Side.B else Side.A
                    val score = if (winner == Side.A) policy.walkoverScoreForSideA else policy.walkoverScoreForSideA.mirrored()
                    result.copy(outcome = MatchOutcome.Walkover(winner), score = score)
                }
            }
        }
    }

    /** O desempate em si — separado para a recursão do empate de dois ler limpa. */
    private class Ranking(
        private val rows: Map<Long, StandingsRow>,
        private val finished: List<MatchResult>,
        private val rules: StandingsRules,
    ) {
        fun rank(ids: List<Long>): List<Long> =
            partition(ids, Criterion.Points, rows, emptyMap()).flatMap { tied ->
                resolve(tied, rules.tieBreakers, from = 0, inTwoWayChain = false)
            }

        private fun resolve(group: List<Long>, chain: List<TieBreaker>, from: Int, inTwoWayChain: Boolean): List<Long> {
            if (group.size <= 1) return group
            val twoWay = rules.twoWayTieBreakers
            if (!inTwoWayChain && twoWay != null && group.size == 2) {
                return resolve(group, twoWay, from = 0, inTwoWayChain = true)
            }
            if (from >= chain.size) return group.sortedBy { rows.getValue(it).drawOrder }

            val criterion = chain[from]
            val headToHead = if (criterion == TieBreaker.HEAD_TO_HEAD) headToHeadPoints(group) else emptyMap()
            val blocks = partition(group, criterion, rows, headToHead)
            return blocks.flatMap { resolve(it, chain, from + 1, inTwoWayChain) }
        }

        /** Pontos de cada um **só nos jogos entre os do [group]**. */
        private fun headToHeadPoints(group: List<Long>): Map<Long, Int> {
            val members = group.toSet()
            val points = group.associateWith { 0 }.toMutableMap()
            finished.forEach { result ->
                if (result.sideAId !in members || result.sideBId !in members) return@forEach
                val winner = result.outcome.winner
                if (winner == null) {
                    if (result.outcome == MatchOutcome.Draw) {
                        points[result.sideAId] = points.getValue(result.sideAId) + rules.pointsPerDraw
                        points[result.sideBId] = points.getValue(result.sideBId) + rules.pointsPerDraw
                    }
                } else {
                    val winnerId = if (winner == Side.A) result.sideAId else result.sideBId
                    val loserId = if (winner == Side.A) result.sideBId else result.sideAId
                    points[winnerId] = points.getValue(winnerId) + rules.pointsPerWin
                    points[loserId] = points.getValue(loserId) + rules.pointsPerLoss
                }
            }
            return points
        }
    }

    /** Os pontos não são critério de desempate — são a ordem primária. */
    private sealed interface Criterion {
        data object Points : Criterion
        data class By(val tieBreaker: TieBreaker) : Criterion
    }

    private fun partition(
        group: List<Long>,
        tieBreaker: TieBreaker,
        rows: Map<Long, StandingsRow>,
        headToHead: Map<Long, Int>,
    ): List<List<Long>> = partition(group, Criterion.By(tieBreaker), rows, headToHead)

    /**
     * Ordena o [group] pelo critério (melhor primeiro) e o devolve em blocos de empatados nele, na
     * ordem. Um bloco só = o critério não separou ninguém.
     */
    private fun partition(
        group: List<Long>,
        criterion: Criterion,
        rows: Map<Long, StandingsRow>,
        headToHead: Map<Long, Int>,
    ): List<List<Long>> {
        if (group.size <= 1) return listOf(group)
        val better = { x: Long, y: Long -> compare(criterion, rows.getValue(x), rows.getValue(y), headToHead) }
        val sorted = group.sortedWith { x, y -> -better(x, y) }
        val blocks = mutableListOf(mutableListOf(sorted.first()))
        for (i in 1 until sorted.size) {
            if (better(sorted[i - 1], sorted[i]) == 0) blocks.last().add(sorted[i]) else blocks.add(mutableListOf(sorted[i]))
        }
        return blocks
    }

    /** `> 0` se [x] fica à frente de [y] pelo critério; `0` se empatam nele. */
    private fun compare(criterion: Criterion, x: StandingsRow, y: StandingsRow, headToHead: Map<Long, Int>): Int =
        when (criterion) {
            Criterion.Points -> x.points.compareTo(y.points)
            is Criterion.By -> when (criterion.tieBreaker) {
                TieBreaker.HEAD_TO_HEAD ->
                    (headToHead[x.participantId] ?: 0).compareTo(headToHead[y.participantId] ?: 0)
                TieBreaker.SET_DIFFERENCE -> x.setDifference.compareTo(y.setDifference)
                TieBreaker.GAME_DIFFERENCE -> x.gameDifference.compareTo(y.gameDifference)
                TieBreaker.SETS_WON -> x.setsFor.compareTo(y.setsFor)
                TieBreaker.GAMES_WON -> x.gamesFor.compareTo(y.gamesFor)
                TieBreaker.SET_RATIO -> compareRatio(x.setsFor, x.setsAgainst, y.setsFor, y.setsAgainst)
                TieBreaker.GAME_RATIO -> compareRatio(x.gamesFor, x.gamesAgainst, y.gamesFor, y.gamesAgainst)
                TieBreaker.POINTS_PERCENTAGE -> comparePercentage(x, y)
                TieBreaker.WINS -> x.wins.compareTo(y.wins)
                TieBreaker.DRAW_ORDER -> y.drawOrder.compareTo(x.drawOrder)
            }
        }

    /** `won/lost` comparados exatamente. Sem perdido: infinito se ganhou algo, 0 se nada. */
    internal fun compareRatio(wonX: Int, lostX: Int, wonY: Int, lostY: Int): Int {
        val infiniteX = lostX == 0 && wonX > 0
        val infiniteY = lostY == 0 && wonY > 0
        if (infiniteX || infiniteY) return infiniteX.compareTo(infiniteY)
        // Agora ambos finitos: `0 ÷ 0` vale 0 (numerador 0, denominador 1).
        val denX = if (lostX == 0) 1L else lostX.toLong()
        val denY = if (lostY == 0) 1L else lostY.toLong()
        return (wonX.toLong() * denY).compareTo(wonY.toLong() * denX)
    }

    private fun comparePercentage(x: StandingsRow, y: StandingsRow): Int {
        // points/max comparados por produto cruzado; sem jogo, aproveitamento 0.
        val denX = if (x.maxPoints == 0) 1L else x.maxPoints.toLong()
        val denY = if (y.maxPoints == 0) 1L else y.maxPoints.toLong()
        val numX = if (x.maxPoints == 0) 0L else x.points.toLong()
        val numY = if (y.maxPoints == 0) 0L else y.points.toLong()
        return (numX * denY).compareTo(numY * denX)
    }

    private class Tally(val drawOrder: Int) {
        var played = 0
        var wins = 0
        var draws = 0
        var losses = 0
        var points = 0
        var maxPoints = 0
        var setsFor = 0
        var setsAgainst = 0
        var gamesFor = 0
        var gamesAgainst = 0

        fun register(result: MatchResult, side: Side, rules: StandingsRules) {
            played += 1
            maxPoints += rules.pointsPerWin
            setsFor += result.score.sets(side)
            setsAgainst += result.score.sets(side.opposite)
            gamesFor += result.score.games(side)
            gamesAgainst += result.score.games(side.opposite)
            val winner = result.outcome.winner
            when {
                winner == side -> {
                    wins += 1
                    points += rules.pointsPerWin
                }
                winner != null -> {
                    losses += 1
                    points += rules.pointsPerLoss
                }
                result.outcome == MatchOutcome.Draw -> {
                    draws += 1
                    points += rules.pointsPerDraw
                }
            }
        }

        fun toRow(id: Long, withdrawn: Boolean) = StandingsRow(
            position = 0,
            participantId = id,
            drawOrder = drawOrder,
            played = played,
            wins = wins,
            draws = draws,
            losses = losses,
            points = points,
            maxPoints = maxPoints,
            setsFor = setsFor,
            setsAgainst = setsAgainst,
            gamesFor = gamesFor,
            gamesAgainst = gamesAgainst,
            withdrawn = withdrawn,
        )
    }
}
