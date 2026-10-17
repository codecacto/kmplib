package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.currency.Money
import br.com.codecacto.kmplib.mask.MONEY_INPUT_MAX_DIGITS
import br.com.codecacto.kmplib.mask.MoneyInputTransformation
import br.com.codecacto.kmplib.mask.MoneyOutputTransformation
import br.com.codecacto.kmplib.mask.moneyFromInputDigits
import br.com.codecacto.kmplib.mask.toMoneyInputDigits

/**
 * # Campo de dinheiro em QUALQUER moeda (2.303.0)
 *
 * Digita-se da direita para a esquerda, como em todo app de banco: "2" "8" "8" "0" "0" "0" →
 * `R$ 2.880,00`. O símbolo, a posição dele e os separadores são os da REGIÃO do aparelho, pelo
 * formatador oficial da plataforma (`RegionalFormat.formatMoney`): USD em en-US `$2,880.00`, EUR em
 * de-DE `2.880,00 €`, JPY (0 casas) `￥2,880`.
 *
 * O texto mora no campo (`TextFieldState` + [MoneyInputTransformation]/[MoneyOutputTransformation]) e
 * guarda só algarismos = a quantia em UNIDADES MENORES de [currency] (ISO 4217); o ViewModel recebe
 * [Money] pronto — nunca `Double`, nunca string formatada. Valor negativo não se digita.
 *
 * @param value o valor atual; `null` = vazio. Muda por fora (reset do formulário) reescreve o campo.
 * @param onValueChange recebe `Money(minorUnits, currency)` a cada algarismo, ou `null` quando esvazia.
 * @param currency moeda de exibição E do [Money] emitido. Trocar a moeda NÃO converte o valor: os
 *   mesmos algarismos passam a ser lidos na escala da moeda nova (decida no ViewModel o que fazer).
 * @param showZeroWhenEmpty `false` (default): vazio fica vazio e o [placeholder] aparece.
 * @param saveable `false` = valor fora do estado salvo (dado sensível); ver [rememberSyncedTextFieldState].
 */
@Composable
fun MoneyField(
    value: Money?,
    onValueChange: (Money?) -> Unit,
    currency: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    showZeroWhenEmpty: Boolean = false,
    maxDigits: Int = MONEY_INPUT_MAX_DIGITS,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    saveable: Boolean = true,
) {
    val state = rememberSyncedTextFieldState(
        text = value.toMoneyInputDigits(),
        onTextChange = { digitos -> onValueChange(moneyFromInputDigits(digitos, currency)) },
        saveable = saveable,
    )
    val entrada = remember(maxDigits) { MoneyInputTransformation(maxDigits) }
    val saida = remember(currency, showZeroWhenEmpty) { MoneyOutputTransformation(currency, showZeroWhenEmpty) }

    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = (errorMessage ?: helperText)?.let { { Text(it) } },
        isError = errorMessage != null,
        inputTransformation = entrada,
        outputTransformation = saida,
        keyboardOptions = appKeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        onKeyboardAction = keyboardActions.toKeyboardActionHandler(imeAction),
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error,
        ),
    )
}
