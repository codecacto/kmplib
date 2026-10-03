package br.com.codecacto.kmplib.tournament

/**
 * Todos contra todos pelo **método do círculo**.
 *
 * Fixa o primeiro participante e gira os demais em torno dele. É o algoritmo canônico de
 * round-robin: gera cada confronto **exatamente uma vez**, no menor número de rodadas possível
 * (`n-1` com número par, `n` com ímpar), e **ninguém joga duas vezes na mesma rodada**.
 *
 * Com número ímpar entra um adversário fictício ([REST]): quem cai contra ele descansa naquela
 * rodada. Como o giro leva todo mundo a passar pela posição da folga, cada um descansa uma vez.
 */
object RoundRobin {

    /** Adversário fictício do método do círculo. Nunca chega a virar confronto. */
    const val REST: Long = -1L

    /**
     * @param doubleRound turno e returno: o returno repete o turno com os lados invertidos, nas
     *   rodadas seguintes.
     */
    fun schedule(participantIds: List<Long>, doubleRound: Boolean = false): List<RoundRobinMatch> {
        if (participantIds.size < 2) return emptyList()
        require(REST !in participantIds) { "o id $REST é reservado para a folga do método do círculo" }

        val withRest = participantIds.toMutableList()
        if (withRest.size % 2 == 1) withRest.add(REST)

        val total = withRest.size
        val half = total / 2
        var wheel: List<Long> = withRest.toList()
        val firstLeg = mutableListOf<RoundRobinMatch>()

        for (round in 0 until total - 1) {
            for (i in 0 until half) {
                var sideA = wheel[i]
                var sideB = wheel[total - 1 - i]
                // Alternar o lado a cada rodada evita que o mesmo participante seja sempre o lado A.
                if (round % 2 == 1) {
                    val kept = sideA
                    sideA = sideB
                    sideB = kept
                }
                if (sideA == REST || sideB == REST) continue
                firstLeg.add(RoundRobinMatch(round = round + 1, sideAId = sideA, sideBId = sideB))
            }
            // O giro: o primeiro fica parado, o último passa para a segunda posição.
            wheel = listOf(wheel[0], wheel[total - 1]) + wheel.subList(1, total - 1)
        }

        if (!doubleRound) return firstLeg

        val roundsPerLeg = total - 1
        return firstLeg + firstLeg.map {
            RoundRobinMatch(round = it.round + roundsPerLeg, sideAId = it.sideBId, sideBId = it.sideAId)
        }
    }

    /** Quantas rodadas o formato produz — o resumo que a tela de criação mostra. */
    fun roundCount(participants: Int, doubleRound: Boolean = false): Int {
        if (participants < 2) return 0
        val even = if (participants % 2 == 1) participants + 1 else participants
        return (even - 1) * (if (doubleRound) 2 else 1)
    }

    /** Quantos confrontos o formato produz. */
    fun matchCount(participants: Int, doubleRound: Boolean = false): Int {
        if (participants < 2) return 0
        val matches = participants * (participants - 1) / 2
        return if (doubleRound) matches * 2 else matches
    }
}

/** Um confronto de round-robin com a rodada (1-based) em que ele acontece. */
data class RoundRobinMatch(val round: Int, val sideAId: Long, val sideBId: Long)
