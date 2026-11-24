package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3Api::class)
class UnsavedChangesGuardTest {

    private class Form(var dirty: Boolean) {
        val guard = UnsavedChangesGuardState { dirty }
    }

    @Test
    fun `vazio ou salvo sai direto sem confirmar`() {
        val f = Form(dirty = false)
        var saiu = 0
        f.guard.requestExit { saiu++ }
        assertEquals(1, saiu)
        assertFalse(f.guard.isConfirming)
    }

    @Test
    fun `com algo preenchido pede confirmacao e nao sai`() {
        val f = Form(dirty = true)
        var saiu = 0
        f.guard.requestExit { saiu++ }
        assertEquals(0, saiu)
        assertTrue(f.guard.isConfirming)
    }

    @Test
    fun `descartar chama a saida uma vez e fecha a confirmacao`() {
        val f = Form(dirty = true)
        var saiu = 0
        f.guard.requestExit { saiu++ }
        f.guard.confirmDiscard()
        assertEquals(1, saiu)
        assertFalse(f.guard.isConfirming)
        f.guard.confirmDiscard() // sem pendente: nada
        assertEquals(1, saiu)
    }

    @Test
    fun `continuar editando nao sai`() {
        val f = Form(dirty = true)
        var saiu = 0
        f.guard.requestExit { saiu++ }
        f.guard.cancelDiscard()
        assertEquals(0, saiu)
        assertFalse(f.guard.isConfirming)
        assertTrue(f.guard.isDirty)
    }

    @Test
    fun `pedido repetido troca a saida pendente`() {
        val f = Form(dirty = true)
        val saidas = mutableListOf<String>()
        f.guard.requestExit { saidas += "voltar" }
        f.guard.requestExit { saidas += "aba" }
        f.guard.confirmDiscard()
        assertEquals(listOf("aba"), saidas)
    }

    @Test
    fun `depois de salvar a saida nao pergunta`() {
        val f = Form(dirty = true)
        f.guard.markSaved()
        var saiu = 0
        f.guard.requestExit { saiu++ }
        assertEquals(1, saiu)
        assertFalse(f.guard.isConfirming)
    }

    @Test
    fun `mudanca do dirty depois de salvar volta a guardar`() {
        val f = Form(dirty = true)
        f.guard.markSaved()
        assertFalse(f.guard.isDirty)
        f.dirty = false; f.guard.onDirtyChanged()
        f.dirty = true; f.guard.onDirtyChanged()
        assertTrue(f.guard.isDirty)
        var saiu = 0
        f.guard.requestExit { saiu++ }
        assertEquals(0, saiu)
    }

    @Test
    fun `o dirty e lido na hora da decisao`() {
        val f = Form(dirty = false)
        f.dirty = true
        var saiu = 0
        f.guard.requestExit { saiu++ }
        assertEquals(0, saiu)
    }

    @Test
    fun `folha segura o Hidden so com algo nao salvo`() {
        val f = Form(dirty = true)
        var saiu = 0
        assertTrue(sheetValueChangeAllowed(SheetValue.Expanded, f.guard) { saiu++ })
        assertFalse(sheetValueChangeAllowed(SheetValue.Hidden, f.guard) { saiu++ })
        assertTrue(f.guard.isConfirming)
        f.guard.confirmDiscard()
        assertEquals(1, saiu)
        // descartado: o hide() animado que vem depois passa
        assertTrue(sheetValueChangeAllowed(SheetValue.Hidden, f.guard) { saiu++ })
        assertEquals(1, saiu)
    }

    @Test
    fun `folha limpa fecha sem perguntar`() {
        val f = Form(dirty = false)
        assertTrue(sheetValueChangeAllowed(SheetValue.Hidden, f.guard) { error("não deveria") })
        assertFalse(f.guard.isConfirming)
    }

    @Test
    fun `dismissRequest do dialogo segue a guarda`() {
        val f = Form(dirty = true)
        var fechou = 0
        val dismiss = f.guard.dismissRequest { fechou++ }
        dismiss()
        assertEquals(0, fechou)
        f.guard.confirmDiscard()
        assertEquals(1, fechou)
    }

    @Test
    fun `textos padrao do fundador`() {
        val t = UnsavedChangesTexts()
        assertEquals("Descartar o que foi preenchido?", t.title)
        assertEquals("Se sair agora, o que você preencheu será perdido.", t.message)
        assertEquals("Continuar editando", t.keepEditing)
        assertEquals("Descartar", t.discard)
    }
}
