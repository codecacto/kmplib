package br.com.codecacto.kmplib.contact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactPrefillTest {

    @Test
    fun semListaDeAssuntosCaiNoTextoLivre() {
        assertNull(contactSubjectOptions(null, "Erro"))
        assertNull(contactSubjectOptions(emptyList(), "Erro"))
        assertNull(contactSubjectOptions(listOf(" ", ""), null))
    }

    @Test
    fun listaEhAparadaSemVaziosNemRepetidosNaOrdemDoApp() {
        assertEquals(
            listOf("Sugestão", "Erro", "Outro"),
            contactSubjectOptions(listOf(" Sugestão ", "Erro", "", "Erro", "Outro"), null),
        )
    }

    @Test
    fun assuntoInicialDaListaNaoDuplica() {
        assertEquals(listOf("A", "B"), contactSubjectOptions(listOf("A", "B"), " B "))
    }

    @Test
    fun assuntoInicialForaDaListaEntraNoTopo() {
        assertEquals(
            listOf("Erro em dado de candidato", "Sugestão", "Outro"),
            contactSubjectOptions(listOf("Sugestão", "Outro"), "Erro em dado de candidato"),
        )
    }

    @Test
    fun assuntoInicialEhAparadoEMensagemNao() {
        assertEquals("Erro", contactInitialSubject("  Erro "))
        assertEquals("", contactInitialSubject(null))
        assertEquals("Cargo: Prefeito\nNúmero: 13\n\n", contactInitialMessage("Cargo: Prefeito\nNúmero: 13\n\n"))
        assertEquals("", contactInitialMessage(null))
    }
}
