package br.com.codecacto.kmplib.tournament

/**
 * Os dois lados de um confronto. **Não é "casa/fora"**: em pênalti [A] é quem bate primeiro; no
 * tênis, a dupla escrita à esquerda na súmula. Nenhuma regra do motor depende de mando.
 */
enum class Side {
    A,
    B,
    ;

    val opposite: Side get() = if (this == A) B else A
}

/**
 * Placar de um confronto, do jeito que a **classificação** precisa dele.
 *
 * Duas formas, e a classificação não pergunta qual está em uso — pergunta só [games] e [sets]:
 * - [Points] — um par de números (gol, cobrança convertida, ponto corrido). Aqui **"games" são os
 *   pontos** e **sets não existem** (sempre 0). É o placar do futebol e do pênalti.
 * - [Sets] — a lista de sets de um esporte de raquete (`6-4`, `7-6(5)`). Sem guardar set a set não
 *   existe "saldo de sets", que é critério de desempate do regulamento de beach tennis.
 *
 * O placar não sabe se é **válido** — `6-5` é um [SetScore] perfeitamente construível. Quem valida
 * é o [MatchFormat], porque a validade depende do formato escolhido (6-4 fecha um set de 6, mas não
 * um pro-set de 8).
 */
sealed interface Score {

    /** Games (raquete) ou pontos ([Points]) do [side]. */
    fun games(side: Side): Int

    /** Sets vencidos pelo [side]. Sempre `0` em [Points]. */
    fun sets(side: Side): Int

    /** O mesmo placar visto do outro lado (A↔B). */
    fun mirrored(): Score

    /** Placar por pontos (gols, cobranças). */
    data class Points(val a: Int, val b: Int) : Score {

        val isLevel: Boolean get() = a == b

        /** Lado à frente, ou `null` se empatado. */
        val leader: Side? get() = when {
            a > b -> Side.A
            b > a -> Side.B
            else -> null
        }

        fun of(side: Side): Int = if (side == Side.A) a else b

        override fun games(side: Side): Int = of(side)

        override fun sets(side: Side): Int = 0

        override fun mirrored(): Points = Points(b, a)

        companion object {
            val ZERO = Points(0, 0)
        }
    }

    /** Placar por sets. A ordem da lista é a ordem em que os sets foram jogados. */
    data class Sets(val sets: List<SetScore>) : Score {

        constructor(vararg sets: SetScore) : this(sets.toList())

        override fun games(side: Side): Int = sets.sumOf { it.games(side) }

        override fun sets(side: Side): Int = sets.count { it.winner == side }

        override fun mirrored(): Sets = Sets(sets.map { it.mirrored() })
    }
}

/** Saldo de sets do [side] neste placar (`0` em [Score.Points]). */
fun Score.setDifference(side: Side): Int = sets(side) - sets(side.opposite)

/** Saldo de games (ou de pontos, em [Score.Points]) do [side]. */
fun Score.gameDifference(side: Side): Int = games(side) - games(side.opposite)

/**
 * Um set: games de cada lado e, quando houve, o tie-break.
 *
 * Convenção de registro, a do ITF: o set decidido no tie-break se escreve com os games **finais**
 * (`7-6`) e o tie-break à parte (`TieBreak(7, 5)`). O **super tie-break** (match tie-break de 10 no
 * lugar do 3º set) conta como **um set e um game** — registre com [matchTieBreak], que monta `1-0`
 * com o tie-break dentro.
 */
data class SetScore(
    val a: Int,
    val b: Int,
    val tieBreak: TieBreak? = null,
) {

    fun games(side: Side): Int = if (side == Side.A) a else b

    /**
     * Quem venceu o set: quem tem mais games; empatado em games (registro incompleto), quem venceu
     * o tie-break; sem isso, ninguém.
     */
    val winner: Side? get() = when {
        a > b -> Side.A
        b > a -> Side.B
        else -> tieBreak?.winner
    }

    fun mirrored(): SetScore = SetScore(b, a, tieBreak?.mirrored())

    companion object {
        /**
         * O super tie-break (match tie-break) registrado como set: `1-0` para quem venceu, com os
         * pontos no [tieBreak]. É assim que ele conta **um set e um game** no saldo (ITF).
         */
        fun matchTieBreak(pointsA: Int, pointsB: Int): SetScore = SetScore(
            a = if (pointsA > pointsB) 1 else 0,
            b = if (pointsB > pointsA) 1 else 0,
            tieBreak = TieBreak(pointsA, pointsB),
        )
    }
}

/** Pontos de um tie-break (de 7 ou o super, de 10). */
data class TieBreak(val a: Int, val b: Int) {

    fun points(side: Side): Int = if (side == Side.A) a else b

    val winner: Side? get() = when {
        a > b -> Side.A
        b > a -> Side.B
        else -> null
    }

    fun mirrored(): TieBreak = TieBreak(b, a)
}
