package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A regra de classificação de janela (GAP-TABLET-01).
 *
 * `ProvideWindowSizeClass` depende de `BoxWithConstraints` (host Compose), que não roda no
 * `testDebugUnitTest` deste ambiente — mesma limitação dos demais testes de UI da lib. O que se cobre
 * aqui é a parte que decide o layout: [windowSizeClassFor] e as derivações dela.
 *
 * Os casos são **larguras de aparelho real**, não números redondos: é neles que um limiar errado
 * aparece. Um `<=` no lugar de `<` faria um telefone de exatamente 600dp virar tablet.
 */
class WindowSizeTest {

    @Test
    fun telefone_em_retrato_e_compacta() {
        // Pixel 8 (412dp), iPhone 15 (393dp), telefone pequeno (360dp).
        assertEquals(WindowSizeClass.COMPACTA, windowSizeClassFor(360.dp))
        assertEquals(WindowSizeClass.COMPACTA, windowSizeClassFor(393.dp))
        assertEquals(WindowSizeClass.COMPACTA, windowSizeClassFor(412.dp))
    }

    @Test
    fun limiar_de_600_e_exclusivo() {
        // 599 ainda é telefone; 600 já é média. O erro de `<=` aqui transformaria um telefone
        // grande em tablet, e ele ganharia navigation rail no lugar da barra inferior.
        assertEquals(WindowSizeClass.COMPACTA, windowSizeClassFor(599.dp))
        assertEquals(WindowSizeClass.MEDIA, windowSizeClassFor(600.dp))
    }

    @Test
    fun tablet_em_retrato_e_media() {
        // iPad 10.9" retrato (820dp), iPad mini retrato (744dp), telefone em paisagem (~740dp).
        assertEquals(WindowSizeClass.MEDIA, windowSizeClassFor(744.dp))
        assertEquals(WindowSizeClass.MEDIA, windowSizeClassFor(820.dp))
    }

    @Test
    fun limiar_de_840_e_exclusivo() {
        assertEquals(WindowSizeClass.MEDIA, windowSizeClassFor(839.dp))
        assertEquals(WindowSizeClass.EXPANDIDA, windowSizeClassFor(840.dp))
    }

    @Test
    fun tablet_em_paisagem_e_desktop_sao_expandida() {
        // iPad 10.9" paisagem (1180dp), tablet Android paisagem (1280dp), desktop (1440dp).
        assertEquals(WindowSizeClass.EXPANDIDA, windowSizeClassFor(1180.dp))
        assertEquals(WindowSizeClass.EXPANDIDA, windowSizeClassFor(1280.dp))
        assertEquals(WindowSizeClass.EXPANDIDA, windowSizeClassFor(1440.dp))
    }

    @Test
    fun navegacao_lateral_so_fora_do_telefone() {
        assertFalse(WindowSizeClass.COMPACTA.temNavegacaoLateral)
        assertTrue(WindowSizeClass.MEDIA.temNavegacaoLateral)
        assertTrue(WindowSizeClass.EXPANDIDA.temNavegacaoLateral)
    }

    @Test
    fun dois_paineis_so_na_expandida() {
        // Tablet em RETRATO não ganha dois painéis: caberiam, mas cada um sairia com menos de 400dp
        // — duas colunas espremidas, que é pior que uma boa.
        assertFalse(WindowSizeClass.COMPACTA.temDoisPaineis)
        assertFalse(WindowSizeClass.MEDIA.temDoisPaineis)
        assertTrue(WindowSizeClass.EXPANDIDA.temDoisPaineis)
    }

    @Test
    fun grade_cresce_com_a_janela() {
        assertEquals(1, gridColumnsFor(WindowSizeClass.COMPACTA))
        assertEquals(2, gridColumnsFor(WindowSizeClass.MEDIA))
        assertEquals(3, gridColumnsFor(WindowSizeClass.EXPANDIDA))
    }

    @Test
    fun leitura_tem_teto_fora_do_telefone() {
        // No telefone o texto usa a largura toda (não há o que limitar). No tablet ele PRECISA de
        // teto: linha de 1280dp faz o olho perder a volta.
        assertEquals(androidx.compose.ui.unit.Dp.Unspecified, leituraMaxWidth(WindowSizeClass.COMPACTA))
        assertEquals(640.dp, leituraMaxWidth(WindowSizeClass.MEDIA))
        assertEquals(720.dp, leituraMaxWidth(WindowSizeClass.EXPANDIDA))
    }
}
