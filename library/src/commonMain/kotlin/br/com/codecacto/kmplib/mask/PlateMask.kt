package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Comprimento máximo de uma placa brasileira (sem separadores).
 */
private const val PLATE_MAX_LENGTH = 7

/**
 * Normaliza uma placa brasileira para a forma canônica usada em armazenamento/comparação:
 * - converte para maiúsculas;
 * - remove traços, espaços e qualquer caractere não alfanumérico;
 * - limita a no máximo 7 caracteres.
 *
 * Não valida o padrão — apenas higieniza a entrada. Use [isValidPlate] para validar.
 *
 * ```kotlin
 * normalizePlate("abc-1d23") // "ABC1D23"
 * normalizePlate("abc 1234") // "ABC1234"
 * ```
 */
fun normalizePlate(raw: String): String =
    raw.uppercase()
        .filter { it in 'A'..'Z' || it in '0'..'9' }
        .take(PLATE_MAX_LENGTH)

/**
 * Valida uma placa brasileira nos dois padrões vigentes:
 * - **Antiga**: `AAA0000` (3 letras + 4 dígitos).
 * - **Mercosul**: `AAA0A00` (3 letras + 1 dígito + 1 letra + 2 dígitos).
 *
 * A entrada é normalizada antes da validação, então aceita variações com
 * traços, espaços e minúsculas (ex.: "abc-1d23").
 *
 * ```kotlin
 * isValidPlate("ABC1234")  // true  (antiga)
 * isValidPlate("ABC1D23")  // true  (Mercosul)
 * isValidPlate("ABC12D3")  // false
 * ```
 */
fun isValidPlate(plate: String): Boolean {
    val p = normalizePlate(plate)
    if (p.length != PLATE_MAX_LENGTH) return false

    // Posições 0..2 sempre letras.
    if (!(p[0].isLetter() && p[1].isLetter() && p[2].isLetter())) return false
    // Posição 3 sempre dígito.
    if (!p[3].isDigit()) return false

    // Posição 4 discrimina o padrão:
    //  - dígito  -> antiga  (AAA0000): posições 5 e 6 também dígitos.
    //  - letra   -> Mercosul (AAA0A00): posições 5 e 6 dígitos.
    return when {
        p[4].isDigit() -> p[5].isDigit() && p[6].isDigit()
        p[4].isLetter() -> p[5].isDigit() && p[6].isDigit()
        else -> false
    }
}

/**
 * [VisualTransformation] do Compose para placas brasileiras.
 *
 * Formata a entrada conforme o padrão detectado pelo 5º caractere (índice 4):
 * - **antiga** (`AAA0000`) exibida como `AAA-0000`;
 * - **Mercosul** (`AAA0A00`) exibida como `AAA0A00` (sem traço, conforme padrão Mercosul).
 *
 * Enquanto o padrão ainda não pode ser determinado (≤ 4 caracteres), exibe a
 * entrada crua em maiúsculas. A formatação é puramente visual; o valor real do
 * estado deve ser mantido normalizado via [normalizePlate].
 *
 * ```kotlin
 * TextField(
 *     value = placa,
 *     onValueChange = { placa = normalizePlate(it) },
 *     visualTransformation = PlateVisualTransformation()
 * )
 * ```
 */
class PlateVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = normalizePlate(text.text)
        val formatted = formatPlate(raw)
        return TransformedText(
            AnnotatedString(formatted),
            PlateOffsetMapping(raw, formatted)
        )
    }

    private fun formatPlate(raw: String): String {
        if (raw.isEmpty()) return ""

        // Detecta padrão antigo: 5º caractere (índice 4) é dígito.
        val isOldPattern = raw.length > 4 && raw[4].isDigit()

        return if (isOldPattern) {
            // AAA-0000 (traço após o 3º caractere).
            buildString {
                append(raw.take(3))
                if (raw.length > 3) {
                    append('-')
                    append(raw.substring(3))
                }
            }
        } else {
            // Mercosul (AAA0A00) ou entrada parcial: sem separadores.
            raw
        }
    }

    private class PlateOffsetMapping(
        private val original: String,
        private val formatted: String
    ) : OffsetMapping {

        override fun originalToTransformed(offset: Int): Int {
            var transformedOffset = 0
            var originalCount = 0
            for (char in formatted) {
                if (originalCount >= offset) break
                transformedOffset++
                if (char != '-') originalCount++
            }
            return transformedOffset
        }

        override fun transformedToOriginal(offset: Int): Int {
            var originalOffset = 0
            for (i in 0 until minOf(offset, formatted.length)) {
                if (formatted[i] != '-') originalOffset++
            }
            return minOf(originalOffset, original.length)
        }
    }
}
