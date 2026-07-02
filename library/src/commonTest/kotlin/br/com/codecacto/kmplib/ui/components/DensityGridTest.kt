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
