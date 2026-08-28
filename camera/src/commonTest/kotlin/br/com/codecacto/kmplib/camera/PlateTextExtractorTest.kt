package br.com.codecacto.kmplib.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlateTextExtractorTest {

    @Test
    fun extraiPlacaMercosulComSeparadorEMinusculas() {
        assertEquals("ABC1D23", extractPlate("placa: abc-1d23"))
    }

    @Test
    fun extraiPlacaAntigaComTraco() {
        assertEquals("ABC1234", extractPlate("ABC-1234"))
    }

    @Test
    fun extraiPlacaEntreRuidoMultilinha() {
        val ocr = "BRASIL\nABC1D23\nMERCOSUL"
        assertEquals("ABC1D23", extractPlate(ocr))
    }

    @Test
    fun extraiPlacaQuebradaEmDoisTokens() {
        // OCR separou as letras dos números em "palavras" distintas.
        assertEquals("ABC1D23", extractPlate("ABC 1D23"))
    }

    @Test
    fun retornaNullQuandoNaoHaPlacaValida() {
        assertNull(extractPlate("sem placa aqui"))
        assertNull(extractPlate("ABC12D3")) // padrão inválido
    }

    @Test
    fun retornaNullParaTextoVazio() {
        assertNull(extractPlate(""))
        assertNull(extractPlate("   "))
    }

    @Test
    fun retornaPrimeiraPlacaValidaQuandoHaVarias() {
        assertEquals("ABC1234", extractPlate("ABC1234 XYZ9Z88"))
    }
}
