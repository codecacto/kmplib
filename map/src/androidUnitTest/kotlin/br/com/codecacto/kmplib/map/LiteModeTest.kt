package br.com.codecacto.kmplib.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O modo lite não desenha `GroundOverlay`: mapa só-leitura com imagem não pode ser lite (2.382.1). */
class LiteModeTest {
    @Test fun so_leitura_sem_imagem_e_lite() = assertTrue(liteModeFor(interactive = false, hasGroundOverlays = false))

    @Test fun so_leitura_com_imagem_nao_e_lite() = assertFalse(liteModeFor(interactive = false, hasGroundOverlays = true))

    @Test fun interativo_nunca_e_lite() {
        assertFalse(liteModeFor(interactive = true, hasGroundOverlays = false))
        assertFalse(liteModeFor(interactive = true, hasGroundOverlays = true))
    }
}
