package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Mascara para numero CREFITO: 6 digitos + letra (F ou T).
 *
 * "123456F" -> "123456-F"
 */
class CrefitoVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = if (raw.length > 6) raw.substring(0, 6) + "-" + raw.substring(6) else raw

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 6) offset else offset + 1

            override fun transformedToOriginal(offset: Int): Int =
                if (offset <= 6) offset else offset - 1
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Mantem apenas digitos (max 6) e uma letra final F ou T (maiuscula).
 */
fun filterCrefitoInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(6)
    val letter = raw.uppercase().lastOrNull { it == 'F' || it == 'T' }
    return if (digits.length == 6 && letter != null) "$digits$letter" else digits
}
