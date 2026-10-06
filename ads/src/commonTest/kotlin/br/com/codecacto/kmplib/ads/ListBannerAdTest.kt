package br.com.codecacto.kmplib.ads

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListBannerAdTest {

    @Test
    fun `carregando nao e vazio`() {
        // Carregando com zero itens ainda: nada de banner grande, senao ele aparece e some.
        assertEquals(ListAdState.LOADING, listAdStateOf(isLoading = true, isEmpty = false))
        assertEquals(ListAdState.LOADING, listAdStateOf(isLoading = true, isEmpty = true))
        assertEquals(ListAdState.EMPTY, listAdStateOf(isLoading = false, isEmpty = true))
        assertEquals(ListAdState.CONTENT, listAdStateOf(isLoading = false, isEmpty = false))
    }

    @Test
    fun `rodape so tem banner com itens e sempre o padrao`() {
        assertEquals(BannerSize.STANDARD, footerBannerSizeFor(ListAdState.CONTENT))
        assertNull(footerBannerSizeFor(ListAdState.LOADING))
        assertNull(footerBannerSizeFor(ListAdState.EMPTY))
    }

    @Test
    fun `telefone em pe com sobra alta vira quadrado na largura util`() {
        val spec = emptyStateBannerSpec(availableWidth = 328.dp, availableHeight = 420.dp)
        assertEquals(EmptyStateBannerSpec(BannerSize.SQUARE, 328.dp), spec)
    }

    @Test
    fun `sobra mais baixa que a largura encolhe o quadrado para caber inteiro`() {
        val spec = emptyStateBannerSpec(availableWidth = 328.dp, availableHeight = 280.dp)
        assertEquals(EmptyStateBannerSpec(BannerSize.SQUARE, 280.dp), spec)
    }

    @Test
    fun `quadrado pequeno demais desce para o grande na largura util`() {
        // 200 dp de altura: o quadrado teria 200 (< 240); a faixa 3:1 de 328 mede ~109 e cabe.
        val spec = emptyStateBannerSpec(availableWidth = 328.dp, availableHeight = 200.dp)
        assertEquals(EmptyStateBannerSpec(BannerSize.LARGE, 328.dp), spec)
    }

    @Test
    fun `sem espaco nem para a faixa grande cai no padrao`() {
        val spec = emptyStateBannerSpec(availableWidth = 600.dp, availableHeight = 100.dp)
        // largura limitada ao teto (400): 400/3 = 133 > 100 => padrao.
        assertEquals(EmptyStateBannerSpec(BannerSize.STANDARD, 400.dp), spec)
    }

    @Test
    fun `tablet nao ganha quadrado de 800 dp`() {
        val spec = emptyStateBannerSpec(availableWidth = 800.dp, availableHeight = 900.dp)
        assertEquals(EmptyStateBannerSpec(BannerSize.SQUARE, AdDefaults.EMPTY_STATE_BANNER_MAX_WIDTH), spec)
    }

    @Test
    fun `altura infinita e quadrado na largura util`() {
        val spec = emptyStateBannerSpec(availableWidth = 328.dp, availableHeight = Dp.Infinity)
        assertEquals(EmptyStateBannerSpec(BannerSize.SQUARE, 328.dp), spec)
    }

    @Test
    fun `largura negativa vira zero e nao quebra`() {
        val spec = emptyStateBannerSpec(availableWidth = (-10).dp, availableHeight = 300.dp)
        assertEquals(0.dp, spec.width)
    }
}
