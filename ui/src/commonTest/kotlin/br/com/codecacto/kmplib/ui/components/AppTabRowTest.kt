package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTabRowTest {
    @Test
    fun aba_ativa_fica_dentro_da_faixa() {
        assertEquals(0, appTabRowSelectedIndex(-1, 3))
        assertEquals(2, appTabRowSelectedIndex(9, 3))
        assertEquals(1, appTabRowSelectedIndex(1, 3))
        assertEquals(0, appTabRowSelectedIndex(0, 0))
    }

    @Test
    fun ids_por_aba_com_chave() {
        assertEquals(
            listOf("vistoria-saida-abas-entrada", "vistoria-saida-abas-saida", "vistoria-saida-abas-lado-a-lado"),
            OptionTestTags.options("vistoria-saida-abas", 3, listOf("entrada", "saida", "lado-a-lado")),
        )
    }
}
