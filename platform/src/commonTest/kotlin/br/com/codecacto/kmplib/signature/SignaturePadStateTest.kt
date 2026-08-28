package br.com.codecacto.kmplib.signature

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes da lógica de estado do [SignaturePadState] (sem render de UI — o desenho em
 * canvas é coberto por teste instrumentado/manual). Cobre `isEmpty`/`clear`/`undo`
 * e o contrato de `toPngBytes` quando vazio/não medido.
 */
class SignaturePadStateTest {

    private fun state() = SignaturePadState()

    @Test
    fun novoEstado_estaVazio() {
        assertTrue(state().isEmpty)
    }

    @Test
    fun aoAdicionarTraco_naoEstaVazio() {
        val s = state()
        s.strokes.add(mutableListOf(Offset2D(1f, 1f), Offset2D(2f, 2f)))
        assertFalse(s.isEmpty)
    }

    @Test
    fun clear_removeTodosOsTracos() {
        val s = state()
        s.strokes.add(mutableListOf(Offset2D(1f, 1f)))
        s.strokes.add(mutableListOf(Offset2D(2f, 2f)))
        s.clear()
        assertTrue(s.isEmpty)
        assertEquals(0, s.strokes.size)
    }

    @Test
    fun undo_removeApenasUltimoTraco() {
        val s = state()
        s.strokes.add(mutableListOf(Offset2D(1f, 1f)))
        s.strokes.add(mutableListOf(Offset2D(2f, 2f)))
        s.undo()
        assertEquals(1, s.strokes.size)
        assertFalse(s.isEmpty)
        s.undo()
        assertTrue(s.isEmpty)
    }

    @Test
    fun undo_emEstadoVazio_eNoOp() {
        val s = state()
        s.undo()
        assertTrue(s.isEmpty)
    }

    @Test
    fun toPngBytes_retornaNull_quandoVazio() {
        assertNull(state().toPngBytes())
    }

    @Test
    fun toPngBytes_retornaNull_quandoAreaNaoMedida() {
        val s = state()
        s.strokes.add(mutableListOf(Offset2D(1f, 1f), Offset2D(2f, 2f)))
        // canvasWidthPx/HeightPx permanecem 0 (composable nunca desenhou)
        assertNull(s.toPngBytes())
    }
}
