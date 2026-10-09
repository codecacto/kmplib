package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import java.io.File
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Estado NÃO salvável para dado sensível (2.271.0): `rememberSyncedTextFieldState(saveable = false)`
 * e `AppDatePickerDialog(ephemeral = true)`.
 *
 * A promessa tem duas metades e as duas quebram com build verde: (1) o caminho efêmero não passa
 * por `rememberSaveable`/`rememberTextFieldState`/`rememberDatePickerState` — que são os que gravam
 * no `Bundle` —, e (2) fora disso o comportamento é o MESMO (reconciler com fila; min/max e UTC).
 * Teste de UI não existe na kmplib (decisão de jun/2026), então (1) é conferida no fonte e (2) pelo
 * construtor do estado.
 */
@OptIn(ExperimentalMaterial3Api::class)
class EstadoNaoSalvavelTest {

    private fun fonte(nome: String): String =
        File("src/commonMain/kotlin/br/com/codecacto/kmplib/ui/components/$nome")
            .also { assertTrue(it.exists(), "fonte não encontrada a partir de ${File(".").canonicalPath}") }
            .readText()

    /** Corpo da função [nome] (do `fun` até a chave que fecha). */
    private fun corpo(fonte: String, nome: String): String {
        val inicio = Regex("""fun\s+$nome\s*\(""").find(fonte)!!.range.first
        // Fim da lista de parâmetros: a linha que começa com ")" (com ou sem tipo de retorno).
        val abre = Regex("""\n\)[^\n{]*\{""").find(fonte, inicio)!!.range.last
        var nivel = 0
        for (i in abre until fonte.length) {
            when (fonte[i]) {
                '{' -> nivel++
                '}' -> if (--nivel == 0) return fonte.substring(abre, i + 1)
            }
        }
        error("corpo de $nome não fecha")
    }

    @Test
    fun `campo sincronizado nao salvavel usa remember e o MESMO reconciler`() {
        val c = corpo(fonte("AppSearchField.kt"), "rememberSyncedTextFieldState")
        assertTrue(
            c.contains("if (saveable) rememberTextFieldState(text) else remember { TextFieldState(text) }"),
            "o caminho não salvável tem de ser remember { TextFieldState(text) }",
        )
        // Um reconciler só, para os dois caminhos — é a fila de pendentes que segura o eco atrasado.
        assertEquals(1, Regex("""TextInputReconciler\(""").findAll(c).count())
        assertFalse(c.contains("rememberSaveable"))
    }

    @Test
    fun `calendario efemero nao passa pelo rememberDatePickerState`() {
        val c = corpo(fonte("AppDatePicker.kt"), "AppDatePickerDialog")
        val efemero = c.substringAfter("if (ephemeral) {").substringBefore("} else {")
        assertTrue(efemero.contains("ephemeralDatePickerState("), efemero)
        assertFalse(efemero.contains("rememberDatePickerState"), efemero)
        assertFalse(efemero.contains("rememberSaveable"), efemero)
    }

    @Test
    fun `campo de data repassa ephemeral ao dialogo`() {
        val c = corpo(fonte("AppDatePicker.kt"), "AppDatePicker")
        assertTrue(c.contains("ephemeral = ephemeral"))
    }

    @Test
    fun `estado efemero abre na data escolhida em UTC`() {
        val data = LocalDate(2026, 3, 5)
        val s = ephemeralDatePickerState(Locale.forLanguageTag("pt-BR"), data, DatePickerDefaults.AllDates)
        assertEquals(dataParaMillisDoCalendario(data), s.selectedDateMillis)
        assertEquals(data, millisDoCalendarioParaData(s.selectedDateMillis!!))
    }

    @Test
    fun `estado efemero sem data abre vazio`() {
        val s = ephemeralDatePickerState(Locale.US, null, DatePickerDefaults.AllDates)
        assertNull(s.selectedDateMillis)
    }

    @Test
    fun `estado efemero respeita min e max`() {
        val min = LocalDate(2026, 1, 10)
        val max = LocalDate(2026, 1, 20)
        val s = ephemeralDatePickerState(Locale.US, null, DateRangeSelectableDates(min, max))
        assertTrue(s.selectableDates.isSelectableDate(dataParaMillisDoCalendario(min)))
        assertTrue(s.selectableDates.isSelectableDate(dataParaMillisDoCalendario(max)))
        assertFalse(s.selectableDates.isSelectableDate(dataParaMillisDoCalendario(LocalDate(2026, 1, 9))))
        assertFalse(s.selectableDates.isSelectableDate(dataParaMillisDoCalendario(LocalDate(2026, 1, 21))))
    }
}
