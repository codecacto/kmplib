package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A régua do "digite EXCLUIR" que liga o `confirmEnabled` do `AppInputDialog` (2.281.0). */
class TypedConfirmationMatchesTest {

    @Test
    fun palavraExataConfere() = assertTrue(typedConfirmationMatches("EXCLUIR", "EXCLUIR"))

    @Test
    fun caixaNaoImporta() {
        assertTrue(typedConfirmationMatches("excluir", "EXCLUIR"))
        assertTrue(typedConfirmationMatches("Excluir", "EXCLUIR"))
        assertTrue(typedConfirmationMatches("EXCLUIR", "excluir"))
    }

    @Test
    fun espacosNaoImportam() {
        assertTrue(typedConfirmationMatches("  EXCLUIR  ", "EXCLUIR"))
        assertTrue(typedConfirmationMatches("EX CLUIR", "EXCLUIR"))
        assertTrue(typedConfirmationMatches("\tEXCLUIR\n", "EXCLUIR"))
    }

    @Test
    fun vazioOuIncompletoNaoConfere() {
        assertFalse(typedConfirmationMatches("", "EXCLUIR"))
        assertFalse(typedConfirmationMatches("   ", "EXCLUIR"))
        assertFalse(typedConfirmationMatches("EXCLUI", "EXCLUIR"))
        assertFalse(typedConfirmationMatches("EXCLUIRR", "EXCLUIR"))
    }

    @Test
    fun outraPalavraNaoConfere() {
        assertFalse(typedConfirmationMatches("DELETE", "EXCLUIR"))
        assertFalse(typedConfirmationMatches("EXCLUÍR", "EXCLUIR"))
    }

    @Test
    fun esperadoEmBrancoNuncaConfere() {
        assertFalse(typedConfirmationMatches("", ""))
        assertFalse(typedConfirmationMatches("qualquer", "  "))
    }

    @Test
    fun outrosIdiomasDaTela() {
        assertTrue(typedConfirmationMatches("delete", "DELETE"))
        assertTrue(typedConfirmationMatches("Eliminar ", "ELIMINAR"))
    }
}
