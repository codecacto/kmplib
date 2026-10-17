package br.com.codecacto.kmplib.mask

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import br.com.codecacto.kmplib.core.currency.Money
import br.com.codecacto.kmplib.core.locale.RegionalFormat

/*
 * Máscara de dinheiro para QUALQUER moeda (2.303.0) — o par internacional do `CurrencyVisualTransformation`
 * (que é só BRL, com "R$ " e separadores escritos à mão).
 *
 * Mesmo modelo de sempre: o ESTADO do campo guarda só algarismos = a quantia em UNIDADES MENORES da
 * moeda (centavos em BRL/USD/EUR, o próprio iene em JPY — casas da ISO 4217), digitada da direita para
 * a esquerda ("2" "8" "8" "0" "0" "0" → R$ 2.880,00). A EXIBIÇÃO é a do formatador oficial da
 * plataforma na região do aparelho (`RegionalFormat.formatMoney`): símbolo, posição, espaço e
 * separadores corretos para USD em en-US ("$2,880.00"), EUR em de-DE ("2.880,00 €"), JPY ("￥2,880").
 */

/** Teto de algarismos do campo de dinheiro: 15 cabem com folga no `Long` (R$ 9 trilhões). */
const val MONEY_INPUT_MAX_DIGITS: Int = 15

/** Só algarismos, sem zeros à esquerda, no máximo [maxDigits]. Colar "R$ 1.234,56" guarda "123456". */
fun filterMoneyInput(input: String, maxDigits: Int = MONEY_INPUT_MAX_DIGITS): String =
    input.filter { it.isDigit() }.trimStart('0').take(maxDigits)

/** Algarismos do estado → [Money] (`""` → `null`). */
fun moneyFromInputDigits(digits: String, currency: String): Money? {
    val limpos = filterMoneyInput(digits)
    if (limpos.isEmpty()) return null
    return Money(limpos.toLong(), currency)
}

/** [Money] → algarismos do estado (`null`/zero/negativo → `""`: o campo não digita valor negativo). */
fun Money?.toMoneyInputDigits(): String =
    if (this == null || minorUnits <= 0L) "" else minorUnits.toString()

/** O texto que o campo mostra para [digits] — `""` quando vazio e `!showZeroWhenEmpty`. */
fun formatMoneyInput(digits: String, currency: String, showZeroWhenEmpty: Boolean): String {
    val limpos = filterMoneyInput(digits)
    if (limpos.isEmpty() && !showZeroWhenEmpty) return ""
    return RegionalFormat.formatMoney(limpos.ifEmpty { "0" }.toLong(), currency, showCentsWhenWhole = true)
}

/**
 * Dinheiro em [currency] para o campo de `value`/`onValueChange`. O `value` guarda só algarismos
 * (passe por [filterMoneyInput] no `onValueChange`); o cursor fica sempre no fim, como no BRL.
 *
 * @param showZeroWhenEmpty `false` (default aqui): vazio fica VAZIO e o placeholder aparece — um zero
 *   desenhado é lido como valor já informado.
 */
class MoneyVisualTransformation(
    private val currency: String,
    private val showZeroWhenEmpty: Boolean = false,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = filterMoneyInput(text.text)
        val formatted = formatMoneyInput(digits, currency, showZeroWhenEmpty)
        return TransformedText(AnnotatedString(formatted), EndCursorMapping(text.text.length, formatted.length))
    }

    override fun equals(other: Any?): Boolean =
        other is MoneyVisualTransformation && other.currency == currency && other.showZeroWhenEmpty == showZeroWhenEmpty

    override fun hashCode(): Int = 31 * currency.hashCode() + showZeroWhenEmpty.hashCode()
}

/** Cursor sempre no fim: os algarismos entram pela direita. */
private class EndCursorMapping(private val originalLength: Int, private val transformedLength: Int) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = if (offset == 0) 0 else transformedLength
    override fun transformedToOriginal(offset: Int): Int = if (offset == 0) 0 else originalLength
}

/**
 * Dinheiro no ESTADO do campo (`TextFieldState`): só algarismos, sem zero à esquerda, no máximo
 * [maxDigits]. Par de exibição: [MoneyOutputTransformation].
 */
class MoneyInputTransformation(private val maxDigits: Int = MONEY_INPUT_MAX_DIGITS) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val proposto = asCharSequence().toString()
        val aceito = filterMoneyInput(proposto, maxDigits)
        if (aceito != proposto) {
            replace(0, length, aceito)
            placeCursorAtEnd()
        }
    }

    override fun equals(other: Any?): Boolean = other is MoneyInputTransformation && other.maxDigits == maxDigits
    override fun hashCode(): Int = maxDigits
}

/**
 * Exibição do estado de [MoneyInputTransformation] em [currency], pelo formatador da plataforma.
 * Estado com algo que não é algarismo é mostrado como está.
 */
class MoneyOutputTransformation(
    private val currency: String,
    private val showZeroWhenEmpty: Boolean = false,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val estado = asCharSequence().toString()
        if (!estado.all { it.isDigit() }) return
        val formatado = formatMoneyInput(estado, currency, showZeroWhenEmpty)
        if (formatado != estado) replace(0, length, formatado)
    }

    override fun equals(other: Any?): Boolean =
        other is MoneyOutputTransformation && other.currency == currency && other.showZeroWhenEmpty == showZeroWhenEmpty

    override fun hashCode(): Int = 31 * currency.hashCode() + showZeroWhenEmpty.hashCode()
}
