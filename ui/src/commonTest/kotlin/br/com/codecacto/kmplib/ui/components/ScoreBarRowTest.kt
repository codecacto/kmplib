package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A fração da barra é a única aritmética desta linha — e é onde ela erra: valor acima do máximo
 * estourando a barra para fora da caixa, ou negativo desenhando para a esquerda.
 */
class ScoreBarRowTest {

    private fun fracao(value: Double, max: Double): Float =
        if (max <= 0.0) 0f else (value / max).coerceIn(0.0, 1.0).toFloat()

    @Test
    fun `metade da escala e meia barra`() = assertEquals(0.5f, fracao(2.5, 5.0))

    @Test
    fun `acima do maximo satura em vez de estourar a caixa`() = assertEquals(1f, fracao(7.0, 5.0))

    @Test
    fun `negativo vira zero, nunca barra para a esquerda`() = assertEquals(0f, fracao(-1.0, 5.0))

    @Test
    fun `maximo zero nao divide por zero`() = assertEquals(0f, fracao(3.0, 0.0))
}
