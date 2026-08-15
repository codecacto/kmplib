package br.com.codecacto.kmplib.pix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Provas do **gerador** de BR Code.
 *
 * ## A prova que importa é o ida-e-volta
 * O gerador não é conferido contra uma string colada: ele é conferido **relendo o próprio payload
 * com o parser da lib** ([parseBrCode]), que já é provado à parte contra o *check value* publicado
 * do CRC-16/CCITT-FALSE. Se o gerador escrever um tamanho errado, um campo fora de ordem ou um CRC
 * que não fecha, o parser recusa — e é isso que faz esta suíte valer alguma coisa.
 *
 * Comparar com uma string fixa provaria só que ninguém mexeu no arquivo.
 */
class BrCodeBuilderTest {

    private fun ok(charge: PixCharge): String {
        val r = buildPixBrCode(charge)
        assertTrue(r is PixBrCodeResult.Ok, "esperava payload, veio $r")
        return r.payload
    }

    private fun erro(charge: PixCharge): PixBrCodeError {
        val r = buildPixBrCode(charge)
        assertTrue(r is PixBrCodeResult.Invalid, "esperava recusa, veio $r")
        return r.error
    }

    private val base = PixCharge(
        key = PixFixtures.CPF_KEY,
        merchantName = "Rosangela Ferreira",
        merchantCity = "Campinas",
    )

    // -- Ida e volta -------------------------------------------------------------------------

    @Test
    fun `payload gerado e relido pelo parser como Pix valido`() {
        val payload = ok(base)

        val leitura = parseBrCode(payload)
        assertTrue(leitura is BrCodeReading.Pix, "o parser da lib recusou o payload da lib: $leitura")

        val conta = leitura.brCode.accounts.single()
        assertEquals(PixFixtures.CPF_KEY, conta.key)
        assertEquals(PixInitiationMethod.Static, leitura.brCode.initiationMethod)
        assertEquals(BrCodeTag.CURRENCY_BRL, leitura.brCode.currency)
        assertEquals(BrCodeTag.COUNTRY_BR, leitura.brCode.countryCode)
    }

    @Test
    fun `CRC fecha — a validacao do proprio modulo aceita`() {
        val payload = ok(base)
        assertTrue(PixCrc.isValid(payload), "CRC não fecha no payload gerado")
    }

    @Test
    fun `alterar um caractere invalida o CRC`() {
        val payload = ok(base)
        // Troca um dígito no meio da chave: o enquadramento continua de pé, o CRC não.
        val adulterado = payload.replaceFirst(PixFixtures.CPF_KEY, "12345678902")
        assertTrue(adulterado != payload)
        assertTrue(!PixCrc.isValid(adulterado), "CRC aceitou payload adulterado")
    }

    @Test
    fun `os quatro tipos de chave geram payload valido`() {
        listOf(
            PixFixtures.CPF_KEY,
            PixFixtures.CNPJ_KEY,
            PixFixtures.EMAIL_KEY,
            PixFixtures.PHONE_KEY,
            PixFixtures.RANDOM_KEY,
        ).forEach { chave ->
            val leitura = parseBrCode(ok(base.copy(key = chave)))
            assertTrue(leitura is BrCodeReading.Pix, "chave $chave não gerou Pix legível")
            assertEquals(chave, leitura.brCode.accounts.single().key)
        }
    }

    // -- Valor -------------------------------------------------------------------------------

    @Test
    fun `sem valor a tag 54 nao existe — e o QR e o da plaquinha`() {
        val leitura = parseBrCode(ok(base.copy(amount = null)))
        assertTrue(leitura is BrCodeReading.Pix)
        assertNull(leitura.brCode.amount)
    }

    @Test
    fun `as quatro formas que uma tela produz viram o mesmo valor`() {
        listOf("150", "150,00", "150.00", "R$ 150,00").forEach { entrada ->
            val leitura = parseBrCode(ok(base.copy(amount = entrada)))
            assertTrue(leitura is BrCodeReading.Pix, "entrada $entrada")
            assertEquals("150.00", leitura.brCode.amount, "entrada $entrada")
        }
    }

    @Test
    fun `valor com 3 casas, zero ou texto e recusado antes de virar QR`() {
        assertEquals(PixBrCodeError.InvalidAmount, erro(base.copy(amount = "150.0055")))
        assertEquals(PixBrCodeError.InvalidAmount, erro(base.copy(amount = "0")))
        assertEquals(PixBrCodeError.InvalidAmount, erro(base.copy(amount = "0,00")))
        assertEquals(PixBrCodeError.InvalidAmount, erro(base.copy(amount = "grátis")))
        assertEquals(PixBrCodeError.InvalidAmount, erro(base.copy(amount = "99999999999")))
    }

    @Test
    fun `centavos incompletos completam com zero`() {
        assertEquals("150.50", normalizeAmount("150,5"))
        assertEquals("0.50", normalizeAmount("0,50"))
    }

    // -- Nome e cidade -----------------------------------------------------------------------

    @Test
    fun `acento sai, maiuscula entra — e a contagem de caracteres passa a bater com a de bytes`() {
        val payload = ok(base.copy(merchantName = "Rosângela Conceição", merchantCity = "São Paulo"))
        val leitura = parseBrCode(payload)
        assertTrue(leitura is BrCodeReading.Pix)
        assertEquals("ROSANGELA CONCEICAO", leitura.brCode.merchantName)
        assertEquals("SAO PAULO", leitura.brCode.merchantCity)
        // Sem acento, o payload é ASCII puro: as duas contagens coincidem.
        assertEquals(payload.length, payload.encodeToByteArray().size)
    }

    @Test
    fun `nome longo e truncado no limite da tag, depois de limpo`() {
        val leitura = parseBrCode(ok(base.copy(merchantName = "Maria das Graças de Souza Albuquerque")))
        assertTrue(leitura is BrCodeReading.Pix)
        assertEquals(25, leitura.brCode.merchantName?.length)
    }

    @Test
    fun `emoji e ideograma viram espaco e somem no colapso, sem corromper o payload`() {
        val payload = ok(base.copy(merchantName = "Rosa 🌵 Servicos"))
        val leitura = parseBrCode(payload)
        assertTrue(leitura is BrCodeReading.Pix)
        assertEquals("ROSA SERVICOS", leitura.brCode.merchantName)
    }

    @Test
    fun `nome ou cidade que somem na limpeza recusam, em vez de gerar QR sem recebedor`() {
        assertEquals(PixBrCodeError.EmptyMerchantName, erro(base.copy(merchantName = "🌵🌵")))
        assertEquals(PixBrCodeError.EmptyMerchantName, erro(base.copy(merchantName = "   ")))
        assertEquals(PixBrCodeError.EmptyMerchantCity, erro(base.copy(merchantCity = "")))
    }

    // -- txid --------------------------------------------------------------------------------

    @Test
    fun `sem txid o payload leva o marcador do padrao`() {
        val payload = ok(base.copy(txid = null))
        assertTrue(payload.contains("62070503$TXID_NOT_INFORMED"), "txid ausente deveria virar ***")
    }

    @Test
    fun `txid com traco ou espaco e recusado — passaria aqui e quebraria no PSP`() {
        assertEquals(PixBrCodeError.InvalidTxid, erro(base.copy(txid = "PEDIDO-42")))
        assertEquals(PixBrCodeError.InvalidTxid, erro(base.copy(txid = "PEDIDO 42")))
        assertEquals(PixBrCodeError.InvalidTxid, erro(base.copy(txid = "a".repeat(26))))
    }

    @Test
    fun `txid alfanumerico sobrevive ao ida e volta`() {
        val leitura = parseBrCode(ok(base.copy(txid = "DIARIA2026081501")))
        assertTrue(leitura is BrCodeReading.Pix)
        assertEquals("DIARIA2026081501", leitura.brCode.txid)
    }

    // -- Chave e descrição -------------------------------------------------------------------

    @Test
    fun `chave vazia recusa em vez de gerar QR sintaticamente valido e inutil`() {
        assertEquals(PixBrCodeError.EmptyKey, erro(base.copy(key = "")))
        assertEquals(PixBrCodeError.EmptyKey, erro(base.copy(key = "   ")))
    }

    @Test
    fun `descricao entra na conta e volta pelo parser`() {
        val leitura = parseBrCode(ok(base.copy(description = "Faxina 20/08")))
        assertTrue(leitura is BrCodeReading.Pix)
        assertEquals("FAXINA 20/08", leitura.brCode.accounts.single().description)
    }

    @Test
    fun `descricao encolhe para caber nos 99 caracteres da conta, e o QR continua valido`() {
        // Chave aleatória (36) + descrição longa estouraria o template. O pagamento não pode falhar
        // por causa de um texto decorativo: quem encolhe é a descrição.
        val payload = ok(
            base.copy(key = PixFixtures.RANDOM_KEY, description = "Faxina completa com passar roupa e organizacao"),
        )
        val leitura = parseBrCode(payload)
        assertTrue(leitura is BrCodeReading.Pix, "descrição longa quebrou o payload: $leitura")

        val conta = leitura.brCode.accounts.single()
        assertEquals(PixFixtures.RANDOM_KEY, conta.key)
        assertTrue(conta.description!!.isNotEmpty())
        // O valor do template `26` cabe no campo de tamanho de 2 dígitos.
        assertTrue(conta.fields.sumOf { it.value.length + 4 } <= 99)
    }

    @Test
    fun `chave no limite de 77 sem descricao ainda cabe`() {
        val chaveLonga = "a".repeat(65) + "@example.com"
        assertEquals(77, chaveLonga.length)
        val leitura = parseBrCode(ok(base.copy(key = chaveLonga)))
        assertTrue(leitura is BrCodeReading.Pix)
        assertEquals(chaveLonga, leitura.brCode.accounts.single().key)
    }

    // -- Identidade --------------------------------------------------------------------------

    /**
     * **Contrato entre as duas libs.**
     *
     * Esta string está fixada IDÊNTICA no `src/pix/brCode.test.ts` da weblib. É o único jeito de
     * garantir que o app e a web geram o MESMO QR para a MESMA cobrança — duas implementações da
     * mesma especificação divergem em silêncio, e a que erra produz um código que **abre no banco e
     * falha na confirmação**.
     *
     * Se este teste quebrar, **não atualize o valor**: descubra qual dos dois lados mudou e por quê.
     */
    @Test
    fun `gera exatamente o payload fixado tambem na weblib`() {
        val payload = ok(
            PixCharge(
                key = "12345678901",
                merchantName = "Rosângela Ferreira",
                merchantCity = "São Paulo",
                amount = "150,00",
                txid = "DIARIA01",
            ),
        )
        assertEquals(PAYLOAD_CONTRATO, payload)
    }

    @Test
    fun `dois payloads da mesma cobranca sao identicos — o QR estatico e reprodutivel`() {
        assertEquals(ok(base.copy(amount = "150,00")), ok(base.copy(amount = "150.00")))
    }

    @Test
    fun `o recebedor lido do payload gerado e o que foi pedido`() {
        val leitura = parseBrCode(ok(base))
        assertTrue(leitura is BrCodeReading.Pix)
        val recebedor = pixReceiverOf(leitura.brCode)
        assertNotNull(recebedor)
        assertEquals(PixFixtures.CPF_KEY, recebedor.key)
    }

    private companion object {
        /** Ver o teste de paridade. Alterar aqui sem alterar a weblib é quebrar o contrato. */
        const val PAYLOAD_CONTRATO: String =
            "00020101021126330014br.gov.bcb.pix0111123456789015204000053039865406150.005802BR5918ROSANGELA FERREIRA6009SAO PAULO62120508DIARIA0163047096"
    }
}
