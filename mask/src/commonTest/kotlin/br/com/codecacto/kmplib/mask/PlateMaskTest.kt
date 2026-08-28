package br.com.codecacto.kmplib.mask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlateMaskTest {

    // ---------------------------------------------------------------------
    // normalizePlate
    // ---------------------------------------------------------------------

    @Test
    fun normalizeConverteParaMaiusculas() {
        assertEquals("ABC1D23", normalizePlate("abc1d23"))
        assertEquals("ABC1234", normalizePlate("abc1234"))
    }

    @Test
    fun normalizeRemoveTracosEEspacos() {
        assertEquals("ABC1234", normalizePlate("ABC-1234"))
        assertEquals("ABC1234", normalizePlate("ABC 1234"))
        assertEquals("ABC1D23", normalizePlate("abc-1d23"))
    }

    @Test
    fun normalizeRemoveCaracteresInvalidos() {
        assertEquals("ABC1234", normalizePlate("A@B#C.1/2*3 4"))
    }

    @Test
    fun normalizeLimitaA7Caracteres() {
        assertEquals("ABC1234", normalizePlate("ABC1234567"))
        assertEquals("ABC1D23", normalizePlate("ABC1D2399"))
    }

    @Test
    fun normalizeStringVaziaRetornaVazio() {
        assertEquals("", normalizePlate(""))
        assertEquals("", normalizePlate("---   ..."))
    }

    @Test
    fun normalizeEntradaParcial() {
        assertEquals("ABC", normalizePlate("abc"))
        assertEquals("ABC1", normalizePlate("ABC-1"))
    }

    // ---------------------------------------------------------------------
    // isValidPlate — padrão antigo (AAA0000)
    // ---------------------------------------------------------------------

    @Test
    fun validaPlacaAntigaValida() {
        assertTrue(isValidPlate("ABC1234"))
        assertTrue(isValidPlate("XYZ9876"))
    }

    @Test
    fun validaPlacaAntigaComSeparadoresEMinusculas() {
        assertTrue(isValidPlate("abc-1234"))
        assertTrue(isValidPlate("ABC 1234"))
    }

    // ---------------------------------------------------------------------
    // isValidPlate — padrão Mercosul (AAA0A00)
    // ---------------------------------------------------------------------

    @Test
    fun validaPlacaMercosulValida() {
        assertTrue(isValidPlate("ABC1D23"))
        assertTrue(isValidPlate("BRA2E19"))
    }

    @Test
    fun validaPlacaMercosulComMinusculas() {
        assertTrue(isValidPlate("abc1d23"))
        assertTrue(isValidPlate("abc-1d23"))
    }

    // ---------------------------------------------------------------------
    // isValidPlate — inválidas
    // ---------------------------------------------------------------------

    @Test
    fun invalidaComprimentoErrado() {
        assertFalse(isValidPlate(""))
        assertFalse(isValidPlate("ABC123"))  // 6 chars
        assertFalse(isValidPlate("AB12"))    // 4 chars
    }

    @Test
    fun entradaMaiorQue7EhTruncadaAntesDeValidar() {
        // "ABC12345" normaliza para "ABC1234" (7 chars, válido padrão antigo)
        assertTrue(isValidPlate("ABC12345"))
    }

    @Test
    fun invalidaPrimeirosTresNaoLetras() {
        assertFalse(isValidPlate("AB11234"))  // 3a posição dígito
        assertFalse(isValidPlate("1BC1234"))  // 1a posição dígito
    }

    @Test
    fun invalidaQuartaPosicaoNaoDigito() {
        assertFalse(isValidPlate("ABCD123"))  // 4a posição letra
    }

    @Test
    fun invalidaUltimasPosicoesIncorretas() {
        assertFalse(isValidPlate("ABC1DD3")) // posição 5 letra (Mercosul exige dígito)
        assertFalse(isValidPlate("ABC12D3")) // posição 6 letra em padrão antigo
    }

    @Test
    fun invalidaTotalmenteForaDePadrao() {
        assertFalse(isValidPlate("1234567"))
        assertFalse(isValidPlate("ABCDEFG"))
    }

    // ---------------------------------------------------------------------
    // formatPlateForDisplay
    // ---------------------------------------------------------------------

    @Test
    fun formataMercosulSemSeparador() {
        assertEquals("ABC1D23", formatPlateForDisplay("abc1d23"))
        assertEquals("ABC1D23", formatPlateForDisplay("ABC-1D23"))
    }

    @Test
    fun formataAntigaComHifen() {
        assertEquals("ABC-1234", formatPlateForDisplay("abc1234"))
        assertEquals("ABC-1234", formatPlateForDisplay("ABC1234"))
    }

    @Test
    fun formataIncompletaApenasNormaliza() {
        assertEquals("ABC12", formatPlateForDisplay("abc 12"))
        assertEquals("", formatPlateForDisplay("---"))
    }
}
