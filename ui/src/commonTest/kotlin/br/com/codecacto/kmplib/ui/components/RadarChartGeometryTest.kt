package br.com.codecacto.kmplib.ui.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RadarChartGeometryTest {

    private fun quase(esperado: Float, obtido: Float, folga: Float = 0.01f) =
        assertTrue(abs(esperado - obtido) < folga, "esperava ~$esperado, veio $obtido")

    // ── ângulos ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `o primeiro vertice fica no TOPO, nao a direita`() {
        // Se começasse à direita, o primeiro domínio da lista cairia no meio da lateral, onde
        // ninguém procura o que leu primeiro.
        val angulos = angulosDosVertices(7)
        assertEquals(-PI / 2, angulos.first(), 1e-9)
    }

    @Test
    fun `os vertices se distribuem por igual`() {
        val angulos = angulosDosVertices(4)
        assertEquals(4, angulos.size)
        val passo = angulos[1] - angulos[0]
        assertEquals(PI / 2, passo, 1e-9)
    }

    @Test
    fun `zero eixos nao gera angulo nenhum`() {
        assertTrue(angulosDosVertices(0).isEmpty())
    }

    // ── escala ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `valor acima do maximo SATURA em vez de estourar a caixa`() {
        assertEquals(1f, fracaoDoRaio(140.0, 100.0))
    }

    @Test
    fun `valor negativo vira zero — ponta para dentro leria como o oposto`() {
        assertEquals(0f, fracaoDoRaio(-30.0, 100.0))
    }

    @Test
    fun `maximo zero nao divide por zero`() {
        assertEquals(0f, fracaoDoRaio(50.0, 0.0))
    }

    @Test
    fun `metade da escala e metade do raio`() {
        quase(0.5f, fracaoDoRaio(50.0, 100.0))
    }

    // ── vértices ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `o vertice de valor maximo encosta no raio, no topo`() {
        val pontos = verticesDaSerie(listOf(100.0, 0.0, 0.0), 100.0, raio = 50f, centroX = 60f, centroY = 60f)
        quase(60f, pontos[0].x)
        quase(10f, pontos[0].y) // topo = centro menos o raio
    }

    @Test
    fun `valor zero cai exatamente no centro`() {
        val pontos = verticesDaSerie(listOf(0.0, 0.0, 0.0), 100.0, raio = 50f, centroX = 60f, centroY = 60f)
        pontos.forEach {
            quase(60f, it.x)
            quase(60f, it.y)
        }
    }

    @Test
    fun `nenhum vertice sai do raio, com valores fora da escala`() {
        val pontos = verticesDaSerie(
            listOf(500.0, -80.0, 100.0, 0.0, 1e9, 42.0, 99.0),
            maximo = 100.0,
            raio = 40f,
            centroX = 100f,
            centroY = 100f,
        )
        pontos.forEach { ponto ->
            val distancia = kotlin.math.sqrt(
                (ponto.x - 100f) * (ponto.x - 100f) + (ponto.y - 100f) * (ponto.y - 100f),
            )
            assertTrue(distancia <= 40.01f, "vértice a $distancia do centro, com raio 40")
        }
    }

    @Test
    fun `a grade de fracao 1 encosta no raio em todos os eixos`() {
        val pontos = verticesDaGrade(7, fracao = 1f, raio = 30f, centroX = 50f, centroY = 50f)
        assertEquals(7, pontos.size)
        pontos.forEach { ponto ->
            val distancia = kotlin.math.sqrt(
                (ponto.x - 50f) * (ponto.x - 50f) + (ponto.y - 50f) * (ponto.y - 50f),
            )
            quase(30f, distancia, 0.05f)
        }
    }

    // ── rótulos ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `rotulo curto nao se quebra`() {
        assertEquals(listOf("Autoconsciência"), quebrarRotuloDoVertice("Autoconsciência", 16))
    }

    @Test
    fun `rotulo longo quebra no espaco mais proximo do meio`() {
        assertEquals(
            listOf("Flexibilidade", "Comportamental"),
            quebrarRotuloDoVertice("Flexibilidade Comportamental", 14),
        )
    }

    @Test
    fun `quebra no meio, nao no primeiro espaco`() {
        // "Conversão em Ação" tem espaço no índice 9 e no 12; o meio é 8,5.
        assertEquals(listOf("Conversão", "em Ação"), quebrarRotuloDoVertice("Conversão em Ação", 12))
    }

    @Test
    fun `palavra unica gigante NAO e cortada ao meio`() {
        // Cortar inventaria uma sílaba que não existe.
        assertEquals(listOf("Superextraordinário"), quebrarRotuloDoVertice("Superextraordinário", 10))
    }

    @Test
    fun `nunca passa de duas linhas`() {
        val linhas = quebrarRotuloDoVertice("Regulação Emocional Sob Pressão Constante", 10)
        assertTrue(linhas.size <= 2, "saíram ${linhas.size} linhas")
    }

    // ── ancoragem ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `o rotulo da direita ancora pelo inicio e cresce para fora`() {
        assertEquals(AncoraDoRotulo.INICIO, ancoraDoRotulo(0.0))
    }

    @Test
    fun `o rotulo da esquerda ancora pelo fim`() {
        assertEquals(AncoraDoRotulo.FIM, ancoraDoRotulo(PI))
    }

    @Test
    fun `o rotulo do topo e do rodape ficam centrados`() {
        assertEquals(AncoraDoRotulo.CENTRO, ancoraDoRotulo(-PI / 2))
        assertEquals(AncoraDoRotulo.CENTRO, ancoraDoRotulo(PI / 2))
    }
}

class RotuloDoAnelTest {

    @Test
    fun `escala longa sai inteira`() {
        // 100 com 4 anéis: 25, 50, 75, 100 — a casa decimal aqui só polui.
        assertEquals("25", rotuloDoAnel(100.0, 0.25f))
        assertEquals("50", rotuloDoAnel(100.0, 0.5f))
        assertEquals("100", rotuloDoAnel(100.0, 1f))
    }

    @Test
    fun `escala curta ganha uma casa, porque o anel cai em quebrado`() {
        // 5 com 4 anéis: 1,25 · 2,5 · 3,75 · 5. Arredondar para inteiro imprimiria "1, 3, 4, 5" —
        // uma progressão que mente sobre onde as linhas da grade estão.
        assertEquals("1,3", rotuloDoAnel(5.0, 0.25f))
        assertEquals("2,5", rotuloDoAnel(5.0, 0.5f))
        assertEquals("3,8", rotuloDoAnel(5.0, 0.75f))
        assertEquals("5,0", rotuloDoAnel(5.0, 1f))
    }

    @Test
    fun `a virgula e decimal, nunca ponto`() {
        assertTrue(rotuloDoAnel(5.0, 0.5f).contains(','))
        assertTrue(!rotuloDoAnel(5.0, 0.5f).contains('.'))
    }
}
