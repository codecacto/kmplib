package br.com.codecacto.kmplib.monetization.purchase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Leitura do catálogo de itens NÃO-CONSUMÍVEIS.
 *
 * O caso que dá nome a este arquivo é o do **id que a loja não conhece**: ela não erra, ela omite o
 * produto e responde com sucesso. Sem [StoreItemsOutcome.missingProductIds] o app mostraria um curso
 * a menos e nada falharia — nem build, nem log, nem alerta.
 */
class StoreItemsOutcomeTest {

    private fun item(id: String) = StoreItem(
        productId = id,
        title = "Curso $id",
        description = "…",
        priceLabel = "R$ 149,90",
        priceAmountMicros = 149_900_000,
        currencyCode = "BRL",
    )

    @Test
    fun `id que a loja nao devolveu vira missingProductIds e incidente`() {
        val r = StoreItemsOutcome.from(
            requestedProductIds = listOf("curso_a", "curso_b", "curso_c"),
            readItems = listOf(item("curso_a"), item("curso_c")),
        )

        assertIs<StoreItemsOutcome.Available>(r)
        assertEquals(listOf("curso_b"), r.missingProductIds)
        assertTrue(r.incident, "produto que ninguém consegue comprar é incidente da fábrica")
    }

    @Test
    fun `catalogo completo nao e incidente`() {
        val r = StoreItemsOutcome.from(listOf("curso_a"), listOf(item("curso_a")))

        assertIs<StoreItemsOutcome.Available>(r)
        assertTrue(r.missingProductIds.isEmpty())
        assertFalse(r.incident)
    }

    @Test
    fun `a ordem e a do NOSSO catalogo e nao a da loja`() {
        val r = StoreItemsOutcome.from(
            requestedProductIds = listOf("destaque", "segundo", "terceiro"),
            readItems = listOf(item("terceiro"), item("destaque"), item("segundo")),
        )

        assertEquals(listOf("destaque", "segundo", "terceiro"), r.items.map { it.productId })
    }

    @Test
    fun `item devolvido sem ter sido pedido continua vendavel`() {
        // Some da ordenação, mas não é descartado: sumir com produto pago é pior que ordem estranha.
        val r = StoreItemsOutcome.from(listOf("curso_a"), listOf(item("curso_a"), item("brinde")))

        assertEquals(listOf("curso_a", "brinde"), r.items.map { it.productId })
        assertTrue(r.missingProductIds.isEmpty())
    }

    @Test
    fun `loja sem nenhum dos ids pedidos e Empty e sempre incidente`() {
        val r = StoreItemsOutcome.from(listOf("curso_a", "curso_b"), emptyList())

        assertIs<StoreItemsOutcome.Empty>(r)
        assertEquals(listOf("curso_a", "curso_b"), r.missingProductIds)
        assertTrue(r.incident, "loja respondeu e não há o que vender — alguém daqui precisa agir")
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun `Available com lista vazia e proibido pelo tipo`() {
        // É exatamente a ambiguidade que este selado existe para matar.
        assertFailsWith<IllegalArgumentException> { StoreItemsOutcome.Available(emptyList()) }
    }

    @Test
    fun `falha de rede nao e incidente e falha de configuracao e`() {
        val rede = StoreItemsOutcome.Failed("offline", PurchaseErrorCode.NETWORK_ERROR)
        val config = StoreItemsOutcome.Failed("sem chave", PurchaseErrorCode.CONFIGURATION_ERROR)

        assertFalse(rede.incident, "usuário sem rede não pode queimar o alerta da sessão")
        assertTrue(config.incident)
        assertTrue(rede.missingProductIds.isEmpty(), "não saber o que a loja tem ≠ saber o que falta")
    }

    @Test
    fun `build sem billing e Unavailable e nunca alerta`() {
        val r: StoreItemsOutcome = StoreItemsOutcome.Unavailable

        assertFalse(r.incident, "a loja não foi consultada — dizer que ela está vazia seria mentira")
        assertTrue(r.items.isEmpty())
        assertTrue(r.missingProductIds.isEmpty())
    }
}

/**
 * Os dois alertas que a venda avulsa trouxe (2.192.0) — sem eles, `StoreItemsOutcome.incident` e
 * `StoreVerification.FAILED` não teriam para onde ir, e o desenho todo seria decorativo.
 */
class ItemSaleAlertKindTest {

    @Test
    fun `os slugs dos alertas novos sao unicos e estaveis`() {
        val slugs = br.com.codecacto.kmplib.monetization.alert.PaymentAlertKind.entries.map { it.slug }

        assertEquals(slugs.size, slugs.distinct().size, "slug repetido agrupa dois incidentes na mesma issue")
        assertTrue("item_indisponivel_na_loja" in slugs)
        assertTrue("verificacao_de_compra_falhou" in slugs)
    }
}
