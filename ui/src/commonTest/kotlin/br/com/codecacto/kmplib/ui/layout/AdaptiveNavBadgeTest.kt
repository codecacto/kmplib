package br.com.codecacto.kmplib.ui.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import br.com.codecacto.kmplib.ui.components.BottomNavTestTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveNavBadgeTest {

    private val textos = AdaptiveNavBadgeTexts()

    @Test
    fun rotulo_do_contador() {
        assertNull(navBadgeLabel(0))
        assertNull(navBadgeLabel(-3))
        assertEquals("1", navBadgeLabel(1))
        assertEquals("99", navBadgeLabel(99))
        assertEquals("99+", navBadgeLabel(100))
        assertEquals("9+", navBadgeLabel(12, max = 9))
    }

    @Test
    fun visibilidade() {
        assertFalse((null as NavBadge?).isVisible())
        assertFalse(NavBadge.Count(0).isVisible())
        assertTrue(NavBadge.Count(2).isVisible())
        assertTrue(NavBadge.Dot().isVisible())
    }

    @Test
    fun descricao_para_leitor_de_tela_inclui_o_numero() {
        assertEquals("Alertas, 1 notificação", textos.describe("Alertas", NavBadge.Count(1)))
        assertEquals("Alertas, 3 notificações", textos.describe("Alertas", NavBadge.Count(3)))
        assertEquals("Alertas, mais de 99 notificações", textos.describe("Alertas", NavBadge.Count(250)))
        assertEquals("Início, há novidades", textos.describe("Início", NavBadge.Dot()))
    }

    @Test
    fun sem_selo_visivel_nao_ha_descricao_extra() {
        assertNull(textos.describe("Alertas", null))
        assertNull(textos.describe("Alertas", NavBadge.Count(0)))
    }

    @Test
    fun descricao_propria_do_app_vence_o_padrao() {
        assertEquals(
            "Alertas, 3 alertas abertos",
            textos.describe("Alertas", NavBadge.Count(3, contentDescription = "3 alertas abertos")),
        )
    }

    @Test
    fun destino_antigo_compila_sem_selo_e_id_do_selo_e_estavel() {
        // Forma de antes da 2.314.0 (4 parâmetros posicionais): continua compilando, sem selo.
        val d = AdaptiveDestination("alertas", "Alertas", Icons.Filled.Home, Icons.Filled.Home)
        assertNull(d.badge)
        assertEquals("nav-badge-alertas", BottomNavTestTags.badge("alertas"))
    }
}
