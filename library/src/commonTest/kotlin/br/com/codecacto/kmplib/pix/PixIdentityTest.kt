package br.com.codecacto.kmplib.pix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Identidade de plaquinha e comparação nos **dois regimes**.
 *
 * O caso que dá nome ao módulo está em
 * `QR dinamico legitimo com URL diferente e a MESMA plaquinha`: é lá que a igualdade exata de
 * payload — o erro clássico — marcaria fraude em toda plaquinha honesta.
 */
class PixIdentityTest {

    private fun ler(payload: String) = parseBrCode(payload)

    private fun identidade(payload: String): PixIdentity =
        assertIs<PixIdentityResult.Available>(pixIdentityOf(ler(payload))).identity

    // -------------------------------------------------------------------------------------------
    // Derivação da identidade
    // -------------------------------------------------------------------------------------------

    @Test
    fun `identidade de QR estatico e o payload inteiro`() {
        val payload = PixFixtures.staticPix()
        val identity = assertIs<PixIdentity.Static>(identidade(payload))

        assertEquals(payload, identity.payload)
        assertEquals(PixInitiationMethod.Static, identity.initiationMethod)
        assertEquals(PixFixtures.CPF_KEY, identity.receiver.key)
        assertEquals(PixKeyType.CPF, identity.receiver.keyType)
        assertEquals("CODECACTO SERVICOS", identity.receiver.name)
    }

    @Test
    fun `identidade de QR dinamico e host mais prefixo de caminho`() {
        val identity = assertIs<PixIdentity.Dynamic>(
            identidade(PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/abc123")),
        )

        assertEquals("pix.example.com", identity.host)
        assertEquals("qr/v2/cobv", identity.pathPrefix)
        assertEquals(PixInitiationMethod.Dynamic, identity.initiationMethod)
    }

    @Test
    fun `nao ha identidade sem base - fail-closed em cada caso`() {
        assertEquals(
            PixNotComparableReason.ScannedNotPix,
            assertIs<PixIdentityResult.Unavailable>(
                pixIdentityOf(ler(PixFixtures.otherArrangement())),
            ).reason,
        )
        assertEquals(
            PixNotComparableReason.ScannedInvalidCrc,
            assertIs<PixIdentityResult.Unavailable>(
                pixIdentityOf(ler(PixFixtures.withBrokenCrc(PixFixtures.staticPix()))),
            ).reason,
        )
        assertEquals(
            PixNotComparableReason.ScannedNotEmv,
            assertIs<PixIdentityResult.Unavailable>(pixIdentityOf(parseBrCode("texto qualquer"))).reason,
        )
        assertEquals(
            PixNotComparableReason.UnknownInitiationMethod,
            assertIs<PixIdentityResult.Unavailable>(
                pixIdentityOf(ler(PixFixtures.staticPix(initiation = "13"))),
            ).reason,
        )
        // Dinâmico declarado, mas sem URL de payload: nada estável em que ancorar.
        assertEquals(
            PixNotComparableReason.UnusablePayloadUrl,
            assertIs<PixIdentityResult.Unavailable>(
                pixIdentityOf(ler(PixFixtures.staticPix(initiation = "12"))),
            ).reason,
        )

        assertNull(parseBrCode("texto qualquer").pixIdentityOrNull())
    }

    @Test
    fun `estatico que tambem traz URL continua sendo tratado como estatico`() {
        val payload = PixFixtures.dynamicPix(
            url = "pix.example.com/qr/v2/abc",
            initiation = "11",
        )
        assertIs<PixIdentity.Static>(identidade(payload))
    }

    // -------------------------------------------------------------------------------------------
    // Regime ESTÁTICO
    // -------------------------------------------------------------------------------------------

    @Test
    fun `estatico com o mesmo payload e a mesma plaquinha`() {
        val payload = PixFixtures.staticPix()
        val comparison = comparePix(identidade(payload), ler(payload))

        assertEquals(PixComparison.SamePlaque, comparison)
        assertTrue(comparison.isMatch)
        assertFalse(comparison.isDivergent)
    }

    @Test
    fun `estatico com payload diferente e o MESMO recebedor avisa que o codigo mudou`() {
        val cadastrada = identidade(PixFixtures.staticPix())
        // Mesma chave/nome/cidade, mas agora com valor fixo embutido: outro payload.
        val lida = ler(PixFixtures.staticPix(amount = "50.00"))

        val comparison = assertIs<PixComparison.PayloadChanged>(comparePix(cadastrada, lida))
        assertNotEquals(comparison.expectedPayload, comparison.foundPayload)
        assertTrue(comparison.isDivergent, "não é golpe clássico, mas não é a plaquinha cadastrada")
    }

    @Test
    fun `estatico com chave trocada e RECEBEDOR DIFERENTE`() {
        val cadastrada = identidade(PixFixtures.staticPix(key = PixFixtures.CPF_KEY))
        val lida = ler(PixFixtures.staticPix(key = PixFixtures.CNPJ_KEY))

        val comparison = assertIs<PixComparison.ReceiverChanged>(comparePix(cadastrada, lida))
        assertEquals(setOf(PixReceiverField.KEY), comparison.changed)
        assertEquals(PixFixtures.CPF_KEY, comparison.expected.key)
        assertEquals(PixFixtures.CNPJ_KEY, comparison.found.key)
    }

    // -------------------------------------------------------------------------------------------
    // Regime DINÂMICO — o erro clássico
    // -------------------------------------------------------------------------------------------

    @Test
    fun `QR dinamico legitimo com URL diferente e a MESMA plaquinha`() {
        // A mesma plaquinha, lida em dois momentos: o PSP emitiu outra cobrança e trocou o último
        // segmento da URL. O payload muda; a plaquinha, não.
        val noCadastro = PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/cobranca-de-ontem")
        val naRonda = PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/cobranca-de-hoje")

        // O CONTROLE: a comparação ingênua (payload cru) marcaria fraude na plaquinha honesta.
        assertNotEquals(noCadastro, naRonda, "o payload dinâmico muda a cada cobrança")

        assertEquals(PixComparison.SamePlaque, comparePix(identidade(noCadastro), ler(naRonda)))
    }

    @Test
    fun `QR dinamico com a query trocada continua sendo a mesma plaquinha`() {
        val noCadastro = PixFixtures.dynamicPix("pix.example.com/qr/v2/loja?id=111")
        val naRonda = PixFixtures.dynamicPix("pix.example.com/qr/v2/loja?id=999")

        assertNotEquals(noCadastro, naRonda)
        assertEquals(PixComparison.SamePlaque, comparePix(identidade(noCadastro), ler(naRonda)))
    }

    @Test
    fun `recebedor trocado com o HOST IGUAL e recebedor diferente, nao endpoint diferente`() {
        // Dois clientes do mesmo PSP: o host bate, mas quem recebe é outro. É o caso mais grave e
        // tem precedência sobre qualquer outra divergência.
        val cadastrada = identidade(
            PixFixtures.dynamicPix(
                url = "pix.example.com/qr/v2/cobv/abc",
                name = "PADARIA DO JOAO",
                key = PixFixtures.CPF_KEY,
            ),
        )
        val lida = ler(
            PixFixtures.dynamicPix(
                url = "pix.example.com/qr/v2/cobv/xyz",
                name = "OUTRO RECEBEDOR",
                key = PixFixtures.CNPJ_KEY,
            ),
        )

        val comparison = assertIs<PixComparison.ReceiverChanged>(comparePix(cadastrada, lida))
        assertEquals(setOf(PixReceiverField.KEY, PixReceiverField.NAME), comparison.changed)
    }

    @Test
    fun `host diferente com o mesmo nome exibido e ENDPOINT DIFERENTE`() {
        // O nome do recebedor é texto livre escolhido por quem emite o QR: um golpista copia.
        // O host, não.
        val cadastrada = identidade(PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/abc"))
        val lida = ler(PixFixtures.dynamicPix("pix.servidor-do-golpe.com/qr/v2/cobv/abc"))

        val comparison = assertIs<PixComparison.EndpointChanged>(comparePix(cadastrada, lida))
        assertTrue(comparison.hostChanged)
        assertEquals("pix.example.com", comparison.expected.host)
        assertEquals("pix.servidor-do-golpe.com", comparison.found.host)
    }

    @Test
    fun `caminho estavel diferente no mesmo host tambem e endpoint diferente`() {
        val cadastrada = identidade(PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/abc"))
        val lida = ler(PixFixtures.dynamicPix("pix.example.com/outra/rota/abc"))

        val comparison = assertIs<PixComparison.EndpointChanged>(comparePix(cadastrada, lida))
        assertFalse(comparison.hostChanged)
        assertEquals("qr/v2/cobv", comparison.expected.pathPrefix)
        assertEquals("outra/rota", comparison.found.pathPrefix)
    }

    @Test
    fun `acento e caixa no nome nao produzem alarme falso`() {
        val cadastrada = identidade(
            PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/abc", name = "PADARIA DO JOAO"),
        )
        val lida = ler(
            PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/def", name = "Padaria  do  João"),
        )

        assertEquals(PixComparison.SamePlaque, comparePix(cadastrada, lida))
    }

    // -------------------------------------------------------------------------------------------
    // Precedência e "não comparável"
    // -------------------------------------------------------------------------------------------

    @Test
    fun `regime trocado com o mesmo recebedor e RegimeChanged`() {
        val cadastrada = identidade(PixFixtures.staticPix(key = PixFixtures.CPF_KEY))
        val lida = ler(
            PixFixtures.dynamicPix(
                url = "pix.example.com/qr/v2/cobv/abc",
                key = PixFixtures.CPF_KEY,
            ),
        )

        val comparison = assertIs<PixComparison.RegimeChanged>(comparePix(cadastrada, lida))
        assertEquals(PixInitiationMethod.Static, comparison.expected)
        assertEquals(PixInitiationMethod.Dynamic, comparison.found)
    }

    @Test
    fun `recebedor diferente tem precedencia sobre regime diferente`() {
        val cadastrada = identidade(PixFixtures.staticPix(key = PixFixtures.CPF_KEY))
        val lida = ler(
            PixFixtures.dynamicPix(
                url = "pix.example.com/qr/v2/cobv/abc",
                key = PixFixtures.CNPJ_KEY,
            ),
        )

        assertIs<PixComparison.ReceiverChanged>(comparePix(cadastrada, lida))
    }

    @Test
    fun `chave presente em um lado so e divergencia de recebedor`() {
        val cadastrada = identidade(
            PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/abc", key = PixFixtures.CPF_KEY),
        )
        val lida = ler(PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/abc"))

        val comparison = assertIs<PixComparison.ReceiverChanged>(comparePix(cadastrada, lida))
        assertEquals(setOf(PixReceiverField.KEY), comparison.changed)
    }

    @Test
    fun `cidade trocada e divergencia de recebedor`() {
        val cadastrada = identidade(PixFixtures.staticPix(city = "SAO PAULO"))
        val lida = ler(PixFixtures.staticPix(city = "CURITIBA"))

        val comparison = assertIs<PixComparison.ReceiverChanged>(comparePix(cadastrada, lida))
        assertEquals(setOf(PixReceiverField.CITY), comparison.changed)
    }

    @Test
    fun `plaquinha nao cadastrada nao e divergencia - e falta de base`() {
        val comparison = assertIs<PixComparison.NotComparable>(
            comparePix(null, ler(PixFixtures.staticPix())),
        )
        assertEquals(PixNotComparableReason.NothingRegistered, comparison.reason)
        assertFalse(comparison.isDivergent, "não cadastrada não é fraude")
    }

    @Test
    fun `leitura sem integridade ou sem Pix nao vira divergencia`() {
        val cadastrada = identidade(PixFixtures.staticPix())

        assertEquals(
            PixNotComparableReason.ScannedInvalidCrc,
            assertIs<PixComparison.NotComparable>(
                comparePix(cadastrada, ler(PixFixtures.withBrokenCrc(PixFixtures.staticPix()))),
            ).reason,
        )
        assertEquals(
            PixNotComparableReason.ScannedNotPix,
            assertIs<PixComparison.NotComparable>(
                comparePix(cadastrada, ler(PixFixtures.otherArrangement())),
            ).reason,
        )
        assertEquals(
            PixNotComparableReason.ScannedNotEmv,
            assertIs<PixComparison.NotComparable>(
                comparePix(cadastrada, parseBrCode("https://codecacto.com.br")),
            ).reason,
        )
    }

    // -------------------------------------------------------------------------------------------
    // Persistência
    // -------------------------------------------------------------------------------------------

    @Test
    fun `identidade sobrevive ao round-trip de persistencia nos dois regimes`() {
        val estatica = identidade(PixFixtures.staticPix())
        val dinamica = identidade(PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/abc"))

        listOf(estatica, dinamica).forEach { original ->
            val encoded = PixIdentity.encode(original)
            assertEquals(original, PixIdentity.decode(encoded))
        }

        assertTrue(PixIdentity.encode(estatica).contains("\"static\""))
        assertTrue(PixIdentity.encode(dinamica).contains("\"dynamic\""))
    }

    @Test
    fun `decode nunca lanca - texto nulo, vazio ou corrompido devolve null`() {
        listOf(null, "", "   ", "{", "não é json", """{"type":"marciano"}""").forEach { texto ->
            assertNull(PixIdentity.decode(texto), "entrada: $texto")
        }
    }

    @Test
    fun `identidade guardada continua comparavel depois de persistida`() {
        val payload = PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/hoje")
        val guardada = PixIdentity.decode(PixIdentity.encode(identidade(payload)))

        val lidaAmanha = ler(PixFixtures.dynamicPix("pix.example.com/qr/v2/cobv/amanha"))
        assertEquals(PixComparison.SamePlaque, comparePix(guardada, lidaAmanha))
    }

    // -------------------------------------------------------------------------------------------
    // Endpoint e normalização
    // -------------------------------------------------------------------------------------------

    @Test
    fun `pixEndpointOf aceita URL com e sem esquema e ignora query e fragmento`() {
        val esperado = PixEndpoint("pix.example.com", "qr/v2")

        assertEquals(esperado, pixEndpointOf("pix.example.com/qr/v2/abc"))
        assertEquals(esperado, pixEndpointOf("https://pix.example.com/qr/v2/abc"))
        assertEquals(esperado, pixEndpointOf("HTTPS://PIX.EXAMPLE.COM/qr/v2/abc"))
        assertEquals(esperado, pixEndpointOf("pix.example.com/qr/v2/abc?token=1"))
        assertEquals(esperado, pixEndpointOf("pix.example.com/qr/v2/abc#frag"))
        assertEquals(esperado, pixEndpointOf("  pix.example.com/qr/v2/abc  "))
    }

    @Test
    fun `pixEndpointOf ancora o host DEPOIS do arroba - a armadilha de phishing`() {
        // "https://banco-de-verdade.com@servidor-do-golpe.com/x": o olho lê o primeiro nome,
        // o aparelho conecta no segundo.
        val endpoint = pixEndpointOf("https://banco-de-verdade.com@servidor-do-golpe.com/qr/v2/abc")
        assertEquals("servidor-do-golpe.com", endpoint?.host)
    }

    @Test
    fun `pixEndpointOf com um segmento so deixa o prefixo vazio`() {
        assertEquals(PixEndpoint("qr.example.com", ""), pixEndpointOf("qr.example.com/abc123"))
        assertEquals(PixEndpoint("qr.example.com", ""), pixEndpointOf("qr.example.com"))
    }

    @Test
    fun `pixEndpointOf preserva a porta e recusa entrada inutilizavel`() {
        assertEquals(
            PixEndpoint("pix.example.com:8443", "qr/v2"),
            pixEndpointOf("https://pix.example.com:8443/qr/v2/abc"),
        )

        listOf(null, "", "   ", "mailto:golpe@example.com", "javascript:alert(1)", "pix example.com/qr")
            .forEach { assertNull(pixEndpointOf(it), "entrada: $it") }
    }

    @Test
    fun `normalizePixKeyForCompare apara apresentacao sem aproximar chaves distintas`() {
        assertEquals("12345678901", normalizePixKeyForCompare("123.456.789-01"))
        assertEquals("5511999998888", normalizePixKeyForCompare("+55 11 99999-8888"))
        assertEquals("pagamentos@example.com", normalizePixKeyForCompare("Pagamentos@Example.COM"))
        assertEquals(
            PixFixtures.RANDOM_KEY,
            normalizePixKeyForCompare(PixFixtures.RANDOM_KEY.uppercase()),
        )
        // Chave de formato desconhecido é comparada COMO VEIO.
        assertEquals("Chave-Estranha", normalizePixKeyForCompare("  Chave-Estranha  "))
        assertEquals("", normalizePixKeyForCompare(null))

        // Dois CPFs diferentes não podem colidir depois de normalizados.
        assertNotEquals(
            normalizePixKeyForCompare("123.456.789-01"),
            normalizePixKeyForCompare("123.456.789-02"),
        )
    }

    @Test
    fun `normalizePixTextForCompare colapsa apresentacao e nada mais`() {
        assertEquals("PADARIA DO JOAO", normalizePixTextForCompare("  padaria  do   João "))
        assertEquals("SAO PAULO", normalizePixTextForCompare("São Paulo"))
        assertEquals("", normalizePixTextForCompare(null))

        // Nomes diferentes seguem diferentes: nada é truncado nem aproximado.
        assertNotEquals(
            normalizePixTextForCompare("PADARIA DO JOAO"),
            normalizePixTextForCompare("PADARIA DO JOAO II"),
        )
    }

    @Test
    fun `receiver expoe o dado de quem recebe mesmo sem cadastro - o diferencial do produto`() {
        val brCode = assertIs<BrCodeReading.Pix>(
            ler(PixFixtures.staticPix(key = PixFixtures.EMAIL_KEY, name = "Café da Esquina")),
        ).brCode

        // Para exibir, o texto original.
        assertEquals("Café da Esquina", brCode.merchantName)
        assertEquals(PixKeyType.EMAIL, brCode.pixKeyType)

        // Para comparar, o normalizado.
        val receiver = pixReceiverOf(brCode)
        assertEquals("CAFE DA ESQUINA", receiver.name)
        assertEquals(PixFixtures.EMAIL_KEY, receiver.key)
        assertFalse(receiver.isEmpty)
    }
}
