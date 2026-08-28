package br.com.codecacto.kmplib.pix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parser TLV do EMV MPM: **estrito no enquadramento, tolerante com ID desconhecido**.
 *
 * A assimetria é o contrato: estrutura que não fecha é payload corrompido (recusar), ID que a lib
 * não conhece é campo de PSP novo (preservar).
 */
class EmvTlvTest {

    @Test
    fun `payload estatico valido e lido campo a campo`() {
        val payload = PixFixtures.staticPix()
        val fields = assertIs<EmvTlvResult.Success>(parseEmvTlv(payload)).fields

        assertEquals("01", fields.emvValue(BrCodeTag.FORMAT_INDICATOR))
        assertEquals("0000", fields.emvValue(BrCodeTag.MERCHANT_CATEGORY_CODE))
        assertEquals("986", fields.emvValue(BrCodeTag.TRANSACTION_CURRENCY))
        assertEquals("BR", fields.emvValue(BrCodeTag.COUNTRY_CODE))
        assertEquals(PixFixtures.MERCHANT_NAME, fields.emvValue(BrCodeTag.MERCHANT_NAME))
        assertEquals(PixFixtures.MERCHANT_CITY, fields.emvValue(BrCodeTag.MERCHANT_CITY))
        assertEquals(4, fields.emvValue(BrCodeTag.CRC)?.length)
    }

    @Test
    fun `template aninhado e aberto em sub-campos`() {
        val fields = assertIs<EmvTlvResult.Success>(parseEmvTlv(PixFixtures.staticPix())).fields

        val account = fields.emvField("26")!!
        assertTrue(account.hasChildren)
        assertEquals(BrCodeTag.PIX_GUI, account.childValue(BrCodeTag.ACCOUNT_GUI))
        assertEquals(PixFixtures.CPF_KEY, account.childValue(BrCodeTag.ACCOUNT_KEY))

        val additional = fields.emvField(BrCodeTag.ADDITIONAL_DATA)!!
        assertEquals("***", additional.childValue(BrCodeTag.ADDITIONAL_TXID))
    }

    @Test
    fun `tamanho declarado maior que o resto do payload e recusado`() {
        // Campo "59" declara 40 caracteres, mas só existem 9 até o fim.
        val payload = PixFixtures.tlv(BrCodeTag.FORMAT_INDICATOR, "01") + "5940CODECACTO"
        val failure = assertIs<EmvTlvResult.Failure>(parseEmvTlv(payload))

        assertEquals(EmvTlvError.LengthOverflow, failure.error)
        assertEquals(6, failure.position, "a falha aponta o início do campo problemático")
    }

    @Test
    fun `sobra de caracteres insuficiente para um cabecalho e recusada`() {
        val payload = PixFixtures.tlv(BrCodeTag.FORMAT_INDICATOR, "01") + "590"
        val failure = assertIs<EmvTlvResult.Failure>(parseEmvTlv(payload))
        assertEquals(EmvTlvError.Truncated, failure.error)
    }

    @Test
    fun `tamanho nao-numerico e recusado, inclusive digito nao-ASCII`() {
        val naoNumerico = assertIs<EmvTlvResult.Failure>(parseEmvTlv("59XXCODECACTO"))
        assertEquals(EmvTlvError.InvalidLength, naoNumerico.error)

        // '٣' é dígito Unicode (árabe-índico). String.toIntOrNull() o converteria; a lib não.
        val digitoUnicode = assertIs<EmvTlvResult.Failure>(parseEmvTlv("59٣٣CODECACTO"))
        assertEquals(EmvTlvError.InvalidLength, digitoUnicode.error)
    }

    @Test
    fun `id nao-numerico e recusado - e o caso de um payload que nem e EMV`() {
        val link = assertIs<EmvTlvResult.Failure>(parseEmvTlv("https://codecacto.com.br"))
        assertEquals(EmvTlvError.InvalidId, link.error)
        assertEquals(0, link.position)
    }

    @Test
    fun `entrada vazia e recusada`() {
        val failure = assertIs<EmvTlvResult.Failure>(parseEmvTlv(""))
        assertEquals(EmvTlvError.Blank, failure.error)
    }

    @Test
    fun `id desconhecido e PRESERVADO, nao recusado`() {
        // "70" está em faixa reservada para uso futuro: nenhuma versão da lib o interpreta.
        val payload = PixFixtures.tlv(BrCodeTag.FORMAT_INDICATOR, "01") +
            PixFixtures.tlv("70", "PSP-NOVO") +
            PixFixtures.tlv(BrCodeTag.MERCHANT_NAME, "CODECACTO")

        val fields = assertIs<EmvTlvResult.Success>(parseEmvTlv(payload)).fields
        assertEquals(3, fields.size)
        assertEquals("PSP-NOVO", fields.emvValue("70"))
        assertFalse(fields.emvField("70")!!.hasChildren, "campo comum não é template")
    }

    @Test
    fun `campo de tamanho zero e valido`() {
        val payload = PixFixtures.tlv(BrCodeTag.FORMAT_INDICATOR, "01") + "6100" +
            PixFixtures.tlv(BrCodeTag.MERCHANT_NAME, "CODECACTO")

        val fields = assertIs<EmvTlvResult.Success>(parseEmvTlv(payload)).fields
        assertEquals("", fields.emvValue(BrCodeTag.POSTAL_CODE))
        assertEquals("CODECACTO", fields.emvValue(BrCodeTag.MERCHANT_NAME))
    }

    @Test
    fun `template com interior ilegivel vira folha com o valor preservado`() {
        // "62" é template, mas o interior aqui é texto comum — não pode derrubar o payload todo.
        val payload = PixFixtures.tlv(BrCodeTag.FORMAT_INDICATOR, "01") +
            PixFixtures.tlv(BrCodeTag.ADDITIONAL_DATA, "texto solto")

        val fields = assertIs<EmvTlvResult.Success>(parseEmvTlv(payload)).fields
        val additional = fields.emvField(BrCodeTag.ADDITIONAL_DATA)!!
        assertFalse(additional.hasChildren)
        assertEquals("texto solto", additional.value)
        assertNull(additional.childValue(BrCodeTag.ADDITIONAL_TXID))
    }

    @Test
    fun `descendIntoTemplates false le apenas o primeiro nivel`() {
        val fields = assertIs<EmvTlvResult.Success>(
            parseEmvTlv(PixFixtures.staticPix(), descendIntoTemplates = false),
        ).fields

        val account = fields.emvField("26")!!
        assertFalse(account.hasChildren)
        assertTrue(account.value.contains(BrCodeTag.PIX_GUI), "o valor cru continua inteiro")
    }

    @Test
    fun `isEmvTemplateId cobre as faixas do padrao`() {
        assertTrue(isEmvTemplateId("26"))
        assertTrue(isEmvTemplateId("51"))
        assertTrue(isEmvTemplateId("62"))
        assertTrue(isEmvTemplateId("64"))
        assertTrue(isEmvTemplateId("80"))
        assertTrue(isEmvTemplateId("99"))

        assertFalse(isEmvTemplateId("25"))
        assertFalse(isEmvTemplateId("52"))
        assertFalse(isEmvTemplateId("63"))
        assertFalse(isEmvTemplateId("70"))
        assertFalse(isEmvTemplateId("xx"))
    }
}
