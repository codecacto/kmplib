package br.com.codecacto.kmplib.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import br.com.codecacto.kmplib.ui.components.BottomNavItem
import br.com.codecacto.kmplib.ui.components.BottomNavTestTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Id de automação por item da [br.com.codecacto.kmplib.ui.components.AppBottomNavBar] (2.240.0).
 * O flow do Maestro toca em `nav-item-<route>`; o contrato do nome é o que este teste trava.
 */
class BottomNavTestTagsTest {

    private fun item(route: String, testTag: String? = null) =
        BottomNavItem(icon = Icons.Default.Home, label = "Início", route = route, testTag = testTag)

    @Test
    fun idPadraoVemDaRota() {
        assertEquals("nav-item-clientes", item("clientes").effectiveTestTag)
        assertEquals("nav-item-clientes", BottomNavTestTags.item("clientes"))
    }

    @Test
    fun idInformadoPeloAppVence() {
        assertEquals("aba-clientes", item("clientes", testTag = "aba-clientes").effectiveTestTag)
    }

    @Test
    fun semIdInformadoOCampoContinuaNulo() {
        // Default preserva o construtor de quem já usa a barra (parâmetro novo, no fim).
        assertNull(item("clientes").testTag)
    }

    @Test
    fun itensDeRotasDiferentesNaoDividemId() {
        val ids = listOf("agenda", "clientes", "servicos", "config").map { item(it).effectiveTestTag }
        assertEquals(ids.size, ids.toSet().size)
    }
}
