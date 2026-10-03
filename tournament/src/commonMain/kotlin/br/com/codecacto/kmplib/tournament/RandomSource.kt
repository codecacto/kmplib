package br.com.codecacto.kmplib.tournament

import kotlin.random.Random

/**
 * De onde vem o acaso do sorteio. Injetável para o teste rodar com fonte determinística; a troca por
 * uma fonte mais forte, se um dia fizer sentido, é de uma linha.
 */
fun interface RandomSource {
    /** Inteiro uniformemente distribuído em `0 until bound` ([bound] sempre ≥ 1). */
    fun nextInt(bound: Int): Int

    companion object {
        /** Fonte sobre um [Random] qualquer — `RandomSource.of(Random(42))` no teste. */
        fun of(random: Random): RandomSource = RandomSource { random.nextInt(it) }
    }
}

/**
 * Fonte padrão: [Random.Default] da stdlib.
 *
 * **Por que não um CSPRNG.** O sorteio precisa de **imparcialidade**, não de imprevisibilidade contra
 * um adversário: ordena algumas dezenas de nomes num torneio amador, offline, sem prêmio
 * manipulável por quem preveja a semente. [Random.Default] entrega isso em todas as plataformas sem
 * `expect/actual`. Se um caso real pedir fonte forte, implementa-se outra [RandomSource] — nenhum
 * algoritmo muda.
 */
object SystemRandomSource : RandomSource {
    override fun nextInt(bound: Int): Int = Random.Default.nextInt(bound)
}

/**
 * Fisher-Yates: percorre de trás para frente trocando cada posição por uma sorteada entre as ainda
 * não fixadas. Toda permutação sai com a mesma probabilidade, e nenhum elemento some ou se duplica.
 */
fun <T> List<T>.shuffledBy(source: RandomSource = SystemRandomSource): List<T> {
    val items = toMutableList()
    for (i in items.lastIndex downTo 1) {
        val j = source.nextInt(i + 1)
        val kept = items[i]
        items[i] = items[j]
        items[j] = kept
    }
    return items
}
