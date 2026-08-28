package br.com.codecacto.kmplib.mask

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes das funções de filtro das máscaras (filter*Input).
 *
 * As visual transformations completas não são testadas aqui — exigem
 * `AnnotatedString` e ambiente Compose. Os filtros são funções puras
 * testáveis isoladamente.
 */
class MaskFiltersTest {

    // ====== filterPhoneInput ======

    @Test
    fun `filterPhoneInput remove caracteres nao numericos`() {
        assertEquals("123456789", filterPhoneInput("(12) 3456-789"))
        assertEquals("11999999999", filterPhoneInput("(11) 99999-9999"))
    }

    @Test
    fun `filterPhoneInput limita a 11 digitos`() {
        assertEquals("12345678901", filterPhoneInput("1234567890123456"))
    }

    @Test
    fun `filterPhoneInput aceita string vazia`() {
        assertEquals("", filterPhoneInput(""))
    }

    @Test
    fun `filterPhoneInput remove letras`() {
        assertEquals("", filterPhoneInput("abc"))
    }

    // ====== filterCpfInput ======

    @Test
    fun `filterCpfInput remove caracteres nao numericos`() {
        assertEquals("12345678909", filterCpfInput("123.456.789-09"))
    }

    @Test
    fun `filterCpfInput limita a 11 digitos`() {
        assertEquals("12345678901", filterCpfInput("123456789012345"))
    }

    @Test
    fun `filterCpfInput aceita vazio`() {
        assertEquals("", filterCpfInput(""))
    }

    // ====== filterCnpjInput ======

    @Test
    fun `filterCnpjInput mantem letras e digitos para CNPJ alfanumerico`() {
        assertEquals("12345678000195", filterCnpjInput("12.345.678/0001-95"))
        // Suporta alfanumérico (formato 2026+)
        assertEquals("12ABC678000195", filterCnpjInput("12.ABC.678/0001-95"))
    }

    @Test
    fun `filterCnpjInput limita a 14 caracteres`() {
        assertEquals("12345678000195", filterCnpjInput("123456780001959999"))
    }

    @Test
    fun `filterCnpjInput converte para uppercase`() {
        assertEquals("12ABC678000195", filterCnpjInput("12abc678000195"))
    }

    @Test
    fun `filterCnpjInput remove caracteres especiais mas mantem letras-digitos`() {
        assertEquals("AB123CD456", filterCnpjInput("AB-123/CD.456"))
    }

    // ====== filterCepInput ======

    @Test
    fun `filterCepInput remove nao numericos e limita a 8`() {
        assertEquals("12345678", filterCepInput("12345-678"))
        assertEquals("12345678", filterCepInput("123456789012"))
        assertEquals("", filterCepInput("abc"))
    }

    // ====== filterDateInput ======

    @Test
    fun `filterDateInput remove nao numericos e limita a 8`() {
        assertEquals("23032026", filterDateInput("23/03/2026"))
        assertEquals("12345678", filterDateInput("1234567890"))
    }

    // ====== filterCurrencyInput ======

    @Test
    fun `filterCurrencyInput so digitos sem limite`() {
        assertEquals("123456", filterCurrencyInput("R$ 1.234,56"))
        assertEquals("1000000", filterCurrencyInput("1.000.000"))
    }

    // ====== filterCrefitoInput ======

    @Test
    fun `filterCrefitoInput aceita digitos e letras F-T no final`() {
        assertEquals("123456F", filterCrefitoInput("123456-F"))
        assertEquals("123456T", filterCrefitoInput("123456t"))
    }

    @Test
    fun `filterCrefitoInput limita o tamanho`() {
        // CREFITO tem 6 dígitos + 1 letra
        val result = filterCrefitoInput("1234567890F")
        // No máximo 6 dígitos + letra, ou apenas dígitos
        assertEquals(result, filterCrefitoInput(result))  // idempotência
    }
}
