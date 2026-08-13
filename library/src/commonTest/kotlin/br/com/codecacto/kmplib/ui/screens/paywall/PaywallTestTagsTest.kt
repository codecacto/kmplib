package br.com.codecacto.kmplib.ui.screens.paywall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O id de teste é contrato: o flow do Maestro e o spec do Playwright escrevem esta string. Mudar o
 * sufixo sem querer não quebra build nenhum — quebra a suíte de pagamento de todos os apps, com uma
 * mensagem que manda procurar bug de pagamento onde só mudou um nome.
 */
class PaywallTestTagsTest {

    private fun plano(id: String, meses: Int?) =
        PaywallPlan(id = id, name = id, priceLabel = "R$ 1,00", durationMonths = meses)

    @Test
    fun `sufixo vem da duracao canonica, nao do id da loja`() {
        assertEquals("paywall-plano-mensal", PaywallTestTags.plano(plano("\$rc_monthly", 1)))
        assertEquals("paywall-plano-semestral", PaywallTestTags.plano(plano("\$rc_six_month", 6)))
        assertEquals("paywall-plano-anual", PaywallTestTags.plano(plano("premium_anual_x", 12)))
    }

    @Test
    fun `o CTA de cada plano tem id PROPRIO`() {
        val ids = listOf(1, 6, 12).map { PaywallTestTags.botaoAssinar(plano("p$it", it)) }

        // O assert que importa: três CTAs, três ids. Um id único para os três faria o teste tocar no
        // primeiro da tela e comprar o plano errado — verde, e errado.
        assertEquals(ids.toSet().size, ids.size)
        assertEquals(
            listOf(
                "paywall-btn-assinar-mensal",
                "paywall-btn-assinar-semestral",
                "paywall-btn-assinar-anual",
            ),
            ids,
        )
    }

    @Test
    fun `duracao nao-canonica cai no id sanitizado, sem inventar sufixo`() {
        // Trimestral residual e `lifetime` existem no mundo real (o `$rc_three_month` vazio do Super 8).
        // Inventar "trimestral" seria criar vocabulário para um plano que o padrão da fábrica não tem;
        // colapsar tudo em "outro" faria dois planos dividirem o mesmo id na mesma tela.
        assertEquals(
            "paywall-plano-rc-three-month",
            PaywallTestTags.plano(plano("\$rc_three_month", 3)),
        )
        assertEquals("paywall-plano-vitalicio", PaywallTestTags.plano(plano("vitalicio", null)))
    }

    @Test
    fun `id que sanitiza para vazio nao produz tag pendurada`() {
        val tag = PaywallTestTags.plano(plano("\$\$\$", null))

        assertEquals("paywall-plano-desconhecido", tag)
        assertTrue(tag.none { it == '$' }, "id de teste com \$ não é selecionável em shell/YAML")
    }

    @Test
    fun `dois planos nao-canonicos diferentes nao colidem`() {
        val a = PaywallTestTags.plano(plano("plano_a", null))
        val b = PaywallTestTags.plano(plano("plano_b", null))

        assertTrue(a != b)
    }
}
