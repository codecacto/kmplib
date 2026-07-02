package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes da regra pura da grade acessível: o mapeamento densidade → nº de colunas (chave de layout
 * do [DensityGrid]).
 */
class DensityGridTest {

    @Test
    fun `mapeia densidade para colunas`() {
        assertEquals(1, GridDensity.One.columns)
        assertEquals(2, GridDensity.Two.columns)
        assertEquals(3, GridDensity.Three.columns)
    }

    @Test
    fun `todas as densidades tem pelo menos uma coluna`() {
        GridDensity.entries.forEach { density ->
            assert(density.columns >= 1) { "$density deve ter >= 1 coluna" }
        }
    }

    @Test
    fun `ordem dos degraus e crescente`() {
        val columns = GridDensity.entries.map { it.columns }
        assertEquals(listOf(1, 2, 3), columns)
    }
}
