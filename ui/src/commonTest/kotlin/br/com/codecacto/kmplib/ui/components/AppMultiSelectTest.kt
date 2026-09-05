package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O que um toque no chip faz com a seleção do [AppMultiSelect].
 *
 * É a única regra do componente que não depende de tela — e a que decide o que o formulário
 * recebe. A ordem importa: "seg, qua, sex" enviado fora de ordem vira sujeira de diff no servidor
 * e lista embaralhada na volta.
 */
class AppMultiSelectTest {

    @Test
    fun `tocar um item nao marcado adiciona no FIM`() {
        assertEquals(listOf("seg", "qua"), alternarSelecao(listOf("seg"), "qua"))
    }

    @Test
    fun `tocar um item marcado remove, sem mexer nos outros`() {
        assertEquals(
            listOf("seg", "sex"),
            alternarSelecao(listOf("seg", "qua", "sex"), "qua"),
        )
    }

    @Test
    fun `primeira marcacao sai de vazio`() {
        assertEquals(listOf("seg"), alternarSelecao(emptyList<String>(), "seg"))
    }

    @Test
    fun `desmarcar o unico item devolve lista vazia — e nao null`() {
        assertEquals(emptyList<String>(), alternarSelecao(listOf("seg"), "seg"))
    }

    @Test
    fun `marcar e desmarcar volta ao estado anterior`() {
        val antes = listOf("seg", "qua")
        val depois = alternarSelecao(alternarSelecao(antes, "sex"), "sex")
        assertEquals(antes, depois)
    }

    @Test
    fun `funciona com qualquer tipo, nao so String`() {
        assertEquals(listOf(1, 3), alternarSelecao(listOf(1, 2, 3), 2))
    }
}
