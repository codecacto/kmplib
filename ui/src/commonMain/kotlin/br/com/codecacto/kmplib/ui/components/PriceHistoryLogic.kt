package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.core.format.formatCurrencyBRL
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// ---------------------------------------------------------------------------------------------
// Lógica PURA do `PriceHistoryChart` — o que tem regra mora aqui, e é o que os testes cobrem
// sem aparelho. O composable só desenha o que estas funções devolvem.
// ---------------------------------------------------------------------------------------------

/**
 * Uma **captura** de preço: o instante em que o preço foi visto e o valor, em **centavos**.
 *
 * Centavos em `Long`, nunca `Double` — dinheiro em ponto flutuante erra o centavo na soma e na
 * comparação ("o menor preço" deixa de ser o menor por 0,0000001).
 */
@Immutable
data class PricePoint(
    val atEpochMillis: Long,
    val priceCents: Long,
)

/**
 * Um **degrau**: o intervalo em que um preço esteve em vigor.
 *
 * Preço de loja não varia de forma contínua entre duas capturas — ele fica parado e SALTA. Por isso
 * o gráfico é em degrau: a linha fica no valor até a próxima captura e sobe/desce na vertical.
 * Uma linha inclinada entre R$ 100 e R$ 80 afirmaria que em algum momento o produto custou R$ 90,
 * e isso nunca aconteceu.
 *
 * @property startMillis início do degrau **dentro da janela** exibida.
 * @property endMillis fim do degrau (a próxima captura, ou o fim da janela).
 * @property capturedAtMillis quando este preço foi de fato visto. Igual a [startMillis], exceto no
 *   degrau [carriedOver].
 * @property carriedOver `true` quando o preço foi capturado **antes** da janela e continuava valendo
 *   no início dela — é o que evita a janela de 30 dias começar "no vazio" até a primeira captura.
 */
@Immutable
data class PriceStep(
    val startMillis: Long,
    val endMillis: Long,
    val priceCents: Long,
    val capturedAtMillis: Long,
    val carriedOver: Boolean,
)

/** Um extremo da série (menor ou maior preço) e o degrau em que ele aparece. */
@Immutable
data class PriceExtreme(
    val priceCents: Long,
    val capturedAtMillis: Long,
    val stepIndex: Int,
)

/**
 * A série pronta para desenhar: degraus dentro da janela e os extremos.
 *
 * @property lowest menor preço **em vigor na janela**; empate = a ocorrência **mais recente** (é
 *   a que a pessoa procura: "quando foi a última vez que chegou nesse valor").
 * @property highest maior preço em vigor na janela; empate = a mais recente.
 * @property current o último degrau (o preço que vale no fim da janela).
 */
@Immutable
data class PriceHistorySeries(
    val steps: List<PriceStep>,
    val startMillis: Long,
    val endMillis: Long,
    val lowest: PriceExtreme?,
    val highest: PriceExtreme?,
) {
    val isEmpty: Boolean get() = steps.isEmpty()
    val current: PriceStep? get() = steps.lastOrNull()
}

/**
 * Monta os degraus de [points] na janela `[startMillis, endMillis]`.
 *
 * - As capturas são ordenadas pelo instante; duas no MESMO instante ficam com a última da lista.
 * - A captura imediatamente anterior a [startMillis] vira o degrau inicial ([PriceStep.carriedOver])
 *   — o preço que já valia quando a janela começou. Captura depois de [endMillis] é ignorada.
 * - [startMillis] `null` = a primeira captura; [endMillis] `null` = a última captura. Passe o
 *   "agora" em [endMillis] para a linha seguir até hoje (o último preço continua valendo).
 */
fun buildPriceHistory(
    points: List<PricePoint>,
    startMillis: Long? = null,
    endMillis: Long? = null,
): PriceHistorySeries {
    // Ordena e deduplica pelo instante (a última da lista vence).
    val sorted = points
        .withIndex()
        .sortedWith(compareBy({ it.value.atEpochMillis }, { it.index }))
        .map { it.value }
        .let { list -> list.filterIndexed { i, p -> i == list.lastIndex || list[i + 1].atEpochMillis != p.atEpochMillis } }

    val start = startMillis ?: sorted.firstOrNull()?.atEpochMillis ?: 0L
    val end = maxOf(endMillis ?: sorted.lastOrNull()?.atEpochMillis ?: start, start)

    val carried = sorted.lastOrNull { it.atEpochMillis < start }
    val inside = sorted.filter { it.atEpochMillis in start..end }

    data class Begin(val at: Long, val point: PricePoint, val carried: Boolean)

    val begins = buildList {
        if (carried != null && inside.firstOrNull()?.atEpochMillis != start) {
            add(Begin(start, carried, carried = true))
        }
        inside.forEach { add(Begin(it.atEpochMillis, it, carried = false)) }
    }
    val steps = begins.mapIndexed { i, b ->
        PriceStep(
            startMillis = b.at,
            endMillis = begins.getOrNull(i + 1)?.at ?: end,
            priceCents = b.point.priceCents,
            capturedAtMillis = b.point.atEpochMillis,
            carriedOver = b.carried,
        )
    }

    // Empate = a ocorrência mais recente.
    fun extremeOf(target: Long?): PriceExtreme? {
        if (target == null) return null
        val i = steps.indexOfLast { it.priceCents == target }
        return PriceExtreme(steps[i].priceCents, steps[i].capturedAtMillis, i)
    }
    val lowest = extremeOf(steps.minOfOrNull { it.priceCents })
    val highest = extremeOf(steps.maxOfOrNull { it.priceCents })
    return PriceHistorySeries(steps, start, end, lowest, highest)
}

/**
 * Índice do degrau em vigor em [timeMillis] (antes do primeiro = o primeiro; depois do último = o
 * último). `-1` só com a série vazia. É o que o toque usa para achar "o preço naquele dia".
 */
fun priceStepIndexAt(steps: List<PriceStep>, timeMillis: Long): Int {
    if (steps.isEmpty()) return -1
    val idx = steps.indexOfLast { it.startMillis <= timeMillis }
    return if (idx < 0) 0 else idx
}

/**
 * Marcas do eixo Y em **centavos**, em passos "redondos" (1, 2, 2,5 e 5 × 10ⁿ): R$ 2.500,
 * R$ 3.000, R$ 3.500 — nunca R$ 2.873,41.
 *
 * O eixo é **real**: as marcas envolvem o menor e o maior preço, e o gráfico não começa no zero
 * quando os preços estão longe dele (uma queda de R$ 3.000 para R$ 2.700 some num eixo de 0 a
 * 3.000). Nunca passa para baixo de zero se o menor preço não for negativo. Com um preço só, abre
 * uma faixa em volta dele para a linha não colar na borda.
 *
 * @param maxTicks teto de marcas (≥ 2).
 */
fun priceAxisTicks(minCents: Long, maxCents: Long, maxTicks: Int = 4): List<Long> {
    val lo0 = minOf(minCents, maxCents)
    val hi0 = maxOf(minCents, maxCents)
    val cap = maxTicks.coerceAtLeast(2)
    val span = if (hi0 == lo0) maxOf(kotlin.math.abs(lo0) / 10, 100L) else hi0 - lo0
    val rawStep = span.toDouble() / (cap - 1)
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    var niceIdx = NICE_STEPS.indexOfFirst { it * magnitude >= rawStep }.let { if (it < 0) NICE_STEPS.lastIndex else it }

    while (true) {
        val step = maxOf(1L, (NICE_STEPS[niceIdx] * magnitude).toLong())
        var lo = floor(lo0.toDouble() / step).toLong() * step
        var hi = ceil(hi0.toDouble() / step).toLong() * step
        if (lo0 >= 0 && lo < 0) lo = 0
        if (hi == lo) {
            hi = lo + step
            if (lo0 == hi0 && lo > 0 && lo == lo0) lo -= step
            if (lo0 >= 0 && lo < 0) lo = 0
        }
        val count = ((hi - lo) / step + 1).toInt()
        if (count <= cap + 1 || niceIdx == NICE_STEPS.lastIndex) {
            return (0 until count).map { lo + it * step }
        }
        niceIdx++
    }
}

private val NICE_STEPS = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0)

/**
 * Formato de preço padrão (BRL): `R$ 1.234,56`. Troque pelo formatador da moeda do app quando
 * ele não vender em real.
 */
fun defaultPriceFormatter(cents: Long): String = formatCurrencyBRL(cents / 100.0)

/**
 * Formato das marcas do eixo Y: sem centavos quando a marca é redonda (`R$ 2.500`), com centavos
 * quando não é (`R$ 12,50`) — o eixo mostra quatro números num espaço estreito.
 */
fun defaultPriceAxisFormatter(cents: Long): String {
    val full = defaultPriceFormatter(cents)
    return if (cents % 100 == 0L) full.removeSuffix(",00") else full
}

/** Posição horizontal (0..1) de [timeMillis] na janela; janela de largura zero = meio. */
internal fun priceTimeFraction(timeMillis: Long, startMillis: Long, endMillis: Long): Float {
    if (endMillis <= startMillis) return 0.5f
    return ((timeMillis - startMillis).toDouble() / (endMillis - startMillis)).coerceIn(0.0, 1.0).toFloat()
}

/** Posição vertical (0 = base, 1 = topo) de [cents] entre as marcas extremas. */
internal fun priceValueFraction(cents: Long, lowCents: Long, highCents: Long): Float {
    if (highCents <= lowCents) return 0.5f
    return ((cents - lowCents).toDouble() / (highCents - lowCents)).coerceIn(0.0, 1.0).toFloat()
}

/** Troca `%1$s`, `%2$s`… de um texto de recurso pelos argumentos, na ordem. */
internal fun fillPriceTemplate(template: String, vararg args: String): String {
    var out = template
    args.forEachIndexed { i, arg -> out = out.replace("%${i + 1}\$s", arg) }
    return out
}
