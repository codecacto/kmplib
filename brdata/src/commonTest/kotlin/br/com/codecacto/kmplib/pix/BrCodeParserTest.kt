package br.com.codecacto.kmplib.pix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `parseBrCode` — o ponto de entrada. Contrato: **nunca lança** e sempre classifica em um dos quatro
 * desfechos, com "CRC não confere" separado de "não é EMV".
 */
class BrCodeParserTest {

    // -------------------------------------------------------------------------------------------
    // Caminho feliz
    // -------------------------------------------------------------------------------------------

    @Test
    fun `payload estatico valido e lido como Pix`() {
        val reading = parseBrCode(PixFixtures.staticPix())
        val brCode = assertIs<BrCodeReading.Pix>(reading).brCode

        assertTrue(brCode.isPix)
        assertTrue(brCode.isStatic)
        assertFalse(brCode.isDynamic)
        assertEquals("01", brCode.formatIndicator)
        assertEquals(PixFixtures.CPF_KEY, brCode.pixKey)
        assertEquals(PixKeyType.CPF, brCode.pixKeyType)
        assertEquals(PixFixtures.MERCHANT_NAME, brCode.merchantName)
        assertEquals(PixFixtures.MERCHANT_CITY, brCode.merchantCity)
        assertEquals("986", brCode.currency)
        assertEquals("BR", brCode.countryCode)
        assertEquals("***", brCode.txid)
        assertEquals("26", brCode.account?.templateId)
        assertTrue(reading.isValidPix)
        assertFalse(reading.isIntegrityFailure)
    }

    @Test
    fun `payload dinamico traz a URL de cobranca`() {
        val url = "pix.example.com/qr/v2/cobv/abc123"
        val brCode = assertIs<BrCodeReading.Pix>(parseBrCode(PixFixtures.dynamicPix(url))).brCode

        assertTrue(brCode.isDynamic)
        assertEquals(PixInitiationMethod.Dynamic, brCode.initiationMethod)
        assertEquals(url, brCode.payloadUrl)
        assertNull(brCode.pixKey, "dinâmico puro não traz chave")
        assertTrue(brCode.account!!.hasUrl)
    }

    @Test
    fun `sem a tag 01 o payload e ESTATICO - e o default do padrao`() {
        val payload = PixFixtures.staticPix(initiation = null)
        assertFalse(payload.contains("010211"), "a fixture não deve escrever a tag 01")

        val brCode = assertIs<BrCodeReading.Pix>(parseBrCode(payload)).brCode
        assertEquals(PixInitiationMethod.Static, brCode.initiationMethod)
        assertNull(brCode.initiationMethodRaw)
        assertTrue(brCode.isStatic)
    }

    @Test
    fun `tag 01 igual a 11 e estatico e igual a 12 e dinamico`() {
        val estatico = assertIs<BrCodeReading.Pix>(parseBrCode(PixFixtures.staticPix(initiation = "11"))).brCode
        assertEquals(PixInitiationMethod.Static, estatico.initiationMethod)
        assertEquals("11", estatico.initiationMethodRaw)

        val dinamico = assertIs<BrCodeReading.Pix>(
            parseBrCode(PixFixtures.dynamicPix("pix.example.com/qr/v2/abc")),
        ).brCode
        assertEquals(PixInitiationMethod.Dynamic, dinamico.initiationMethod)
    }

    @Test
    fun `tag 01 fora do padrao nao e normalizada para estatico`() {
        val brCode = assertIs<BrCodeReading.Pix>(parseBrCode(PixFixtures.staticPix(initiation = "13"))).brCode
        assertEquals(PixInitiationMethod.Unknown, brCode.initiationMethod)
        assertEquals("13", brCode.initiationMethodRaw)
        assertFalse(brCode.isStatic)
        assertFalse(brCode.isDynamic)
    }

    @Test
    fun `GUI em MAIUSCULO continua sendo Pix`() {
        val brCode = assertIs<BrCodeReading.Pix>(
            parseBrCode(PixFixtures.staticPix(gui = "BR.GOV.BCB.PIX")),
        ).brCode

        assertTrue(brCode.isPix)
        assertEquals("BR.GOV.BCB.PIX", brCode.account?.gui, "o valor cru é preservado")
    }

    @Test
    fun `valor da transacao presente e ausente`() {
        val comValor = assertIs<BrCodeReading.Pix>(parseBrCode(PixFixtures.staticPix(amount = "12.34"))).brCode
        assertEquals("12.34", comValor.amount)
        assertTrue(comValor.hasAmount)

        val semValor = assertIs<BrCodeReading.Pix>(parseBrCode(PixFixtures.staticPix())).brCode
        assertNull(semValor.amount, "plaquinha de balcão em geral não fixa valor")
        assertFalse(semValor.hasAmount)
    }

    @Test
    fun `cada tipo de chave e inferido pelo formato`() {
        fun tipoDe(key: String): PixKeyType =
            assertIs<BrCodeReading.Pix>(parseBrCode(PixFixtures.staticPix(key = key))).brCode.pixKeyType

        assertEquals(PixKeyType.CPF, tipoDe(PixFixtures.CPF_KEY))
        assertEquals(PixKeyType.CNPJ, tipoDe(PixFixtures.CNPJ_KEY))
        assertEquals(PixKeyType.EMAIL, tipoDe(PixFixtures.EMAIL_KEY))
        assertEquals(PixKeyType.PHONE, tipoDe(PixFixtures.PHONE_KEY))
        assertEquals(PixKeyType.RANDOM, tipoDe(PixFixtures.RANDOM_KEY))
        assertEquals(PixKeyType.UNKNOWN, tipoDe("chave-esquisita"))
        assertEquals(PixKeyType.UNKNOWN, tipoDe("123456"))

        // Emissor fora do padrão que grava com máscara não muda o tipo.
        assertEquals(PixKeyType.PHONE, tipoDe("+55 11 99999-8888"))
        assertEquals(PixKeyType.CPF, tipoDe("12345678901"))
    }

    @Test
    fun `CPF ou CNPJ com digito verificador invalido NAO e recusado`() {
        // DV inválido é problema do PSP que aceitou a chave; recusar aqui esconderia do usuário
        // exatamente o QR que ele precisa ver para desconfiar.
        val brCode = assertIs<BrCodeReading.Pix>(parseBrCode(PixFixtures.staticPix(key = "11111111111"))).brCode
        assertEquals(PixKeyType.CPF, brCode.pixKeyType)
        assertEquals("11111111111", brCode.pixKey)
    }

    @Test
    fun `template Pix em outro id da faixa 26-51 e encontrado`() {
        val brCode = assertIs<BrCodeReading.Pix>(
            parseBrCode(PixFixtures.staticPix(accountTemplateId = "40")),
        ).brCode

        assertEquals("40", brCode.account?.templateId)
        assertEquals(PixFixtures.CPF_KEY, brCode.pixKey)
    }

    @Test
    fun `id desconhecido nao impede a leitura e fica preservado`() {
        val payload = PixFixtures.staticPix(extraFields = PixFixtures.tlv("70", "CAMPO-DE-PSP-NOVO"))
        val brCode = assertIs<BrCodeReading.Pix>(parseBrCode(payload)).brCode

        assertTrue(brCode.isPix)
        assertEquals("CAMPO-DE-PSP-NOVO", brCode.rawValue("70"))
    }

    @Test
    fun `bordas sujas sao aparadas sem alterar o interior`() {
        val payload = PixFixtures.staticPix()
        val reading = parseBrCode("\uFEFF  \n$payload\t\n ")

        val brCode = assertIs<BrCodeReading.Pix>(reading).brCode
        assertEquals(payload, brCode.payload, "o payload validado é o texto exato, sem as bordas")
    }

    @Test
    fun `descricao do recebedor e lida da sub-tag 02`() {
        val brCode = assertIs<BrCodeReading.Pix>(
            parseBrCode(PixFixtures.staticPix(description = "Mesa 4")),
        ).brCode
        assertEquals("Mesa 4", brCode.account?.description)
    }

    // -------------------------------------------------------------------------------------------
    // Integridade: CRC
    // -------------------------------------------------------------------------------------------

    @Test
    fun `CRC errado e classificado como InvalidCrc, nunca como nao-EMV`() {
        val payload = PixFixtures.staticPix()
        val adulterado = PixFixtures.withBrokenCrc(payload)

        val reading = assertIs<BrCodeReading.InvalidCrc>(parseBrCode(adulterado))
        assertEquals(PixCrc.declaredCrcOf(adulterado), reading.declaredCrc)
        assertEquals(PixCrc.declaredCrcOf(payload), reading.computedCrc)
        assertTrue(reading.isIntegrityFailure)
        assertFalse(reading.isValidPix)

        // O decodificado vem como DIAGNÓSTICO — e não é oferecido como leitura válida.
        assertNotNull(reading.brCode)
        assertNull(reading.validBrCode)
    }

    @Test
    fun `payload truncado no meio do valor e recusado no enquadramento`() {
        val payload = PixFixtures.staticPix()
        val truncado = payload.dropLast(12)

        val reading = assertIs<BrCodeReading.NotEmv>(parseBrCode(truncado))
        assertEquals(BrCodeError.InvalidFraming, reading.error)
        assertEquals(EmvTlvError.LengthOverflow, reading.tlvError)
    }

    @Test
    fun `payload sem a tag 63 no fim nao e um BR Code`() {
        val semCrc = PixFixtures.staticPix().let { it.dropLast(PixCrc.VALUE_LENGTH + 4) }
        // Sobra um enquadramento válido, mas sem tag de CRC.
        val reading = assertIs<BrCodeReading.NotEmv>(parseBrCode(semCrc))
        assertEquals(BrCodeError.MissingCrc, reading.error)
        assertNull(reading.tlvError)
    }

    @Test
    fun `CRC em minusculo e aceito`() {
        val payload = PixFixtures.staticPix()
        val minusculo = payload.dropLast(PixCrc.VALUE_LENGTH) +
            payload.takeLast(PixCrc.VALUE_LENGTH).lowercase()

        val brCode = assertIs<BrCodeReading.Pix>(parseBrCode(minusculo)).brCode
        assertEquals(payload.takeLast(PixCrc.VALUE_LENGTH).uppercase(), brCode.crc)
    }

    // -------------------------------------------------------------------------------------------
    // Não-Pix e não-EMV: cidadãos de primeira classe
    // -------------------------------------------------------------------------------------------

    @Test
    fun `EMV valido de outro arranjo e NotPix, nao erro`() {
        val reading = assertIs<BrCodeReading.NotPix>(parseBrCode(PixFixtures.otherArrangement()))

        assertFalse(reading.brCode.isPix)
        assertNull(reading.brCode.account)
        assertEquals("LOJA EXEMPLO", reading.brCode.merchantName)
        assertNotNull(reading.validBrCode, "é íntegro: dá para exibir o que traz")
    }

    @Test
    fun `link, texto livre e vCard nao sao EMV`() {
        listOf(
            "https://codecacto.com.br/pagar",
            "Obrigado pela visita, volte sempre!",
            "BEGIN:VCARD\nVERSION:3.0\nFN:Loja\nEND:VCARD",
            "WIFI:S:LojaWiFi;T:WPA;P:senha123;;",
        ).forEach { texto ->
            val reading = assertIs<BrCodeReading.NotEmv>(parseBrCode(texto), "esperado NotEmv para: $texto")
            assertEquals(BrCodeError.InvalidFraming, reading.error)
        }
    }

    @Test
    fun `entrada nula, vazia ou so espacos e Blank`() {
        listOf(null, "", "   ", "\n\t ", "\uFEFF").forEach { texto ->
            val reading = assertIs<BrCodeReading.NotEmv>(parseBrCode(texto))
            assertEquals(BrCodeError.Blank, reading.error, "entrada: $texto")
        }
    }

    @Test
    fun `payload com dois templates Pix devolve os dois, e account usa o de menor id`() {
        val segundoTemplate = PixFixtures.tlv(
            "27",
            PixFixtures.tlv(BrCodeTag.ACCOUNT_GUI, BrCodeTag.PIX_GUI) +
                PixFixtures.tlv(BrCodeTag.ACCOUNT_KEY, PixFixtures.CNPJ_KEY),
        )
        // O template extra entra na posição de campo adicional; o parser lê por ID, não por ordem.
        val brCode = assertIs<BrCodeReading.Pix>(
            parseBrCode(PixFixtures.staticPix(extraFields = segundoTemplate)),
        ).brCode

        assertEquals(2, brCode.accounts.size)
        assertEquals("26", brCode.account?.templateId)
        assertEquals(PixFixtures.CPF_KEY, brCode.pixKey)
    }
}
