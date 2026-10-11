package br.com.codecacto.kmplib.monetization.ui

import br.com.codecacto.kmplib.core.locale.FactoryLocales
import br.com.codecacto.kmplib.core.locale.KmpLibLocales
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_price_per_month
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_price_per_semester
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_price_per_year
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_price_per_months
import br.com.codecacto.kmplib.monetization.purchase.loadPurchaseErrorTexts
import br.com.codecacto.kmplib.ui.locale.kmpGetString
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallPlan
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallTexts
import br.com.codecacto.kmplib.ui.screens.paywall.loadPaywallPlanLabels
import java.io.File
import java.util.Locale
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * GAP-PT-M31 (2.288.0), o caso do App do Personal: app só pt-BR num aparelho em INGLÊS. O paywall
 * saía com "R$ 190,80/year", "Yearly", "No plans available". Com `KmpLibLocales.configure(PT_BR)`,
 * os modelos de preço por período, os nomes de plano e as mensagens de compra saem em pt-BR.
 */
class PaywallAppLanguageTest {

    private val padrao = Locale.getDefault()

    @BeforeTest
    fun aparelhoEmIngles() {
        Locale.setDefault(Locale.US)
        KmpLibLocales.configure(listOf(FactoryLocales.PT_BR))
    }

    @AfterTest
    fun restaurar() {
        Locale.setDefault(padrao)
        KmpLibLocales.reset()
    }

    private fun plano(meses: Int) = PaywallPlan(id = "x", name = "x", priceLabel = "R$ 190,80", durationMonths = meses)

    @Test
    fun `sufixo de periodo do preco segue o idioma do app`() = runTest {
        val textos = PaywallTexts(
            pricePerMonthTemplate = kmpGetString(Res.string.kmplib_paywall_price_per_month),
            pricePerSemesterTemplate = kmpGetString(Res.string.kmplib_paywall_price_per_semester),
            pricePerYearTemplate = kmpGetString(Res.string.kmplib_paywall_price_per_year),
            pricePerMonthsTemplate = kmpGetString(Res.string.kmplib_paywall_price_per_months),
        )
        assertEquals("R$ 190,80/ano", textos.pricePerPeriod(plano(12)))
        assertEquals("R$ 190,80/mês", textos.pricePerPeriod(plano(1)))
        assertEquals("R$ 190,80/semestre", textos.pricePerPeriod(plano(6)))
        assertFalse(textos.pricePerPeriod(plano(12)).contains("year"))
    }

    @Test
    fun `nomes de plano e duracoes seguem o idioma do app`() = runTest {
        val rotulos = loadPaywallPlanLabels()
        assertEquals("Anual", rotulos.yearly)
        assertEquals("Mensal", rotulos.monthly)
        assertEquals("1 ano", rotulos.oneYear)
    }

    @Test
    fun `mensagens de compra montadas no ViewModel seguem o idioma do app`() = runTest {
        val base = Regex("""<string name="kmplib_purchase_network">(.*?)</string>""")
            .find(File("../ui/src/commonMain/composeResources/values/strings.xml").readText())!!.groupValues[1]
        assertEquals(base, loadPurchaseErrorTexts().networkError)
    }
}
