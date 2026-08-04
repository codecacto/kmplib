package br.com.codecacto.kmplib.camera.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Núcleo puro do leitor: dígito verificador, expansão de UPC-E, normalização entre plataformas e
 * entrada manual. É o que impede um código parcial de virar produto errado no estoque.
 */
class BarcodeParserTest {

    // ---------------------------------------------------------------- dígito verificador

    @Test
    fun `valida GTIN de 8, 12, 13 e 14 digitos`() {
        assertTrue(Gtin.isValid("96385074"), "EAN-8")
        assertTrue(Gtin.isValid("036000291452"), "UPC-A")
        assertTrue(Gtin.isValid("7891000100103"), "EAN-13 (Nestlé)")
        assertTrue(Gtin.isValid("07891000100103"), "GTIN-14 (zero à esquerda não muda o dígito)")
    }

    @Test
    fun `rejeita verificador errado, tamanho invalido e nao-digito`() {
        assertFalse(Gtin.isValid("7891000100104"), "último dígito trocado")
        assertFalse(Gtin.isValid("789100010010"), "12 dígitos que não são UPC-A válido")
        assertFalse(Gtin.isValid("789100010010A"))
        assertFalse(Gtin.isValid(""))
        assertNull(Gtin.checkDigit("12A4"))
    }

    // ---------------------------------------------------------------- UPC-E

    @Test
    fun `expande UPC-E de 8 digitos para UPC-A`() {
        assertEquals("012345000065", Gtin.expandUpcE("01234565"))
        assertTrue(Gtin.isValid(Gtin.expandUpcE("01234565")!!))
    }

    @Test
    fun `expande UPC-E de 6 digitos assumindo sistema numerico zero`() {
        val expanded = Gtin.expandUpcE("123456")
        assertEquals("012345000065", expanded)
    }

    @Test
    fun `rejeita UPC-E com verificador que nao fecha ou sistema numerico invalido`() {
        assertNull(Gtin.expandUpcE("01234560"), "verificador errado")
        assertNull(Gtin.expandUpcE("21234565"), "sistema numérico 2 não existe em UPC-E")
        assertNull(Gtin.expandUpcE("1234567"), "tamanho não suportado")
    }

    // ---------------------------------------------------------------- parseBarcode

    @Test
    fun `aceita EAN-13 valido e recusa leitura parcial`() {
        val ok = parseBarcode("7891000100103", BarcodeFormat.EAN_13)
        assertEquals("7891000100103", ok?.value)
        assertEquals(BarcodeFormat.EAN_13, ok?.format)

        // Um dígito lido errado (borrão, código amassado) NÃO pode virar produto.
        assertNull(parseBarcode("7891000100104", BarcodeFormat.EAN_13))
        assertNull(parseBarcode("789100010010", BarcodeFormat.EAN_13))
        assertNull(parseBarcode("  ", BarcodeFormat.EAN_13))
        assertNull(parseBarcode(null, BarcodeFormat.EAN_13))
    }

    @Test
    fun `Android e iOS produzem a MESMA chave para o mesmo UPC-A`() {
        // ML Kit (Android): 12 dígitos, tipo UPC_A.
        val android = parseBarcode("036000291452", BarcodeFormat.UPC_A)
        // AVFoundation/Vision (iOS): UPC-A não existe como simbologia — vem como EAN-13 com zero.
        val ios = parseBarcode("0036000291452", BarcodeFormat.EAN_13)

        assertEquals("0036000291452", android?.toGtin13())
        assertEquals("0036000291452", ios?.toGtin13())
        assertEquals(android?.toGtin13(), ios?.toGtin13())
        assertEquals("00036000291452", android?.toGtin14())
    }

    @Test
    fun `UPC-A entregue com 13 digitos e zero a esquerda e normalizado`() {
        val scanned = parseBarcode("0036000291452", BarcodeFormat.UPC_A)
        assertEquals("036000291452", scanned?.value)
    }

    @Test
    fun `UPC-E guarda a forma canonica e expande na chave GTIN`() {
        val scanned = parseBarcode("01234565", BarcodeFormat.UPC_E)
        assertEquals("01234565", scanned?.value)
        assertEquals("0012345000065", scanned?.toGtin13())
        assertEquals("00012345000065", scanned?.toGtin14())
    }

    @Test
    fun `EAN-8 vira chave de 13 digitos sem perder o verificador`() {
        val scanned = parseBarcode("96385074", BarcodeFormat.EAN_8)
        val gtin13 = scanned?.toGtin13()
        assertEquals("00000096385074".takeLast(13), gtin13)
        assertTrue(Gtin.isValid(gtin13!!))
    }

    @Test
    fun `simbologia livre passa sem verificador e nao tem chave GTIN`() {
        val qr = parseBarcode("  https://codecacto.com.br/x  ", BarcodeFormat.QR_CODE)
        assertEquals("https://codecacto.com.br/x", qr?.value)
        assertFalse(qr!!.isRetail)
        assertNull(qr.toGtin13())

        val code128 = parseBarcode("LOTE-2026-08-A", BarcodeFormat.CODE_128)
        assertEquals("LOTE-2026-08-A", code128?.value)
    }

    @Test
    fun `ITF exige tamanho par e confere verificador quando e GTIN-14`() {
        assertEquals("07891000100103", parseBarcode("07891000100103", BarcodeFormat.ITF)?.value)
        assertNull(parseBarcode("07891000100104", BarcodeFormat.ITF), "GTIN-14 com verificador errado")
        assertNull(parseBarcode("12345", BarcodeFormat.ITF), "tamanho ímpar")
        assertEquals("123456", parseBarcode("123456", BarcodeFormat.ITF)?.value)
    }

    @Test
    fun `ITF-14 e chave de produto, mas so vira GTIN-13 se o excesso for zero`() {
        val caixaComZero = parseBarcode("07891000100103", BarcodeFormat.ITF)!!
        assertTrue(caixaComZero.isProductCode)
        assertEquals("07891000100103", caixaComZero.toGtin14())
        assertEquals("7891000100103", caixaComZero.toGtin13())

        // Indicador != 0: é OUTRO item (a caixa), não pode ser confundido com a unidade.
        val caixa = parseBarcode("17891000100100", BarcodeFormat.ITF)
        assertTrue(Gtin.isValid("17891000100100"), "pré-condição do caso")
        assertEquals("17891000100100", caixa?.toGtin14())
        assertNull(caixa?.toGtin13())
    }

    @Test
    fun `simbologia livre nunca vira chave de catalogo`() {
        val qr = parseBarcode("7891000100103", BarcodeFormat.QR_CODE)!!
        assertFalse(qr.isProductCode)
        assertNull(qr.toGtin14())
    }

    @Test
    fun `codigo de varejo com caractere nao numerico e descartado`() {
        assertNull(parseBarcode("789100010010X", BarcodeFormat.EAN_13))
    }

    // ---------------------------------------------------------------- entrada manual

    @Test
    fun `entrada manual infere a simbologia pelo tamanho e limpa separadores`() {
        assertEquals(BarcodeFormat.EAN_13, parseTypedRetailBarcode("789 1000 100103")?.format)
        assertEquals("7891000100103", parseTypedRetailBarcode("7.891.000.100-103")?.value)
        assertEquals(BarcodeFormat.EAN_8, parseTypedRetailBarcode("96385074")?.format)
        assertEquals(BarcodeFormat.UPC_A, parseTypedRetailBarcode("036000291452")?.format)
    }

    @Test
    fun `entrada manual recusa tamanho impossivel e verificador errado`() {
        assertNull(parseTypedRetailBarcode("12345"))
        assertNull(parseTypedRetailBarcode("7891000100104"))
        assertNull(parseTypedRetailBarcode(""))
        assertNull(parseTypedRetailBarcode(null))
    }

    // ---------------------------------------------------------------- presets

    @Test
    fun `preset RETAIL cobre so a familia GTIN e COMMON acrescenta etiqueta e QR`() {
        assertTrue(BarcodeFormats.RETAIL.all { it.isRetail })
        assertEquals(4, BarcodeFormats.RETAIL.size)
        assertTrue(BarcodeFormat.CODE_128 in BarcodeFormats.COMMON)
        assertTrue(BarcodeFormat.QR_CODE in BarcodeFormats.COMMON)
        assertTrue(BarcodeFormats.RETAIL.all { it in BarcodeFormats.COMMON })
        assertEquals(BarcodeFormat.entries.size, BarcodeFormats.ALL.size)
    }
}
