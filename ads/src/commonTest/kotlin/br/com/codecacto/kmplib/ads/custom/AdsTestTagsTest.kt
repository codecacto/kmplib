package br.com.codecacto.kmplib.ads.custom

import coil3.compose.AsyncImagePainter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Os ids são CONTRATO com os flows Maestro dos apps (`<app>/mobile/.maestro/`) e com a captura de
 * print de loja: mudar o texto de um quebra a prova de publicidade em todos os apps de uma vez, sem
 * erro de compilação em lugar nenhum. Por isso o valor literal é travado aqui.
 */
class AdsTestTagsTest {

    @Test
    fun `ids literais sao estaveis`() {
        assertEquals("ads-banner", AdsTestTags.BANNER)
        assertEquals("ads-banner-carregado", AdsTestTags.BANNER_CARREGADO)
        assertEquals("ads-interstitial", AdsTestTags.INTERSTITIAL)
        assertEquals("ads-interstitial-carregado", AdsTestTags.INTERSTITIAL_CARREGADO)
        assertEquals("ads-btn-fechar-interstitial", AdsTestTags.BTN_FECHAR_INTERSTITIAL)
        assertEquals("ads-estado-vazio-banner", AdsTestTags.BANNER_ESTADO_VAZIO)
    }

    @Test
    fun `todos os ids estao listados e sao unicos`() {
        assertEquals(6, AdsTestTags.all.size)
        assertEquals(AdsTestTags.all.size, AdsTestTags.all.toSet().size)
    }

    @Test
    fun `ids seguem o vocabulario da fabrica - minusculas com hifen e prefixo ads`() {
        val vocabulario = Regex("^ads(-[a-z0-9]+)+$")
        AdsTestTags.all.forEach { id ->
            assertTrue(vocabulario.matches(id), "id fora do vocabulário: $id")
        }
    }

    @Test
    fun `id de carregado estende o do conteiner do mesmo formato`() {
        // O Maestro aceita regex no `id:`; com o prefixo comum, `ads-banner.*` casa os dois.
        assertTrue(AdsTestTags.BANNER_CARREGADO.startsWith(AdsTestTags.BANNER + "-"))
        assertTrue(AdsTestTags.INTERSTITIAL_CARREGADO.startsWith(AdsTestTags.INTERSTITIAL + "-"))
    }

    @Test
    fun `imagem so ganha id de carregado quando carregou`() {
        assertEquals(
            AdsTestTags.BANNER_CARREGADO,
            creativeTestTag(AdCreativeLoad.Loaded, AdsTestTags.BANNER_CARREGADO),
        )
        assertNull(creativeTestTag(AdCreativeLoad.Loading, AdsTestTags.BANNER_CARREGADO))
        assertNull(creativeTestTag(AdCreativeLoad.Failed, AdsTestTags.BANNER_CARREGADO))
    }

    @Test
    fun `estado vazio e carregando do coil nao contam como apareceu`() {
        assertEquals(AdCreativeLoad.Loading, AsyncImagePainter.State.Empty.toAdCreativeLoad())
        assertEquals(AdCreativeLoad.Loading, AsyncImagePainter.State.Loading(null).toAdCreativeLoad())
        assertNull(
            creativeTestTag(
                AsyncImagePainter.State.Loading(null).toAdCreativeLoad(),
                AdsTestTags.INTERSTITIAL_CARREGADO,
            ),
        )
    }
}
