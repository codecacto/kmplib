package br.com.codecacto.kmplib.mask

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange

/*
 * Máscaras de DOCUMENTO para o campo com estado (`TextFieldState`) — 2.329.0.
 *
 * Mesma forma do par do telefone/CEP (`TextFieldStateMasks.kt`): a `InputTransformation` decide o
 * que FICA no estado (o valor cru, que vai para o servidor) e a `OutputTransformation` só DESENHA
 * os separadores. É o caminho que não perde dígito no iOS em digitação rápida: o texto mora no
 * campo, não num `value` que dá a volta pelo ViewModel (a classe de defeito corrigida aos pedaços
 * na 2.258.0 e na 2.262.1). As `*VisualTransformation` de CPF/CNPJ/data/placa/CREFITO continuam
 * funcionando, `@Deprecated` apontando para cá.
 *
 * Diferença deliberada para o par antigo do telefone: aqui o cursor **fica onde estava** quando a
 * entrada traz um caractere recusado (digitar "a" no meio do CPF não pula o cursor para o fim), e
 * digitar com o campo CHEIO é recusado inteiro (como o `InputTransformation.maxLength` do Compose),
 * em vez de empurrar o último algarismo para fora.
 *
 * ```kotlin
 * val cpf = rememberTextOnlyTextFieldState()   // kmplib-ui
 * OutlinedTextField(
 *     state = cpf,
 *     inputTransformation = CpfInputTransformation,
 *     outputTransformation = CpfOutputTransformation,
 *     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
 * )
 * // cpf.text = "12345678909" — 11 algarismos, sem pontuação
 * ```
 */

/**
 * Filtra o buffer caractere a caractere: [accept] devolve o caractere a guardar (já normalizado,
 * ex.: maiúsculo) ou `null` para recusar. O cursor conta só os caracteres aceitos antes dele.
 * Passou de [maxLength]: inserção simples num campo que já estava cheio é recusada inteira
 * (`revertAllChanges`); colagem/substituição é cortada em [maxLength].
 */
internal fun TextFieldBuffer.filterKeepingCursor(maxLength: Int, accept: (Char) -> Char?) {
    val proposed = asCharSequence().toString()
    val cursorEnd = selection.end
    val result = StringBuilder(proposed.length)
    var cursor = 0
    proposed.forEachIndexed { index, c ->
        val kept = accept(c) ?: return@forEachIndexed
        result.append(kept)
        if (index < cursorEnd) cursor++
    }
    if (result.length > maxLength) {
        if (originalSelection.collapsed && originalText.length >= maxLength) {
            revertAllChanges()
            return
        }
        result.setLength(maxLength)
    }
    val accepted = result.toString()
    if (accepted != proposed) {
        replace(0, length, accepted)
        selection = TextRange(cursor.coerceAtMost(accepted.length))
    }
}

private fun digitOnly(c: Char): Char? = if (c in '0'..'9') c else null

private fun asciiAlnumUpper(c: Char): Char? {
    val u = c.uppercaseChar()
    return if (u in 'A'..'Z' || u in '0'..'9') u else null
}

/** Insere [separators] (posição no texto CRU → texto) da direita para a esquerda. */
private fun TextFieldBuffer.insertSeparators(vararg separators: Pair<Int, String>) {
    val n = length
    separators.sortedByDescending { it.first }.forEach { (at, sep) -> if (n > at) insert(at, sep) }
}

// --- CPF -----------------------------------------------------------------------------------------

/** CPF no estado: **só algarismos, no máximo 11** (o mesmo de [filterCpfInput]). */
object CpfInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() = filterKeepingCursor(11, ::digitOnly)
}

/** Exibição `000.000.000-00`, montada enquanto se digita (a mesma do [CpfVisualTransformation]). */
object CpfOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length == 0 || !asCharSequence().all { it in '0'..'9' }) return
        insertSeparators(3 to ".", 6 to ".", 9 to "-")
    }
}

// --- CNPJ ----------------------------------------------------------------------------------------

/**
 * CNPJ no estado: **até 14 caracteres A-Z/0-9, em maiúsculas** — numérico ou alfanumérico (formato
 * da Receita a partir de 2026). Letra acentuada não entra (o [filterCnpjInput] antigo deixava).
 */
object CnpjInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() = filterKeepingCursor(14, ::asciiAlnumUpper)
}

/** Exibição `00.000.000/0000-00` (a mesma do [CnpjVisualTransformation]). */
object CnpjOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length == 0 || !asCharSequence().all { it in 'A'..'Z' || it in '0'..'9' }) return
        insertSeparators(2 to ".", 5 to ".", 8 to "/", 12 to "-")
    }
}

// --- Data ----------------------------------------------------------------------------------------

/** Data no estado: **só algarismos, no máximo 8** (`DDMMAAAA`, o mesmo de [filterDateInput]). */
object DateInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() = filterKeepingCursor(8, ::digitOnly)
}

/** Exibição `DD/MM/AAAA` — a barra aparece quando o algarismo seguinte chega (como no antigo). */
object DateOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length == 0 || !asCharSequence().all { it in '0'..'9' }) return
        insertSeparators(2 to "/", 4 to "/")
    }
}

// --- Placa ---------------------------------------------------------------------------------------

/** Placa no estado: **até 7 caracteres A-Z/0-9, em maiúsculas** — a forma de [normalizePlate]. */
object PlateInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() = filterKeepingCursor(7, ::asciiAlnumUpper)
}

/**
 * Exibição pela 5ª posição, como o [PlateVisualTransformation]: antiga (`AAA0000`) com hífen
 * (`AAA-0000`); Mercosul (`AAA0A00`) e entrada parcial sem separador.
 */
object PlateOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val raw = asCharSequence()
        if (raw.length > 4 && raw[4] in '0'..'9' && raw.all { it in 'A'..'Z' || it in '0'..'9' }) {
            insert(3, "-")
        }
    }
}

// --- CREFITO -------------------------------------------------------------------------------------

/**
 * CREFITO no estado: até 6 algarismos e, **só depois do 6º**, a letra `F` ou `T` (maiúscula) — a
 * regra de [filterCrefitoInput]. Com os 6 algarismos já digitados, um 7º algarismo é recusado.
 */
object CrefitoInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val proposed = asCharSequence().toString()
        val originalDigits = originalText.count { it in '0'..'9' }
        val proposedDigits = proposed.count { it in '0'..'9' }
        if (proposedDigits > 6 && originalDigits >= 6 && originalSelection.collapsed) {
            revertAllChanges()
            return
        }
        val accepted = filterCrefitoInput(proposed)
        if (accepted == proposed) return
        val cursorEnd = selection.end
        val cursor = if (cursorEnd >= proposed.length) {
            accepted.length
        } else {
            proposed.take(cursorEnd).count { it in '0'..'9' }.coerceAtMost(accepted.length)
        }
        replace(0, length, accepted)
        selection = TextRange(cursor)
    }
}

/** Exibição `000000-F` (a mesma do [CrefitoVisualTransformation]). */
object CrefitoOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length > 6) insert(6, "-")
    }
}
