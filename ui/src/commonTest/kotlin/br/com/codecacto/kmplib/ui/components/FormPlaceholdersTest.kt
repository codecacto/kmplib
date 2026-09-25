package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Trava a regra "placeholder nunca é dado real" (15/set/2026): instrução ou formato, nunca um
 * valor que alguém poderia ter digitado.
 */
class FormPlaceholdersTest {

    private val instrucoes = listOf(
        FormPlaceholders.NAME,
        FormPlaceholders.EMAIL,
        FormPlaceholders.EMAIL_OR_USERNAME,
        FormPlaceholders.USERNAME,
        FormPlaceholders.PASSWORD,
        FormPlaceholders.CONFIRM_PASSWORD,
        FormPlaceholders.ADDRESS_NUMBER,
    )

    @Test
    fun instrucoesNaoTrazemValorPlausivel() {
        instrucoes.forEach { p ->
            assertFalse('@' in p, "e-mail de exemplo no placeholder: $p")
            assertFalse(p.any { it.isDigit() }, "número no placeholder: $p")
            assertFalse('•' in p, "senha já 'digitada' no placeholder: $p")
            assertFalse(p.startsWith("Ex", ignoreCase = true), "exemplo no placeholder: $p")
            assertTrue(p.isNotBlank())
        }
    }

    @Test
    fun telefoneEhFormatoComZeros() {
        assertEquals("(00) 00000-0000", FormPlaceholders.PHONE)
        assertTrue(FormPlaceholders.PHONE.filter { it.isDigit() }.all { it == '0' })
    }
}
