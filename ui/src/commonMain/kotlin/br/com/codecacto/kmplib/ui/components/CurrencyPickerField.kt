package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.core.locale.Currencies
import br.com.codecacto.kmplib.core.locale.CurrencyInfo
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_currency
import br.com.codecacto.kmplib.generated.resources.kmplib_locale_tag
import br.com.codecacto.kmplib.platform.deviceRegion
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * # Seletor de moeda (2.303.0)
 *
 * Escolha única de moeda ISO 4217 num sheet com busca — o "moeda da conta" de um app global.
 *
 * - **Ordem:** a moeda da REGIÃO do aparelho primeiro, depois BRL, USD, EUR, GBP
 *   ([Currencies.preferredCodes]), e então todas as moedas em circulação em ordem alfabética.
 * - **Nomes no idioma da tela** (CLDR do sistema), rótulo `Real brasileiro (BRL)`; a busca acha por
 *   pedaço do nome ou pelo código, sem acento nem caixa ("dolar", "eur", "iene").
 * - O [value] é o código ISO (`"BRL"`), que é o que vai ao servidor.
 *
 * @param region região do aparelho (default `deviceRegion()`); `null` = só as comuns no topo.
 */
@Composable
fun CurrencyPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = kmpStringResource(Res.string.kmplib_currency),
    placeholder: String? = null,
    helperText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    region: String? = remember { deviceRegion() },
) {
    val idioma = kmpStringResource(Res.string.kmplib_locale_tag)
    val opcoes = remember(idioma, region) {
        currencyPickerOptions(Currencies.ordered(region, Currencies.all(idioma)))
    }
    AppPickerField(
        value = value,
        onValueChange = onValueChange,
        options = opcoes,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        helperText = helperText,
        errorMessage = errorMessage,
        enabled = enabled,
        searchable = true,
    )
}

/** As opções do [CurrencyPickerField], na ordem recebida: `Real brasileiro (BRL)`. Pura. */
fun currencyPickerOptions(currencies: List<CurrencyInfo>): List<PickerOption> =
    currencies.map { PickerOption(value = it.code, label = "${it.name} (${it.code})") }
