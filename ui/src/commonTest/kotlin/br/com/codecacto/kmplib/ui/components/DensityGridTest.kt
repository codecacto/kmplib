package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Testes da regra pura da grade acessível: o mapeamento densidade → nº de colunas (chave de layout
 * do [DensityGrid]) e o mapeamento tom → token / borda de alto contraste do [CommunicationTile].
 */
class DensityGridTest {

    @Test
    fun `mapeia densidade para colunas`() {
        assertEquals(1, GridDensity.One.columns)
        assertEquals(2, GridDensity.Two.columns)
        assertEquals(3, GridDensity.Three.columns)
        assertEquals(4, GridDensity.Four.columns)
        assertEquals(5, GridDensity.Five.columns)
    }

    @Test
    fun `gridDensityOf acha pelo numero de colunas e cai no fallback fora da faixa`() {
        GridDensity.entries.forEach { density ->
            assertEquals(density, gridDensityOf(density.columns))
        }
        assertEquals(GridDensity.Three, gridDensityOf(0))
        assertEquals(GridDensity.Three, gridDensityOf(9))
        assertEquals(GridDensity.One, gridDensityOf(9, fallback = GridDensity.One))
    }

    @Test
    fun `colunas efetivas em celular = densidade base`() {
        // ~400dp (largura de referência de celular): respeita a densidade escolhida.
        assertEquals(1, effectiveGridColumns(GridDensity.One, availableWidthDp = 400))
        assertEquals(2, effectiveGridColumns(GridDensity.Two, availableWidthDp = 400))
        assertEquals(3, effectiveGridColumns(GridDensity.Three, availableWidthDp = 400))
    }

    @Test
    fun `colunas efetivas ganham em tablet`() {
        // ~800dp (tablet): a grade ganha colunas proporcionalmente.
        assertEquals(2, effectiveGridColumns(GridDensity.One, availableWidthDp = 800))
        assertEquals(4, effectiveGridColumns(GridDensity.Two, availableWidthDp = 800))
        assertEquals(6, effectiveGridColumns(GridDensity.Three, availableWidthDp = 800))
    }

    @Test
    fun `colunas efetivas nunca abaixo da densidade base nem acima do maximo`() {
        // Tela estreita não colapsa a escolha do usuário.
        assertEquals(3, effectiveGridColumns(GridDensity.Three, availableWidthDp = 200))
        // Largura inválida cai na base.
        assertEquals(2, effectiveGridColumns(GridDensity.Two, availableWidthDp = 0))
        // Clamp no máximo em telas enormes.
        assertEquals(8, effectiveGridColumns(GridDensity.Three, availableWidthDp = 5000))
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
        assertEquals(listOf(1, 2, 3, 4, 5), columns)
    }

    // --- CommunicationTile: tom → token (todos preenchidos, nenhum usa surface) ---

    @Test
    fun `Normal usa primary preenchido (nao surface)`() {
        assertEquals(
            TileColorRole.Primary to TileColorRole.OnPrimary,
            communicationTileRoles(TileTone.Normal)
        )
    }

    @Test
    fun `Quick usa secondaryContainer tonal`() {
        assertEquals(
            TileColorRole.SecondaryContainer to TileColorRole.OnSecondaryContainer,
            communicationTileRoles(TileTone.Quick)
        )
    }

    @Test
    fun `Alert usa error`() {
        assertEquals(
            TileColorRole.Error to TileColorRole.OnError,
            communicationTileRoles(TileTone.Alert)
        )
    }

    @Test
    fun `cada tom mapeia container e conteudo distintos`() {
        TileTone.entries.forEach { tone ->
            val (container, content) = communicationTileRoles(tone)
            assertNotEquals(container, content, "$tone deve ter container != conteudo")
        }
    }

    // --- CommunicationTile: borda por alto contraste (none vs grossa) ---

    @Test
    fun `sem alto contraste nao tem borda`() {
        assertEquals(0.dp, communicationTileBorderWidth(highContrast = false))
    }

    @Test
    fun `alto contraste desenha borda grossa`() {
        val width = communicationTileBorderWidth(highContrast = true)
        assertEquals(HIGH_CONTRAST_TILE_BORDER, width)
        assertEquals(3.dp, width)
        assertTrue(width > 0.dp)
    }
}
