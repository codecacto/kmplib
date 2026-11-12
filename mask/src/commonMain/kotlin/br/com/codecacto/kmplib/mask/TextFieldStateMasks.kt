package br.com.codecacto.kmplib.mask

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.placeCursorAtEnd

/*
 * Máscaras para o campo com ESTADO (`TextFieldState`, `BasicTextField(state = …)`,
 * `OutlinedTextField(state = …)`) — 2.270.0 (GAP-PT-M22).
 *
 * É a forma oficial do Compose para texto no campo: o estado mora no componente, e a máscara se
 * divide em duas — `InputTransformation` decide o que FICA no estado (só os algarismos) e
 * `OutputTransformation` decide como o estado APARECE (parênteses, espaço, hífen), sem que esses
 * caracteres entrem no valor. O mapeamento de cursor sai sozinho das inserções que a saída faz.
 *
 * As `VisualTransformation` (`PhoneVisualTransformation`, `CepVisualTransformation`) continuam para
 * o campo de `value`/`onValueChange`; as duas famílias não se misturam no mesmo campo.
 *
 * ```kotlin
 * val telefone = rememberTextOnlyTextFieldState()   // kmplib-ui: sem o desfazer no estado salvo (CMP-9960, iOS)
 * OutlinedTextField(
 *     state = telefone,
 *     inputTransformation = PhoneInputTransformation,
 *     outputTransformation = PhoneBrOutputTransformation,
 *     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
 * )
 * // telefone.text = "65999998888" — é isso que vai para o servidor
 * ```
 */

/**
 * Telefone brasileiro no estado: **só algarismos, no máximo 11** (DDD + número). Colar
 * "+55 (65) 99999-8888" guarda "65999998888" — o DDI 55 de um número colado com 12 ou 13
 * algarismos sai, em vez de empurrar o final do número para fora. O 12º algarismo digitado não
 * entra. Par de exibição: [PhoneBrOutputTransformation].
 */
object PhoneInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val proposed = asCharSequence().toString()
        val digits = normalizeBrPhoneDigits(proposed)
        if (digits != proposed) {
            replace(0, length, digits)
            placeCursorAtEnd()
        }
    }
}

/**
 * Exibição `(AA) NNNNN-NNNN` (11 algarismos, celular) ou `(AA) NNNN-NNNN` (até 10, fixo), montada
 * enquanto se digita — a mesma regra do [PhoneVisualTransformation]. Espera o estado só com
 * algarismos ([PhoneInputTransformation]); com qualquer outro caractere, mostra o texto como está.
 */
object PhoneBrOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val n = length
        if (n == 0 || !asCharSequence().all { it.isDigit() }) return
        // Inserções da direita para a esquerda, para os índices não andarem.
        val split = if (n == 11) 5 else 4
        if (n > 2 + split) insert(2 + split, "-")
        if (n > 2) insert(2, ") ")
        insert(0, "(")
    }
}

/**
 * CEP no estado: **só algarismos, no máximo 8** ([filterCepInput]). Colar "78000-000" guarda
 * "78000000". Par de exibição: [CepOutputTransformation]. (Era `internal` no `AddressFields`
 * até a 2.269.0; o `AddressFields` passou a usar este.)
 */
object CepInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val proposed = asCharSequence().toString()
        val digits = filterCepInput(proposed)
        if (digits != proposed) {
            replace(0, length, digits)
            placeCursorAtEnd()
        }
    }
}

/** Exibição `00000-000` — o hífen é só visual; o estado guarda os 8 algarismos. */
object CepOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length > 5) insert(5, "-")
    }
}

/**
 * Telefone internacional no estado (2.359.0): `+` só no começo e algarismos, até 15 (E.164) — a mesma
 * regra do [filterInternationalPhoneInput]. Colar "+351 912 345 678" guarda "+351912345678". Sem par de
 * exibição de propósito: fora do Brasil o formato é livre (o número é mostrado como digitado, só com
 * algarismos). Para decidir entre este e o [PhoneInputTransformation], [PhoneInputFormat.forRegion].
 */
object InternationalPhoneInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val proposed = asCharSequence().toString()
        val filtered = filterInternationalPhoneInput(proposed)
        if (filtered != proposed) {
            replace(0, length, filtered)
            placeCursorAtEnd()
        }
    }
}

/**
 * **Os algarismos de um telefone brasileiro (DDD + número), prontos para guardar ou enviar** — a regra
 * do [PhoneInputTransformation], exposta para o app aplicar ao valor que chega de FORA do campo (do
 * servidor, de um contato, de um texto colado em outro lugar) sem copiar a regra (pública desde 2.359.0).
 *
 * - Tira tudo que não é algarismo: `"(65) 99999-8888"` → `"65999998888"`.
 * - Tira o DDI 55 de número com 12 ou 13 algarismos que começa por 55 e sobra 10-11:
 *   `"+55 65 99999-8888"` → `"65999998888"`. Um número de 11 algarismos com DDD 55 (RS) fica como está.
 * - Corta em 11 algarismos (o máximo de um número BR com DDD).
 *
 * Não valida (DDD existente, celular com 9): para isso, `PhoneValidator`. Vazio devolve vazio.
 */
fun normalizeBrPhoneDigits(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.length > 11 && digits.startsWith("55") && digits.length - 2 in 10..11) {
        return digits.drop(2)
    }
    return digits.take(11)
}
