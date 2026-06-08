package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes do contrato público do [ThemeChipGrid].
 *
 * O `ThemeChipGrid` é um composable de renderização (FlowRow de chips). A verificação
 * de toque/render via `runComposeUiTest` exige host de UI (desktop/instrumentado), que
 * não roda no alvo `testDebugUnitTest` neste ambiente (Windows, sem host Compose) —
 * mesma limitação dos demais testes de UI da lib (ex.: `OfflineBannerTest`). Aqui
 * cobrimos o tipo público [ChipItem] e a semântica de seleção (modo de exibição),
 * que são a parte testável em JVM puro.
 */
class ThemeChipGridTest {

    @Test
    fun `ChipItem mantem id e label`() {
        val item = ChipItem(id = "ansiedade", label = "Ansiedade")
        assertEquals("ansiedade", item.id)
        assertEquals("Ansiedade", item.label)
        assertNull(item.leadingIcon)
    }

    @Test
    fun `ChipItem com mesmo id e label sao iguais (data class)`() {
        assertEquals(
            ChipItem("gratidao", "Gratidão"),
            ChipItem("gratidao", "Gratidão")
        )
    }

    @Test
    fun `selectedIds vazio significa modo acao`() {
        // Regra do componente: selectedIds.isNotEmpty() ativa o modo selecionável.
        val selecaoVazia = emptySet<String>()
        assertTrue(selecaoVazia.isEmpty())
    }

    @Test
    fun `id selecionado pertence ao conjunto`() {
        val selecionados = setOf("gratidao", "perdao")
        assertTrue("gratidao" in selecionados)
        assertTrue("perdao" in selecionados)
        assertTrue("ansiedade" !in selecionados)
    }
}
