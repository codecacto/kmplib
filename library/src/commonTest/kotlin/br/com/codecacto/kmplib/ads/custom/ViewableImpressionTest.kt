package br.com.codecacto.kmplib.ads.custom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A regra de VISIBILIDADE da impressao viewable.
 *
 * O que se testa aqui e a aritmetica de `visibleFractionOf` — a decisao "isto conta como visto?".
 * O relogio continuo e o "dispara uma vez" vivem no `LaunchedEffect` do modifier, que precisa de
 * arvore Compose; a parte que erra calado, e que fazia o numero inflar, e esta.
 */
class ViewableImpressionTest {

    @Test
    fun `elemento inteiro na tela conta como 100 por cento`() {
        val f = visibleFractionOf(totalWidth = 300, totalHeight = 60, visibleWidth = 300f, visibleHeight = 60f)
        assertEquals(1f, f)
        assertTrue(f >= VIEWABLE_MIN_FRACTION, "banner inteiro visivel tem de passar do minimo")
    }

    @Test
    fun `metade cortada pela borda fica exatamente no limite`() {
        // Banner de 60dp de altura com 30 visiveis: 50%, o minimo do criterio MRC/IAB.
        val f = visibleFractionOf(totalWidth = 300, totalHeight = 60, visibleWidth = 300f, visibleHeight = 30f)
        assertEquals(0.5f, f)
        assertTrue(f >= VIEWABLE_MIN_FRACTION, "exatamente 50% conta — o criterio e >=, nao >")
    }

    @Test
    fun `so uma tira aparecendo no rodape NAO conta`() {
        // O caso que inflava no web antes da 0.93.0 e no mobile ate agora: o banner existe na
        // arvore, mas o usuario nunca chegou nele.
        val f = visibleFractionOf(totalWidth = 300, totalHeight = 60, visibleWidth = 300f, visibleHeight = 12f)
        assertEquals(0.2f, f)
        assertTrue(f < VIEWABLE_MIN_FRACTION, "20% visivel nao pode contar impressao")
    }

    @Test
    fun `fora da tela conta zero`() {
        val f = visibleFractionOf(totalWidth = 300, totalHeight = 60, visibleWidth = 0f, visibleHeight = 0f)
        assertEquals(0f, f)
    }

    @Test
    fun `layout ainda nao medido conta zero, e nao estoura na divisao`() {
        // Primeira passada de layout: `coords.size` e 0x0. Sem a guarda, isto seria divisao por zero
        // e o resultado NaN passaria pelo `>=` como false — funcionaria por acidente, nao por regra.
        val f = visibleFractionOf(totalWidth = 0, totalHeight = 0, visibleWidth = 0f, visibleHeight = 0f)
        assertEquals(0f, f)
    }

    @Test
    fun `bounds negativo -- elemento acima do topo -- conta zero`() {
        // `boundsInWindow` pode devolver largura/altura negativas quando o elemento saiu por cima.
        // Sem o `coerceAtLeast(0f)`, dois negativos multiplicados dariam area POSITIVA e o anuncio
        // fora da tela contaria impressao.
        val f = visibleFractionOf(totalWidth = 300, totalHeight = 60, visibleWidth = -300f, visibleHeight = -60f)
        assertEquals(0f, f, "negativo tem de virar zero, nunca area positiva")
    }

    @Test
    fun `fracao nunca passa de 1`() {
        // Defesa contra bounds maior que o proprio layout (arredondamento de escala).
        val f = visibleFractionOf(totalWidth = 300, totalHeight = 60, visibleWidth = 320f, visibleHeight = 70f)
        assertEquals(1f, f)
    }

    @Test
    fun `o criterio e o mesmo da weblib`() {
        // Se algum dia alguem afrouxar isto, os numeros de app e de site deixam de ser somaveis na
        // mesma coluna de `monitoramento.ad_stats` — e ninguem percebe, porque nada quebra.
        assertEquals(0.5f, VIEWABLE_MIN_FRACTION, "MRC/IAB para display: 50% dos pixels")
        assertEquals(1_000L, VIEWABLE_MIN_DURATION_MS, "MRC/IAB para display: 1 segundo continuo")
    }
}
