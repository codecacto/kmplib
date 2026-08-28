package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O resumo do campo fechado do [AppMultiDropdownField]. É ele que decide se a pessoa confere o que
 * marcou sem reabrir o menu — e é a única parte do componente que não depende de tela.
 */
class AppDropdownFieldTest {

    @Test
    fun `sem nada marcado o campo fica vazio, nunca com zero`() {
        assertEquals("", dropdownFieldSummary(emptyList()))
    }

    @Test
    fun `ate o limite mostra todos os nomes`() {
        assertEquals("Centro, Jardim Aurora", dropdownFieldSummary(listOf("Centro", "Jardim Aurora")))
    }

    @Test
    fun `acima do limite conta os que sobraram`() {
        val bairros = listOf("Centro", "Jardim Aurora", "Vila Nova", "São José")
        assertEquals("Centro, Jardim Aurora +2", dropdownFieldSummary(bairros))
    }

    @Test
    fun `limite de um nome mostra um nome`() {
        assertEquals("Centro +1", dropdownFieldSummary(listOf("Centro", "Vila Nova"), maxLabels = 1))
    }

    @Test
    fun `limite invalido cai na contagem, e nao numa lista sem fim`() {
        assertEquals("3", dropdownFieldSummary(listOf("A", "B", "C"), maxLabels = 0))
    }
}
