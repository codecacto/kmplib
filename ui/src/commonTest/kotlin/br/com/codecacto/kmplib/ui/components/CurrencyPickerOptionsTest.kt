package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.core.locale.Currencies
import br.com.codecacto.kmplib.core.locale.CurrencyInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurrencyPickerOptionsTest {

    @Test
    fun rotuloTemNomeECodigo_eOrdemEPreservada() {
        val opcoes = currencyPickerOptions(
            listOf(CurrencyInfo("JPY", "Iene japonês", "¥"), CurrencyInfo("BRL", "Real brasileiro", "R$")),
        )
        assertEquals(listOf(PickerOption("JPY", "Iene japonês (JPY)"), PickerOption("BRL", "Real brasileiro (BRL)")), opcoes)
    }

    @Test
    fun listaRealComARegiaoNoTopo() {
        val opcoes = currencyPickerOptions(Currencies.ordered("JP", Currencies.all("pt-BR")))
        assertEquals(listOf("JPY", "BRL", "USD", "EUR", "GBP"), opcoes.take(5).map { it.value })
        assertTrue(opcoes.size > 100, "moedas em circulação: ${opcoes.size}")
    }
}
