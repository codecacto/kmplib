package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceHistoryLogicTest {

    private val dia = 86_400_000L

    @Test
    fun `serie vazia nao tem degrau nem extremos`() {
        val s = buildPriceHistory(emptyList())
        assertTrue(s.isEmpty)
        assertNull(s.lowest)
        assertNull(s.highest)
        assertNull(s.current)
    }

    @Test
    fun `cada captura vira um degrau ate a proxima e o ultimo vai ate o fim da janela`() {
        val s = buildPriceHistory(
            listOf(PricePoint(0, 10_000), PricePoint(2 * dia, 8_000), PricePoint(5 * dia, 9_000)),
            endMillis = 10 * dia,
        )
        assertEquals(listOf(0L to 2 * dia, 2 * dia to 5 * dia, 5 * dia to 10 * dia), s.steps.map { it.startMillis to it.endMillis })
        assertEquals(listOf(10_000L, 8_000L, 9_000L), s.steps.map { it.priceCents })
        assertEquals(9_000L, s.current!!.priceCents)
    }

    @Test
    fun `ordena capturas fora de ordem e no mesmo instante vale a ultima da lista`() {
        val s = buildPriceHistory(
            listOf(PricePoint(3 * dia, 7_000), PricePoint(0, 10_000), PricePoint(3 * dia, 6_500)),
        )
        assertEquals(listOf(10_000L, 6_500L), s.steps.map { it.priceCents })
    }

    @Test
    fun `captura anterior a janela vira o preco de abertura`() {
        val s = buildPriceHistory(
            listOf(PricePoint(0, 10_000), PricePoint(40 * dia, 9_000)),
            startMillis = 30 * dia,
            endMillis = 60 * dia,
        )
        val abertura = s.steps.first()
        assertTrue(abertura.carriedOver)
        assertEquals(30 * dia, abertura.startMillis)
        assertEquals(0L, abertura.capturedAtMillis)
        assertEquals(10_000L, abertura.priceCents)
        assertFalse(s.steps[1].carriedOver)
    }

    @Test
    fun `captura exatamente no inicio da janela dispensa o preco de abertura`() {
        val s = buildPriceHistory(
            listOf(PricePoint(0, 10_000), PricePoint(30 * dia, 9_000)),
            startMillis = 30 * dia,
        )
        assertEquals(1, s.steps.size)
        assertFalse(s.steps.single().carriedOver)
    }

    @Test
    fun `captura depois do fim da janela e ignorada`() {
        val s = buildPriceHistory(listOf(PricePoint(0, 10_000), PricePoint(20 * dia, 1)), endMillis = 10 * dia)
        assertEquals(listOf(10_000L), s.steps.map { it.priceCents })
    }

    @Test
    fun `menor e maior preco em empate ficam com a ocorrencia mais recente`() {
        val s = buildPriceHistory(
            listOf(
                PricePoint(0, 9_000), PricePoint(dia, 12_000), PricePoint(2 * dia, 9_000), PricePoint(3 * dia, 12_000),
            ),
        )
        val menor = s.lowest!!
        assertEquals(9_000L, menor.priceCents)
        assertEquals(2 * dia, menor.capturedAtMillis)
        assertEquals(2, menor.stepIndex)
        assertEquals(3 * dia, s.highest?.capturedAtMillis)
    }

    @Test
    fun `degrau em vigor num instante`() {
        val s = buildPriceHistory(listOf(PricePoint(10, 1), PricePoint(20, 2), PricePoint(30, 3)))
        assertEquals(0, priceStepIndexAt(s.steps, 5))
        assertEquals(0, priceStepIndexAt(s.steps, 19))
        assertEquals(1, priceStepIndexAt(s.steps, 20))
        assertEquals(2, priceStepIndexAt(s.steps, 99))
        assertEquals(-1, priceStepIndexAt(emptyList(), 0))
    }

    @Test
    fun `eixo em passos redondos que envolvem os precos sem forcar o zero`() {
        val ticks = priceAxisTicks(270_000, 300_000)
        assertEquals(listOf(270_000L, 280_000L, 290_000L, 300_000L), ticks)
    }

    @Test
    fun `eixo envolve precos quebrados`() {
        val ticks = priceAxisTicks(8_990, 14_990)
        assertTrue(ticks.first() <= 8_990 && ticks.last() >= 14_990, "$ticks")
        assertTrue(ticks.size in 2..5, "$ticks")
        val passo = ticks[1] - ticks[0]
        assertTrue(ticks.zipWithNext().all { (a, b) -> b - a == passo })
    }

    @Test
    fun `preco unico abre uma faixa em volta dele`() {
        val ticks = priceAxisTicks(10_000, 10_000)
        assertTrue(ticks.first() < 10_000 && ticks.last() > 10_000, "$ticks")
    }

    @Test
    fun `eixo nunca desce abaixo de zero com precos positivos`() {
        assertTrue(priceAxisTicks(50, 120).first() >= 0)
        assertTrue(priceAxisTicks(0, 0).first() >= 0)
    }

    @Test
    fun `formatos de preco`() {
        assertEquals("R$ 2.500", defaultPriceAxisFormatter(250_000).replace(' ', ' '))
        assertEquals("R$ 12,50", defaultPriceAxisFormatter(1_250).replace(' ', ' '))
        assertEquals("R$ 99,90", defaultPriceFormatter(9_990).replace(' ', ' '))
    }

    @Test
    fun `fracoes de posicao`() {
        assertEquals(0.5f, priceTimeFraction(5, 0, 10))
        assertEquals(0.5f, priceTimeFraction(5, 7, 7))
        assertEquals(1f, priceTimeFraction(50, 0, 10))
        assertEquals(0f, priceValueFraction(100, 100, 200))
        assertEquals(1f, priceValueFraction(200, 100, 200))
    }

    @Test
    fun `molde de texto troca os argumentos na ordem`() {
        assertEquals("R$ 1 em 12/09", fillPriceTemplate("%1\$s em %2\$s", "R$ 1", "12/09"))
    }
}
