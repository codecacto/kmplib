package br.com.codecacto.kmplib.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O `AppTimePicker` (2.329.0) é fachada do modal do `AppTimePickerDialog` e não tem mais texto fixo
 * em pt-BR. Sem teste de UI na kmplib (jun/2026), a promessa é conferida no fonte; a tradução dos
 * recursos `kmplib_time_*` nos 4 idiomas é coberta pelo `LibStringResourcesParityTest`.
 */
class AppTimePickerFachadaTest {

    private val fonte: String =
        File("src/commonMain/kotlin/br/com/codecacto/kmplib/ui/components/AppTimePicker.kt")
            .also { assertTrue(it.exists(), "fonte não encontrada a partir de ${File(".").canonicalPath}") }
            .readText()

    @Test
    fun `rotulos dos botoes vem dos recursos traduzidos`() {
        assertFalse(Regex("""confirmLabel:\s*String\s*=\s*"""").containsMatchIn(fonte), "confirmLabel com texto fixo")
        assertFalse(Regex("""dismissLabel:\s*String\s*=\s*"""").containsMatchIn(fonte), "dismissLabel com texto fixo")
        assertTrue("rememberAppTimeFieldTexts().confirm" in fonte)
        assertTrue("rememberAppTimeFieldTexts().dismiss" in fonte)
    }

    @Test
    fun `e fachada do mesmo modal do AppTimePickerDialog`() {
        assertTrue("TimePickerDialogCore(" in fonte, "o AppTimePicker deve delegar ao núcleo do AppTimePickerDialog")
        assertFalse("AlertDialog(" in fonte, "o diálogo antigo próprio não pode voltar")
    }
}
