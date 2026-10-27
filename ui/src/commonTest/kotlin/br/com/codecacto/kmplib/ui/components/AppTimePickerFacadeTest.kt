package br.com.codecacto.kmplib.ui.components

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `AppTimePicker` como fachada do `AppTimePickerDialog` (2.329.0): o contrato antigo de callbacks
 * continua o mesmo, e os rótulos saem dos recursos traduzidos da lib.
 */
class AppTimePickerFacadeTest {

    private class Registro {
        val selecionados = mutableListOf<Pair<Int, Int>>()
        var fechamentos = 0
    }

    private fun ponte(r: Registro, horaDeEntrada: Pair<Int, Int>? = null) = LegacyTimePickerCallbacks(
        onTimeSelected = { h, m -> r.selecionados += h to m },
        // `onDismiss` default do AppTimePicker = onTimeSelected(hora de entrada)
        onDismiss = { if (horaDeEntrada != null) r.selecionados += horaDeEntrada else r.fechamentos++ },
    )

    @Test
    fun `confirmar entrega a hora e NAO chama o onDismiss que o dialogo novo dispara em seguida`() {
        val r = Registro()
        val p = ponte(r, horaDeEntrada = 8 to 30)
        p.confirm(LocalTime(14, 5))
        p.dismiss() // o AppTimePickerDialog chama onDismiss logo depois do confirmar
        assertEquals(listOf(14 to 5), r.selecionados, "a hora velha não pode sobrescrever a escolhida")
    }

    @Test
    fun `cancelar ou tocar fora chama so o onDismiss do chamador`() {
        val r = Registro()
        val p = ponte(r)
        p.dismiss()
        assertEquals(1, r.fechamentos)
        assertTrue(r.selecionados.isEmpty())
    }

    @Test
    fun `onDismiss default devolve a hora de entrada como antes`() {
        val r = Registro()
        ponte(r, horaDeEntrada = 8 to 30).dismiss()
        assertEquals(listOf(8 to 30), r.selecionados)
    }

    @Test
    fun `reabrir depois de confirmar volta a repassar o fechamento`() {
        val r = Registro()
        val p = ponte(r)
        p.confirm(LocalTime(9, 0))
        p.dismiss()
        p.dismiss()
        assertEquals(1, r.fechamentos)
    }

    @Test
    fun `hora de entrada fora da faixa e puxada para dentro`() {
        assertEquals(LocalTime(23, 59), legacyInitialTime(25, 75))
        assertEquals(LocalTime(0, 0), legacyInitialTime(-1, -3))
        assertEquals(LocalTime(8, 30), legacyInitialTime(8, 30))
    }
}
